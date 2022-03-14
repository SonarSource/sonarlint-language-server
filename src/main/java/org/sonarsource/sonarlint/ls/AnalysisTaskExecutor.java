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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.GetJavaConfigResponse;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.VersionnedOpenFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.sonarsource.sonarlint.ls.Utils.pluralize;

public class AnalysisTaskExecutor {

  private final ScmIgnoredCache filesIgnoredByScmCache;
  private final LanguageClientLogger lsLogOutput;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ProjectBindingManager bindingManager;
  private final JavaConfigCache javaConfigCache;
  private final SettingsManager settingsManager;
  private final FileTypeClassifier fileTypeClassifier;
  private final IssuesCache issuesCache;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final SonarLintTelemetry telemetry;
  private final SkippedPluginsNotifier skippedPluginsNotifier;
  private final StandaloneEngineManager standaloneEngineManager;
  private final DiagnosticPublisher diagnosticPublisher;

  public AnalysisTaskExecutor(ScmIgnoredCache filesIgnoredByScmCache, LanguageClientLogger lsLogOutput,
    WorkspaceFoldersManager workspaceFoldersManager, ProjectBindingManager bindingManager, JavaConfigCache javaConfigCache, SettingsManager settingsManager,
    FileTypeClassifier fileTypeClassifier, IssuesCache issuesCache, TaintVulnerabilitiesCache taintVulnerabilitiesCache, SonarLintTelemetry telemetry,
    SkippedPluginsNotifier skippedPluginsNotifier, StandaloneEngineManager standaloneEngineManager, DiagnosticPublisher diagnosticPublisher) {
    this.filesIgnoredByScmCache = filesIgnoredByScmCache;
    this.lsLogOutput = lsLogOutput;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.bindingManager = bindingManager;
    this.javaConfigCache = javaConfigCache;
    this.settingsManager = settingsManager;
    this.fileTypeClassifier = fileTypeClassifier;
    this.issuesCache = issuesCache;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.telemetry = telemetry;
    this.skippedPluginsNotifier = skippedPluginsNotifier;
    this.standaloneEngineManager = standaloneEngineManager;
    this.diagnosticPublisher = diagnosticPublisher;
  }

  public void run(AnalysisTask task) {
    try {
      task.checkCanceled();
      analyze(task);
    } catch (CanceledException e) {
      lsLogOutput.debug("Analysis canceled");
    } finally {
      task.setFinished(true);
    }
  }

  private void analyze(AnalysisTask task) {
    var filesToAnalyze = task.getFilesToAnalyze().stream().collect(Collectors.toMap(VersionnedOpenFile::getUri, f -> f));

    var scmIgnored = filesToAnalyze.keySet().stream()
      .filter(this::scmIgnored)
      .collect(toSet());

    scmIgnored.forEach(f -> {
      lsLogOutput.debug(format("Skip analysis for SCM ignored file: '%s'", f));
      clearIssueCacheAndPublishEmptyDiagnostics(f);
      filesToAnalyze.remove(f);
    });

    var filesToAnalyzePerFolder = filesToAnalyze.entrySet().stream()
      .collect(groupingBy(entry -> workspaceFoldersManager.findFolderForFile(entry.getKey()), mapping(Entry::getValue, toMap(VersionnedOpenFile::getUri, f -> f))));
    filesToAnalyzePerFolder.forEach((folder, filesToAnalyzeInFolder) -> analyze(task, folder, filesToAnalyzeInFolder));
  }

  private boolean scmIgnored(URI fileUri) {
    var isIgnored = filesIgnoredByScmCache.isIgnored(fileUri).orElse(false);
    if (Boolean.TRUE.equals(isIgnored)) {
      lsLogOutput.debug(format("Skip analysis for SCM ignored file: '%s'", fileUri));
      return true;
    }
    return false;
  }

  private void clearIssueCacheAndPublishEmptyDiagnostics(URI f) {
    issuesCache.clear(f);
    diagnosticPublisher.publishDiagnostics(f);
  }

  private void analyze(AnalysisTask task, Optional<WorkspaceFolderWrapper> workspaceFolder, Map<URI, VersionnedOpenFile> filesToAnalyze) {
    if (workspaceFolder.isPresent()) {
      // We can only have the same binding for a given folder
      var binding = bindingManager.getBinding(workspaceFolder.get());
      analyze(task, workspaceFolder, binding, filesToAnalyze);
    } else {
      // Files outside a folder can possibly have a different binding, so fork one analysis per binding
      // TODO is it really possible to have different settings (=binding) for files outside workspace folder
      filesToAnalyze.entrySet().stream()
        .collect(groupingBy(entry -> bindingManager.getBinding(entry.getKey()), mapping(Entry::getValue, toMap(VersionnedOpenFile::getUri, f -> f))))
        .forEach((binding, files) -> analyze(task, Optional.empty(), binding, files));
    }
  }

  private void analyze(AnalysisTask task, Optional<WorkspaceFolderWrapper> workspaceFolder, Optional<ProjectBindingWrapper> binding, Map<URI, VersionnedOpenFile> filesToAnalyze) {
    Map<Boolean, Map<URI, VersionnedOpenFile>> splitJavaAndNonJavaFiles = filesToAnalyze.entrySet().stream().collect(partitioningBy(
      entry -> entry.getValue().isJava(),
      toMap(Entry::getKey, Entry::getValue)));
    Map<URI, VersionnedOpenFile> javaFiles = ofNullable(splitJavaAndNonJavaFiles.get(true)).orElse(Map.of());
    Map<URI, VersionnedOpenFile> nonJavaFiles = ofNullable(splitJavaAndNonJavaFiles.get(false)).orElse(Map.of());

    Map<URI, GetJavaConfigResponse> javaFilesWithConfig = new HashMap<>();
    javaFiles.forEach((uri, openFile) -> {
      var javaConfigOpt = javaConfigCache.getOrFetch(uri);
      if (javaConfigOpt.isEmpty()) {
        lsLogOutput.debug(format("Skipping analysis of Java file '%s' because SonarLint was unable to query project configuration (classpath, source level, ...)", uri));
        clearIssueCacheAndPublishEmptyDiagnostics(uri);
      } else {
        javaFilesWithConfig.put(uri, javaConfigOpt.get());
      }
    });

    if (nonJavaFiles.isEmpty() && javaFilesWithConfig.isEmpty()) {
      return;
    }

    // We need to run one separate analysis per Java module. Analyze non Java files with the first Java module, if any
    Map<String, Set<URI>> javaFilesByProjectRoot = javaFilesWithConfig.entrySet().stream()
      .collect(groupingBy(e -> e.getValue().getProjectRoot(), mapping(Entry::getKey, toSet())));
    if (javaFilesByProjectRoot.isEmpty()) {
      analyzeSingleModule(task, workspaceFolder, binding, nonJavaFiles, javaFilesWithConfig);
    } else {
      var isFirst = true;
      for (var javaFilesForSingleProjectRoot : javaFilesByProjectRoot.values()) {
        Map<URI, VersionnedOpenFile> toAnalyze = new HashMap<>();
        javaFilesForSingleProjectRoot.forEach(uri -> toAnalyze.put(uri, javaFiles.get(uri)));
        if (isFirst) {
          toAnalyze.putAll(nonJavaFiles);
          analyzeSingleModule(task, workspaceFolder, binding, toAnalyze, javaFilesWithConfig);
        } else {
          analyzeSingleModule(task, workspaceFolder, binding, toAnalyze, javaFilesWithConfig);
        }
        isFirst = false;
      }
    }
  }

  /**
   * Here we have only files from the same folder, same binding, same Java module, so we can run the analysis engine.
   */
  private void analyzeSingleModule(AnalysisTask task, Optional<WorkspaceFolderWrapper> workspaceFolder, Optional<ProjectBindingWrapper> binding,
    Map<URI, VersionnedOpenFile> filesToAnalyze,
    Map<URI, GetJavaConfigResponse> javaConfigs) {
    var settings = workspaceFolder.map(WorkspaceFolderWrapper::getSettings)
      .orElse(settingsManager.getCurrentDefaultFolderSettings());

    var baseDirUri = workspaceFolder.map(WorkspaceFolderWrapper::getUri)
      // if files are not part of any workspace folder, take the common ancestor of all files (assume all files will have the same root)
      .orElse(findCommonPrefix(filesToAnalyze.keySet().stream().map(Paths::get).collect(toList())).toUri());

    var nonExcludedFiles = new HashMap<>(filesToAnalyze);
    if (binding.isPresent()) {
      var connectedEngine = binding.get().getEngine();
      var excludedByServerConfiguration = connectedEngine.getExcludedFiles(binding.get().getBinding(),
        filesToAnalyze.keySet(),
        uri -> getFileRelativePath(Paths.get(baseDirUri), uri),
        uri -> fileTypeClassifier.isTest(settings, uri, javaConfigCache.getOrFetch(uri)));
      excludedByServerConfiguration.forEach(f -> {
        lsLogOutput.debug(format("Skip analysis of file '%s' excluded by server configuration", f));
        nonExcludedFiles.remove(f);
        clearIssueCacheAndPublishEmptyDiagnostics(f);
      });
    }

    if (!nonExcludedFiles.isEmpty()) {
      analyzeSingleModuleNonExcluded(task, settings, binding, nonExcludedFiles, baseDirUri, javaConfigs);
    }

  }

  String getFileRelativePath(Path baseDir, URI uri) {
    try {
      return baseDir.relativize(Paths.get(uri)).toString();
    } catch (IllegalArgumentException e) {
      // Possibly the file has not the same root as baseDir
      lsLogOutput.debug("Unable to relativize " + uri + " to " + baseDir);
      return Paths.get(uri).toString();
    }
  }

  private static Path findCommonPrefix(List<Path> paths) {
    Path currentPrefixCandidate = paths.get(0).getParent();
    while (currentPrefixCandidate.getNameCount() > 0 && !isPrefixForAll(currentPrefixCandidate, paths)) {
      currentPrefixCandidate = currentPrefixCandidate.getParent();
    }
    return currentPrefixCandidate;
  }

  private static boolean isPrefixForAll(Path prefixCandidate, Collection<Path> paths) {
    return paths.stream().allMatch(p -> p.startsWith(prefixCandidate));
  }

  private void analyzeSingleModuleNonExcluded(AnalysisTask task, WorkspaceFolderSettings settings, Optional<ProjectBindingWrapper> binding,
    Map<URI, VersionnedOpenFile> filesToAnalyze, URI baseDirUri, Map<URI, GetJavaConfigResponse> javaConfigs) {
    task.checkCanceled();
    if (filesToAnalyze.size() == 1) {
      lsLogOutput.info(format("Analyzing file '%s'...", filesToAnalyze.keySet().iterator().next()));
    } else {
      lsLogOutput.info(format("Analyzing %d files...", filesToAnalyze.size()));
    }

    filesToAnalyze.forEach((fileUri, openFile) -> {
      issuesCache.analysisStarted(openFile);
      if (!binding.isPresent()) {
        // Clear taint vulnerabilities if the folder was previously bound and just now changed to standalone
        taintVulnerabilitiesCache.clear(fileUri);
      }
    });

    var ruleKeys = new HashSet<String>();
    var issueListener = createIssueListener(filesToAnalyze, ruleKeys);

    AnalysisResultsWrapper analysisResults;
    var filesSuccessfullyAnalyzed = new HashSet<>(filesToAnalyze.keySet());
    try {
      if (binding.isPresent()) {
        analysisResults = analyzeConnected(task, binding.get(), settings, baseDirUri, filesToAnalyze, javaConfigs, issueListener);
      } else {
        analysisResults = analyzeStandalone(task, settings, baseDirUri, filesToAnalyze, javaConfigs, issueListener);
      }
      task.checkCanceled();
      skippedPluginsNotifier.notifyOnceForSkippedPlugins(analysisResults.results, analysisResults.allPlugins);

      var analyzedLanguages = analysisResults.results.languagePerFile().values();
      if (!analyzedLanguages.isEmpty()) {
        telemetry.analysisDoneOnSingleLanguage(analyzedLanguages.iterator().next(), analysisResults.analysisTime);
      }

      // Ignore files with parsing error
      analysisResults.results.failedAnalysisFiles().stream()
        .map(ClientInputFile::getClientObject)
        .map(URI.class::cast)
        .forEach(fileUri -> {
          filesSuccessfullyAnalyzed.remove(fileUri);
          issuesCache.analysisFailed(filesToAnalyze.get(fileUri));
        });
    } catch (Exception e) {
      lsLogOutput.error("Analysis failed.", e);
      return;
    }

    var totalIssueCount = new AtomicInteger();
    filesSuccessfullyAnalyzed.forEach(f -> {
      issuesCache.analysisSucceeded(filesToAnalyze.get(f));
      var foundIssues = issuesCache.count(f);
      totalIssueCount.addAndGet(foundIssues);
      diagnosticPublisher.publishDiagnostics(f);
    });
    telemetry.addReportedRules(ruleKeys);
    lsLogOutput.info(format("Found %s %s", totalIssueCount.get(), pluralize(totalIssueCount.get(), "issue")));
  }

  private IssueListener createIssueListener(Map<URI, VersionnedOpenFile> filesToAnalyze, Set<String> ruleKeys) {
    return issue -> {
      var inputFile = issue.getInputFile();
      // FIXME SLVSCODE-255 support project level issues
      if (inputFile != null) {
        URI uri = inputFile.getClientObject();
        var versionnedOpenFile = filesToAnalyze.get(uri);
        issuesCache.reportIssue(versionnedOpenFile, issue);
        ruleKeys.add(issue.getRuleKey());
      }
    };
  }

  private static final class TaskProgressMonitor implements ClientProgressMonitor {
    private final AnalysisTask task;

    private TaskProgressMonitor(AnalysisTask task) {
      this.task = task;
    }

    @Override
    public boolean isCanceled() {
      return task.isCanceled();
    }

    @Override
    public void setMessage(String msg) {
      // No-op
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
      // No-op
    }

    @Override
    public void setFraction(float fraction) {
      // No-op
    }
  }

  static class AnalysisResultsWrapper {
    private final AnalysisResults results;
    private final int analysisTime;
    private final Collection<PluginDetails> allPlugins;

    AnalysisResultsWrapper(AnalysisResults results, int analysisTime, Collection<PluginDetails> allPlugins) {
      this.results = results;
      this.analysisTime = analysisTime;
      this.allPlugins = allPlugins;
    }
  }

  private AnalysisResultsWrapper analyzeStandalone(AnalysisTask task, WorkspaceFolderSettings settings, URI baseDirUri, Map<URI, VersionnedOpenFile> filesToAnalyze,
    Map<URI, GetJavaConfigResponse> javaConfigs, IssueListener issueListener) {
    var baseDir = Paths.get(baseDirUri);
    var configurationBuilder = StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .setModuleKey(baseDirUri)
      .putAllExtraProperties(settings.getAnalyzerProperties())
      .putAllExtraProperties(javaConfigCache.configureJavaProperties(filesToAnalyze.keySet(), javaConfigs))
      .addExcludedRules(settingsManager.getCurrentSettings().getExcludedRules())
      .addIncludedRules(settingsManager.getCurrentSettings().getIncludedRules())
      .addRuleParameters(settingsManager.getCurrentSettings().getRuleParameters());
    filesToAnalyze.forEach((uri, openFile) -> configurationBuilder
      .addInputFiles(
        new AnalysisClientInputFile(uri, getFileRelativePath(baseDir, uri), openFile.getContent(),
          fileTypeClassifier.isTest(settings, uri, ofNullable(javaConfigs.get(uri))),
          openFile.getLanguageId())));

    var configuration = configurationBuilder.build();
    lsLogOutput.debug(format("Analysis triggered with configuration:%n%s", configuration.toString()));

    var engine = standaloneEngineManager.getOrCreateStandaloneEngine();
    return analyzeWithTiming(() -> engine.analyze(configuration, issueListener, new LanguageClientLogOutput(lsLogOutput, true), new TaskProgressMonitor(task)),
      engine.getPluginDetails(),
      () -> {
      });
  }

  public AnalysisResultsWrapper analyzeConnected(AnalysisTask task, ProjectBindingWrapper binding, WorkspaceFolderSettings settings, URI baseDirUri,
    Map<URI, VersionnedOpenFile> filesToAnalyze,
    Map<URI, GetJavaConfigResponse> javaConfigs, IssueListener issueListener) {
    var baseDir = Paths.get(baseDirUri);
    var configurationBuilder = ConnectedAnalysisConfiguration.builder()
      .setProjectKey(settings.getProjectKey())
      .setBaseDir(baseDir)
      .setModuleKey(baseDirUri)
      .putAllExtraProperties(settings.getAnalyzerProperties())
      .putAllExtraProperties(javaConfigCache.configureJavaProperties(filesToAnalyze.keySet(), javaConfigs));
    filesToAnalyze.forEach((uri, openFile) -> configurationBuilder
      .addInputFiles(
        new AnalysisClientInputFile(uri, getFileRelativePath(baseDir, uri), openFile.getContent(),
          fileTypeClassifier.isTest(settings, uri, ofNullable(javaConfigs.get(uri))),
          openFile.getLanguageId())));

    var configuration = configurationBuilder.build();
    if (settingsManager.getCurrentSettings().hasLocalRuleConfiguration()) {
      lsLogOutput.debug("Local rules settings are ignored, using quality profile from server");
    }
    lsLogOutput.debug(format("Analysis triggered with configuration:%n%s", configuration.toString()));

    var engine = binding.getEngine();
    var serverIssueTracker = binding.getServerIssueTracker();
    var issuesPerFiles = new HashMap<URI, List<Issue>>();
    IssueListener accumulatorIssueListener = i -> issuesPerFiles.computeIfAbsent(i.getInputFile().getClientObject(), uri -> new ArrayList<>()).add(i);
    return analyzeWithTiming(() -> engine.analyze(configuration, accumulatorIssueListener, new LanguageClientLogOutput(lsLogOutput, true), new TaskProgressMonitor(task)),
      engine.getPluginDetails(),
      () -> filesToAnalyze.forEach((fileUri, openFile) -> {
        var issues = issuesPerFiles.computeIfAbsent(fileUri, uri -> List.of());
        var filePath = FileUtils.toSonarQubePath(getFileRelativePath(baseDir, fileUri));
        serverIssueTracker.matchAndTrack(filePath, issues, issueListener, task.shouldFetchServerIssues());
        if (task.shouldFetchServerIssues()) {
          var serverIssues = engine.getServerIssues(binding.getBinding(), filePath);
          taintVulnerabilitiesCache.reload(fileUri, serverIssues);
          long foundVulnerabilities = taintVulnerabilitiesCache.getAsDiagnostics(fileUri).count();
          if (foundVulnerabilities > 0) {
            lsLogOutput
              .info(format("Fetched %s %s from %s", foundVulnerabilities, pluralize(foundVulnerabilities, "vulnerability", "vulnerabilities"), binding.getConnectionId()));
          }
        }
      }));
  }

  /**
   * @param analyze Analysis callback
   * @param postAnalysisTask Code that will be run after the analysis, but still counted in the total analysis duration.
   */
  private static AnalysisResultsWrapper analyzeWithTiming(Supplier<AnalysisResults> analyze, Collection<PluginDetails> allPlugins, Runnable postAnalysisTask) {
    long start = System.currentTimeMillis();
    var analysisResults = analyze.get();
    postAnalysisTask.run();
    int analysisTime = (int) (System.currentTimeMillis() - start);
    return new AnalysisResultsWrapper(analysisResults, analysisTime, allPlugins);
  }
}
