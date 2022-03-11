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

import com.google.gson.JsonPrimitive;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.GetJavaConfigResponse;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
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
import org.sonarsource.sonarlint.ls.java.JavaSdkUtil;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.sonarsource.sonarlint.ls.Utils.buildMessageWithPluralizedSuffix;
import static org.sonarsource.sonarlint.ls.Utils.locationMatches;
import static org.sonarsource.sonarlint.ls.Utils.pluralize;
import static org.sonarsource.sonarlint.ls.Utils.severity;

public class AnalysisManager implements WorkspaceSettingsChangeListener, WorkspaceFolderLifecycleListener {

  private static final int DEFAULT_TIMER_MS = 2000;
  private static final int QUEUE_POLLING_PERIOD_MS = 200;

  private static final String SECURITY_REPOSITORY_HINT = "security";
  public static final String TYPESCRIPT_PATH_PROP = "sonar.typescript.internal.typescriptLocation";
  static final String SONARLINT_SOURCE = "sonarlint";
  public static final String SONARQUBE_TAINT_SOURCE = "SonarQube Taint Analyzer";

  public static final String ITEM_LOCATION = "location";
  public static final String ITEM_FLOW = "flow";

  private final SonarLintExtendedLanguageClient client;
  private final FileTypeClassifier fileTypeClassifier;
  private final OpenFilesCache openFilesCache;
  private final JavaConfigCache javaConfigCache;
  private boolean firstSecretIssueDetected;
  private final Map<URI, Integer> analyzedVersionPerFileURI = new ConcurrentHashMap<>();
  private final Map<URI, Map<String, Issue>> issuesPerIdPerFileURI = new ConcurrentHashMap<>();
  private final Map<Path, List<Path>> jvmClasspathPerJavaHome = new ConcurrentHashMap<>();
  // entries in this map mean that the file is "dirty"
  private final Map<URI, Long> eventMap = new ConcurrentHashMap<>();

  private final SonarLintTelemetry telemetry;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private final EventWatcher watcher;
  private final LanguageClientLogger lsLogOutput;
  private final ScmIgnoredCache filesIgnoredByScmCache;
  private final StandaloneEngineManager standaloneEngineManager;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;

  private final ExecutorService analysisExecutor;

  public AnalysisManager(LanguageClientLogger lsLogOutput, StandaloneEngineManager standaloneEngineManager, SonarLintExtendedLanguageClient client, SonarLintTelemetry telemetry,
    WorkspaceFoldersManager workspaceFoldersManager, SettingsManager settingsManager, ProjectBindingManager bindingManager, FileTypeClassifier fileTypeClassifier,
    OpenFilesCache openFilesCache, JavaConfigCache javaConfigCache, TaintVulnerabilitiesCache taintVulnerabilitiesCache) {
    this.lsLogOutput = lsLogOutput;
    this.standaloneEngineManager = standaloneEngineManager;
    this.client = client;
    this.telemetry = telemetry;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.settingsManager = settingsManager;
    this.bindingManager = bindingManager;
    this.fileTypeClassifier = fileTypeClassifier;
    this.openFilesCache = openFilesCache;
    this.javaConfigCache = javaConfigCache;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.analysisExecutor = Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint analysis", false));
    this.watcher = new EventWatcher();
    this.filesIgnoredByScmCache = new ScmIgnoredCache(client);
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
          var inputFile = new InFolderClientInputFile(fileUri, getFileRelativePath(baseDir, fileUri), fileTypeClassifier.isTest(settings, fileUri, javaConfig));

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
    private boolean stop = false;

    EventWatcher() {
      this.setDaemon(true);
      this.setName("sonarlint-auto-trigger");
    }

    public void stopWatcher() {
      stop = true;
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
          it.remove();
        }
      }
      analyzeAsync(filesToTrigger, false);
    }
  }

  public void didClose(URI fileUri) {
    lsLogOutput.debug("File '" + fileUri + "' closed. Cleaning diagnostics.");
    javaConfigCache.remove(fileUri);
    issuesPerIdPerFileURI.remove(fileUri);
    analyzedVersionPerFileURI.remove(fileUri);
    eventMap.remove(fileUri);
    client.publishDiagnostics(newPublishDiagnostics(fileUri));
    filesIgnoredByScmCache.remove(fileUri);
  }

  public void didSave(VersionnedOpenFile file) {
    analyzeAsync(List.of(file), false);
  }

  void analyzeAsync(List<VersionnedOpenFile> files, boolean shouldFetchServerIssues) {
    var trueFileUris = files.stream().filter(f -> {
      if (!f.getUri().getScheme().equalsIgnoreCase("file")) {
        lsLogOutput.warn(format("URI '%s' is not in local filesystem, analysis not supported", f.getUri()));
        return false;
      }
      return true;
    }).collect(toList());
    if (trueFileUris.isEmpty()) {
      return;
    }
    if (trueFileUris.size() == 1) {
      lsLogOutput.debug(format("Queuing analysis of file '%s'", trueFileUris.get(0).getUri()));
    } else {
      lsLogOutput.debug(format("Queuing analysis of %d files", trueFileUris.size()));
    }
    analysisExecutor.execute(() -> analyze(trueFileUris, shouldFetchServerIssues));
  }

  private void analyze(List<VersionnedOpenFile> files, boolean shouldFetchServerIssues) {
    var filesToAnalyze = files.stream().collect(Collectors.toMap(VersionnedOpenFile::getUri, f -> f));

    var scmIgnored = filesToAnalyze.keySet().stream()
      .filter(this::scmIgnored)
      .collect(toSet());

    scmIgnored.forEach(f -> {
      lsLogOutput.debug(format("Skip analysis for SCM ignored file: '%s'", f));
      clearDiagnostics(f);
      filesToAnalyze.remove(f);
    });

    var filesToAnalyzePerFolder = filesToAnalyze.entrySet().stream()
      .collect(groupingBy(entry -> workspaceFoldersManager.findFolderForFile(entry.getKey()), mapping(Entry::getValue, toMap(VersionnedOpenFile::getUri, f -> f))));
    filesToAnalyzePerFolder.forEach((folder, filesToAnalyzeInFolder) -> analyze(folder, filesToAnalyzeInFolder, shouldFetchServerIssues));
  }

  private void clearDiagnostics(URI f) {
    issuesPerIdPerFileURI.remove(f);
    client.publishDiagnostics(newPublishDiagnostics(f));
  }

  private boolean scmIgnored(URI fileUri) {
    var isIgnored = filesIgnoredByScmCache.isIgnored(fileUri).orElse(false);
    if (Boolean.TRUE.equals(isIgnored)) {
      lsLogOutput.debug(format("Skip analysis for SCM ignored file: '%s'", fileUri));
      return true;
    }
    return false;
  }

  private void analyze(Optional<WorkspaceFolderWrapper> workspaceFolder, Map<URI, VersionnedOpenFile> filesToAnalyze, boolean shouldFetchServerIssues) {
    if (workspaceFolder.isPresent()) {
      // We can only have the same binding for a given folder
      var binding = bindingManager.getBinding(workspaceFolder.get());
      analyze(workspaceFolder, binding, filesToAnalyze, shouldFetchServerIssues);
    } else {
      // Files outside a folder can possibly have a different binding, so fork one analysis per binding
      // TODO is it really possible to have different settings (=binding) for files outside workspace folder
      filesToAnalyze.entrySet().stream()
        .collect(groupingBy(entry -> bindingManager.getBinding(entry.getKey()), mapping(Entry::getValue, toMap(VersionnedOpenFile::getUri, f -> f))))
        .forEach((binding, files) -> analyze(Optional.empty(), binding, files, shouldFetchServerIssues));
    }
  }

  private void analyze(Optional<WorkspaceFolderWrapper> workspaceFolder, Optional<ProjectBindingWrapper> binding, Map<URI, VersionnedOpenFile> filesToAnalyze,
    boolean shouldFetchServerIssues) {
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
        clearDiagnostics(uri);
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
      analyzeSingleModule(workspaceFolder, binding, nonJavaFiles, javaFilesWithConfig, shouldFetchServerIssues);
    } else {
      var isFirst = true;
      for (var javaFilesForSingleProjectRoot : javaFilesByProjectRoot.values()) {
        Map<URI, VersionnedOpenFile> toAnalyze = new HashMap<>();
        javaFilesForSingleProjectRoot.forEach(uri -> toAnalyze.put(uri, javaFiles.get(uri)));
        if (isFirst) {
          toAnalyze.putAll(nonJavaFiles);
          analyzeSingleModule(workspaceFolder, binding, toAnalyze, javaFilesWithConfig, shouldFetchServerIssues);
        } else {
          analyzeSingleModule(workspaceFolder, binding, toAnalyze, javaFilesWithConfig, shouldFetchServerIssues);
        }
        isFirst = false;
      }
    }
  }

  private static <G> List<G> join(Collection<G> left, Collection<G> right) {
    return Stream.concat(left.stream(), right.stream()).collect(toList());
  }

  /**
   * Here we have only files from the same folder, same binding, same Java module, so we can run the analysis engine.
   */
  private void analyzeSingleModule(Optional<WorkspaceFolderWrapper> workspaceFolder, Optional<ProjectBindingWrapper> binding, Map<URI, VersionnedOpenFile> filesToAnalyze,
    Map<URI, GetJavaConfigResponse> javaConfigs, boolean shouldFetchServerIssues) {
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
        clearDiagnostics(f);
      });
    }

    if (!nonExcludedFiles.isEmpty()) {
      analyzeSingleModuleNonExcluded(settings, binding, nonExcludedFiles, baseDirUri, javaConfigs, shouldFetchServerIssues);
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

  private void analyzeSingleModuleNonExcluded(WorkspaceFolderSettings settings, Optional<ProjectBindingWrapper> binding,
    Map<URI, VersionnedOpenFile> filesToAnalyze, URI baseDirUri, Map<URI, GetJavaConfigResponse> javaConfigs, boolean shouldFetchServerIssues) {
    if (filesToAnalyze.size() == 1) {
      lsLogOutput.info(format("Analyzing file '%s'...", filesToAnalyze.keySet().iterator().next()));
    } else {
      lsLogOutput.info(format("Analyzing %d files...", filesToAnalyze.size()));
    }

    filesToAnalyze.forEach((fileUri, openFile) -> {
      // Remember last document version that have been analyzed, to later ensure quick fixes consistency
      analyzedVersionPerFileURI.put(fileUri, openFile.getVersion());

      if (!binding.isPresent()) {
        // Clear taint vulnerabilities if the folder was previously bound and just now changed to standalone
        taintVulnerabilitiesCache.clear(fileUri);
      }

      // FIXME SLVSCODE-250 we should not clear issues if the file have parsing errors
      issuesPerIdPerFileURI.remove(fileUri);
    });

    var issueListener = createIssueListener();

    AnalysisResultsWrapper analysisResults;
    var filesSuccessfullyAnalyzed = new HashSet<>(filesToAnalyze.keySet());
    try {
      if (binding.isPresent()) {
        analysisResults = analyzeConnected(binding.get(), settings, baseDirUri, filesToAnalyze, javaConfigs, issueListener, shouldFetchServerIssues);
      } else {
        analysisResults = analyzeStandalone(settings, baseDirUri, filesToAnalyze, javaConfigs, issueListener);
      }
      SkippedPluginsNotifier.notifyOnceForSkippedPlugins(analysisResults.results, analysisResults.allPlugins, client);

      var analyzedLanguages = analysisResults.results.languagePerFile().values();
      if (!analyzedLanguages.isEmpty()) {
        telemetry.analysisDoneOnSingleLanguage(analyzedLanguages.iterator().next(), analysisResults.analysisTime);
      }

      // Ignore files with parsing error
      analysisResults.results.failedAnalysisFiles().stream()
        .map(ClientInputFile::getClientObject)
        .map(URI.class::cast)
        .forEach(filesSuccessfullyAnalyzed::remove);
    } catch (Exception e) {
      lsLogOutput.error("Analysis failed.", e);
      return;
    }

    var totalIssueCount = new AtomicInteger();
    filesSuccessfullyAnalyzed.forEach(f -> {
      var foundIssues = issuesPerIdPerFileURI.getOrDefault(f, emptyMap()).size();
      totalIssueCount.addAndGet(foundIssues);
      client.publishDiagnostics(newPublishDiagnostics(f));
      telemetry.addReportedRules(collectAllRuleKeys());
    });
    lsLogOutput.info(format("Found %s %s", totalIssueCount.get(), pluralize(totalIssueCount.get(), "issue")));
  }

  private Set<String> collectAllRuleKeys() {
    return issuesPerIdPerFileURI.values().stream()
      .flatMap(m -> m.values().stream())
      .map(Issue::getRuleKey)
      .collect(Collectors.toSet());
  }

  private IssueListener createIssueListener() {
    return issue -> {
      showFirstSecretDetectionNotificationIfNeeded(issue);
      var inputFile = issue.getInputFile();
      if (inputFile != null) {
        URI uri = inputFile.getClientObject();
        issuesPerIdPerFileURI.computeIfAbsent(uri, u -> new HashMap<>()).put(UUID.randomUUID().toString(), issue);
      }
    };
  }

  void showFirstSecretDetectionNotificationIfNeeded(Issue issue) {
    if (!firstSecretIssueDetected && issue.getRuleKey().startsWith(Language.SECRETS.getPluginKey())) {
      client.showFirstSecretDetectionNotification();
      firstSecretIssueDetected = true;
    }
  }

  @CheckForNull
  Integer getAnalyzedVersion(URI fileUri) {
    return analyzedVersionPerFileURI.get(fileUri);
  }

  Optional<Issue> getIssueForDiagnostic(URI fileUri, Diagnostic d) {
    var issuesForFile = issuesPerIdPerFileURI.getOrDefault(fileUri, emptyMap());
    var issueKey = Optional.ofNullable(d.getData())
      .map(JsonPrimitive.class::cast)
      .map(JsonPrimitive::getAsString)
      .orElse(null);
    if (issuesForFile.containsKey(issueKey)) {
      return Optional.of(issuesForFile.get(issueKey));
    } else {
      return issuesForFile.values()
        .stream()
        .filter(i -> i.getRuleKey().equals(d.getCode().getLeft()) && locationMatches(i, d))
        .findFirst();
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

  private AnalysisResultsWrapper analyzeStandalone(WorkspaceFolderSettings settings, URI baseDirUri, Map<URI, VersionnedOpenFile> filesToAnalyze,
    Map<URI, GetJavaConfigResponse> javaConfigs, IssueListener issueListener) {
    var baseDir = Paths.get(baseDirUri);
    var configurationBuilder = StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .setModuleKey(baseDirUri)
      .putAllExtraProperties(settings.getAnalyzerProperties())
      .putAllExtraProperties(configureJavaProperties(filesToAnalyze.keySet(), javaConfigs))
      .addExcludedRules(settingsManager.getCurrentSettings().getExcludedRules())
      .addIncludedRules(settingsManager.getCurrentSettings().getIncludedRules())
      .addRuleParameters(settingsManager.getCurrentSettings().getRuleParameters());
    filesToAnalyze.forEach((uri, openFile) -> configurationBuilder
      .addInputFiles(
        new AnalysisClientInputFile(uri, getFileRelativePath(baseDir, uri), openFile.getContent(), fileTypeClassifier.isTest(settings, uri, ofNullable(javaConfigs.get(uri))),
          openFile.getLanguageId())));

    var configuration = configurationBuilder.build();
    lsLogOutput.debug(format("Analysis triggered with configuration:%n%s", configuration.toString()));

    var engine = standaloneEngineManager.getOrCreateStandaloneEngine();
    return analyzeWithTiming(() -> engine.analyze(configuration, issueListener, new LanguageClientLogOutput(lsLogOutput, true), null),
      engine.getPluginDetails(),
      () -> {
      });
  }

  public AnalysisResultsWrapper analyzeConnected(ProjectBindingWrapper binding, WorkspaceFolderSettings settings, URI baseDirUri, Map<URI, VersionnedOpenFile> filesToAnalyze,
    Map<URI, GetJavaConfigResponse> javaConfigs, IssueListener issueListener, boolean shouldFetchServerIssues) {
    var baseDir = Paths.get(baseDirUri);
    var configurationBuilder = ConnectedAnalysisConfiguration.builder()
      .setProjectKey(settings.getProjectKey())
      .setBaseDir(baseDir)
      .setModuleKey(baseDirUri)
      .putAllExtraProperties(settings.getAnalyzerProperties())
      .putAllExtraProperties(configureJavaProperties(filesToAnalyze.keySet(), javaConfigs));
    filesToAnalyze.forEach((uri, openFile) -> configurationBuilder
      .addInputFiles(
        new AnalysisClientInputFile(uri, getFileRelativePath(baseDir, uri), openFile.getContent(), fileTypeClassifier.isTest(settings, uri, ofNullable(javaConfigs.get(uri))),
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
    return analyzeWithTiming(() -> engine.analyze(configuration, accumulatorIssueListener, new LanguageClientLogOutput(lsLogOutput, true), null),
      engine.getPluginDetails(),
      () -> filesToAnalyze.forEach((fileUri, openFile) -> {
        var issues = issuesPerFiles.computeIfAbsent(fileUri, uri -> List.of());
        var filePath = FileUtils.toSonarQubePath(getFileRelativePath(baseDir, fileUri));
        serverIssueTracker.matchAndTrack(filePath, issues, issueListener, shouldFetchServerIssues);
        var serverIssues = engine.getServerIssues(binding.getBinding(), filePath);

        taintVulnerabilitiesCache.put(fileUri, serverIssues.stream()
          .filter(it -> it.ruleKey().contains(SECURITY_REPOSITORY_HINT))
          .filter(it -> it.resolution().isEmpty())
          .collect(Collectors.toList()));
        long foundVulnerabilities = taintVulnerabilitiesCache.getAsDiagnostic(fileUri).count();
        if (foundVulnerabilities > 0 && shouldFetchServerIssues) {
          lsLogOutput
            .info(format("Fetched %s %s from %s", foundVulnerabilities, pluralize(foundVulnerabilities, "vulnerability", "vulnerabilities"), binding.getConnectionId()));
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

  private String getFileRelativePath(Path baseDir, URI uri) {
    try {
      return baseDir.relativize(Paths.get(uri)).toString();
    } catch (IllegalArgumentException e) {
      // Possibly the file has not the same root as baseDir
      lsLogOutput.debug("Unable to relativize " + uri + " to " + baseDir);
      return Paths.get(uri).toString();
    }
  }

  static Optional<Diagnostic> convert(Map.Entry<String, Issue> entry) {
    var issue = entry.getValue();
    if (issue.getStartLine() != null) {
      var range = Utils.convert(issue);
      var diagnostic = new Diagnostic();
      var severity = severity(issue.getSeverity());

      diagnostic.setSeverity(severity);
      diagnostic.setRange(range);
      diagnostic.setCode(issue.getRuleKey());
      diagnostic.setMessage(message(issue));
      diagnostic.setSource(SONARLINT_SOURCE);
      diagnostic.setData(entry.getKey());

      return Optional.of(diagnostic);
    }
    return Optional.empty();
  }

  static String message(Issue issue) {
    if (issue.flows().isEmpty()) {
      return issue.getMessage();
    } else if (issue.flows().size() == 1) {
      return buildMessageWithPluralizedSuffix(issue.getMessage(), issue.flows().get(0).locations().size(), ITEM_LOCATION);
    } else if (issue.flows().stream().allMatch(f -> f.locations().size() == 1)) {
      int nbLocations = issue.flows().size();
      return buildMessageWithPluralizedSuffix(issue.getMessage(), nbLocations, ITEM_LOCATION);
    } else {
      int nbFlows = issue.flows().size();
      return buildMessageWithPluralizedSuffix(issue.getMessage(), nbFlows, ITEM_FLOW);
    }
  }

  private PublishDiagnosticsParams newPublishDiagnostics(URI newUri) {
    var p = new PublishDiagnosticsParams();

    var localDiagnostics = issuesPerIdPerFileURI.getOrDefault(newUri, emptyMap()).entrySet()
      .stream()
      .flatMap(i -> AnalysisManager.convert(i).stream());
    var taintDiagnostics = taintVulnerabilitiesCache.getAsDiagnostic(newUri);

    p.setDiagnostics(Stream.concat(localDiagnostics, taintDiagnostics)
      .sorted(AnalysisManager.byLineNumber())
      .collect(Collectors.toList()));
    p.setUri(newUri.toString());

    return p;
  }

  private static Comparator<? super Diagnostic> byLineNumber() {
    return Comparator.comparing((Diagnostic d) -> d.getRange().getStart().getLine())
      .thenComparing(Diagnostic::getMessage);
  }

  public void initialize(Boolean firstSecretDetected) {
    firstSecretIssueDetected = firstSecretDetected;
    watcher.start();
  }

  public void shutdown() {
    watcher.stopWatcher();
    eventMap.clear();
    analysisExecutor.shutdown();
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

  private Map<String, String> configureJavaProperties(Set<URI> fileInTheSameModule, Map<URI, GetJavaConfigResponse> javaConfigs) {
    var partitionMainTest = fileInTheSameModule.stream().filter(javaConfigs::containsKey).collect(groupingBy(f -> javaConfigs.get(f).isTest()));
    var mainFiles = ofNullable(partitionMainTest.get(false)).orElse(List.of());
    var testFiles = ofNullable(partitionMainTest.get(true)).orElse(List.of());

    if (mainFiles.isEmpty() && testFiles.isEmpty()) {
      return Map.of();
    }

    Map<String, String> props = new HashMap<>();

    // Assume all files in the same module have the same vmLocation
    var commonConfig = javaConfigs.get(javaConfigs.keySet().iterator().next());
    var vmLocationStr = commonConfig.getVmLocation();
    List<Path> jdkClassesRoots = new ArrayList<>();
    if (vmLocationStr != null) {
      var vmLocation = Paths.get(vmLocationStr);
      jdkClassesRoots = getVmClasspathFromCacheOrCompute(vmLocation);
      props.put("sonar.java.jdkHome", vmLocationStr);
    }

    // Assume all main files have the same classpath
    if (!mainFiles.isEmpty()) {
      var mainConfig = javaConfigs.get(mainFiles.get(0));
      var classpath = computeClasspathSkipNonExisting(jdkClassesRoots, mainConfig);
      props.put("sonar.java.libraries", classpath);
    }

    // Assume all test files have the same classpath
    if (!testFiles.isEmpty()) {
      var testConfig = javaConfigs.get(testFiles.get(0));
      var classpath = computeClasspathSkipNonExisting(jdkClassesRoots, testConfig);
      props.put("sonar.java.test.libraries", classpath);
    }

    return props;
  }

  private String computeClasspathSkipNonExisting(List<Path> jdkClassesRoots, GetJavaConfigResponse testConfig) {
    return Stream.concat(
      jdkClassesRoots.stream().map(Path::toAbsolutePath).map(Path::toString),
      Stream.of(testConfig.getClasspath()))
      .filter(path -> {
        boolean exists = new File(path).exists();
        if (!exists) {
          lsLogOutput.debug(format("Classpath '%s' from configuration does not exist, skipped", path));
        }
        return exists;
      })
      .collect(joining(","));
  }

  private List<Path> getVmClasspathFromCacheOrCompute(Path vmLocation) {
    return jvmClasspathPerJavaHome.computeIfAbsent(vmLocation, JavaSdkUtil::getJdkClassesRoots);
  }

  public void didClasspathUpdate(URI projectUri) {
    javaConfigCache.clear(projectUri);
    analyzeAllOpenJavaFiles();
  }

  public void didServerModeChange(SonarLintExtendedLanguageServer.ServerMode serverMode) {
    lsLogOutput.debug("Clearing Java config cache on server mode change");
    javaConfigCache.clear();
    if (serverMode == SonarLintExtendedLanguageServer.ServerMode.STANDARD) {
      analyzeAllOpenJavaFiles();
    }
  }

}
