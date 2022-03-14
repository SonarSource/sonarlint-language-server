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
import java.nio.file.Paths;
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
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.FolderFileSystem;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionnedOpenFile;
import org.sonarsource.sonarlint.ls.folders.InFolderClientInputFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderLifecycleListener;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersProvider;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class AnalysisManager implements WorkspaceSettingsChangeListener, WorkspaceFolderLifecycleListener {

  private static final AnalysisTask EMPTY_FINISHED_ANALYSIS_TASK = new AnalysisTask(Set.of(), false).setFinished(true);
  private static final int DEFAULT_TIMER_MS = 2000;
  private static final int QUEUE_POLLING_PERIOD_MS = 200;

  public static final String TYPESCRIPT_PATH_PROP = "sonar.typescript.internal.typescriptLocation";
  static final String SONARLINT_SOURCE = "sonarlint";
  public static final String SONARQUBE_TAINT_SOURCE = "SonarQube Taint Analyzer";

  public static final String ITEM_LOCATION = "location";
  public static final String ITEM_FLOW = "flow";

  private final FileTypeClassifier fileTypeClassifier;
  private final OpenFilesCache openFilesCache;
  private final JavaConfigCache javaConfigCache;
  // entries in this map mean that the file is "dirty"
  private final Map<URI, Long> eventMap = new ConcurrentHashMap<>();

  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ProjectBindingManager bindingManager;
  private final EventWatcher watcher;
  private final LanguageClientLogger lsLogOutput;
  private final StandaloneEngineManager standaloneEngineManager;
  private final AnalysisTaskExecutor analysisTaskExecutor;

  private final ExecutorService analysisExecutor;

  public AnalysisManager(LanguageClientLogger lsLogOutput, StandaloneEngineManager standaloneEngineManager,
    WorkspaceFoldersManager workspaceFoldersManager, ProjectBindingManager bindingManager, FileTypeClassifier fileTypeClassifier,
    OpenFilesCache openFilesCache, JavaConfigCache javaConfigCache,
    AnalysisTaskExecutor analysisTaskExecutor) {
    this.lsLogOutput = lsLogOutput;
    this.standaloneEngineManager = standaloneEngineManager;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.bindingManager = bindingManager;
    this.fileTypeClassifier = fileTypeClassifier;
    this.openFilesCache = openFilesCache;
    this.javaConfigCache = javaConfigCache;
    this.analysisTaskExecutor = analysisTaskExecutor;
    this.analysisExecutor = Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint analysis", false));
    this.watcher = new EventWatcher();
  }

  public void didChangeWatchedFiles(List<FileEvent> changes) {
    changes.forEach(f -> {
      var fileUri = URI.create(f.getUri());
      workspaceFoldersManager.findFolderForFile(fileUri)
        .ifPresent(folder -> {
          var settings = folder.getSettings();
          var baseDir = folder.getRootPath();

          var binding = bindingManager.getBinding(fileUri);

          var engineForFile = binding.isPresent() ? binding.get().getEngine() : standaloneEngineManager.getOrCreateStandaloneEngine();

          var javaConfig = javaConfigCache.getOrFetch(fileUri);
          var inputFile = new InFolderClientInputFile(fileUri, baseDir.relativize(Paths.get(fileUri)).toString(), fileTypeClassifier.isTest(settings, fileUri, javaConfig));

          engineForFile.fireModuleFileEvent(WorkspaceFoldersProvider.key(folder), ClientModuleFileEvent.of(inputFile, translate(f.getType())));
        });
    });
  }

  private static ModuleFileEvent.Type translate(FileChangeType type) {
    switch (type) {
      case Created:
        return ModuleFileEvent.Type.CREATED;
      case Changed:
        return ModuleFileEvent.Type.MODIFIED;
      case Deleted:
        return ModuleFileEvent.Type.DELETED;
    }
    throw new IllegalArgumentException("Unknown event type: " + type);
  }

  public void didOpen(VersionnedOpenFile file) {
    analyzeAsync(List.of(file), true);
  }

  public void didChange(URI fileUri) {
    eventMap.put(fileUri, System.currentTimeMillis());
  }

  private SonarLintEngine findEngineFor(WorkspaceFolderWrapper folder) {
    return bindingManager.getBinding(folder)
      .map(ProjectBindingWrapper::getEngine)
      .map(SonarLintEngine.class::cast)
      .orElseGet(standaloneEngineManager::getOrCreateStandaloneEngine);
  }

  @Override
  public void added(WorkspaceFolderWrapper addedFolder) {
    analysisExecutor.execute(() -> {
      var folderFileSystem = new FolderFileSystem(addedFolder, javaConfigCache, fileTypeClassifier);
      findEngineFor(addedFolder).declareModule(new ClientModuleInfo(WorkspaceFoldersProvider.key(addedFolder), folderFileSystem));
    });
  }

  @Override
  public void removed(WorkspaceFolderWrapper removedFolder) {
    analysisExecutor.execute(() -> findEngineFor(removedFolder).stopModule(WorkspaceFoldersProvider.key(removedFolder)));
  }

  private class EventWatcher extends Thread {
    private AnalysisTask onChangeCurrentTask = EMPTY_FINISHED_ANALYSIS_TASK;
    private boolean stop = false;

    EventWatcher() {
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
        if (e.getValue() + DEFAULT_TIMER_MS < now) {
          openFilesCache.getFile(e.getKey()).ifPresent(filesToTrigger::add);
        }
      }
      triggerFiles(filesToTrigger);
    }

    private void triggerFiles(List<VersionnedOpenFile> filesToTrigger) {
      if (!filesToTrigger.isEmpty()) {
        if (!onChangeCurrentTask.isFinished()) {
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

  public void didSave(VersionnedOpenFile file) {
    analyzeAsync(List.of(file), false);
  }

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
      lsLogOutput.debug(format("Queuing analysis of file '%s'", trueFileUris.iterator().next().getUri()));
    } else {
      lsLogOutput.debug(format("Queuing analysis of %d files", trueFileUris.size()));
    }
    var task = new AnalysisTask(trueFileUris, shouldFetchServerIssues);
    analysisExecutor.execute(() -> analysisTaskExecutor.run(task));
    return task;
  }

  public void initialize() {
    watcher.start();
  }

  public void shutdown() {
    watcher.stopWatcher();
    eventMap.clear();
    analysisExecutor.shutdownNow();
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
