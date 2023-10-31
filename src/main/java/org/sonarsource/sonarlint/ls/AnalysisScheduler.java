/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.ls;

import com.google.common.annotations.VisibleForTesting;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.notebooks.VersionedOpenNotebook;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;

/**
 * Responsible to manage all analyses scheduling
 */
public class AnalysisScheduler implements WorkspaceSettingsChangeListener, WorkspaceFolderSettingsChangeListener {

  private static final CompletableFuture<Void> COMPLETED_FUTURE = CompletableFuture.completedFuture(null);
  private static final int DEFAULT_TIMER_MS = 2000;

  static final String SONARLINT_SOURCE = "sonarlint";
  public static final String SONARQUBE_TAINT_SOURCE = "Latest SonarQube Analysis";
  public static final String SONARCLOUD_TAINT_SOURCE = "Latest SonarCloud Analysis";

  public static final String ITEM_LOCATION = "location";
  public static final String ITEM_FLOW = "flow";

  private final OpenFilesCache openFilesCache;
  private final OpenNotebooksCache openNotebooksCache;

  // entries in this set mean that the file is "dirty"
  private final Set<URI> events = new HashSet<>();
  private final AtomicLong oldestEvent = new AtomicLong(Long.MAX_VALUE);

  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ProjectBindingManager bindingManager;
  private final EventWatcher watcher;
  private final LanguageClientLogger lsLogOutput;
  private final AnalysisTaskExecutor analysisTaskExecutor;
  private final SonarLintExtendedLanguageClient client;

  private final ExecutorService asyncExecutor;

  public AnalysisScheduler(LanguageClientLogger lsLogOutput, WorkspaceFoldersManager workspaceFoldersManager, ProjectBindingManager bindingManager, OpenFilesCache openFilesCache,
    OpenNotebooksCache openNotebooksCache, AnalysisTaskExecutor analysisTaskExecutor, SonarLintExtendedLanguageClient client) {
    this(lsLogOutput, workspaceFoldersManager, bindingManager, openFilesCache, openNotebooksCache, analysisTaskExecutor, DEFAULT_TIMER_MS, client);
  }

  @VisibleForTesting
  AnalysisScheduler(LanguageClientLogger lsLogOutput, WorkspaceFoldersManager workspaceFoldersManager, ProjectBindingManager bindingManager, OpenFilesCache openFilesCache,
    OpenNotebooksCache openNotebooksCache, AnalysisTaskExecutor analysisTaskExecutor, long analysisTimerMs, SonarLintExtendedLanguageClient client) {
    this.lsLogOutput = lsLogOutput;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.bindingManager = bindingManager;
    this.openFilesCache = openFilesCache;
    this.openNotebooksCache = openNotebooksCache;
    this.analysisTaskExecutor = analysisTaskExecutor;
    this.asyncExecutor = Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint Language Server Analysis Scheduler", false));
    this.watcher = new EventWatcher(analysisTimerMs);
    this.client = client;
  }

  public void didOpen(VersionedOpenFile file) {
    analyzeAsync(AnalysisParams.newAnalysisParams(List.of(file)).withFetchServerIssues());
  }

  public void didChange(URI fileUri) {
    recordEvent(fileUri);
  }

  public void didReceiveHotspotEvent(URI fileUri) {
    recordEvent(fileUri);
  }

  private void recordEvent(URI fileUri) {
    oldestEvent.compareAndSet(Long.MAX_VALUE, System.currentTimeMillis());
    events.add(fileUri);
  }

  private class EventWatcher extends Thread {
    private Future<?> onChangeCurrentTask = COMPLETED_FUTURE;
    private boolean stop = false;
    private final long analysisTimerMs;

    EventWatcher(long analysisTimerMs) {
      this.analysisTimerMs = analysisTimerMs;
      this.setDaemon(true);
      this.setName("sonarlint-auto-trigger");
    }

    public void stopWatcher() {
      stop = true;
      onChangeCurrentTask.cancel(false);
      this.interrupt();
    }

    @Override
    public void run() {
      while (!stop) {
        checkTimers();
        try {
          Thread.sleep(this.analysisTimerMs / 10);
        } catch (InterruptedException e) {
          // continue until stop flag is set
        }
      }
    }

    private void checkTimers() {
      long now = System.currentTimeMillis();
      long oldestEventTimeMs = oldestEvent.get();
      if (now > oldestEventTimeMs + analysisTimerMs && !events.isEmpty()) {
        var it = events.iterator();
        var filesToTrigger = new ArrayList<VersionedOpenFile>();
        while (it.hasNext()) {
          var e = it.next();
          openFilesCache.getFile(e).ifPresent(filesToTrigger::add);
          openNotebooksCache.getFile(e).ifPresent(notebook -> filesToTrigger.add(notebook.asVersionedOpenFile()));
        }
        triggerFiles(filesToTrigger);
        oldestEvent.set(Long.MAX_VALUE);
      }
    }

    private void triggerFiles(List<VersionedOpenFile> filesToTrigger) {
      if (!filesToTrigger.isEmpty()) {
        if (!onChangeCurrentTask.isDone()) {
          lsLogOutput.debug("Attempt to cancel previous analysis...");
          onChangeCurrentTask.cancel(false);
          // Wait for the next loop of EventWatcher to recheck if task has been successfully cancelled and then trigger the analysis
          return;
        }
        filesToTrigger.forEach(f -> events.remove(f.getUri()));
        onChangeCurrentTask = analyzeAsync(AnalysisParams.newAnalysisParams(filesToTrigger));
      }
    }
  }

  public void didClose(URI fileUri) {
    events.remove(fileUri);
  }

  public static class AnalysisParams {
    private final List<VersionedOpenFile> files;
    private final boolean shouldFetchServerIssues;
    private final boolean shouldKeepHotspotsOnly;
    private final boolean shouldShowProgress;

    private AnalysisParams(
      List<VersionedOpenFile> files,
      boolean shouldFetchServerIssues,
      boolean shouldKeepHotspotsOnly,
      boolean shouldShowProgress
    ) {
      this.files = List.copyOf(files);
      this.shouldFetchServerIssues = shouldFetchServerIssues;
      this.shouldKeepHotspotsOnly = shouldKeepHotspotsOnly;
      this.shouldShowProgress = shouldShowProgress;
    }

    static AnalysisParams newAnalysisParams(List<VersionedOpenFile> files) {
      return new AnalysisParams(files, false, false, false);
    }

    AnalysisParams withFetchServerIssues() {
      return new AnalysisParams(files, true, shouldKeepHotspotsOnly, shouldShowProgress);
    }

    AnalysisParams withOnlyHotspots() {
      return new AnalysisParams(files, shouldFetchServerIssues, true, shouldShowProgress);
    }

    AnalysisParams withProgress() {
      return new AnalysisParams(files, shouldFetchServerIssues, shouldKeepHotspotsOnly, true);
    }
  }

  /**
   * Handle analysis asynchronously to not block client events for too long
   */
  Future<?> analyzeAsync(AnalysisParams params) {
    var trueFileUris = params.files.stream().filter(f -> {
      if (!Utils.uriHasFileScheme(f.getUri())) {
        lsLogOutput.warn(format("URI '%s' is not in local filesystem, analysis not supported", f.getUri()));
        return false;
      }
      return true;
    }).collect(toSet());
    if (trueFileUris.isEmpty()) {
      return COMPLETED_FUTURE;
    }
    if (trueFileUris.size() == 1) {
      VersionedOpenFile openFile = trueFileUris.iterator().next();
      lsLogOutput.debug(format("Queuing analysis of file \"%s\" (version %d)", openFile.getUri(), openFile.getVersion()));
    } else {
      lsLogOutput.debug(format("Queuing analysis of %d files", trueFileUris.size()));
    }
    var task = new AnalysisTask(trueFileUris, params.shouldFetchServerIssues, params.shouldKeepHotspotsOnly, params.shouldShowProgress);
    var future = asyncExecutor.submit(() -> analysisTaskExecutor.run(task));
    task.setFuture(future);
    return future;
  }

  public void initialize() {
    watcher.start();
  }

  public void shutdown() {
    watcher.stopWatcher();
    events.clear();
    Utils.shutdownAndAwait(asyncExecutor, true);
  }

  public void analyzeAllOpenFilesInFolder(@Nullable WorkspaceFolderWrapper folder) {
    var openedFileUrisInFolder = openFilesCache.getAll().stream()
      .filter(f -> belongToFolder(folder, f.getUri()))
      .toList();
    analyseNotIgnoredFiles(openedFileUrisInFolder);
  }

  private void analyseNotIgnoredFiles(List<VersionedOpenFile> files) {
    var uriStrings = files.stream().map(it -> it.getUri().toString()).toList();
    var fileUrisParams = new SonarLintExtendedLanguageClient.FileUrisParams(uriStrings);
    client.filterOutExcludedFiles(fileUrisParams)
      .thenAccept(notIgnoredFileUris -> {
        var notIgnoredFiles = files
          .stream().filter(it -> notIgnoredFileUris.getFileUris().contains(it.getUri().toString()))
          .toList();
        analyzeAsync(AnalysisParams.newAnalysisParams(notIgnoredFiles));
      });
  }

  private boolean belongToFolder(WorkspaceFolderWrapper folder, URI fileUri) {
    var actualFolder = workspaceFoldersManager.findFolderForFile(fileUri);
    return (actualFolder.map(f -> f.equals(folder)).orElse(folder == null));
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    if (oldValue == null) {
      // This is when settings are loaded, not really a user change
      return;
    }
    if (!Objects.equals(oldValue.getExcludedRules(), newValue.getExcludedRules()) ||
      !Objects.equals(oldValue.getIncludedRules(), newValue.getIncludedRules()) ||
      !Objects.equals(oldValue.getRuleParameters(), newValue.getRuleParameters())) {
      analyzeAllUnboundOpenFiles();
      analyzeAllOpenNotebooks();
    }
  }

  @Override
  public void onChange(@Nullable WorkspaceFolderWrapper folder, @Nullable WorkspaceFolderSettings oldValue, WorkspaceFolderSettings newValue) {
    if (oldValue == null) {
      // This is when settings are loaded, not really a user change
      return;
    }
    if (!Objects.equals(oldValue.getPathToCompileCommands(), newValue.getPathToCompileCommands())) {
      analyzeAllOpenCOrCppFilesInFolder(folder);
    }
  }

  public void analyzeAllOpenCOrCppFilesInFolder(@Nullable WorkspaceFolderWrapper folder) {
    var openedCorCppFileUrisInFolder = openFilesCache.getAll().stream()
      .filter(VersionedOpenFile::isCOrCpp)
      .filter(f -> belongToFolder(folder, f.getUri()))
      .toList();
    analyseNotIgnoredFiles(openedCorCppFileUrisInFolder);
  }

  public void scanForHotspotsInFiles(List<VersionedOpenFile> files) {
    analyzeAsync(AnalysisParams.newAnalysisParams(files).withOnlyHotspots().withProgress());
  }

  private void analyzeAllUnboundOpenFiles() {
    var openedUnboundFileUris = openFilesCache.getAll().stream()
      .filter(f -> bindingManager.getBinding(f.getUri()).isEmpty())
      .toList();
    analyseNotIgnoredFiles(openedUnboundFileUris);
  }

  private void analyzeAllOpenNotebooks() {
    var openNotebookUris = openNotebooksCache.getAll().stream()
      .map(VersionedOpenNotebook::asVersionedOpenFile)
      .toList();
    analyseNotIgnoredFiles(openNotebookUris);
  }

  private void analyzeAllOpenJavaFiles() {
    var openedJavaFileUris = openFilesCache.getAll().stream()
      .filter(VersionedOpenFile::isJava)
      .toList();
    analyseNotIgnoredFiles(openedJavaFileUris);
  }

  public void didClasspathUpdate() {
    analyzeAllOpenJavaFiles();
  }

  public void didServerModeChange(SonarLintExtendedLanguageServer.ServerMode serverMode) {
    if (serverMode == SonarLintExtendedLanguageServer.ServerMode.STANDARD) {
      analyzeAllOpenJavaFiles();
    }
  }
}
