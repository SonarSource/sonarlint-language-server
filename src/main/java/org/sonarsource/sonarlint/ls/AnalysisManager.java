/*
 * SonarLint Language Server
 * Copyright (C) 2009-2021 SonarSource SA
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssueLocation;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.GetJavaConfigResponse;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.ServerIssueTrackerWrapper;
import org.sonarsource.sonarlint.ls.file.FileLanguageCache;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.FolderFileSystem;
import org.sonarsource.sonarlint.ls.folders.InFolderClientInputFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderLifecycleListener;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersProvider;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.java.JavaSdkUtil;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.joining;
import static org.sonarsource.sonarlint.ls.Utils.pluralize;

public class AnalysisManager implements WorkspaceSettingsChangeListener, WorkspaceFolderLifecycleListener {

  private static final int DELAY_MS = 500;
  private static final int QUEUE_POLLING_PERIOD_MS = 200;

  private static final Logger LOG = Loggers.get(AnalysisManager.class);

  private static final String SECURITY_REPOSITORY_HINT = "security";
  public static final String TYPESCRIPT_PATH_PROP = "sonar.typescript.internal.typescriptLocation";
  static final String SONARLINT_SOURCE = "sonarlint";
  static final String SONARQUBE_TAINT_SOURCE = "SonarQube Taint Analyzer";

  private static final String MESSAGE_WITH_PLURALIZED_SUFFIX = "%s [+%d %s]";
  private static final String ITEM_LOCATION = "location";
  private static final String ITEM_FLOW = "flow";

  private final SonarLintExtendedLanguageClient client;

  private final FileTypeClassifier fileTypeClassifier;
  private final FileLanguageCache fileLanguageCache;
  private final JavaConfigCache javaConfigCache;
  private final Map<URI, String> fileContentPerFileURI = new ConcurrentHashMap<>();
  private final Map<URI, Map<String, Issue>> issuesPerIdPerFileURI = new ConcurrentHashMap<>();
  private final Map<URI, List<ServerIssue>> taintVulnerabilitiesPerFile;
  private final Map<Path, List<Path>> jvmClasspathPerJavaHome = new ConcurrentHashMap<>();
  // entries in this map mean that the file is "dirty"
  private final Map<URI, Long> eventMap = new ConcurrentHashMap<>();

  private final SonarLintTelemetry telemetry;
  private final EnginesFactory enginesFactory;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private final EventWatcher watcher;
  private final LanguageClientLogOutput lsLogOutput;
  private StandaloneSonarLintEngine standaloneEngine;

  private final ExecutorService analysisExecutor;

  public AnalysisManager(LanguageClientLogOutput lsLogOutput, EnginesFactory enginesFactory, SonarLintExtendedLanguageClient client, SonarLintTelemetry telemetry,
    WorkspaceFoldersManager workspaceFoldersManager, SettingsManager settingsManager, ProjectBindingManager bindingManager, FileTypeClassifier fileTypeClassifier,
    FileLanguageCache fileLanguageCache, JavaConfigCache javaConfigCache) {
    this(lsLogOutput, enginesFactory, client, telemetry, workspaceFoldersManager, settingsManager, bindingManager, fileTypeClassifier, fileLanguageCache, javaConfigCache,
      new ConcurrentHashMap<>());
  }

  public AnalysisManager(LanguageClientLogOutput lsLogOutput, EnginesFactory enginesFactory, SonarLintExtendedLanguageClient client, SonarLintTelemetry telemetry,
    WorkspaceFoldersManager workspaceFoldersManager, SettingsManager settingsManager, ProjectBindingManager bindingManager, FileTypeClassifier fileTypeClassifier,
    FileLanguageCache fileLanguageCache, JavaConfigCache javaConfigCache,
    Map<URI, List<ServerIssue>> taintVulnerabilitiesPerFile) {
    this.lsLogOutput = lsLogOutput;
    this.enginesFactory = enginesFactory;
    this.client = client;
    this.telemetry = telemetry;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.settingsManager = settingsManager;
    this.bindingManager = bindingManager;
    this.fileTypeClassifier = fileTypeClassifier;
    this.fileLanguageCache = fileLanguageCache;
    this.javaConfigCache = javaConfigCache;
    this.analysisExecutor = Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint analysis", false));
    this.watcher = new EventWatcher();
    this.taintVulnerabilitiesPerFile = taintVulnerabilitiesPerFile;
  }

  synchronized StandaloneSonarLintEngine getOrCreateStandaloneEngine() {
    if (standaloneEngine == null) {
      standaloneEngine = enginesFactory.createStandaloneEngine();
    }
    return standaloneEngine;
  }

  public void didChangeWatchedFiles(List<FileEvent> changes) {
    changes.forEach(f -> {
      URI fileUri = URI.create(f.getUri());
      workspaceFoldersManager.findFolderForFile(fileUri)
        .ifPresent(folder -> {
          WorkspaceFolderSettings settings = folder.getSettings();
          Path baseDir = folder.getRootPath();

          Optional<ProjectBindingWrapper> binding = bindingManager.getBinding(fileUri);

          SonarLintEngine engineForFile = binding.isPresent() ? binding.get().getEngine() : getOrCreateStandaloneEngine();

          Optional<GetJavaConfigResponse> javaConfig = javaConfigCache.getOrFetch(fileUri);
          ClientInputFile inputFile = new InFolderClientInputFile(fileUri, getFileRelativePath(baseDir, fileUri), fileTypeClassifier.isTest(settings, fileUri, javaConfig));

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

  public void didOpen(URI fileUri, String languageId, String fileContent) {
    fileLanguageCache.put(fileUri, languageId);
    fileContentPerFileURI.put(fileUri, fileContent);
    analyzeAsync(fileUri, true);
  }

  public void didChange(URI fileUri, String fileContent) {
    fileContentPerFileURI.put(fileUri, fileContent);
    eventMap.put(fileUri, System.currentTimeMillis());
  }

  private SonarLintEngine findEngineFor(WorkspaceFolderWrapper folder) {
    return bindingManager.getBinding(folder)
      .map(ProjectBindingWrapper::getEngine)
      .map(SonarLintEngine.class::cast)
      .orElseGet(this::getOrCreateStandaloneEngine);
  }

  @Override
  public void added(WorkspaceFolderWrapper addedFolder) {
    analysisExecutor.execute(() -> {
      FolderFileSystem folderFileSystem = new FolderFileSystem(addedFolder, javaConfigCache, fileTypeClassifier);
      findEngineFor(addedFolder).declareModule(new ModuleInfo(WorkspaceFoldersProvider.key(addedFolder), folderFileSystem));
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
      long t = System.currentTimeMillis();

      Iterator<Map.Entry<URI, Long>> it = eventMap.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<URI, Long> e = it.next();
        if (e.getValue() + DELAY_MS < t) {
          analyzeAsync(e.getKey(), false);
          it.remove();
        }
      }
    }
  }

  public void didClose(URI fileUri) {
    LOG.debug("File '{}' closed. Cleaning diagnostics.", fileUri);
    fileLanguageCache.remove(fileUri);
    fileContentPerFileURI.remove(fileUri);
    javaConfigCache.remove(fileUri);
    issuesPerIdPerFileURI.remove(fileUri);
    taintVulnerabilitiesPerFile.remove(fileUri);
    eventMap.remove(fileUri);
    client.publishDiagnostics(newPublishDiagnostics(fileUri));
  }

  public void didSave(URI fileUri, String fileContent) {
    fileContentPerFileURI.put(fileUri, fileContent);
    analyzeAsync(fileUri, false);
  }

  void analyzeAsync(URI fileUri, boolean shouldFetchServerIssues) {
    if (!fileUri.getScheme().equalsIgnoreCase("file")) {
      LOG.warn("URI '{}' is not a file, analysis not supported", fileUri);
      return;
    }
    LOG.debug("Queuing analysis of file '{}'", fileUri);
    analysisExecutor.execute(() -> analyze(fileUri, shouldFetchServerIssues));
  }

  private void analyze(URI fileUri, boolean shouldFetchServerIssues) {
    final Optional<GetJavaConfigResponse> javaConfigOpt = javaConfigCache.getOrFetch(fileUri);
    if (fileLanguageCache.isJava(fileUri) && !javaConfigOpt.isPresent()) {
      LOG.debug("Skipping analysis of Java file '{}' because SonarLint was unable to query project configuration (classpath, source level, ...)", fileUri);
      return;
    }
    String content = fileContentPerFileURI.get(fileUri);
    if (content == null) {
      LOG.debug("Skipping analysis of file '{}', content has disappeared", fileUri);
      return;
    }
    Map<String, Issue> newIssuesPerId = new HashMap<>();
    issuesPerIdPerFileURI.put(fileUri, newIssuesPerId);
    taintVulnerabilitiesPerFile.put(fileUri, new ArrayList<>());

    Optional<WorkspaceFolderWrapper> workspaceFolder = workspaceFoldersManager.findFolderForFile(fileUri);

    WorkspaceFolderSettings settings = workspaceFolder.map(WorkspaceFolderWrapper::getSettings)
      .orElse(settingsManager.getCurrentDefaultFolderSettings());

    URI baseDirUri = workspaceFolder.map(WorkspaceFolderWrapper::getUri)
      // Default to take file parent dir if file is not part of any workspace
      .orElse(Paths.get(fileUri).getParent().toUri());

    IssueListener issueListener = createIssueListener();

    Optional<ProjectBindingWrapper> binding = bindingManager.getBinding(fileUri);
    AnalysisResultsWrapper analysisResults;
    try {
      if (binding.isPresent()) {
        ConnectedSonarLintEngine connectedEngine = binding.get().getEngine();
        if (!connectedEngine.getExcludedFiles(binding.get().getBinding(),
          singleton(fileUri),
          uri -> getFileRelativePath(Paths.get(baseDirUri), uri),
          uri -> fileTypeClassifier.isTest(settings, uri, javaConfigOpt))
          .isEmpty()) {
          LOG.debug("Skip analysis of excluded file: {}", fileUri);
          return;
        }
        LOG.info("Analyzing file '{}'...", fileUri);
        analysisResults = analyzeConnected(binding.get(), settings, baseDirUri, fileUri, content, issueListener, shouldFetchServerIssues, javaConfigOpt);
      } else {
        LOG.info("Analyzing file '{}'...", fileUri);
        analysisResults = analyzeStandalone(settings, baseDirUri, fileUri, content, issueListener, javaConfigOpt);
      }
      SkippedPluginsNotifier.notifyOnceForSkippedPlugins(analysisResults.results, analysisResults.allPlugins, client);

      Collection<Language> analyzedLanguages = analysisResults.results.languagePerFile().values();
      if (!analyzedLanguages.isEmpty()) {
        telemetry.analysisDoneOnSingleLanguage(analyzedLanguages.iterator().next(), analysisResults.analysisTime);
      }

      // Ignore files with parsing error
      analysisResults.results.failedAnalysisFiles().stream()
        .map(ClientInputFile::getClientObject)
        .map(URI.class::cast)
        .forEach(issuesPerIdPerFileURI::remove);
    } catch (Exception e) {
      LOG.error("Analysis failed.", e);
    }

    // Check if file has not being closed during the analysis
    if (fileContentPerFileURI.containsKey(fileUri)) {
      int foundIssues = newIssuesPerId.size();
      LOG.info("Found {} {}", foundIssues, pluralize(foundIssues, "issue"));
      client.publishDiagnostics(newPublishDiagnostics(fileUri));
    }
  }

  private IssueListener createIssueListener() {
    return issue -> {
      ClientInputFile inputFile = issue.getInputFile();
      if (inputFile != null) {
        URI uri = inputFile.getClientObject();
        issuesPerIdPerFileURI.computeIfAbsent(uri, u -> new HashMap<>()).put(UUID.randomUUID().toString(), issue);
      }
    };
  }

  Optional<Issue> getIssueForDiagnostic(URI fileUri, Diagnostic d) {
    Map<String, Issue> issuesForFile = issuesPerIdPerFileURI.getOrDefault(fileUri, emptyMap());
    String issueKey = Optional.ofNullable(d.getData())
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

  Optional<ServerIssue> getTaintVulnerabilityForDiagnostic(URI fileUri, Diagnostic d) {
    return taintVulnerabilitiesPerFile.getOrDefault(fileUri, Collections.emptyList())
      .stream()
      .filter(i -> hasSameKey(d, i) || hasSameRuleKeyAndLocation(d, i))
      .findFirst();
  }

  private static boolean hasSameKey(Diagnostic d, ServerIssue i) {
    return d.getData() != null && d.getData().equals(i.key());
  }

  private static boolean hasSameRuleKeyAndLocation(Diagnostic d, ServerIssue i) {
    return i.ruleKey().equals(d.getCode().getLeft()) && locationMatches(i, d);
  }

  Optional<ServerIssue> getTaintVulnerabilityByKey(String issueId) {
    return taintVulnerabilitiesPerFile.values().stream()
      .flatMap(List::stream)
      .filter(i -> issueId.equals(i.key()))
      .findFirst();
  }

  static boolean locationMatches(Issue i, Diagnostic d) {
    return position(i).equals(d.getRange());
  }

  static boolean locationMatches(ServerIssue i, Diagnostic d) {
    return position(i).equals(d.getRange());
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

  private AnalysisResultsWrapper analyzeStandalone(WorkspaceFolderSettings settings, URI baseDirUri, URI uri, String content, IssueListener issueListener,
    Optional<GetJavaConfigResponse> javaConfigOpt) {
    Path baseDir = Paths.get(baseDirUri);
    StandaloneAnalysisConfiguration configuration = StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .setModuleKey(baseDirUri)
      .addInputFiles(new AnalysisClientInputFile(uri, getFileRelativePath(baseDir, uri), content, fileTypeClassifier.isTest(settings, uri, javaConfigOpt),
        fileLanguageCache.getLanguageFor(uri)))
      .putAllExtraProperties(settings.getAnalyzerProperties())
      .putAllExtraProperties(configureJavaProperties(uri))
      .addExcludedRules(settingsManager.getCurrentSettings().getExcludedRules())
      .addIncludedRules(settingsManager.getCurrentSettings().getIncludedRules())
      .addRuleParameters(settingsManager.getCurrentSettings().getRuleParameters())
      .build();
    LOG.debug("Analysis triggered on '{}' with configuration: \n{}", uri, configuration.toString());

    StandaloneSonarLintEngine engine = getOrCreateStandaloneEngine();
    return analyzeWithTiming(() -> engine.analyze(configuration, issueListener, null, null),
      engine.getPluginDetails(),
      () -> {
      });
  }

  public AnalysisResultsWrapper analyzeConnected(ProjectBindingWrapper binding, WorkspaceFolderSettings settings, URI baseDirUri, URI uri, String content,
    IssueListener issueListener, boolean shouldFetchServerIssues, Optional<GetJavaConfigResponse> javaConfig) {
    Path baseDir = Paths.get(baseDirUri);
    ConnectedAnalysisConfiguration configuration = ConnectedAnalysisConfiguration.builder()
      .setProjectKey(settings.getProjectKey())
      .setBaseDir(baseDir)
      .setModuleKey(baseDirUri)
      .addInputFile(
        new AnalysisClientInputFile(uri, getFileRelativePath(baseDir, uri), content, fileTypeClassifier.isTest(settings, uri, javaConfig), fileLanguageCache.getLanguageFor(uri)))
      .putAllExtraProperties(settings.getAnalyzerProperties())
      .putAllExtraProperties(configureJavaProperties(uri))
      .build();
    if (settingsManager.getCurrentSettings().hasLocalRuleConfiguration()) {
      LOG.debug("Local rules settings are ignored, using quality profile from server");
    }
    LOG.debug("Analysis triggered on '{}' with configuration: \n{}", uri, configuration.toString());

    List<Issue> issues = new LinkedList<>();
    IssueListener collector = issues::add;

    ConnectedSonarLintEngine engine = binding.getEngine();
    return analyzeWithTiming(() -> engine.analyze(configuration, collector, null, null),
      engine.getPluginDetails(),
      () -> {
        String filePath = FileUtils.toSonarQubePath(getFileRelativePath(baseDir, uri));
        ServerIssueTrackerWrapper serverIssueTracker = binding.getServerIssueTracker();
        serverIssueTracker.matchAndTrack(filePath, issues, issueListener, shouldFetchServerIssues);
        List<ServerIssue> serverIssues = engine.getServerIssues(binding.getBinding(), filePath);

        taintVulnerabilitiesPerFile.put(uri, serverIssues.stream()
          .filter(it -> it.ruleKey().contains(SECURITY_REPOSITORY_HINT))
          .filter(it -> it.resolution().isEmpty())
          .collect(Collectors.toList()));
        int foundVulnerabilities = taintVulnerabilitiesPerFile.getOrDefault(uri, Collections.emptyList()).size();
        if (foundVulnerabilities > 0) {
          LOG.info("Fetched {} {} from {}", foundVulnerabilities, pluralize(foundVulnerabilities, "vulnerability", "vulnerabilities"), binding.getConnectionId());
        }
      });
  }

  /**
   * @param analyze Analysis callback
   * @param postAnalysisTask Code that will be logged outside the analysis flag, but still counted in the total analysis duration.
   */
  private AnalysisResultsWrapper analyzeWithTiming(Supplier<AnalysisResults> analyze, Collection<PluginDetails> allPlugins, Runnable postAnalysisTask) {
    long start = System.currentTimeMillis();
    AnalysisResults analysisResults;
    try {
      lsLogOutput.setAnalysis(true);
      analysisResults = analyze.get();
    } finally {
      lsLogOutput.setAnalysis(false);
    }

    postAnalysisTask.run();

    int analysisTime = (int) (System.currentTimeMillis() - start);
    return new AnalysisResultsWrapper(analysisResults, analysisTime, allPlugins);
  }

  private static String getFileRelativePath(Path baseDir, URI uri) {
    return baseDir.relativize(Paths.get(uri)).toString();
  }

  static Optional<Diagnostic> convert(Map.Entry<String, Issue> entry) {
    Issue issue = entry.getValue();
    if (issue.getStartLine() != null) {
      Range range = position(issue);
      Diagnostic diagnostic = new Diagnostic();
      DiagnosticSeverity severity = severity(issue.getSeverity());

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

  static Optional<Diagnostic> convert(ServerIssue issue) {
    if (issue.getStartLine() != null) {
      Range range = position(issue);
      Diagnostic diagnostic = new Diagnostic();
      DiagnosticSeverity severity = severity(issue.severity());

      diagnostic.setSeverity(severity);
      diagnostic.setRange(range);
      diagnostic.setCode(issue.ruleKey());
      diagnostic.setMessage(message(issue));
      diagnostic.setSource(SONARQUBE_TAINT_SOURCE);
      diagnostic.setData(issue.key());

      return Optional.of(diagnostic);
    }
    return Optional.empty();
  }

  private static DiagnosticSeverity severity(String severity) {
    switch (severity.toUpperCase(Locale.ENGLISH)) {
      case "BLOCKER":
      case "CRITICAL":
      case "MAJOR":
        return DiagnosticSeverity.Warning;
      case "MINOR":
        return DiagnosticSeverity.Information;
      case "INFO":
      default:
        return DiagnosticSeverity.Hint;
    }
  }

  private static Range position(Issue issue) {
    return new Range(
      new Position(
        issue.getStartLine() - 1,
        issue.getStartLineOffset()),
      new Position(
        issue.getEndLine() - 1,
        issue.getEndLineOffset()));
  }

  private static Range position(ServerIssueLocation issue) {
    return new Range(
      new Position(
        issue.getStartLine() - 1,
        issue.getStartLineOffset()),
      new Position(
        issue.getEndLine() - 1,
        issue.getEndLineOffset()));
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

  static String message(ServerIssue issue) {
    if (issue.getFlows().isEmpty()) {
      return issue.getMessage();
    } else if (issue.getFlows().size() == 1) {
      return buildMessageWithPluralizedSuffix(issue.getMessage(), issue.getFlows().get(0).locations().size(), ITEM_LOCATION);
    } else {
      return buildMessageWithPluralizedSuffix(issue.getMessage(), issue.getFlows().size(), ITEM_FLOW);
    }
  }

  private static String buildMessageWithPluralizedSuffix(@Nullable String issueMessage, long nbItems, String itemName) {
    return String.format(MESSAGE_WITH_PLURALIZED_SUFFIX, issueMessage, nbItems, pluralize(nbItems, itemName));
  }

  private PublishDiagnosticsParams newPublishDiagnostics(URI newUri) {
    PublishDiagnosticsParams p = new PublishDiagnosticsParams();

    Stream<Optional<Diagnostic>> localDiagnostics = issuesPerIdPerFileURI.getOrDefault(newUri, Collections.emptyMap()).entrySet()
      .stream()
      .map(AnalysisManager::convert);
    Stream<Optional<Diagnostic>> taintDiagnostics = taintVulnerabilitiesPerFile.getOrDefault(newUri, Collections.emptyList())
      .stream()
      .map(AnalysisManager::convert);

    p.setDiagnostics(Stream.concat(localDiagnostics, taintDiagnostics)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .sorted(AnalysisManager.byLineNumber())
      .collect(Collectors.toList()));
    p.setUri(newUri.toString());

    return p;
  }

  private static Comparator<? super Diagnostic> byLineNumber() {
    return Comparator.comparing((Diagnostic d) -> d.getRange().getStart().getLine())
      .thenComparing(Diagnostic::getMessage);
  }

  public void initialize() {
    watcher.start();
  }

  public void shutdown() {
    watcher.stopWatcher();
    eventMap.clear();
    analysisExecutor.shutdown();
    if (standaloneEngine != null) {
      standaloneEngine.stop();
    }
  }

  public void analyzeAllOpenFilesInFolder(@Nullable WorkspaceFolderWrapper folder) {
    for (URI fileUri : fileContentPerFileURI.keySet()) {
      Optional<WorkspaceFolderWrapper> actualFolder = workspaceFoldersManager.findFolderForFile(fileUri);
      if (actualFolder.map(f -> f.equals(folder)).orElse(folder == null)) {
        analyzeAsync(fileUri, false);
      }
    }
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
    for (URI fileUri : fileContentPerFileURI.keySet()) {
      Optional<ProjectBindingWrapper> binding = bindingManager.getBinding(fileUri);
      if (!binding.isPresent()) {
        analyzeAsync(fileUri, false);
      }
    }
  }

  private void analyzeAllOpenJavaFiles() {
    for (URI fileUri : fileContentPerFileURI.keySet()) {
      if (fileLanguageCache.isJava(fileUri)) {
        analyzeAsync(fileUri, false);
      }
    }
  }

  private Map<String, String> configureJavaProperties(URI fileUri) {
    return javaConfigCache.get(fileUri).map(cachedJavaConfig -> {
      Map<String, String> props = new HashMap<>();
      String vmLocationStr = cachedJavaConfig.getVmLocation();
      List<Path> jdkClassesRoots = new ArrayList<>();
      if (vmLocationStr != null) {
        Path vmLocation = Paths.get(vmLocationStr);
        jdkClassesRoots = getVmClasspathFromCacheOrCompute(vmLocation);
        props.put("sonar.java.jdkHome", vmLocationStr);
      }
      String classpath = Stream.concat(
        jdkClassesRoots.stream().map(Path::toAbsolutePath).map(Path::toString),
        Stream.of(cachedJavaConfig.getClasspath()))
        .filter(path -> {
          boolean exists = new File(path).exists();
          if (!exists) {
            LOG.debug(String.format("Classpath '%s' from configuration does not exist, skipped", path));
          }
          return exists;
        })
        .collect(joining(","));
      props.put("sonar.java.source", cachedJavaConfig.getSourceLevel());
      if (!cachedJavaConfig.isTest()) {
        props.put("sonar.java.libraries", classpath);
      } else {
        props.put("sonar.java.test.libraries", classpath);
      }
      return props;
    }).orElse(emptyMap());
  }

  private List<Path> getVmClasspathFromCacheOrCompute(Path vmLocation) {
    return jvmClasspathPerJavaHome.computeIfAbsent(vmLocation, JavaSdkUtil::getJdkClassesRoots);
  }

  public void didClasspathUpdate(URI projectUri) {
    javaConfigCache.clear(projectUri);
    analyzeAllOpenJavaFiles();
  }

  public void didServerModeChange(SonarLintExtendedLanguageServer.ServerMode serverMode) {
    LOG.debug("Clearing Java config cache on server mode change");
    javaConfigCache.clear();
    if (serverMode == SonarLintExtendedLanguageServer.ServerMode.STANDARD) {
      analyzeAllOpenJavaFiles();
    }
  }

}
