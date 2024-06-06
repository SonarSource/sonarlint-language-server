/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.progress.CanceledException;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.GetJavaConfigResponse;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.notebooks.NotebookDiagnosticPublisher;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.progress.ProgressFacade;
import org.sonarsource.sonarlint.ls.progress.ProgressManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.sonarsource.sonarlint.ls.backend.BackendServiceFacade.ROOT_CONFIGURATION_SCOPE;
import static org.sonarsource.sonarlint.ls.util.Utils.pluralize;

public class AnalysisTaskExecutor {

  private final ScmIgnoredCache filesIgnoredByScmCache;
  private final LanguageClientLogger clientLogger;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ProjectBindingManager bindingManager;
  private final JavaConfigCache javaConfigCache;
  private final SettingsManager settingsManager;
  private final IssuesCache issuesCache;
  private final HotspotsCache securityHotspotsCache;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final DiagnosticPublisher diagnosticPublisher;
  private final SonarLintExtendedLanguageClient lsClient;
  private final OpenNotebooksCache openNotebooksCache;
  private final NotebookDiagnosticPublisher notebookDiagnosticPublisher;
  private final ProgressManager progressManager;
  private final BackendServiceFacade backendServiceFacade;

  public AnalysisTaskExecutor(ScmIgnoredCache filesIgnoredByScmCache, LanguageClientLogger clientLogger,
    WorkspaceFoldersManager workspaceFoldersManager, ProjectBindingManager bindingManager, JavaConfigCache javaConfigCache, SettingsManager settingsManager,
    IssuesCache issuesCache, HotspotsCache securityHotspotsCache, TaintVulnerabilitiesCache taintVulnerabilitiesCache, DiagnosticPublisher diagnosticPublisher,
    SonarLintExtendedLanguageClient lsClient, OpenNotebooksCache openNotebooksCache, NotebookDiagnosticPublisher notebookDiagnosticPublisher,
    ProgressManager progressManager, BackendServiceFacade backendServiceFacade) {
    this.filesIgnoredByScmCache = filesIgnoredByScmCache;
    this.clientLogger = clientLogger;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.bindingManager = bindingManager;
    this.javaConfigCache = javaConfigCache;
    this.settingsManager = settingsManager;
    this.issuesCache = issuesCache;
    this.securityHotspotsCache = securityHotspotsCache;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.diagnosticPublisher = diagnosticPublisher;
    this.lsClient = lsClient;
    this.openNotebooksCache = openNotebooksCache;
    this.notebookDiagnosticPublisher = notebookDiagnosticPublisher;
    this.progressManager = progressManager;
    this.backendServiceFacade = backendServiceFacade;
  }

  public void run(AnalysisTask task) {
    try {
      task.checkCanceled();
      analyze(task);
    } catch (CanceledException e) {
      clientLogger.debug("Analysis canceled");
    } catch (Exception e) {
      clientLogger.errorWithStackTrace("Analysis failed", e);
    }
  }

  private void analyze(AnalysisTask task) {
    var filesToAnalyze = task.getFilesToAnalyze().stream().collect(Collectors.toMap(VersionedOpenFile::getUri, identity()));

    if (!task.shouldKeepHotspotsOnly()) {
      //
      // If the task is a "scan for hotspots", submitted files are already checked for SCM ignore status on client side
      //
      var scmIgnored = filesToAnalyze.keySet().stream()
        .filter(this::scmIgnored)
        .collect(toSet());

      scmIgnored.forEach(f -> {
        clientLogger.debug(format("Skip analysis for SCM ignored file: \"%s\"", f));
        clearIssueCacheAndPublishEmptyDiagnostics(f);
        filesToAnalyze.remove(f);
      });
    }

    var filesToAnalyzePerFolder = filesToAnalyze.entrySet().stream()
      .collect(groupingBy(entry -> workspaceFoldersManager.findFolderForFile(entry.getKey()), mapping(Entry::getValue, toMap(VersionedOpenFile::getUri, identity()))));
    filesToAnalyzePerFolder.forEach((folder, filesToAnalyzeInFolder) -> analyze(task, folder, filesToAnalyzeInFolder));
  }

  private boolean scmIgnored(URI fileUri) {
    var isIgnored = filesIgnoredByScmCache.isIgnored(fileUri).orElse(false);
    return Boolean.TRUE.equals(isIgnored);
  }

  private void clearIssueCacheAndPublishEmptyDiagnostics(URI f) {
    issuesCache.clear(f);
    securityHotspotsCache.clear(f);
    diagnosticPublisher.publishDiagnostics(f, false);
  }

  private void analyze(AnalysisTask task, Optional<WorkspaceFolderWrapper> workspaceFolder, Map<URI, VersionedOpenFile> filesToAnalyze) {
    if (workspaceFolder.isPresent()) {

      var notebooksToAnalyze = new HashMap<URI, VersionedOpenFile>();
      var nonNotebooksToAnalyze = new HashMap<URI, VersionedOpenFile>();
      filesToAnalyze.forEach((uri, file) -> {
        if (openNotebooksCache.isNotebook(uri)) {
          notebooksToAnalyze.put(uri, file);
        } else {
          nonNotebooksToAnalyze.put(uri, file);
        }
      });

      // Notebooks must be analyzed without a binding
      analyze(task, workspaceFolder, Optional.empty(), notebooksToAnalyze);

      // All other files are analyzed with the binding configured for the folder
      var binding = bindingManager.getBinding(workspaceFolder.get());
      analyze(task, workspaceFolder, binding, nonNotebooksToAnalyze);
    } else {
      // Files outside a folder can possibly have a different binding, so fork one analysis per binding
      // TODO is it really possible to have different settings (=binding) for files outside workspace folder
      filesToAnalyze.entrySet().stream()
        .collect(groupingBy(entry -> bindingManager.getBinding(entry.getKey()), mapping(Entry::getValue, toMap(VersionedOpenFile::getUri, identity()))))
        .forEach((binding, files) -> analyze(task, Optional.empty(), binding, files));
    }
  }

  private void analyze(AnalysisTask task, Optional<WorkspaceFolderWrapper> workspaceFolder, Optional<ProjectBinding> binding, Map<URI, VersionedOpenFile> filesToAnalyze) {
    Map<Boolean, Map<URI, VersionedOpenFile>> splitJavaAndNonJavaFiles = filesToAnalyze.entrySet().stream().collect(partitioningBy(
      entry -> entry.getValue().isJava(),
      toMap(Entry::getKey, Entry::getValue)));
    Map<URI, VersionedOpenFile> javaFiles = ofNullable(splitJavaAndNonJavaFiles.get(true)).orElse(Map.of());
    Map<URI, VersionedOpenFile> nonJavaFiles = ofNullable(splitJavaAndNonJavaFiles.get(false)).orElse(Map.of());

    Map<URI, GetJavaConfigResponse> javaFilesWithConfig = collectJavaFilesWithConfig(javaFiles);
    var javaFilesWithoutConfig = javaFiles.entrySet()
      .stream().filter(it -> !javaFilesWithConfig.containsKey(it.getKey()))
      .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    nonJavaFiles.putAll(javaFilesWithoutConfig);
    var settings = workspaceFolder.map(WorkspaceFolderWrapper::getSettings)
      .orElse(settingsManager.getCurrentDefaultFolderSettings());

    nonJavaFiles = excludeCAndCppFilesIfMissingCompilationDatabase(nonJavaFiles, settings);

    if (nonJavaFiles.isEmpty() && javaFilesWithConfig.isEmpty()) {
      return;
    }

    // We need to run one separate analysis per Java module. Analyze non Java files with the first Java module, if any
    Map<String, Set<URI>> javaFilesByProjectRoot = javaFilesWithConfig.entrySet().stream()
      .collect(groupingBy(e -> e.getValue().getProjectRoot(), mapping(Entry::getKey, toSet())));
    if (javaFilesByProjectRoot.isEmpty()) {
      analyzeSingleModule(task, workspaceFolder, settings, binding, nonJavaFiles, javaFilesWithConfig);
    } else {
      var isFirst = true;
      for (var javaFilesForSingleProjectRoot : javaFilesByProjectRoot.values()) {
        Map<URI, VersionedOpenFile> toAnalyze = new HashMap<>();
        javaFilesForSingleProjectRoot.forEach(uri -> toAnalyze.put(uri, javaFiles.get(uri)));
        if (isFirst) {
          toAnalyze.putAll(nonJavaFiles);
          analyzeSingleModule(task, workspaceFolder, settings, binding, toAnalyze, javaFilesWithConfig);
        } else {
          analyzeSingleModule(task, workspaceFolder, settings, binding, toAnalyze, javaFilesWithConfig);
        }
        isFirst = false;
      }
    }
  }

  private Map<URI, VersionedOpenFile> excludeCAndCppFilesIfMissingCompilationDatabase(Map<URI, VersionedOpenFile> nonJavaFiles, WorkspaceFolderSettings settings) {
    Map<Boolean, Map<URI, VersionedOpenFile>> splitCppAndNonCppFiles = nonJavaFiles.entrySet().stream().collect(partitioningBy(
      entry -> entry.getValue().isCOrCpp(),
      toMap(Entry::getKey, Entry::getValue)));
    Map<URI, VersionedOpenFile> cOrCppFiles = ofNullable(splitCppAndNonCppFiles.get(true)).orElse(Map.of());
    Map<URI, VersionedOpenFile> nonCNOrCppFiles = ofNullable(splitCppAndNonCppFiles.get(false)).orElse(Map.of());
    if (!cOrCppFiles.isEmpty() && (settings.getPathToCompileCommands() == null || !Files.isRegularFile(Paths.get(settings.getPathToCompileCommands())))) {
      if (settings.getPathToCompileCommands() == null) {
        clientLogger.debug("Skipping analysis of C and C++ file(s) because no compilation database was configured");
      } else {
        clientLogger.debug("Skipping analysis of C and C++ file(s) because configured compilation database does not exist: " + settings.getPathToCompileCommands());
      }
      cOrCppFiles.keySet().forEach(this::clearIssueCacheAndPublishEmptyDiagnostics);
      lsClient.needCompilationDatabase();
      return nonCNOrCppFiles;
    }
    return nonJavaFiles;
  }

  private Map<URI, GetJavaConfigResponse> collectJavaFilesWithConfig(Map<URI, VersionedOpenFile> javaFiles) {
    Map<URI, GetJavaConfigResponse> javaFilesWithConfig = new HashMap<>();
    javaFiles.forEach((uri, openFile) -> {
      var javaConfigOpt = javaConfigCache.getOrFetch(uri);
      if (javaConfigOpt.isEmpty()) {
        clientLogger.debug(format("Analysis of Java file \"%s\" may not show all issues because SonarLint" +
          " was unable to query project configuration (classpath, source level, ...)", uri));
        clearIssueCacheAndPublishEmptyDiagnostics(uri);
      } else {
        javaFilesWithConfig.put(uri, javaConfigOpt.get());
      }
    });
    return javaFilesWithConfig;
  }

  /**
   * Here we have only files from the same folder, same binding, same Java module, so we can run the analysis engine.
   */
  private void analyzeSingleModule(AnalysisTask task, Optional<WorkspaceFolderWrapper> workspaceFolder, WorkspaceFolderSettings settings, Optional<ProjectBinding> binding,
    Map<URI, VersionedOpenFile> filesToAnalyze,
    Map<URI, GetJavaConfigResponse> javaConfigs) {

    var folderUri = workspaceFolder.map(WorkspaceFolderWrapper::getUri).orElse(null);

    if (task.shouldShowProgress()) {
      progressManager.doWithProgress(String.format("SonarLint scanning %d files for hotspots", task.getFilesToAnalyze().size()), null, () -> {
        },
        progressFacade -> analyzeSingleModuleNonExcluded(task, settings, binding, filesToAnalyze, folderUri, javaConfigs, progressFacade));
    } else {
      analyzeSingleModuleNonExcluded(task, settings, binding, filesToAnalyze, folderUri, javaConfigs, null);
    }

  }

  private void analyzeSingleModuleNonExcluded(AnalysisTask task, WorkspaceFolderSettings settings, Optional<ProjectBinding> binding,
    Map<URI, VersionedOpenFile> filesToAnalyze, @Nullable URI folderUri, Map<URI, GetJavaConfigResponse> javaConfigs, @Nullable ProgressFacade progressFacade) {
    checkCanceled(task, progressFacade);
    if (filesToAnalyze.size() == 1) {
      clientLogger.info(format("Analyzing file \"%s\"...", filesToAnalyze.keySet().iterator().next()));
    } else {
      clientLogger.info(format("Analyzing %d files...", filesToAnalyze.size()));
    }

    filesToAnalyze.forEach((fileUri, openFile) -> {
      if (!task.shouldKeepHotspotsOnly()) {
        issuesCache.analysisStarted(openFile);
      }
      securityHotspotsCache.analysisStarted(openFile);
      notebookDiagnosticPublisher.cleanupCellsList(fileUri);
      if (binding.isEmpty()) {
        // Clear taint vulnerabilities if the folder was previously bound and just now changed to standalone
        taintVulnerabilitiesCache.clear(fileUri);
      }
    });

    analyzeAndTrack(task, settings, folderUri, filesToAnalyze, javaConfigs);
  }

  private static void checkCanceled(AnalysisTask task, @Nullable ProgressFacade progressFacade) {
    task.checkCanceled();
    if (progressFacade != null) {
      progressFacade.checkCanceled();
    }
  }

  public void handleIssues(Map<URI, List<RaisedFindingDto>> issuesByFileUri) {
    var totalIssueCount = new AtomicInteger();
    issuesCache.reportIssues(issuesByFileUri);
    issuesByFileUri.forEach((uri, issues) -> {
      var foundIssues = issuesCache.count(uri);
      totalIssueCount.addAndGet(foundIssues);
      diagnosticPublisher.publishDiagnostics(uri, true);
      notebookDiagnosticPublisher.cleanupDiagnosticsForCellsWithoutIssues(uri);
      openNotebooksCache.getFile(uri).ifPresent(notebook -> notebookDiagnosticPublisher.publishNotebookDiagnostics(uri, notebook));
    });
    clientLogger.info(format("Found %s %s", totalIssueCount.get(), pluralize(totalIssueCount.get(), "issue")));
  }

  public void handleHotspots(Map<URI, List<RaisedHotspotDto>> hotspotsByFileUri) {
    var totalHotspotCount = new AtomicInteger();
    securityHotspotsCache.reportHotspots(hotspotsByFileUri);
    hotspotsByFileUri.forEach((uri, issues) -> {
      totalHotspotCount.addAndGet(securityHotspotsCache.count(uri));
      diagnosticPublisher.publishHotspots(uri);
      notebookDiagnosticPublisher.cleanupDiagnosticsForCellsWithoutIssues(uri);
      openNotebooksCache.getFile(uri).ifPresent(notebook -> notebookDiagnosticPublisher.publishNotebookDiagnostics(uri, notebook));
    });
    clientLogger.info(format("Found %s %s", totalHotspotCount.get(), pluralize(totalHotspotCount.get(), "security hotspot")));
  }

  private void analyzeAndTrack(AnalysisTask task, WorkspaceFolderSettings settings, @Nullable URI folderUri, Map<URI, VersionedOpenFile> filesToAnalyze,
    Map<URI, GetJavaConfigResponse> javaConfigs) {

    var extraProperties = buildExtraPropertiesMap(settings, filesToAnalyze, javaConfigs);

    backendServiceFacade.getBackendService().analyzeFilesAndTrack(folderUri != null ? folderUri.toString() : ROOT_CONFIGURATION_SCOPE, task.getAnalysisId(),
      filesToAnalyze.keySet().stream().toList(), extraProperties, true).join();
  }

  private Map<String, String> buildExtraPropertiesMap(WorkspaceFolderSettings settings, Map<URI, VersionedOpenFile> filesToAnalyze, Map<URI, GetJavaConfigResponse> javaConfigs) {
    var extraProperties = new HashMap<String, String>();
    extraProperties.putAll(settings.getAnalyzerProperties());
    extraProperties.putAll(javaConfigCache.configureJavaProperties(filesToAnalyze.keySet(), javaConfigs));

    var pathToCompileCommands = settings.getPathToCompileCommands();
    if (pathToCompileCommands != null) {
      extraProperties.put("sonar.cfamily.compile-commands", pathToCompileCommands);
    }
    return extraProperties;
  }
}
