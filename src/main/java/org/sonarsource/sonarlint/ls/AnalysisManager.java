/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.Flow;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.GetJavaConfigResponse;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.ServerIssueTrackerWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

public class AnalysisManager implements WorkspaceSettingsChangeListener {

  private static final int DELAY_MS = 500;
  private static final int QUEUE_POLLING_PERIOD_MS = 200;

  private static final Logger LOG = Loggers.get(AnalysisManager.class);

  public static final String TYPESCRIPT_PATH_PROP = "sonar.typescript.internal.typescriptLocation";
  static final String SONARLINT_SOURCE = "sonarlint";

  private final SonarLintExtendedLanguageClient client;

  private final Map<URI, String> languageIdPerFileURI = new ConcurrentHashMap<>();
  private final Map<URI, String> fileContentPerFileURI = new ConcurrentHashMap<>();
  private final Map<URI, Optional<GetJavaConfigResponse>> javaConfigPerFileURI = new ConcurrentHashMap<>();
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

  private ExecutorService analysisExecutor;

  public AnalysisManager(LanguageClientLogOutput lsLogOutput, EnginesFactory enginesFactory, SonarLintExtendedLanguageClient client, SonarLintTelemetry telemetry,
    WorkspaceFoldersManager workspaceFoldersManager, SettingsManager settingsManager, ProjectBindingManager bindingManager) {
    this.lsLogOutput = lsLogOutput;
    this.enginesFactory = enginesFactory;
    this.client = client;
    this.telemetry = telemetry;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.settingsManager = settingsManager;
    this.bindingManager = bindingManager;
    this.analysisExecutor = Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint analysis", false));
    this.watcher = new EventWatcher();
  }

  synchronized StandaloneSonarLintEngine getOrCreateStandaloneEngine() {
    if (standaloneEngine == null) {
      standaloneEngine = enginesFactory.createStandaloneEngine();
    }
    return standaloneEngine;
  }

  public void didOpen(URI fileUri, String languageId, String fileContent) {
    languageIdPerFileURI.put(fileUri, languageId);
    fileContentPerFileURI.put(fileUri, fileContent);
    analyzeAsync(fileUri, true);
  }

  public void didChange(URI fileUri, String fileContent) {
    fileContentPerFileURI.put(fileUri, fileContent);
    eventMap.put(fileUri, System.currentTimeMillis());
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
    languageIdPerFileURI.remove(fileUri);
    fileContentPerFileURI.remove(fileUri);
    javaConfigPerFileURI.remove(fileUri);
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
    final Optional<GetJavaConfigResponse> javaConfigOpt = getJavaConfigFromCacheOrFetch(fileUri);
    if (isJava(fileUri) && !javaConfigOpt.isPresent()) {
      LOG.debug("Skipping analysis of Java file '{}' because SonarLint was unable to query project configuration (classpath, source level, ...)", fileUri);
      return;
    }
    String content = fileContentPerFileURI.get(fileUri);
    Map<URI, PublishDiagnosticsParams> files = new HashMap<>();
    files.put(fileUri, newPublishDiagnostics(fileUri));

    Optional<WorkspaceFolderWrapper> workspaceFolder = workspaceFoldersManager.findFolderForFile(fileUri);

    WorkspaceFolderSettings settings = workspaceFolder.map(WorkspaceFolderWrapper::getSettings)
      .orElse(settingsManager.getCurrentDefaultFolderSettings());

    Path baseDir = workspaceFolder.map(WorkspaceFolderWrapper::getRootPath)
      // Default to take file parent dir if file is not part of any workspace
      .orElse(Paths.get(fileUri).getParent());

    IssueListener issueListener = createIssueListener(files);

    Optional<ProjectBindingWrapper> binding = bindingManager.getBinding(fileUri);
    AnalysisResultsWrapper analysisResults;
    try {
      if (binding.isPresent()) {
        ConnectedSonarLintEngine connectedEngine = binding.get().getEngine();
        if (!connectedEngine.getExcludedFiles(binding.get().getBinding(),
          singleton(fileUri),
          uri -> getFileRelativePath(baseDir, uri),
          uri -> isTest(settings, uri, javaConfigOpt))
          .isEmpty()) {
          LOG.debug("Skip analysis of excluded file: {}", fileUri);
          return;
        }
        LOG.info("Analyzing file '{}'...", fileUri);
        analysisResults = analyzeConnected(binding.get(), settings, baseDir, fileUri, content, issueListener, shouldFetchServerIssues, javaConfigOpt);
      } else {
        LOG.info("Analyzing file '{}'...", fileUri);
        analysisResults = analyzeStandalone(settings, baseDir, fileUri, content, issueListener, javaConfigOpt);
      }
      SkippedPluginsNotifier.notifyOnceForSkippedPlugins(analysisResults.results, analysisResults.allPlugins, client);

      Collection<Language> analyzedLanguages = analysisResults.results.languagePerFile().values();
      if (!analyzedLanguages.isEmpty()) {
        telemetry.analysisDoneOnSingleLanguage(analyzedLanguages.iterator().next(), analysisResults.analysisTime);
      }

      // Ignore files with parsing error
      analysisResults.results.failedAnalysisFiles().stream()
        .map(ClientInputFile::getClientObject)
        .forEach(files::remove);
    } catch (Exception e) {
      LOG.error("Analysis failed.", e);
    }

    // Check if file has not being closed during the analysis
    if (fileContentPerFileURI.containsKey(fileUri)) {
      LOG.info("Found {} issue(s)", files.values().stream().mapToInt(p -> p.getDiagnostics().size()).sum());
      files.values().forEach(client::publishDiagnostics);
    }
  }

  private Optional<GetJavaConfigResponse> getJavaConfigFromCacheOrFetch(URI fileUri) {
    Optional<GetJavaConfigResponse> javaConfigOpt;
    try {
      javaConfigOpt = getJavaConfigFromCacheOrFetchAsync(fileUri).get(1, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      Utils.interrupted(e);
      javaConfigOpt = empty();
    } catch (Exception e) {
      LOG.warn("Unable to get Java config", e);
      javaConfigOpt = empty();
    }
    return javaConfigOpt;
  }

  private static IssueListener createIssueListener(Map<URI, PublishDiagnosticsParams> files) {
    return issue -> {
      ClientInputFile inputFile = issue.getInputFile();
      if (inputFile != null) {
        URI uri = inputFile.getClientObject();
        PublishDiagnosticsParams publish = files.computeIfAbsent(uri, AnalysisManager::newPublishDiagnostics);
        convert(issue).ifPresent(publish.getDiagnostics()::add);
      }
    };
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

  private AnalysisResultsWrapper analyzeStandalone(WorkspaceFolderSettings settings, Path baseDir, URI uri, String content, IssueListener issueListener,
    Optional<GetJavaConfigResponse> javaConfig) {
    StandaloneAnalysisConfiguration configuration = StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFiles(new DefaultClientInputFile(uri, getFileRelativePath(baseDir, uri), content, isTest(settings, uri, javaConfig), languageIdPerFileURI.get(uri)))
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
      () -> {});
  }


  public AnalysisResultsWrapper analyzeConnected(ProjectBindingWrapper binding, WorkspaceFolderSettings settings, Path baseDir, URI uri, String content,
    IssueListener issueListener, boolean shouldFetchServerIssues, Optional<GetJavaConfigResponse> javaConfig) {
    ConnectedAnalysisConfiguration configuration = ConnectedAnalysisConfiguration.builder()
      .setProjectKey(settings.getProjectKey())
      .setBaseDir(baseDir)
      .addInputFile(new DefaultClientInputFile(uri, getFileRelativePath(baseDir, uri), content, isTest(settings, uri, javaConfig), languageIdPerFileURI.get(uri)))
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

  static Optional<Diagnostic> convert(Issue issue) {
    if (issue.getStartLine() != null) {
      Range range = position(issue);
      Diagnostic diagnostic = new Diagnostic();
      DiagnosticSeverity severity = severity(issue.getSeverity());

      diagnostic.setSeverity(severity);
      diagnostic.setRange(range);
      diagnostic.setCode(issue.getRuleKey());
      diagnostic.setMessage(issue.getMessage());
      diagnostic.setSource(SONARLINT_SOURCE);

      List<Flow> flows = issue.flows();
      // If multiple flows with more than 1 location, keep only the first flow
      if (flows.size() > 1 && flows.stream().anyMatch(f -> f.locations().size() > 1)) {
        flows = Collections.singletonList(flows.get(0));
      }
      diagnostic.setRelatedInformation(flows
        .stream()
        .flatMap(f -> f.locations().stream())
        // Message is mandatory in lsp
        .filter(l -> nonNull(l.getMessage()))
        // Ignore global issue locations
        .filter(l -> nonNull(l.getInputFile()))
        .map(l -> {
          DiagnosticRelatedInformation rel = new DiagnosticRelatedInformation();
          rel.setMessage(l.getMessage());
          rel.setLocation(new Location(l.getInputFile().uri().toString(), position(l)));
          return rel;
        }).collect(Collectors.toList()));

      return Optional.of(diagnostic);
    }
    return Optional.empty();
  }

  private static DiagnosticSeverity severity(String severity) {
    switch (severity.toUpperCase(Locale.ENGLISH)) {
      case "BLOCKER":
      case "CRITICAL":
        return DiagnosticSeverity.Error;
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

  private static Range position(IssueLocation location) {
    return new Range(
      new Position(
        location.getStartLine() - 1,
        location.getStartLineOffset()),
      new Position(
        location.getEndLine() - 1,
        location.getEndLineOffset()));
  }

  private static PublishDiagnosticsParams newPublishDiagnostics(URI newUri) {
    PublishDiagnosticsParams p = new PublishDiagnosticsParams();

    p.setDiagnostics(new ArrayList<>());
    p.setUri(newUri.toString());

    return p;
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
      if (isJava(fileUri)) {
        analyzeAsync(fileUri, false);
      }
    }
  }

  private Map<String, String> configureJavaProperties(URI fileUri) {
    Optional<GetJavaConfigResponse> cachedJavaConfigOpt = ofNullable(javaConfigPerFileURI.get(fileUri)).orElse(empty());
    return cachedJavaConfigOpt.map(cachedJavaConfig -> {
      Map<String, String> props = new HashMap<>();
      props.put("sonar.java.source", cachedJavaConfig.getSourceLevel());
      if (!cachedJavaConfig.isTest()) {
        props.put("sonar.java.libraries", Stream.of(cachedJavaConfig.getClasspath()).collect(joining(",")));
      } else {
        props.put("sonar.java.test.libraries", Stream.of(cachedJavaConfig.getClasspath()).collect(joining(",")));
      }
      return props;
    }).orElse(emptyMap());
  }

  /**
   * Try to fetch Java config. In case of any error, cache an empty result to avoid repeted calls.
   */
  private CompletableFuture<Optional<GetJavaConfigResponse>> getJavaConfigFromCacheOrFetchAsync(URI fileUri) {
    if (!isJava(fileUri)) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    Optional<GetJavaConfigResponse> javaConfigFromCache = javaConfigPerFileURI.get(fileUri);
    if (javaConfigFromCache != null) {
      return CompletableFuture.completedFuture(javaConfigFromCache);
    }
    return client.getJavaConfig(fileUri.toString())
      .handle((r, t) -> {
        if (t != null) {
          LOG.error("Unable to fetch Java configuration of file " + fileUri, t);
        }
        return r;
      })
      .thenApply(javaConfig -> {
        Optional<GetJavaConfigResponse> configOpt = ofNullable(javaConfig);
        javaConfigPerFileURI.put(fileUri, configOpt);
        LOG.debug("Cached Java config for file '{}'", fileUri);
        return configOpt;
      });
  }

  public void didClasspathUpdate(String projectUri) {
    for (Iterator<Entry<URI, Optional<GetJavaConfigResponse>>> it = javaConfigPerFileURI.entrySet().iterator(); it.hasNext();) {
      Entry<URI, Optional<GetJavaConfigResponse>> entry = it.next();
      // If we have cached an empty result, clear the cache on classpath update to give a chance to next analysis to fetch a correct value
      if (entry.getValue().map(c -> c.getProjectRoot().equals(projectUri)).orElse(true)) {
        it.remove();
        LOG.debug("Evicted Java config cache for {}", entry.getKey());
      }
    }
    analyzeAllOpenJavaFiles();
  }

  public void didServerModeChange(SonarLintExtendedLanguageServer.ServerMode serverMode) {
    LOG.debug("Clearing Java config cache on server mode change");
    javaConfigPerFileURI.clear();
    if (serverMode == SonarLintExtendedLanguageServer.ServerMode.STANDARD) {
      analyzeAllOpenJavaFiles();
    }
  }

  private boolean isTest(WorkspaceFolderSettings settings, URI fileUri, Optional<GetJavaConfigResponse> javaConfig) {
    if (isJava(fileUri)
      && javaConfig
        .map(GetJavaConfigResponse::isTest)
        .orElse(false)) {
      LOG.debug("Classified as test by vscode-java");
      return true;
    }
    if (settings.getTestMatcher().matches(Paths.get(fileUri))) {
      LOG.debug("Classified as test by configured 'testFilePattern' setting");
      return true;
    }
    return false;
  }

  private boolean isJava(URI fileUri) {
    return "java".equals(languageIdPerFileURI.get(fileUri));
  }

}
