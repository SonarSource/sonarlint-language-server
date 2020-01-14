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
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.sonar.api.internal.apachecommons.lang.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.Flow;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.ServerIssueTrackerWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;

import static java.util.Collections.singleton;
import static java.util.Objects.nonNull;

public class AnalysisManager {

  private static final Logger LOG = Loggers.get(AnalysisManager.class);

  public static final String TYPESCRIPT_PATH_PROP = "sonar.typescript.internal.typescriptLocation";
  private static final String SONARLINT_SOURCE = "sonarlint";

  private final SonarLintExtendedLanguageClient client;

  private final Map<URI, String> languageIdPerFileURI = new ConcurrentHashMap<>();
  private final Map<URI, String> fileContentPerFileURI = new ConcurrentHashMap<>();

  private final SonarLintTelemetry telemetry;
  private final LanguageClientLogOutput clientLogOutput;
  private final Collection<URL> standaloneAnalyzers;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  @CheckForNull
  private Path typeScriptPath;
  private StandaloneSonarLintEngine standaloneEngine;

  private ExecutorService analysisExecutor;

  public AnalysisManager(Collection<URL> standaloneAnalyzers, LanguageClientLogOutput clientLogOutput, SonarLintExtendedLanguageClient client,
    SonarLintTelemetry telemetry,
    WorkspaceFoldersManager workspaceFoldersManager, SettingsManager settingsManager, ProjectBindingManager bindingManager) {
    this.standaloneAnalyzers = standaloneAnalyzers;
    this.clientLogOutput = clientLogOutput;
    this.client = client;
    this.telemetry = telemetry;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.settingsManager = settingsManager;
    this.bindingManager = bindingManager;
    this.analysisExecutor = Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint analysis", false));
  }

  synchronized StandaloneSonarLintEngine getOrCreateStandaloneEngine() {
    if (standaloneEngine == null) {
      standaloneEngine = createStandaloneEngine();
    }
    return standaloneEngine;
  }

  private StandaloneSonarLintEngine createStandaloneEngine() {
    LOG.debug("Starting standalone SonarLint engine...");
    LOG.debug("Using {} analyzers", standaloneAnalyzers.size());

    try {
      Map<String, String> extraProperties = new HashMap<>();
      if (typeScriptPath != null) {
        extraProperties.put(TYPESCRIPT_PATH_PROP, typeScriptPath.toString());
      }
      StandaloneGlobalConfiguration configuration = StandaloneGlobalConfiguration.builder()
        .setExtraProperties(extraProperties)
        .addPlugins(standaloneAnalyzers.toArray(new URL[0]))
        .setLogOutput(clientLogOutput)
        .build();

      StandaloneSonarLintEngine engine = new StandaloneSonarLintEngineImpl(configuration);
      LOG.debug("Standalone SonarLint engine started");
      return engine;
    } catch (Exception e) {
      LOG.error("Error starting standalone SonarLint engine", e);
      throw new IllegalStateException(e);
    }
  }

  public void didOpen(URI fileUri, String languageId, String fileContent) {
    languageIdPerFileURI.put(fileUri, languageId);
    fileContentPerFileURI.put(fileUri, fileContent);
    analyzeAsync(fileUri, true);
  }

  public void didChange(URI fileUri, String fileContent) {
    fileContentPerFileURI.put(fileUri, fileContent);
    analyzeAsync(fileUri, false);
  }

  public void didClose(URI fileUri) {
    languageIdPerFileURI.remove(fileUri);
    fileContentPerFileURI.remove(fileUri);
    // TODO Clear issues after all pending analysis have been processed
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
    analysisExecutor.execute(() -> analyze(fileUri, shouldFetchServerIssues));
  }

  private void analyze(URI fileUri, boolean shouldFetchServerIssues) {
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
          uri -> isTest(settings, uri))
          .isEmpty()) {
          LOG.debug("Skip analysis of excluded file: {}", fileUri);
          return;
        }
        analysisResults = analyzeConnected(binding.get(), settings, baseDir, fileUri, content, issueListener, shouldFetchServerIssues);
      } else {
        analysisResults = analyzeStandalone(settings, baseDir, fileUri, content, issueListener);
      }

      telemetry.analysisDoneOnSingleFile(StringUtils.substringAfterLast(fileUri.toString(), "."), analysisResults.analysisTime);

      // Ignore files with parsing error
      analysisResults.results.failedAnalysisFiles().stream()
        .map(ClientInputFile::getClientObject)
        .forEach(files::remove);
    } catch (Exception e) {
      LOG.error("Analysis failed.", e);
    }

    files.values().forEach(client::publishDiagnostics);
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

    AnalysisResultsWrapper(AnalysisResults results, int analysisTime) {
      this.results = results;
      this.analysisTime = analysisTime;
    }
  }

  private AnalysisResultsWrapper analyzeStandalone(WorkspaceFolderSettings settings, Path baseDir, URI uri, String content, IssueListener issueListener) {
    StandaloneAnalysisConfiguration configuration = StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFiles(new DefaultClientInputFile(uri, getFileRelativePath(baseDir, uri), content, isTest(settings, uri), languageIdPerFileURI.get(uri)))
      .putAllExtraProperties(settings.getAnalyzerProperties())
      .addExcludedRules(settingsManager.getCurrentSettings().getExcludedRules())
      .addIncludedRules(settingsManager.getCurrentSettings().getIncludedRules())
      .build();
    LOG.debug("Analysis triggered on '{}' with configuration: \n{}", uri, configuration.toString());

    long start = System.currentTimeMillis();
    StandaloneSonarLintEngine engine = getOrCreateStandaloneEngine();
    AnalysisResults analysisResults = engine.analyze(configuration, issueListener, null, null);
    int analysisTime = (int) (System.currentTimeMillis() - start);

    return new AnalysisResultsWrapper(analysisResults, analysisTime);
  }

  public AnalysisResultsWrapper analyzeConnected(ProjectBindingWrapper binding, WorkspaceFolderSettings settings, Path baseDir, URI uri, String content,
    IssueListener issueListener, boolean shouldFetchServerIssues) {
    ConnectedAnalysisConfiguration configuration = ConnectedAnalysisConfiguration.builder()
      .setProjectKey(settings.getProjectKey())
      .setBaseDir(baseDir)
      .addInputFile(new DefaultClientInputFile(uri, getFileRelativePath(baseDir, uri), content, isTest(settings, uri), languageIdPerFileURI.get(uri)))
      .putAllExtraProperties(settings.getAnalyzerProperties())
      .build();
    if (settingsManager.getCurrentSettings().hasLocalRuleConfiguration()) {
      LOG.debug("Local rules settings are ignored, using quality profile from server");
    }
    LOG.debug("Analysis triggered on '{}' with configuration: \n{}", uri, configuration.toString());

    List<Issue> issues = new LinkedList<>();
    IssueListener collector = issues::add;

    long start = System.currentTimeMillis();
    AnalysisResults analysisResults;
    analysisResults = binding.getEngine().analyze(configuration, collector, null, null);

    String filePath = FileUtils.toSonarQubePath(getFileRelativePath(baseDir, uri));
    ServerIssueTrackerWrapper serverIssueTracker = binding.getServerIssueTracker();
    serverIssueTracker.matchAndTrack(filePath, issues, issueListener, shouldFetchServerIssues);

    int analysisTime = (int) (System.currentTimeMillis() - start);

    return new AnalysisResultsWrapper(analysisResults, analysisTime);
  }

  private static boolean isTest(WorkspaceFolderSettings settings, URI uri) {
    return settings.getTestMatcher().matches(Paths.get(uri));
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

  public void initialize(@Nullable Path typeScriptPath) {
    this.typeScriptPath = typeScriptPath;
  }

  public void shutdown() {
    analysisExecutor.shutdown();
    if (standaloneEngine != null) {
      standaloneEngine.stop();
    }
  }

}
