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
import com.google.gson.JsonPrimitive;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeNotebookDocumentParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseNotebookDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenNotebookDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveNotebookDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.NotebookDocumentSyncRegistrationOptions;
import org.eclipse.lsp4j.NotebookSelector;
import org.eclipse.lsp4j.NotebookSelectorCell;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WindowClientCapabilities;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.NotebookDocumentService;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.GetSupportedFilePatternsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.GetSupportedFilePatternsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.binding.GetBindingSuggestionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ReopenIssueResponse;
import org.sonarsource.sonarlint.core.clientapi.client.binding.GetBindingSuggestionsResponse;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult;
import org.sonarsource.sonarlint.ls.backend.BackendInitParams;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.clientapi.SonarLintVSCodeClient;
import org.sonarsource.sonarlint.ls.connected.DelegatingIssue;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.TaintIssuesUpdater;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.connected.api.RequestsHandlerServer;
import org.sonarsource.sonarlint.ls.connected.events.ServerSentEventsHandler;
import org.sonarsource.sonarlint.ls.connected.events.ServerSentEventsHandlerService;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.connected.notifications.TaintVulnerabilityRaisedNotification;
import org.sonarsource.sonarlint.ls.connected.sync.ServerSynchronizer;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.folders.ModuleEventsProcessor;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderBranchManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersProvider;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.notebooks.NotebookDiagnosticPublisher;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.notebooks.VersionedOpenNotebook;
import org.sonarsource.sonarlint.ls.progress.ProgressManager;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.telemetry.TelemetryInitParams;
import org.sonarsource.sonarlint.ls.util.ExitingInputStream;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static java.net.URI.create;
import static java.util.Optional.ofNullable;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_SECURITY_HOTSPOT_FLOWS;
import static org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult.failure;
import static org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult.success;
import static org.sonarsource.sonarlint.ls.util.Utils.getConnectionNameFromConnectionCheckParams;
import static org.sonarsource.sonarlint.ls.util.Utils.getValidateConnectionParamsForNewConnection;
import static org.sonarsource.sonarlint.ls.util.Utils.hotspotStatusOfTitle;
import static org.sonarsource.sonarlint.ls.util.Utils.hotspotStatusValueOfHotspotReviewStatus;

public class SonarLintLanguageServer implements SonarLintExtendedLanguageServer, WorkspaceService, TextDocumentService, NotebookDocumentService {

  public static final String JUPYTER_NOTEBOOK_TYPE = "jupyter-notebook";
  public static final String PYTHON_LANGUAGE = "python";
  private final SonarLintExtendedLanguageClient client;
  private final SonarLintTelemetry telemetry;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private final AnalysisScheduler analysisScheduler;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final OpenFilesCache openFilesCache;
  private final OpenNotebooksCache openNotebooksCache;
  private final EnginesFactory enginesFactory;
  private final StandaloneEngineManager standaloneEngineManager;
  private final CommandManager commandManager;
  private final ProgressManager progressManager;
  private final ExecutorService threadPool;
  private final RequestsHandlerServer requestsHandlerServer;
  private final WorkspaceFolderBranchManager branchManager;
  private final JavaConfigCache javaConfigCache;
  private final IssuesCache issuesCache;
  private final IssuesCache securityHotspotsCache;
  private final DiagnosticPublisher diagnosticPublisher;
  private final ScmIgnoredCache scmIgnoredCache;
  private final ServerSynchronizer serverSynchronizer;
  private final LanguageClientLogger lsLogOutput;

  private final TaintIssuesUpdater taintIssuesUpdater;
  private final NotebookDiagnosticPublisher notebookDiagnosticPublisher;

  private String appName;

  /**
   * Keep track of value 'sonarlint.trace.server' on client side. Not used currently, but keeping it just in case.
   */
  private TraceValue traceLevel;

  private final ModuleEventsProcessor moduleEventsProcessor;
  private final BackendServiceFacade backendServiceFacade;
  private final Collection<Path> analyzers;
  private final CountDownLatch shutdownLatch;

  SonarLintLanguageServer(InputStream inputStream, OutputStream outputStream, Collection<Path> analyzers) {
    this.threadPool = Executors.newCachedThreadPool(Utils.threadFactory("SonarLint LSP message processor", false));
    var input = new ExitingInputStream(inputStream, this);
    var launcher = new Launcher.Builder<SonarLintExtendedLanguageClient>()
      .setLocalService(this)
      .setRemoteInterface(SonarLintExtendedLanguageClient.class)
      .setInput(input)
      .setOutput(outputStream)
      .setExecutorService(threadPool)
      .create();

    this.analyzers = analyzers;
    this.client = launcher.getRemoteProxy();
    this.lsLogOutput = new LanguageClientLogger(this.client);
    var globalLogOutput = new LanguageClientLogOutput(lsLogOutput, false);
    SonarLintLogger.setTarget(globalLogOutput);
    this.openFilesCache = new OpenFilesCache(lsLogOutput);

    this.issuesCache = new IssuesCache();
    this.securityHotspotsCache = new IssuesCache();
    this.taintVulnerabilitiesCache = new TaintVulnerabilitiesCache();
    this.notebookDiagnosticPublisher = new NotebookDiagnosticPublisher(client, issuesCache);
    this.openNotebooksCache = new OpenNotebooksCache(lsLogOutput, notebookDiagnosticPublisher);
    this.notebookDiagnosticPublisher.setOpenNotebooksCache(openNotebooksCache);
    this.diagnosticPublisher = new DiagnosticPublisher(client, taintVulnerabilitiesCache, issuesCache, securityHotspotsCache, openNotebooksCache);
    this.progressManager = new ProgressManager(client);
    this.requestsHandlerServer = new RequestsHandlerServer(client);
    var vsCodeClient = new SonarLintVSCodeClient(client, requestsHandlerServer);
    this.backendServiceFacade = new BackendServiceFacade(new SonarLintBackendImpl(vsCodeClient));
    vsCodeClient.setBackendServiceFacade(backendServiceFacade);
    this.workspaceFoldersManager = new WorkspaceFoldersManager(backendServiceFacade);
    this.settingsManager = new SettingsManager(this.client, this.workspaceFoldersManager, backendServiceFacade);
    vsCodeClient.setSettingsManager(settingsManager);
    backendServiceFacade.setSettingsManager(settingsManager);
    var nodeJsRuntime = new NodeJsRuntime(settingsManager);
    var fileTypeClassifier = new FileTypeClassifier();
    javaConfigCache = new JavaConfigCache(client, openFilesCache, lsLogOutput);
    this.enginesFactory = new EnginesFactory(analyzers, getEmbeddedPluginsToPath(), globalLogOutput, nodeJsRuntime,
      new WorkspaceFoldersProvider(workspaceFoldersManager, fileTypeClassifier, javaConfigCache));
    this.standaloneEngineManager = new StandaloneEngineManager(enginesFactory);
    this.settingsManager.addListener(lsLogOutput);
    this.bindingManager = new ProjectBindingManager(enginesFactory, workspaceFoldersManager, settingsManager, client, globalLogOutput,
      taintVulnerabilitiesCache, diagnosticPublisher, backendServiceFacade, openNotebooksCache);
    vsCodeClient.setBindingManager(bindingManager);
    this.telemetry = new SonarLintTelemetry(settingsManager, bindingManager, nodeJsRuntime, backendServiceFacade);
    backendServiceFacade.setTelemetry(telemetry);
    this.settingsManager.addListener(telemetry);
    this.settingsManager.addListener((WorkspaceSettingsChangeListener) bindingManager);
    this.settingsManager.addListener((WorkspaceFolderSettingsChangeListener) bindingManager);
    this.workspaceFoldersManager.addListener(settingsManager);
    var smartNotifications = new SmartNotifications(client, telemetry);
    vsCodeClient.setSmartNotifications(smartNotifications);
    var skippedPluginsNotifier = new SkippedPluginsNotifier(client);
    this.scmIgnoredCache = new ScmIgnoredCache(client);
    this.moduleEventsProcessor = new ModuleEventsProcessor(standaloneEngineManager, workspaceFoldersManager, bindingManager, fileTypeClassifier, javaConfigCache);
    var analysisTaskExecutor = new AnalysisTaskExecutor(scmIgnoredCache, lsLogOutput, workspaceFoldersManager, bindingManager, javaConfigCache, settingsManager,
      fileTypeClassifier, issuesCache, securityHotspotsCache, taintVulnerabilitiesCache, telemetry, skippedPluginsNotifier, standaloneEngineManager, diagnosticPublisher,
      client, openNotebooksCache, notebookDiagnosticPublisher, progressManager);
    this.analysisScheduler = new AnalysisScheduler(lsLogOutput, workspaceFoldersManager, bindingManager, openFilesCache, openNotebooksCache, analysisTaskExecutor, client);
    this.workspaceFoldersManager.addListener(moduleEventsProcessor);
    bindingManager.setAnalysisManager(analysisScheduler);
    this.settingsManager.addListener((WorkspaceSettingsChangeListener) analysisScheduler);
    this.settingsManager.addListener((WorkspaceFolderSettingsChangeListener) analysisScheduler);
    this.serverSynchronizer = new ServerSynchronizer(client, progressManager, bindingManager, analysisScheduler, backendServiceFacade);
    this.commandManager = new CommandManager(client, settingsManager, bindingManager, serverSynchronizer, telemetry, taintVulnerabilitiesCache,
      issuesCache, securityHotspotsCache, backendServiceFacade, workspaceFoldersManager, openNotebooksCache);
    var taintVulnerabilityRaisedNotification = new TaintVulnerabilityRaisedNotification(client, commandManager);
    ServerSentEventsHandlerService serverSentEventsHandler = new ServerSentEventsHandler(bindingManager, taintVulnerabilitiesCache,
      taintVulnerabilityRaisedNotification, settingsManager, workspaceFoldersManager, analysisScheduler);
    vsCodeClient.setServerSentEventsHandlerService(serverSentEventsHandler);
    this.branchManager = new WorkspaceFolderBranchManager(client, bindingManager, backendServiceFacade);
    this.bindingManager.setBranchResolver(branchManager::getReferenceBranchNameForFolder);
    this.workspaceFoldersManager.addListener(this.branchManager);
    this.workspaceFoldersManager.setBindingManager(bindingManager);
    this.taintIssuesUpdater = new TaintIssuesUpdater(bindingManager, taintVulnerabilitiesCache, workspaceFoldersManager, settingsManager,
      diagnosticPublisher, backendServiceFacade);
    var cleanAsYouCodeManager = new CleanAsYouCodeManager(diagnosticPublisher, openFilesCache, backendServiceFacade);
    this.settingsManager.addListener(cleanAsYouCodeManager);
    this.shutdownLatch = new CountDownLatch(1);
    launcher.startListening();
  }

  static SonarLintLanguageServer bySocket(int port, Collection<Path> analyzers) throws IOException {
    var socket = new Socket("localhost", port);
    return new SonarLintLanguageServer(socket.getInputStream(), socket.getOutputStream(), analyzers);
  }

  static SonarLintLanguageServer byStdio(List<Path> analyzers) {
    return new SonarLintLanguageServer(System.in, System.out, analyzers);
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      this.traceLevel = parseTraceLevel(params.getTrace());

      boolean workDoneSupportedByClient = ofNullable(params.getCapabilities())
        .flatMap(capabilities -> ofNullable(capabilities.getWindow()))
        .map(WindowClientCapabilities::getWorkDoneProgress)
        .orElse(false);
      progressManager.setWorkDoneProgressSupportedByClient(workDoneSupportedByClient);

      workspaceFoldersManager.initialize(params.getWorkspaceFolders());

      var options = Utils.parseToMap(params.getInitializationOptions());
      if (options == null) {
        options = Collections.emptyMap();
      }

      var productKey = (String) options.get("productKey");
      // deprecated, will be ignored when productKey present
      var telemetryStorage = (String) options.get("telemetryStorage");

      var productName = (String) options.get("productName");
      var productVersion = (String) options.get("productVersion");
      var clientInfo = ofNullable(params.getClientInfo());
      this.appName = clientInfo.map(ci -> ci.getName()).orElse("Unknown");
      var workspaceName = (String) options.get("workspaceName");
      var clientVersion = clientInfo.map(ci -> ci.getVersion()).orElse("Unknown");
      var ideVersion = appName + " " + clientVersion;
      var firstSecretDetected = (boolean) options.getOrDefault("firstSecretDetected", false);
      var platform = (String) options.get("platform");
      var architecture = (String) options.get("architecture");
      var additionalAttributes = (Map<String, Object>) options.getOrDefault("additionalAttributes", Map.of());
      var showVerboseLogs = (boolean) options.getOrDefault("showVerboseLogs", true);
      var userAgent = productName + " " + productVersion;

      lsLogOutput.initialize(showVerboseLogs);
      analysisScheduler.initialize();
      diagnosticPublisher.initialize(firstSecretDetected);

      requestsHandlerServer.initialize(clientVersion, workspaceName);
      backendServiceFacade.setTelemetryInitParams(new TelemetryInitParams(productKey, telemetryStorage,
        productName, productVersion, ideVersion, platform, architecture, additionalAttributes));
      enginesFactory.setOmnisharpDirectory((String) additionalAttributes.get("omnisharpDirectory"));

      var c = new ServerCapabilities();
      c.setTextDocumentSync(getTextDocumentSyncOptions());
      c.setCodeActionProvider(true);
      var executeCommandOptions = new ExecuteCommandOptions(CommandManager.SONARLINT_SERVERSIDE_COMMANDS);
      executeCommandOptions.setWorkDoneProgress(true);
      c.setExecuteCommandProvider(executeCommandOptions);
      c.setWorkspace(getWorkspaceServerCapabilities());
      if (isEnableNotebooks(options)) {
        setNotebookSyncOptions(c);
      }

      var info = new ServerInfo("SonarLint Language Server", getServerVersion("slls-version.txt"));
      provideBackendInitData(productKey, userAgent);
      return new InitializeResult(c, info);
    });
  }

  @VisibleForTesting
  public static boolean isEnableNotebooks(Map<String, Object> options) {
    return (boolean) options.getOrDefault("enableNotebooks", false);
  }

  private static void setNotebookSyncOptions(ServerCapabilities c) {
    var noteBookDocumentSyncOptions = new NotebookDocumentSyncRegistrationOptions();
    var notebookSelector = new NotebookSelector();
    notebookSelector.setNotebook(JUPYTER_NOTEBOOK_TYPE);
    notebookSelector.setCells(List.of(new NotebookSelectorCell(PYTHON_LANGUAGE)));
    noteBookDocumentSyncOptions.setNotebookSelector(List.of(notebookSelector));
    c.setNotebookDocumentSync(noteBookDocumentSyncOptions);
  }

  @CheckForNull
  static String getServerVersion(String fileName) {
    var classLoader = ClassLoader.getSystemClassLoader();
    try (var is = classLoader.getResourceAsStream(fileName)) {
      try (var isr = new InputStreamReader(is, StandardCharsets.UTF_8);
           var reader = new BufferedReader(isr)) {
        return reader.lines().findFirst().orElse(null);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read server version", e);
    }
  }

  private static WorkspaceServerCapabilities getWorkspaceServerCapabilities() {
    var options = new WorkspaceFoldersOptions();
    options.setSupported(true);
    options.setChangeNotifications(true);

    var capabilities = new WorkspaceServerCapabilities();
    capabilities.setWorkspaceFolders(options);
    return capabilities;
  }

  private static TextDocumentSyncOptions getTextDocumentSyncOptions() {
    var textDocumentSyncOptions = new TextDocumentSyncOptions();
    textDocumentSyncOptions.setOpenClose(true);
    textDocumentSyncOptions.setChange(TextDocumentSyncKind.Full);
    return textDocumentSyncOptions;
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    List.<Runnable>of(
        // prevent creation of new engines
        enginesFactory::shutdown,
        analysisScheduler::shutdown,
        branchManager::shutdown,
        telemetry::stop,
        settingsManager::shutdown,
        workspaceFoldersManager::shutdown,
        moduleEventsProcessor::shutdown,
        taintIssuesUpdater::shutdown,
        // shutdown engines after the rest so that no operations remain on them, and they won't be recreated accidentally
        bindingManager::shutdown,
        serverSynchronizer::shutdown,
        standaloneEngineManager::shutdown,
        backendServiceFacade::shutdown)
      // Do last
      .forEach(this::invokeQuietly);

    // necessary to let all shutdown jobs to finish
    waitBeforeExit();
    shutdownLatch.countDown();

    return CompletableFuture.completedFuture(null);
  }

  private void waitBeforeExit() {
    try {
      threadPool.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public void waitForShutDown() throws InterruptedException {
    shutdownLatch.await();
  }

  private void invokeQuietly(Runnable call) {
    try {
      call.run();
    } catch (Exception e) {
      lsLogOutput.error("Unable to properly shutdown", e);
    }
  }

  @Override
  public void exit() {
    invokeQuietly(() -> Utils.shutdownAndAwait(threadPool, true));
    // The Socket will be closed by the client, and so remaining threads will die and the JVM will terminate
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return this;
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      return commandManager.computeCodeActions(params, cancelToken);
    });
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    var uri = create(params.getTextDocument().getUri());
    if (openNotebooksCache.isNotebook(uri)) {
      lsLogOutput.debug(String.format("Skipping text document analysis of notebook \"%s\"", uri));
      return;
    }
    runIfAnalysisNeeded(uri.toString(), () -> {
      var file = openFilesCache.didOpen(uri, params.getTextDocument().getLanguageId(), params.getTextDocument().getText(), params.getTextDocument().getVersion());
      analysisScheduler.didOpen(file);
      taintIssuesUpdater.updateTaintIssuesAsync(uri);
    });
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    var uri = create(params.getTextDocument().getUri());
    runIfAnalysisNeeded(params.getTextDocument().getUri(), () -> {
      openFilesCache.didChange(uri, params.getContentChanges().get(0).getText(), params.getTextDocument().getVersion());
      analysisScheduler.didChange(uri);
    });
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    var uri = create(params.getTextDocument().getUri());
    analysisScheduler.didClose(uri);
    openFilesCache.didClose(uri);
    javaConfigCache.didClose(uri);
    scmIgnoredCache.didClose(uri);
    issuesCache.clear(uri);
    securityHotspotsCache.clear(uri);
    diagnosticPublisher.publishDiagnostics(uri, false);
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    // Nothin to do
  }

  @Override
  public CompletableFuture<Map<String, List<Rule>>> listAllRules() {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      return commandManager.listAllStandaloneRules();
    });
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return this;
  }

  @Override
  public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      commandManager.executeCommand(params, cancelToken);
      return null;
    });
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
    settingsManager.didChangeConfiguration();
  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    moduleEventsProcessor.didChangeWatchedFiles(params.getChanges());
  }

  @Override
  public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
    var event = params.getEvent();
    workspaceFoldersManager.didChangeWorkspaceFolders(event);
  }

  @Override
  public void setTrace(SetTraceParams params) {
    this.traceLevel = parseTraceLevel(params.getValue());
  }

  private static TraceValue parseTraceLevel(@Nullable String trace) {
    return ofNullable(trace)
      .map(String::toUpperCase)
      .map(TraceValue::valueOf)
      .orElse(TraceValue.OFF);
  }

  @Override
  public void didOpen(DidOpenNotebookDocumentParams params) {
    var notebookUri = create(params.getNotebookDocument().getUri());
    runIfAnalysisNeeded(params.getNotebookDocument().getUri(), () -> {
      if (openFilesCache.getFile(notebookUri).isPresent()) {
        openFilesCache.didClose(notebookUri);
      }
      var notebookFile =
        openNotebooksCache.didOpen(notebookUri, params.getNotebookDocument().getVersion(), params.getCellTextDocuments());
      analysisScheduler.didOpen(notebookFile.asVersionedOpenFile());
    });
  }

  @Override
  public void didChange(DidChangeNotebookDocumentParams params) {
    runIfAnalysisNeeded(params.getNotebookDocument().getUri(), () -> {
      openNotebooksCache.didChange(create(params.getNotebookDocument().getUri()), params.getNotebookDocument().getVersion(), params.getChange());
      analysisScheduler.didChange(create(params.getNotebookDocument().getUri()));
    });

  }

  @Override
  public void didSave(DidSaveNotebookDocumentParams params) {
    // Nothin to do
  }

  @Override
  public void didClose(DidCloseNotebookDocumentParams params) {
    openNotebooksCache.didClose(create(params.getNotebookDocument().getUri()));
  }

  private enum TraceValue {
    OFF,
    MESSAGES,
    VERBOSE
  }

  @Override
  public void didClasspathUpdate(DidClasspathUpdateParams params) {
    var projectUri = create(params.getProjectUri());
    javaConfigCache.didClasspathUpdate(projectUri);
    analysisScheduler.didClasspathUpdate();
  }

  @Override
  public void didJavaServerModeChange(DidJavaServerModeChangeParams params) {
    var serverModeEnum = ServerMode.of(params.getServerMode());
    javaConfigCache.didServerModeChange(serverModeEnum);
    analysisScheduler.didServerModeChange(serverModeEnum);
  }

  @Override
  public void didLocalBranchNameChange(DidLocalBranchNameChangeParams event) {
    branchManager.didBranchNameChange(create(event.getFolderUri()), event.getBranchName());
  }

  @Override
  public void cancelProgress(WorkDoneProgressCancelParams params) {
    progressManager.cancelProgress(params);
  }

  @Override
  public CompletableFuture<ConnectionCheckResult> checkConnection(ConnectionCheckParams params) {
    var connectionName = getConnectionNameFromConnectionCheckParams(params);
    SonarLintLogger.get().debug("Received a validate connectionName request for {}", connectionName);
    var validateConnectionParams = getValidateConnectionParams(params);
    if (validateConnectionParams != null) {
      return backendServiceFacade.validateConnection(validateConnectionParams)
        .thenApply(validationResult -> validationResult.isSuccess() ? success(connectionName)
          : failure(connectionName, validationResult.getMessage()));
    }
    return CompletableFuture.completedFuture(failure(connectionName, format("Connection '%s' is unknown", connectionName)));
  }

  private ValidateConnectionParams getValidateConnectionParams(ConnectionCheckParams params) {
    var connectionId = params.getConnectionId();
    return connectionId != null ? bindingManager.getValidateConnectionParamsFor(connectionId) : getValidateConnectionParamsForNewConnection(params);
  }

  @Override
  public CompletableFuture<Map<String, String>> getRemoteProjectsForConnection(GetRemoteProjectsForConnectionParams getRemoteProjectsForConnectionParams) {
    return CompletableFuture.completedFuture(
      bindingManager.getRemoteProjects(getRemoteProjectsForConnectionParams.getConnectionId()));
  }

  @Override
  public void onTokenUpdate(OnTokenUpdateNotificationParams onTokenUpdateNotificationParams) {
    SonarLintLogger.get().info("Updating credentials on token change.");
    backendServiceFacade.didChangeCredentials(onTokenUpdateNotificationParams.getConnectionId());
    var updatedConnection = settingsManager.getCurrentSettings().getServerConnections().get(onTokenUpdateNotificationParams.getConnectionId());
    updatedConnection.setToken(onTokenUpdateNotificationParams.getToken());
    bindingManager.validateConnection(onTokenUpdateNotificationParams.getConnectionId());
  }

  @Override
  public CompletableFuture<Map<String, String>> getRemoteProjectNames(GetRemoteProjectsNamesParams params) {
    try {
      return CompletableFuture.completedFuture(
        bindingManager.getRemoteProjects(params.getConnectionId())
          .entrySet()
          .stream()
          .filter(e -> params.getProjectKeys().contains(e.getKey()))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    } catch (IllegalStateException | IllegalArgumentException failed) {
      var responseError = new ResponseError(ResponseErrorCode.InternalError, "Could not get remote project names", failed);
      return CompletableFuture.failedFuture(new ResponseErrorException(responseError));
    }
  }

  @Override
  public CompletableFuture<HelpGenerateUserTokenResponse> generateToken(GenerateTokenParams params) {
    return backendServiceFacade.helpGenerateUserToken(params.getBaseServerUrl(),
      ServerConnectionSettings.isSonarCloudAlias(params.getBaseServerUrl()));
  }

  @Override
  public void openHotspotInBrowser(OpenHotspotInBrowserLsParams params) {
    var hotspotId = params.getHotspotId();
    var fileUri = create(params.getFileUri());
    var folderForFileOptional = workspaceFoldersManager.findFolderForFile(fileUri);
    if (folderForFileOptional.isEmpty()) {
      var message = "Can't find workspace folder for file "
        + fileUri.getPath() + " during attempt to open hotspot in browser.";
      lsLogOutput.error(message);
      client.showMessage(new MessageParams(MessageType.Error, message));
      return;
    }
    var workspaceFolderUri = folderForFileOptional.get().getUri();
    var branchNameOptional = branchManager.getReferenceBranchNameForFolder(workspaceFolderUri);
    if (branchNameOptional.isEmpty()) {
      var message = "Can't find branch for workspace folder "
        + workspaceFolderUri.getPath() + " during attempt to open hotspot in browser.";
      lsLogOutput.error(message);
      client.showMessage(new MessageParams(MessageType.Error, message));
      return;
    }
    var versionedIssue = securityHotspotsCache.get(fileUri).get(hotspotId);
    var delegatingIssue = (DelegatingIssue) versionedIssue.issue();
    var openHotspotInBrowserParams = new OpenHotspotInBrowserParams(workspaceFolderUri.toString(),
      branchNameOptional.get(), delegatingIssue.getServerIssueKey());
    backendServiceFacade.getBackendService().openHotspotInBrowser(openHotspotInBrowserParams);
  }

  @Override
  public CompletableFuture<Void> showHotspotRuleDescription(ShowHotspotRuleDescriptionParams params) {
    var fileUri = params.fileUri;
    var ruleKey = params.ruleKey;
    var issue = securityHotspotsCache.get(create(fileUri)).get(params.getHotspotId());
    var ruleContextKey = Objects.isNull(issue) ? "" : issue.issue().getRuleDescriptionContextKey().orElse("");
    var showHotspotCommandParams = new ExecuteCommandParams(SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND,
      List.of(new JsonPrimitive(ruleKey), new JsonPrimitive(fileUri), new JsonPrimitive(ruleContextKey)));
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      commandManager.executeCommand(showHotspotCommandParams, cancelToken);
      return null;
    });
  }

  @Override
  public CompletableFuture<Void> helpAndFeedbackLinkClicked(HelpAndFeedbackLinkClickedNotificationParams params) {
    telemetry.helpAndFeedbackLinkClicked(params.id);
    return CompletableFuture.completedFuture(null);
  }

  public Map<String, Path> getEmbeddedPluginsToPath() {
    var plugins = new HashMap<String, Path>();
    addPluginPathOrWarn("cfamily", Language.C, plugins);
    addPluginPathOrWarn("html", Language.HTML, plugins);
    addPluginPathOrWarn("js", Language.JS, plugins);
    addPluginPathOrWarn("xml", Language.XML, plugins);
    addPluginPathOrWarn("text", Language.SECRETS, plugins);
    addPluginPathOrWarn("go", Language.GO, plugins);
    addPluginPathOrWarn("iac", Language.CLOUDFORMATION, plugins);
    addPluginPathOrWarn("lintomnisharp", Language.CS, plugins);
    return plugins;
  }

  private void addPluginPathOrWarn(String pluginName, Language language, Map<String, Path> plugins) {
    analyzers.stream().filter(it -> it.toString().endsWith("sonar" + pluginName + ".jar")).findFirst()
      .ifPresentOrElse(
        pluginPath -> plugins.put(language.getPluginKey(), pluginPath),
        () -> lsLogOutput.warn(format("Embedded plugin not found: %s", language.getLabel()))
      );
  }

  void provideBackendInitData(String productKey, String userAgent) {
    BackendInitParams params = backendServiceFacade.getInitParams();
    params.setTelemetryProductKey(productKey);
    var actualSonarLintUserHome = Optional.ofNullable(EnginesFactory.sonarLintUserHomeOverride).orElse(SonarLintUserHome.get());
    params.setStorageRoot(actualSonarLintUserHome.resolve("storage"));
    params.setSonarlintUserHome(actualSonarLintUserHome.toString());

    params.setEmbeddedPluginPaths(new HashSet<>(analyzers));
    params.setConnectedModeEmbeddedPluginPathsByKey(getEmbeddedPluginsToPath());
    params.setEnableSecurityHotspots(true);
    params.setEnabledLanguagesInStandaloneMode(EnginesFactory.getStandaloneLanguages());
    params.setExtraEnabledLanguagesInConnectedMode(EnginesFactory.getConnectedLanguages());
    params.setUserAgent(userAgent);
  }

  public CompletableFuture<Void> showHotspotLocations(ShowHotspotLocationsParams showHotspotLocationsParams) {
    var fileUri = showHotspotLocationsParams.fileUri;
    var hotspotKey = showHotspotLocationsParams.hotspotKey;
    var showHotspotCommandParams = new ExecuteCommandParams(SONARLINT_SHOW_SECURITY_HOTSPOT_FLOWS,
      List.of(new JsonPrimitive(fileUri), new JsonPrimitive(hotspotKey)));
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      commandManager.executeCommand(showHotspotCommandParams, cancelToken);
      return null;
    });
  }

  @Override
  public CompletableFuture<Void> scanFolderForHotspots(ScanFolderForHotspotsParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      runScan(params);
      return null;
    });
  }

  private void runScan(ScanFolderForHotspotsParams params) {
    var filesToAnalyze = params.getDocuments().stream()
      .map(d -> new VersionedOpenFile(create(d.getUri()), d.getLanguageId(), d.getVersion(), d.getText()))
      .toList();
    analysisScheduler.scanForHotspotsInFiles(filesToAnalyze);
  }

  public CompletableFuture<Void> forgetFolderHotspots() {
    var filesToForget = securityHotspotsCache.keepOnly(openFilesCache.getAll());
    filesToForget.forEach(f -> diagnosticPublisher.publishDiagnostics(f, true));
    return null;
  }

  @Override
  public CompletableFuture<GetSupportedFilePatternsResponse> getFilePatternsForAnalysis(UriParams params) {
    return backendServiceFacade.getBackendService().getFilePatternsForAnalysis(new GetSupportedFilePatternsParams(params.getUri()));
  }

  @Override
  public CompletableFuture<GetBindingSuggestionsResponse> getBindingSuggestion(GetBindingSuggestionParams params) {
    return backendServiceFacade.getBackendService().getBindingSuggestion(params);
  }

  @Override
  public CompletableFuture<Void> changeIssueStatus(ChangeIssueStatusParams params) {
    var coreParams = new org.sonarsource.sonarlint.core.clientapi.backend.issue.ChangeIssueStatusParams(
      params.getConfigurationScopeId(), params.getIssueId(), params.getNewStatus(), params.isTaintIssue());
    return backendServiceFacade.getBackendService().changeIssueStatus(coreParams).thenAccept(nothing -> {
      var key = params.getIssueId();
      if (params.isTaintIssue()) {
        taintVulnerabilitiesCache.removeTaintIssue(params.getFileUri(), key);
      } else {
        issuesCache.removeIssueWithServerKey(params.getFileUri(), key);
      }

      diagnosticPublisher.publishDiagnostics(create(params.getFileUri()), false);
      client.showMessage(new MessageParams(MessageType.Info, "Issue status was changed"));
    }).exceptionally(t -> {
      lsLogOutput.error("Error changing issue status", t);
      client.showMessage(new MessageParams(MessageType.Error, "Could not change status for the issue. Look at the SonarLint output for details."));
      return null;
    }).thenAccept(unused -> {
      if (!StringUtils.isEmpty(params.getComment())) {
        addIssueComment(new AddIssueCommentParams(params.getConfigurationScopeId(), params.getIssueId(), params.getComment()));
      }
    });
  }

  private void addIssueComment(AddIssueCommentParams params) {
    backendServiceFacade.getBackendService().addIssueComment(params)
      .thenAccept(nothing -> client.showMessage(new MessageParams(MessageType.Info, "New comment was added")))
      .exceptionally(t -> {
        lsLogOutput.error("Error adding issue comment", t);
        client.showMessage(new MessageParams(MessageType.Error, "Could not add a new issue comment. Look at the SonarLint output for " +
          "details."));
        return null;
      });
  }

  @Override
  public CompletableFuture<CheckLocalDetectionSupportedResponse> checkLocalDetectionSupported(UriParams params) {
    var folderUri = params.getUri();
    return backendServiceFacade.checkLocalDetectionSupported(folderUri)
      .thenApply(response -> new CheckLocalDetectionSupportedResponse(response.isSupported(), response.getReason()))
      .exceptionally(exception -> new CheckLocalDetectionSupportedResponse(false, exception.getCause().getMessage()));
  }

  @Override
  public CompletableFuture<SonarLintExtendedLanguageClient.ShowRuleDescriptionParams> getHotspotDetails(ShowHotspotRuleDescriptionParams params) {
    var fileUri = params.fileUri;
    var ruleKey = params.ruleKey;
    var issue = securityHotspotsCache.get(create(fileUri)).get(params.getHotspotId());
    var ruleContextKey = Objects.isNull(issue) ? "" : issue.issue().getRuleDescriptionContextKey().orElse("");
    return commandManager.getShowRuleDescriptionParams(fileUri, ruleKey, ruleContextKey);
  }

  @Override
  public CompletableFuture<Void> changeHotspotStatus(ChangeHotspotStatusParams params) {
    var workspace = workspaceFoldersManager.findFolderForFile(create(params.getFileUri()))
      .orElseThrow(() -> new IllegalStateException("No workspace found"));
    var workspaceUri = workspace.getUri();
    var hotspotStatus = hotspotStatusOfTitle(params.getNewStatus());
    var coreParams = new org.sonarsource.sonarlint.core.clientapi.backend.hotspot.ChangeHotspotStatusParams(
      workspaceUri.toString(), params.getHotspotKey(), hotspotStatus);
    return backendServiceFacade.getBackendService().changeHotspotStatus(coreParams).thenAccept(nothing -> {
      var key = params.getHotspotKey();
      if (hotspotStatus != HotspotStatus.TO_REVIEW && hotspotStatus != HotspotStatus.ACKNOWLEDGED) {
        securityHotspotsCache.removeIssueWithServerKey(params.getFileUri(), key);
      } else {
        securityHotspotsCache.updateIssueStatus(params.getFileUri(), key, hotspotStatus);
      }
      diagnosticPublisher.publishDiagnostics(create(params.getFileUri()), true);
      client.showMessage(new MessageParams(MessageType.Info, "Hotspot status was changed"));
    }).exceptionally(t -> {
      lsLogOutput.error("Error changing hotspot status", t);
      client.showMessage(new MessageParams(MessageType.Error, "Could not change status for the hotspot. Look at the SonarLint output for details."));
      return null;
    });
  }

  @Override
  public CompletableFuture<GetAllowedHotspotStatusesResponse> getAllowedHotspotStatuses(GetAllowedHotspotStatusesParams params) {
    var folderUri = params.getFolderUri();
    var bindingOptional = bindingManager.getBinding(create(folderUri));
    if (bindingOptional.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    var connectionId = bindingOptional.get().getConnectionId();
    var checkStatusChangePermittedParams = new CheckStatusChangePermittedParams(connectionId, params.getHotspotKey());
    return backendServiceFacade.getBackendService().getAllowedHotspotStatuses(checkStatusChangePermittedParams).thenApply(r -> {
        var delegatingIssue = (DelegatingIssue) securityHotspotsCache
          .findIssuePerId(params.getFileUri(), params.getHotspotKey()).get().getValue().issue();
        var reviewStatus = delegatingIssue.getReviewStatus();
        var statuses = r.getAllowedStatuses().stream().filter(s -> s != hotspotStatusValueOfHotspotReviewStatus(reviewStatus))
          .map(HotspotStatus::getTitle).toList();
        return new GetAllowedHotspotStatusesResponse(
          r.isPermitted(),
          r.getNotPermittedReason(),
          statuses);
      }
    ).exceptionally(t -> {
      lsLogOutput.error("Error changing hotspot status", t);
      client.showMessage(new MessageParams(MessageType.Error, "Could not change status for the hotspot. Look at the SonarLint output for details."));
      return null;
    });
  }

  @Override
  public CompletableFuture<ReopenIssueResponse> reopenResolvedLocalIssues(ReopenAllIssuesForFileParams params) {
    var reopenAllIssuesParams = new org.sonarsource.sonarlint.core.clientapi.backend.issue.ReopenAllIssuesForFileParams(params.getConfigurationScopeId(), params.getRelativePath());
    return backendServiceFacade.reopenAllIssuesForFile(reopenAllIssuesParams).thenApply(r -> {
      if (r.isIssueReopened()) {
        analysisScheduler.didChange(create(params.getFileUri()));
        client.showMessage(new MessageParams(MessageType.Info, "Reopened local issues for " + params.getRelativePath()));
      } else {
        client.showMessage(new MessageParams(MessageType.Info, "There are no resolved issues in file " + params.getRelativePath()));
      }
      return r;
    }).exceptionally(e -> {
      lsLogOutput.error("Error while reopening resolved local issues", e);
      client.showMessage(new MessageParams(MessageType.Error, "Could not reopen resolved local issues. Look at the SonarLint output for details."));
      return null;
    });
  }

  @Override
  public CompletableFuture<Void> analyseOpenFileIgnoringExcludes(AnalyseOpenFileIgnoringExcludesParams params) {
    var notebookUriStr = params.getNotebookUri();
    if (notebookUriStr != null) {
      var version = params.getNotebookVersion();
      var notebookUri = create(notebookUriStr);
      Objects.requireNonNull(version);
      var cells = Objects.requireNonNull(params.getNotebookCells());
      var notebookFile = VersionedOpenNotebook.create(
        notebookUri, version,
        cells, notebookDiagnosticPublisher);
      var versionedOpenFile = notebookFile.asVersionedOpenFile();
      openNotebooksCache.didOpen(notebookUri, version, cells);
      analysisScheduler.didOpen(versionedOpenFile);
    } else {
      var document = Objects.requireNonNull(params.getTextDocument());
      var file = openFilesCache.didOpen(create(document.getUri()), document.getLanguageId(), document.getText(), document.getVersion());
      analysisScheduler.didOpen(file);
    }
    return CompletableFuture.completedFuture(null);
  }

  private void runIfAnalysisNeeded(String uri, Runnable analyse) {
    client.shouldAnalyseFile(new SonarLintExtendedLanguageServer.UriParams(uri)).thenAccept(checkResult -> {
      if (Boolean.TRUE.equals(checkResult.isShouldBeAnalysed())) {
        analyse.run();
      } else {
        var reason = Objects.requireNonNull(checkResult.getReason());
        lsLogOutput.info(reason + " \"" + uri + "\"");
      }
    });
  }
}
