/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionnedOpenFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Responsible to manage all analyses scheduling
 *
 */
public class AnalysisScheduler implements WorkspaceSettingsChangeListener {

  private static final AnalysisTask EMPTY_FINISHED_ANALYSIS_TASK = new AnalysisTask(Set.of(), false).setFinished(true);
  private static final int DEFAULT_TIMER_MS = 2000;
  private static final int QUEUE_POLLING_PERIOD_MS = 200;

  static final String SONARLINT_SOURCE = "sonarlint";
  public static final String SONARQUBE_TAINT_SOURCE = "SonarQube Taint Analyzer";

  public static final String ITEM_LOCATION = "location";
  public static final String ITEM_FLOW = "flow";

  private final OpenFilesCache openFilesCache;
  // entries in this map mean that the file is "dirty"
  private final Map<URI, Long> eventMap = new ConcurrentHashMap<>();

  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ProjectBindingManager bindingManager;
  private final EventWatcher watcher;
  private final LanguageClientLogger lsLogOutput;
  private final AnalysisTaskExecutor analysisTaskExecutor;

  private final ExecutorService asyncExecutor;

  public AnalysisScheduler(LanguageClientLogger lsLogOutput, WorkspaceFoldersManager workspaceFoldersManager, ProjectBindingManager bindingManager, OpenFilesCache openFilesCache,
    AnalysisTaskExecutor analysisTaskExecutor) {
    this(lsLogOutput, workspaceFoldersManager, bindingManager, openFilesCache, analysisTaskExecutor, DEFAULT_TIMER_MS);
  }

  AnalysisScheduler(LanguageClientLogger lsLogOutput, WorkspaceFoldersManager workspaceFoldersManager, ProjectBindingManager bindingManager, OpenFilesCache openFilesCache,
    AnalysisTaskExecutor analysisTaskExecutor, int defaultTimerMs) {
    this.lsLogOutput = lsLogOutput;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.bindingManager = bindingManager;
    this.openFilesCache = openFilesCache;
    this.analysisTaskExecutor = analysisTaskExecutor;
    this.asyncExecutor = Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint Language Server Analysis Scheduler", false));
    this.watcher = new EventWatcher(defaultTimerMs);
  }

  public void didOpen(VersionnedOpenFile file) {
    analyzeAsync(List.of(file), true);
  }

  public void didChange(URI fileUri) {
    eventMap.put(fileUri, System.currentTimeMillis());
  }

  private class EventWatcher extends Thread {
    private AnalysisTask onChangeCurrentTask = EMPTY_FINISHED_ANALYSIS_TASK;
    private boolean stop = false;
    private final int defaultTimerMs;

    EventWatcher(int defaultTimerMs) {
      this.defaultTimerMs = defaultTimerMs;
      this.setDaemon(true);
      this.setName("sonarlint-auto-trigger");
    }

    public void stopWatcher() {
      stop = true;
      onChangeCurrentTask.cancel();
      this.interrupt();
    }

    @Override
    public void run() {
      while (!stop) {
        checkTimers();
        try {
          Thread.sleep(QUEUE_POLLING_PERIOD_MS);
        } catch (InterruptedException e) {
          // continue until stop flag is set
        }
      }
    }

    private void checkTimers() {
      var now = System.currentTimeMillis();

      var it = eventMap.entrySet().iterator();
      var filesToTrigger = new ArrayList<VersionnedOpenFile>();
      while (it.hasNext()) {
        var e = it.next();
        if (e.getValue() + defaultTimerMs < now) {
          openFilesCache.getFile(e.getKey()).ifPresent(filesToTrigger::add);
        }
      }
      triggerFiles(filesToTrigger);
    }

    private void triggerFiles(List<VersionnedOpenFile> filesToTrigger) {
      if (!filesToTrigger.isEmpty()) {
        if (!onChangeCurrentTask.isFinished()) {
          lsLogOutput.debug("Attempt to cancel previous analysis...");
          onChangeCurrentTask.cancel();
          // Wait for the next loop of EventWatcher to recheck if task has been successfully cancelled and then trigger the analysis
          return;
        }
        filesToTrigger.forEach(f -> eventMap.remove(f.getUri()));
        onChangeCurrentTask = analyzeAsync(filesToTrigger, false);
      }
    }
  }

  public void didClose(URI fileUri) {
    eventMap.remove(fileUri);
  }

  /**
   * Handle analysis asynchronously to not block client events for too long
   */
  AnalysisTask analyzeAsync(List<VersionnedOpenFile> files, boolean shouldFetchServerIssues) {
    var trueFileUris = files.stream().filter(f -> {
      if (!f.getUri().getScheme().equalsIgnoreCase("file")) {
        lsLogOutput.warn(format("URI '%s' is not in local filesystem, analysis not supported", f.getUri()));
        return false;
      }
      return true;
    }).collect(toSet());
    if (trueFileUris.isEmpty()) {
      return EMPTY_FINISHED_ANALYSIS_TASK;
    }
    if (trueFileUris.size() == 1) {
      VersionnedOpenFile openFile = trueFileUris.iterator().next();
      lsLogOutput.debug(format("Queuing analysis of file '%s' (version %d)", openFile.getUri(), openFile.getVersion()));
    } else {
      lsLogOutput.debug(format("Queuing analysis of %d files", trueFileUris.size()));
    }
    var task = new AnalysisTask(trueFileUris, shouldFetchServerIssues);
    asyncExecutor.execute(() -> analysisTaskExecutor.run(task));
    return task;
  }

  public void initialize() {
    watcher.start();
  }

  public void shutdown() {
    watcher.stopWatcher();
    eventMap.clear();
    asyncExecutor.shutdownNow();
  }

  public void analyzeAllOpenFilesInFolder(@Nullable WorkspaceFolderWrapper folder) {
    var openedFileUrisInFolder = openFilesCache.getAll().stream()
      .filter(f -> belongToFolder(folder, f.getUri()))
      .collect(Collectors.toList());
    analyzeAsync(openedFileUrisInFolder, false);
  }

  private boolean belongToFolder(WorkspaceFolderWrapper folder, URI fileUri) {
    var actualFolder = workspaceFoldersManager.findFolderForFile(fileUri);
    return (actualFolder.map(f -> f.equals(folder)).orElse(folder == null));
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    if (oldValue == null) {
      return;
    }
    if (!Objects.equals(oldValue.getExcludedRules(), newValue.getExcludedRules()) ||
      !Objects.equals(oldValue.getIncludedRules(), newValue.getIncludedRules()) ||
      !Objects.equals(oldValue.getRuleParameters(), newValue.getRuleParameters())) {
      analyzeAllUnboundOpenFiles();
    }
  }

  private void analyzeAllUnboundOpenFiles() {
    var openedUnboundFileUris = openFilesCache.getAll().stream()
      .filter(f -> bindingManager.getBinding(f.getUri()).isEmpty())
      .collect(Collectors.toList());
    analyzeAsync(openedUnboundFileUris, false);
  }

  private void analyzeAllOpenJavaFiles() {
    var openedJavaFileUris = openFilesCache.getAll().stream()
      .filter(VersionnedOpenFile::isJava)
      .collect(toList());
    analyzeAsync(openedJavaFileUris, false);
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
