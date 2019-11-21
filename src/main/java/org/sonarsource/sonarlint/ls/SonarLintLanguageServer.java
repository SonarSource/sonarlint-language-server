/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.sonar.api.internal.apachecommons.lang.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
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
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

import static java.net.URI.create;
import static java.util.Collections.singleton;
import static java.util.Objects.nonNull;

public class SonarLintLanguageServer implements SonarLintExtendedLanguageServer, WorkspaceService, TextDocumentService {

  private static final Logger LOG = Loggers.get(SonarLintLanguageServer.class);

  public static final String TYPESCRIPT_PATH_PROP = "sonar.typescript.internal.typescriptLocation";
  private static final String TYPESCRIPT_LOCATION = "typeScriptLocation";

  private static final String SONARLINT_SOURCE = "sonarlint";
  private static final String SONARLINT_OPEN_RULE_DESCRIPTION_COMMAND = "SonarLint.OpenRuleDesc";
  private static final String SONARLINT_DEACTIVATE_RULE_COMMAND = "SonarLint.DeactivateRule";
  static final String SONARLINT_UPDATE_ALL_BINDINGS_COMMAND = "SonarLint.UpdateAllBindings";
  static final String SONARLINT_REFRESH_DIAGNOSTICS_COMMAND = "SonarLint.RefreshDiagnostics";
  private static final List<String> SONARLINT_COMMANDS = Arrays.asList(
    SONARLINT_UPDATE_ALL_BINDINGS_COMMAND,
    SONARLINT_REFRESH_DIAGNOSTICS_COMMAND);

  private final SonarLintExtendedLanguageClient client;
  private final Future<?> backgroundProcess;

  private final Map<URI, String> languageIdPerFileURI = new HashMap<>();
  private final SonarLintTelemetry telemetry = new SonarLintTelemetry();

  private final LanguageClientLogOutput clientLogOutput;
  private final Collection<URL> standaloneAnalyzers;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private String typeScriptPath;
  private StandaloneSonarLintEngine standaloneEngine;

  /**
   * Keep track of value 'sonarlint.trace.server' on client side. Not used currently, but keeping it just in case.
   */
  private TraceValues traceLevel;
  private ExecutorService analysisExecutor;

  SonarLintLanguageServer(InputStream inputStream, OutputStream outputStream, Collection<URL> analyzers) {
    this.standaloneAnalyzers = analyzers;
    Launcher<SonarLintExtendedLanguageClient> launcher = new Launcher.Builder<SonarLintExtendedLanguageClient>()
      .setLocalService(this)
      .setRemoteInterface(SonarLintExtendedLanguageClient.class)
      .setInput(inputStream)
      .setOutput(outputStream)
      .create();

    this.client = launcher.getRemoteProxy();

    clientLogOutput = new LanguageClientLogOutput(this.client);
    Loggers.setTarget(clientLogOutput);
    this.settingsManager = new SettingsManager(this.client);
    this.settingsManager.addListener(telemetry);
    this.workspaceFoldersManager = new WorkspaceFoldersManager(settingsManager);
    this.bindingManager = new ProjectBindingManager(workspaceFoldersManager, settingsManager, clientLogOutput, client);
    this.settingsManager.addListener((WorkspaceSettingsChangeListener) bindingManager);
    this.settingsManager.addListener((WorkspaceFolderSettingsChangeListener) bindingManager);
    this.workspaceFoldersManager.addListener(settingsManager);
    this.analysisExecutor = Executors.newSingleThreadExecutor();

    backgroundProcess = launcher.startListening();
  }

  static SonarLintLanguageServer bySocket(int port, Collection<URL> analyzers) throws IOException {
    Socket socket = new Socket("localhost", port);

    return new SonarLintLanguageServer(socket.getInputStream(), socket.getOutputStream(), analyzers);
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      this.traceLevel = parseTraceLevel(params.getTrace());

      workspaceFoldersManager.initialize(params.getWorkspaceFolders());

      Map<String, Object> options = Utils.parseToMap(params.getInitializationOptions());

      String productKey = (String) options.get("productKey");
      // deprecated, will be ignored when productKey present
      String telemetryStorage = (String) options.get("telemetryStorage");

      String productName = (String) options.get("productName");
      String productVersion = (String) options.get("productVersion");
      String ideVersion = (String) options.get("ideVersion");

      typeScriptPath = (String) options.get(TYPESCRIPT_LOCATION);

      bindingManager.initialize(Paths.get(typeScriptPath));

      telemetry.init(productKey, telemetryStorage, productName, productVersion, ideVersion, bindingManager::usesConnectedMode, bindingManager::usesSonarCloud);

      InitializeResult result = new InitializeResult();
      ServerCapabilities c = new ServerCapabilities();
      c.setTextDocumentSync(getTextDocumentSyncOptions());
      c.setCodeActionProvider(true);
      c.setExecuteCommandProvider(new ExecuteCommandOptions(SONARLINT_COMMANDS));
      c.setWorkspace(getWorkspaceServerCapabilities());

      result.setCapabilities(c);
      return result;
    });
  }

  private static WorkspaceServerCapabilities getWorkspaceServerCapabilities() {
    WorkspaceFoldersOptions options = new WorkspaceFoldersOptions();
    options.setSupported(true);
    options.setChangeNotifications(true);

    WorkspaceServerCapabilities capabilities = new WorkspaceServerCapabilities();
    capabilities.setWorkspaceFolders(options);
    return capabilities;
  }

  private static TextDocumentSyncOptions getTextDocumentSyncOptions() {
    TextDocumentSyncOptions textDocumentSyncOptions = new TextDocumentSyncOptions();
    textDocumentSyncOptions.setOpenClose(true);
    textDocumentSyncOptions.setChange(TextDocumentSyncKind.Full);
    textDocumentSyncOptions.setSave(new SaveOptions(true));
    return textDocumentSyncOptions;
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      if (standaloneEngine != null) {
        standaloneEngine.stop();
      }
      bindingManager.shutdown();
      telemetry.stop();
      return new Object();
    });
  }

  @Override
  public void exit() {
    backgroundProcess.cancel(true);
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return this;
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      List<Either<Command, CodeAction>> commands = new ArrayList<>();
      URI uri = create(params.getTextDocument().getUri());
      Optional<ProjectBindingWrapper> binding = bindingManager.getBinding(uri);
      for (Diagnostic d : params.getContext().getDiagnostics()) {
        if (SONARLINT_SOURCE.equals(d.getSource())) {
          String ruleKey = d.getCode();
          List<Object> ruleDescriptionParams = getOpenRuleDescriptionParams(binding, ruleKey);
          // May take time to initialize the engine so check for cancellation just after
          cancelToken.checkCanceled();
          if (!ruleDescriptionParams.isEmpty()) {
            commands.add(Either.forLeft(
              new Command(String.format("Open description of SonarLint rule '%s'", ruleKey),
                SONARLINT_OPEN_RULE_DESCRIPTION_COMMAND,
                ruleDescriptionParams)));
          }
          if (!binding.isPresent()) {
            commands.add(Either.forLeft(
              new Command(String.format("Deactivate rule '%s'", ruleKey),
                SONARLINT_DEACTIVATE_RULE_COMMAND,
                Collections.singletonList(ruleKey))));
          }
        }
      }
      return commands;
    });
  }

  private synchronized StandaloneSonarLintEngine getOrCreateStandaloneEngine() {
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
      extraProperties.put(TYPESCRIPT_PATH_PROP, typeScriptPath);
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

  private List<Object> getOpenRuleDescriptionParams(Optional<ProjectBindingWrapper> binding, String ruleKey) {
    RuleDetails ruleDetails;
    if (!binding.isPresent()) {
      ruleDetails = getOrCreateStandaloneEngine().getRuleDetails(ruleKey)
        .orElseThrow(() -> new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unknown rule with key: " + ruleKey, null)));
    } else {
      ConnectedSonarLintEngine engine = binding.get().getEngine();
      if (engine != null) {
        ruleDetails = engine.getRuleDetails(ruleKey);
      } else {
        return Collections.emptyList();
      }
    }
    String ruleName = ruleDetails.getName();
    String htmlDescription = getHtmlDescription(ruleDetails);
    String type = ruleDetails.getType();
    String severity = ruleDetails.getSeverity();
    return Arrays.asList(ruleKey, ruleName, htmlDescription, type, severity);
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    URI uri = create(params.getTextDocument().getUri());
    languageIdPerFileURI.put(uri, params.getTextDocument().getLanguageId());
    analyzeAsync(uri, params.getTextDocument().getText(), true);
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    URI uri = create(params.getTextDocument().getUri());
    analyzeAsync(uri, params.getContentChanges().get(0).getText(), false);
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    URI uri = create(params.getTextDocument().getUri());
    languageIdPerFileURI.remove(uri);
    // Clear issues
    client.publishDiagnostics(newPublishDiagnostics(uri));
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    String content = params.getText();
    if (content != null) {
      URI uri = create(params.getTextDocument().getUri());
      analyzeAsync(uri, content, false);
    }
  }

  @Override
  public CompletableFuture<Map<String, List<RuleDescription>>> listAllRules() {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      Map<String, List<RuleDescription>> result = new HashMap<>();
      Map<String, String> languagesNameByKey = getOrCreateStandaloneEngine().getAllLanguagesNameByKey();
      getOrCreateStandaloneEngine().getAllRuleDetails()
        .forEach(d -> {
          String languageName = languagesNameByKey.get(d.getLanguageKey());
          if (!result.containsKey(languageName)) {
            result.put(languageName, new ArrayList<>());
          }
          result.get(languageName).add(RuleDescription.of(d));
        });
      return result;
    });
  }

  private void analyzeAsync(URI fileUri, String content, boolean shouldFetchServerIssues) {
    if (!fileUri.getScheme().equalsIgnoreCase("file")) {
      LOG.warn("URI '{}' is not a file, analysis not supported", fileUri);
      return;
    }
    analysisExecutor.execute(() -> {
      analyze(fileUri, content, shouldFetchServerIssues);
    });
  }

  private void analyze(URI fileUri, String content, boolean shouldFetchServerIssues) {
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

  private IssueListener createIssueListener(Map<URI, PublishDiagnosticsParams> files) {
    return issue -> {
      ClientInputFile inputFile = issue.getInputFile();
      if (inputFile != null) {
        URI uri = inputFile.getClientObject();
        PublishDiagnosticsParams publish = files.computeIfAbsent(uri, SonarLintLanguageServer::newPublishDiagnostics);
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

  private boolean isTest(WorkspaceFolderSettings settings, URI uri) {
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

  @Override
  public WorkspaceService getWorkspaceService() {
    return this;
  }

  @Override
  public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      List<Object> args = params.getArguments();
      switch (params.getCommand()) {
        case SONARLINT_UPDATE_ALL_BINDINGS_COMMAND:
          bindingManager.updateAllBindings();
          break;
        case SONARLINT_REFRESH_DIAGNOSTICS_COMMAND:
          Gson gson = new Gson();
          List<Document> docsToRefresh = args == null ? Collections.emptyList()
            : args.stream().map(arg -> gson.fromJson(arg.toString(), Document.class)).collect(Collectors.toList());
          docsToRefresh.forEach(doc -> analyzeAsync(create(doc.uri), doc.text, false));
          break;
        default:
          throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unsupported command: " + params.getCommand(), null));
      }
      return null;
    });
  }

  // visible for testing
  static String getHtmlDescription(RuleDetails ruleDetails) {
    String htmlDescription = ruleDetails.getHtmlDescription();
    String extendedDescription = ruleDetails.getExtendedDescription();
    if (!extendedDescription.isEmpty()) {
      htmlDescription += "<div>" + extendedDescription + "</div>";
    }
    return htmlDescription;
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
    settingsManager.didChangeConfiguration();
    workspaceFoldersManager.didChangeConfiguration();
  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    // No watched files
  }

  @Override
  public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
    workspaceFoldersManager.didChangeWorkspaceFolders(params.getEvent());
  }

  @Override
  public void setTraceNotification(SetTraceNotificationParams params) {
    this.traceLevel = parseTraceLevel(params.getValue());
  }

  private static TraceValues parseTraceLevel(@Nullable String trace) {
    return Optional.ofNullable(trace)
      .map(String::toUpperCase)
      .map(TraceValues::valueOf)
      .orElse(TraceValues.OFF);
  }

  static class Document {
    final String uri;
    final String text;

    public Document(String uri, String text) {
      this.uri = uri;
      this.text = text;
    }
  }
}
