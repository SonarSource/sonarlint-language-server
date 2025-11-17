/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource Sàrl
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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.ClientInfo;
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
import org.eclipse.lsp4j.InitializedParams;
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetRuleFileContentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetRuleFileContentResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetBindingSuggestionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.GetConnectionSuggestionsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.GetMCPServerConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.GetMCPServerConfigurationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.OrganizationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.labs.JoinIdeLabsProgramParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.labs.JoinIdeLabsProgramResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.ChangeDependencyRiskStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.GetBindingSuggestionsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetConnectionSuggestionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FindingsFilteredParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.clientapi.SonarLintVSCodeClient;
import org.sonarsource.sonarlint.ls.connected.DependencyRisksCache;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.connected.api.HostInfoProvider;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.embeddedserver.EmbeddedServerManager;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.flightrecorder.FlightRecorderManager;
import org.sonarsource.sonarlint.ls.folders.ModuleEventsProcessor;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderBranchManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.notebooks.NotebookDiagnosticPublisher;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.notebooks.VersionedOpenNotebook;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.standalone.notifications.PromotionalNotifications;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.util.CatchingRunnable;
import org.sonarsource.sonarlint.ls.util.EnumLabelsMapper;
import org.sonarsource.sonarlint.ls.util.ExitingInputStream;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static java.net.URI.create;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_ISSUE_DETAILS_FROM_CODE_ACTION_COMMAND;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_SECURITY_HOTSPOT_FLOWS;
import static org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult.failure;
import static org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult.success;
import static org.sonarsource.sonarlint.ls.backend.BackendService.ROOT_CONFIGURATION_SCOPE;
import static org.sonarsource.sonarlint.ls.util.URIUtils.getFullFileUriFromFragments;
import static org.sonarsource.sonarlint.ls.util.Utils.getConnectionNameFromConnectionCheckParams;
import static org.sonarsource.sonarlint.ls.util.Utils.getValidateConnectionParamsForNewConnection;
import static org.sonarsource.sonarlint.ls.util.Utils.hotspotStatusOfTitle;

public class SonarLintLanguageServer implements SonarLintExtendedLanguageServer, WorkspaceService, TextDocumentService, NotebookDocumentService {

  public static final String JUPYTER_NOTEBOOK_TYPE = "jupyter-notebook";
  public static final String PYTHON_LANGUAGE = "python";
  private final SonarLintExtendedLanguageClient client;
  private final FlightRecorderManager flightRecorderManager;
  private final EmbeddedServerManager embeddedServerManager;
  private final SonarLintTelemetry telemetry;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private final ForcedAnalysisCoordinator forcedAnalysisCoordinator;
  private final DependencyRisksCache dependencyRisksCache;
  private final OpenFilesCache openFilesCache;
  private final OpenNotebooksCache openNotebooksCache;
  private final CommandManager commandManager;
  private final ExecutorService lspThreadPool;
  private final HostInfoProvider hostInfoProvider;
  private final WorkspaceFolderBranchManager branchManager;
  private final JavaConfigCache javaConfigCache;
  private final IssuesCache issuesCache;
  private final HotspotsCache securityHotspotsCache;
  private final DiagnosticPublisher diagnosticPublisher;
  private final LanguageClientLogger lsLogOutput;

  private final NotebookDiagnosticPublisher notebookDiagnosticPublisher;
  private final SonarLintVSCodeClient vsCodeClient;

  /**
   * Keep track of value 'sonarlint.trace.server' on client side. Not used currently, but keeping it just in case.
   */
  private TraceValue traceLevel;

  private final ModuleEventsProcessor moduleEventsProcessor;
  private final BackendServiceFacade backendServiceFacade;
  private final CountDownLatch shutdownLatch;

  private final ExecutorService branchChangeEventExecutor;

  SonarLintLanguageServer(InputStream inputStream, OutputStream outputStream, List<Path> analyzers) {
    this.lspThreadPool = Executors.newCachedThreadPool(Utils.threadFactory("SonarQube for VS Code LSP message processor", false));

    var input = new ExitingInputStream(inputStream, this);
    var launcher = new Launcher.Builder<SonarLintExtendedLanguageClient>()
      .setLocalService(this)
      .setRemoteInterface(SonarLintExtendedLanguageClient.class)
      .setInput(input)
      .setOutput(outputStream)
      .setExecutorService(lspThreadPool)
      .create();
    this.branchChangeEventExecutor = Executors.newSingleThreadExecutor(Utils.threadFactory("SonarQube for VS Code branch change event handler", false));

    this.client = launcher.getRemoteProxy();
    this.lsLogOutput = new LanguageClientLogger(this.client);
    this.openFilesCache = new OpenFilesCache(lsLogOutput);

    this.flightRecorderManager = new FlightRecorderManager(client);
    this.embeddedServerManager = new EmbeddedServerManager(client);
    this.issuesCache = new IssuesCache();
    this.securityHotspotsCache = new HotspotsCache();
    var taintVulnerabilitiesCache = new TaintVulnerabilitiesCache();
    this.dependencyRisksCache = new DependencyRisksCache();
    this.notebookDiagnosticPublisher = new NotebookDiagnosticPublisher(client, issuesCache);
    this.openNotebooksCache = new OpenNotebooksCache(lsLogOutput, notebookDiagnosticPublisher);
    this.notebookDiagnosticPublisher.setOpenNotebooksCache(openNotebooksCache);
    this.hostInfoProvider = new HostInfoProvider();
    var skippedPluginsNotifier = new SkippedPluginsNotifier(client, lsLogOutput);
    var promotionalNotifications = new PromotionalNotifications(client);
    vsCodeClient = new SonarLintVSCodeClient(client, hostInfoProvider, lsLogOutput, taintVulnerabilitiesCache, dependencyRisksCache, skippedPluginsNotifier,
      promotionalNotifications, flightRecorderManager, embeddedServerManager);
    this.backendServiceFacade = new BackendServiceFacade(vsCodeClient, lsLogOutput, client, new EnabledLanguages(analyzers, lsLogOutput));
    vsCodeClient.setBackendServiceFacade(backendServiceFacade);
    this.workspaceFoldersManager = new WorkspaceFoldersManager(backendServiceFacade, lsLogOutput);
    this.diagnosticPublisher = new DiagnosticPublisher(client, taintVulnerabilitiesCache, issuesCache,
      securityHotspotsCache, openNotebooksCache, dependencyRisksCache);
    vsCodeClient.setDiagnosticPublisher(diagnosticPublisher);
    this.settingsManager = new SettingsManager(this.client, this.workspaceFoldersManager, backendServiceFacade, lsLogOutput);
    vsCodeClient.setSettingsManager(settingsManager);
    vsCodeClient.setWorkspaceFoldersManager(workspaceFoldersManager);
    var fileTypeClassifier = new FileTypeClassifier(lsLogOutput);
    javaConfigCache = new JavaConfigCache(client, openFilesCache, lsLogOutput);
    this.settingsManager.addListener(lsLogOutput);
    this.bindingManager = new ProjectBindingManager(workspaceFoldersManager, settingsManager,
      client, lsLogOutput, backendServiceFacade, openNotebooksCache);
    vsCodeClient.setBindingManager(bindingManager);
    this.telemetry = new SonarLintTelemetry(backendServiceFacade, lsLogOutput);
    this.backendServiceFacade.setTelemetry(telemetry);
    this.settingsManager.addListener(telemetry);
    this.settingsManager.addListener((WorkspaceSettingsChangeListener) bindingManager);
    this.settingsManager.addListener((WorkspaceFolderSettingsChangeListener) bindingManager);
    this.workspaceFoldersManager.addListener(settingsManager);
    var smartNotifications = new SmartNotifications(client, telemetry);
    vsCodeClient.setSmartNotifications(smartNotifications);
    this.moduleEventsProcessor = new ModuleEventsProcessor(workspaceFoldersManager, fileTypeClassifier, javaConfigCache, backendServiceFacade, settingsManager);
    var analysisHelper = new AnalysisHelper(client, lsLogOutput, workspaceFoldersManager, javaConfigCache, settingsManager,
      issuesCache, securityHotspotsCache, diagnosticPublisher,
      openNotebooksCache, notebookDiagnosticPublisher, openFilesCache);
    vsCodeClient.setAnalysisTaskExecutor(analysisHelper);
    this.forcedAnalysisCoordinator = new ForcedAnalysisCoordinator(workspaceFoldersManager, bindingManager, openFilesCache,
      openNotebooksCache, client, backendServiceFacade, settingsManager);
    bindingManager.setAnalysisManager(forcedAnalysisCoordinator);
    this.settingsManager.addListener((WorkspaceSettingsChangeListener) forcedAnalysisCoordinator);
    this.settingsManager.addListener((WorkspaceFolderSettingsChangeListener) forcedAnalysisCoordinator);
    this.commandManager = new CommandManager(client, settingsManager, bindingManager, telemetry, taintVulnerabilitiesCache,
      issuesCache, securityHotspotsCache, backendServiceFacade, workspaceFoldersManager, openNotebooksCache, lsLogOutput);

    this.branchManager = new WorkspaceFolderBranchManager(backendServiceFacade, lsLogOutput);
    vsCodeClient.setBranchManager(branchManager);
    this.workspaceFoldersManager.addListener(this.branchManager);
    this.workspaceFoldersManager.setBindingManager(bindingManager);
    var cleanAsYouCodeManager = new CleanAsYouCodeManager(diagnosticPublisher, openFilesCache, backendServiceFacade);
    this.settingsManager.addListener(cleanAsYouCodeManager);
    this.shutdownLatch = new CountDownLatch(1);
    launcher.startListening();
  }

  static SonarLintLanguageServer bySocket(int port, List<Path> analyzers) throws IOException {
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
      var initializationOptions = parse(params.getInitializationOptions());
      lsLogOutput.initialize(initializationOptions.showVerboseLogs());

      var clientInfo = ofNullable(params.getClientInfo());
      var appName = clientInfo.map(ClientInfo::getName).orElse("Unknown");
      var clientVersion = clientInfo.map(ClientInfo::getVersion).orElse("Unknown");

      var sonarQubeServerConnections = new ArrayList<SonarQubeConnectionConfigurationDto>();
      settingsManager.parseSonarQubeConnectionsWithoutToken(initializationOptions.connections())
        .forEach((connectionId, connectionSettings) -> sonarQubeServerConnections
          .add(new SonarQubeConnectionConfigurationDto(connectionId, connectionSettings.getServerUrl(), connectionSettings.isSmartNotificationsDisabled())));
      var sonarQubeCloudConnections = new ArrayList<SonarCloudConnectionConfigurationDto>();
      settingsManager.parseSonarCloudConnectionsWithoutToken(initializationOptions.connections())
        .forEach((connectionId, connectionSettings) -> sonarQubeCloudConnections.add(new SonarCloudConnectionConfigurationDto(connectionId, connectionSettings.getOrganizationKey(),
          connectionSettings.getRegion(), connectionSettings.isSmartNotificationsDisabled())));

      backendServiceFacade.initialize(initializationOptions, appName, clientVersion, sonarQubeServerConnections, sonarQubeCloudConnections);

      hostInfoProvider.initialize(clientVersion, initializationOptions.workspaceName());
      diagnosticPublisher.initialize(initializationOptions.firstSecretDetected());
      workspaceFoldersManager.initialize(params.getWorkspaceFolders());

      var c = new ServerCapabilities();
      c.setTextDocumentSync(getTextDocumentSyncOptions());
      c.setCodeActionProvider(true);
      var executeCommandOptions = new ExecuteCommandOptions(CommandManager.SONARLINT_SERVERSIDE_COMMANDS);
      executeCommandOptions.setWorkDoneProgress(true);
      c.setExecuteCommandProvider(executeCommandOptions);
      c.setWorkspace(getWorkspaceServerCapabilities());
      if (initializationOptions.enableNotebooks()) {
        setNotebookSyncOptions(c);
      }
      var info = new ServerInfo("SonarLint Language Server", getServerVersion("slls-version.txt"));
      return new InitializeResult(c, info);
    });
  }

  private static SonarLintLanguageServerInitializationOptions parse(Object initializationOptions) {
    var gson = new GsonBuilder().create();
    return gson.fromJson((JsonObject) initializationOptions, SonarLintLanguageServerInitializationOptions.class);
  }

  @Override
  public void initialized(InitializedParams params) {
    CompletableFutures.computeAsync(cancelToken -> {
      lsLogOutput.debug("Language Server initialized");
      settingsManager.didChangeConfiguration();
      flightRecorderManager.initialized();
      embeddedServerManager.initialized();
      return null;
    });
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
      branchManager::shutdown,
      settingsManager::shutdown,
      workspaceFoldersManager::shutdown,
      moduleEventsProcessor::shutdown,
      branchChangeEventExecutor::shutdown,
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
      lspThreadPool.awaitTermination(5, TimeUnit.SECONDS);
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
      lsLogOutput.errorWithStackTrace("Unable to properly shutdown", e);
    }
  }

  @Override
  public void exit() {
    invokeQuietly(() -> Utils.shutdownAndAwait(lspThreadPool, true));
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
    client.isOpenInEditor(uri.toString()).thenAccept(isOpen -> {
      if (Boolean.TRUE.equals(isOpen)) {
        if (openNotebooksCache.isNotebook(uri)) {
          lsLogOutput.debug(String.format("Skipping text document analysis of notebook \"%s\"", uri));
          return;
        }
        var file = openFilesCache.didOpen(uri, params.getTextDocument().getLanguageId(), params.getTextDocument().getText(), params.getTextDocument().getVersion());
        CompletableFutures.computeAsync(cancelChecker -> {
          String configScopeId;
          moduleEventsProcessor.notifyBackendWithFileLanguageAndContent(file);
          var maybeWorkspaceFolder = workspaceFoldersManager.findFolderForFile(uri);
          if (maybeWorkspaceFolder.isPresent()) {
            configScopeId = maybeWorkspaceFolder.get().getUri().toString();
          } else {
            configScopeId = ROOT_CONFIGURATION_SCOPE;
          }
          backendServiceFacade.getBackendService().didOpenFile(configScopeId, uri);
          return null;
        });
      } else {
        lsLogOutput.debug(String.format("Skipping analysis of file not open in the editor: \"%s\"", uri));
      }
    });
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    var uri = create(params.getTextDocument().getUri());
    openFilesCache.didChange(uri, params.getContentChanges().get(0).getText(), params.getTextDocument().getVersion());
    Optional<VersionedOpenFile> file = openFilesCache.getFile(uri);
    if (file.isEmpty()) {
      lsLogOutput.warn("Illegal state: trying to update file that was not open");
    } else {
      // VSCode sends us full file content in the change event
      CompletableFutures.computeAsync(cancelChecker -> {
        moduleEventsProcessor.notifyBackendWithUpdatedContent(file.get());
        return null;
      });
    }
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    var uri = create(params.getTextDocument().getUri());
    openFilesCache.didClose(uri);
    javaConfigCache.didClose(uri);
    issuesCache.clear(uri);
    securityHotspotsCache.clear(uri);
    diagnosticPublisher.publishDiagnostics(uri, false);
    var maybeWorkspaceFolder = workspaceFoldersManager.findFolderForFile(uri);
    var configScopeId = maybeWorkspaceFolder
      .map(WorkspaceFolderWrapper::getUri)
      .map(URI::toString)
      .orElse(ROOT_CONFIGURATION_SCOPE);
    backendServiceFacade.getBackendService().didCloseFile(configScopeId, uri);
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
    var notebookFile = openNotebooksCache.didOpen(notebookUri, params.getNotebookDocument().getVersion(), params.getCellTextDocuments());
    var versionedOpenFile = notebookFile.asVersionedOpenFile();

    if (openFilesCache.getFile(notebookUri).isPresent()) {
      openFilesCache.didClose(notebookUri);
    }
    CompletableFutures.computeAsync(cancelChecker -> {
      moduleEventsProcessor.notifyBackendWithFileLanguageAndContent(versionedOpenFile);
      var maybeWorkspaceFolder = workspaceFoldersManager.findFolderForFile(notebookUri);
      if (maybeWorkspaceFolder.isPresent()) {
        var configScopeId = maybeWorkspaceFolder.get().getUri().toString();
        backendServiceFacade.getBackendService().didOpenFile(configScopeId, notebookUri);
      }
      return null;
    });
  }

  @Override
  public void didChange(DidChangeNotebookDocumentParams params) {
    openNotebooksCache.didChange(create(params.getNotebookDocument().getUri()), params.getNotebookDocument().getVersion(), params.getChange());
    var openNotebook = openNotebooksCache.getFile(create(params.getNotebookDocument().getUri()));
    if (openNotebook.isEmpty()) {
      lsLogOutput.warn("Illegal state: received change event for Notebook that is not open");
    } else {
      var file = openNotebook.get().asVersionedOpenFile();
      CompletableFutures.computeAsync(cancelChecker -> {
        moduleEventsProcessor.notifyBackendWithUpdatedContent(file);
        return null;
      });
    }
  }

  @Override
  public void didSave(DidSaveNotebookDocumentParams params) {
    // Nothin to do
  }

  @Override
  public void didClose(DidCloseNotebookDocumentParams params) {
    var uri = create(params.getNotebookDocument().getUri());
    issuesCache.clear(uri);
    notebookDiagnosticPublisher.removeAllExistingDiagnosticsForNotebook(uri);
    openNotebooksCache.didClose(uri);
    var maybeWorkspaceFolder = workspaceFoldersManager.findFolderForFile(uri);
    if (maybeWorkspaceFolder.isPresent()) {
      var configScopeId = maybeWorkspaceFolder.get().getUri().toString();
      backendServiceFacade.getBackendService().didCloseFile(configScopeId, uri);
    }
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
    forcedAnalysisCoordinator.didClasspathUpdate();
  }

  @Override
  public void didJavaServerModeChange(DidJavaServerModeChangeParams params) {
    var serverModeEnum = ServerMode.of(params.getServerMode());
    javaConfigCache.didServerModeChange();
    forcedAnalysisCoordinator.didServerModeChange(serverModeEnum);
  }

  @Override
  public void didLocalBranchNameChange(DidLocalBranchNameChangeParams event) {
    var branchName = event.getBranchName();
    var folderUri = event.getFolderUri();
    if (branchName != null) {
      lsLogOutput.debug(format("Folder %s is now on branch %s.", folderUri, branchName));
    } else {
      lsLogOutput.debug(format("Folder %s is now on an unknown branch.", folderUri));
      return;
    }
    branchChangeEventExecutor.submit(new CatchingRunnable(() -> backendServiceFacade.getBackendService().notifyBackendOnVcsChange(folderUri),
      t -> lsLogOutput.errorWithStackTrace("Failed to notify backend on VCS change", t)));
  }

  @Override
  public CompletableFuture<ConnectionCheckResult> checkConnection(ConnectionCheckParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      var connectionName = getConnectionNameFromConnectionCheckParams(params);
      lsLogOutput.debug(format("Received a validate connectionName request for %s", connectionName));
      try {
        var validateConnectionParams = getValidateConnectionParams(params);
        if (validateConnectionParams != null) {
          return backendServiceFacade.getBackendService().validateConnection(validateConnectionParams)
            .thenApply(validationResult -> validationResult.isSuccess() ? success(connectionName)
              : failure(connectionName, validationResult.getMessage()))
            .get();
        }
        return failure(connectionName, format("Connection '%s' is unknown", connectionName));
      } catch (IllegalStateException e) {
        // Handle null/empty token validation errors
        return failure(connectionName, "Invalid credentials: " + e.getMessage());
      } catch (Exception e) {
        return failure(connectionName, "Failed to validate connection: " + e.getMessage());
      }
    });
  }

  private ValidateConnectionParams getValidateConnectionParams(ConnectionCheckParams params) {
    var connectionId = params.getConnectionId();
    return connectionId != null ? bindingManager.getValidateConnectionParamsFor(connectionId) : getValidateConnectionParamsForNewConnection(params);
  }

  @Override
  public CompletableFuture<Map<String, String>> getRemoteProjectsForConnection(GetRemoteProjectsForConnectionParams getRemoteProjectsForConnectionParams) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      return bindingManager.getRemoteProjects(getRemoteProjectsForConnectionParams.getConnectionId());
    });
  }

  @Override
  public CompletableFuture<GetSharedConnectedModeConfigFileResponse> getSharedConnectedModeConfigFileContents(GetSharedConnectedModeConfigFileParams params) {
    return backendServiceFacade.getBackendService().getSharedConnectedModeConfigFileContents(params);
  }

  @Override
  public CompletableFuture<GetMCPServerConfigurationResponse> getMCPServerConfiguration(GetMCPServerConfigurationParams params) {
    return backendServiceFacade.getBackendService().getMCPServerConfiguration(params);
  }

  @Override
  public CompletableFuture<GetRuleFileContentResponse> getMCPRuleFileContent(String clientProvidedIde) {
    try {
      var aiAgent = AiAgent.valueOf(clientProvidedIde.toUpperCase(Locale.US));
      var params = new GetRuleFileContentParams(aiAgent);
      return backendServiceFacade.getBackendService().getMCPRuleFileContent(params);
    } catch (IllegalArgumentException e) {
      client.showMessage(new MessageParams(MessageType.Warning, "Rule file creation is not yet supported for AI agent '" + clientProvidedIde + "'."));
      throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unsupported AI agent: " + clientProvidedIde, e));
    }
  }

  @Override
  public void onTokenUpdate(OnTokenUpdateNotificationParams onTokenUpdateNotificationParams) {
    CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      lsLogOutput.info("Updating credentials on token change.");
      backendServiceFacade.getBackendService().didChangeCredentials(onTokenUpdateNotificationParams.getConnectionId());
      var updatedConnection = settingsManager.getCurrentSettings().getServerConnections().get(onTokenUpdateNotificationParams.getConnectionId());
      updatedConnection.setToken(onTokenUpdateNotificationParams.getToken());
      bindingManager.validateConnection(onTokenUpdateNotificationParams.getConnectionId());
      return null;
    });
  }

  @Override
  public CompletableFuture<Map<String, String>> getRemoteProjectNamesByProjectKeys(GetRemoteProjectNamesByKeysParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      try {
        return bindingManager.getRemoteProjectsByKeys(params.connectionId(), params.projectKeys()).get();
      } catch (IllegalStateException | IllegalArgumentException | InterruptedException | ExecutionException failed) {
        var responseError = new ResponseError(ResponseErrorCode.InternalError, "Could not get remote project name", failed);
        throw new ResponseErrorException(responseError);
      }
    });
  }

  @Override
  public CompletableFuture<HelpGenerateUserTokenResponse> generateToken(GenerateTokenParams params) {
    return backendServiceFacade.getBackendService().helpGenerateUserToken(params.getBaseServerUrl());
  }

  @Override
  public void openHotspotInBrowser(OpenHotspotInBrowserLsParams params) {
    CompletableFutures.computeAsync(cancelToken -> {
      var hotspotId = params.getHotspotId();
      var fileUri = create(params.getFileUri());
      var folderForFileOptional = workspaceFoldersManager.findFolderForFile(fileUri);
      if (folderForFileOptional.isEmpty()) {
        var message = "Can't find workspace folder for file "
          + fileUri.getPath() + " during attempt to open hotspot in browser.";
        lsLogOutput.error(message);
        client.showMessage(new MessageParams(MessageType.Error, message));
        return null;
      }
      var workspaceFolderUri = folderForFileOptional.get().getUri();
      var branchNameOptional = bindingManager.resolveBranchNameForFolder(workspaceFolderUri);
      if (branchNameOptional.isEmpty()) {
        var message = "Can't find branch for workspace folder "
          + workspaceFolderUri.getPath() + " during attempt to open hotspot in browser.";
        lsLogOutput.error(message);
        client.showMessage(new MessageParams(MessageType.Error, message));
        return null;
      }
      var raisedHotspotDto = requireNonNull(securityHotspotsCache.get(fileUri).get(hotspotId));
      var openHotspotInBrowserParams = new OpenHotspotInBrowserParams(workspaceFolderUri.toString(), requireNonNull(raisedHotspotDto.getServerIssueKey()));
      backendServiceFacade.getBackendService().openHotspotInBrowser(openHotspotInBrowserParams);
      return null;
    });
  }

  @Override
  public void openDependencyRiskInBrowser(OpenDependencyRiskInBrowserParams params) {
    var issueId = params.issueId();
    var folderUri = params.folderUri();
    backendServiceFacade.getBackendService().openDependencyRiskInBrowser(folderUri, issueId)
      .exceptionally(ex -> {
        var message = "Failed to open dependency risk in browser: " + ex.getMessage();
        lsLogOutput.error(message);
        client.showMessage(new MessageParams(MessageType.Error, message));
        return null;
      });
  }

  @Override
  public CompletableFuture<Void> showHotspotRuleDescription(ShowHotspotRuleDescriptionParams params) {
    var fileUri = params.fileUri;
    var showHotspotCommandParams = new ExecuteCommandParams(SONARLINT_SHOW_ISSUE_DETAILS_FROM_CODE_ACTION_COMMAND,
      List.of(new JsonPrimitive(params.getHotspotId()), new JsonPrimitive(fileUri)));
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

  @Override
  public void lmToolCalled(LMToolCalledParams params) {
    telemetry.toolCalled(params.toolName(), params.success());
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
      telemetry.wholeFolderHotspotsAnalysisTriggered();
      return null;
    });
  }

  private void runScan(ScanFolderForHotspotsParams params) {
    backendServiceFacade.getBackendService().analyzeFullProject(params.getFolderUri(), true);
  }

  public CompletableFuture<Void> forgetFolderHotspots() {
    var filesToForget = securityHotspotsCache.keepOnly(openFilesCache.getAll());
    filesToForget.forEach(diagnosticPublisher::publishHotspots);
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
  public CompletableFuture<GetConnectionSuggestionsResponse> getConnectionSuggestions(GetConnectionSuggestionsParams params) {
    return backendServiceFacade.getBackendService().getConnectionSuggestions(params);
  }

  @Override
  public void didCreateBinding(BindingCreationMode creationMode) {
    switch (creationMode) {
      case AUTOMATIC -> telemetry.addedAutomaticBindings();
      case IMPORTED -> telemetry.addedImportedBindings();
      case MANUAL -> telemetry.addedManualBindings();
    }
  }

  @Override
  public CompletableFuture<List<OrganizationDto>> listUserOrganizations(ListUserOrganizationsParams params) {
    return backendServiceFacade.getBackendService().listUserOrganizations(params.token(), params.region())
      .thenApply(ListUserOrganizationsResponse::getUserOrganizations);
  }

  @Override
  public CompletableFuture<Void> fixSuggestionResolved(FixSuggestionResolvedParams params) {
    telemetry.fixSuggestionResolved(new org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionResolvedParams(params.suggestionId(),
      params.accepted() ? FixSuggestionStatus.ACCEPTED : FixSuggestionStatus.DECLINED, null));
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> findingsFiltered(FindingsFilteredParams params) {
    telemetry.findingFilterApplied(params);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> dependencyRiskIssueInvestigatedLocally() {
    telemetry.dependencyRiskIssueInvestigatedLocally();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<CheckIssueStatusChangePermittedResponse> checkIssueStatusChangePermitted(CheckIssueStatusChangePermittedParams params) {
    var bindingWrapperOpt = bindingManager.getBinding(create(params.getFolderUri()));
    if (bindingWrapperOpt.isEmpty()) {
      return CompletableFuture.completedFuture(
        new CheckIssueStatusChangePermittedResponse(false, "There is no binding for the folder: " + params.getFolderUri(), List.of()));
    }
    var connectionId = bindingWrapperOpt.get().connectionId();
    return backendServiceFacade.getBackendService().checkStatusChangePermitted(connectionId, params.getIssueKey());
  }

  @Override
  public CompletableFuture<Void> changeIssueStatus(ChangeIssueStatusParams params) {
    var coreParams = new org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ChangeIssueStatusParams(
      params.getConfigurationScopeId(), Objects.requireNonNull(params.getIssueId()), EnumLabelsMapper.resolutionStatusFromLabel(params.getNewStatus()), params.isTaintIssue());
    return backendServiceFacade.getBackendService().changeIssueStatus(coreParams).thenAccept(nothing -> {
      var key = params.getIssueId();
      if (!params.isTaintIssue()) {
        issuesCache.removeFindingWithServerKey(params.getFileUri(), key);
      }

      diagnosticPublisher.publishDiagnostics(create(params.getFileUri()), false);
      client.showMessage(new MessageParams(MessageType.Info, "Issue status was changed"));
    }).exceptionally(t -> {
      lsLogOutput.errorWithStackTrace("Error changing issue status", t);
      client.showMessage(new MessageParams(MessageType.Error, "Could not change status for the issue. Look at the SonarQube for IDE output for details."));
      return null;
    }).thenAccept(unused -> {
      if (!StringUtils.isEmpty(params.getComment())) {
        addIssueComment(new AddIssueCommentParams(params.getConfigurationScopeId(), params.getIssueId(), params.getComment()));
      }
    });
  }

  @Override
  public void changeDependencyRiskStatus(ChangeDependencyRiskStatusParams params) {
    backendServiceFacade.getBackendService().changeDependencyRiskStatus(params).thenAccept(
      nothing -> client.showMessage(new MessageParams(MessageType.Info, "Dependency risk status was updated"))).exceptionally(t -> {
        lsLogOutput.errorWithStackTrace("Error changing dependency risk status", t);
        client.showMessage(new MessageParams(MessageType.Error, "Could not change status for the dependency risk. Check SonarQube for IDE output for details."));
        return null;
      });
  }

  @Override
  public CompletableFuture<GetDependencyRiskTransitionsResponse> getDependencyRiskTransitions(GetDependencyRiskTransitionsParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      var transitions = dependencyRisksCache.getAllowedTransitionsForDependencyRisk(params.dependencyRiskId().toString());
      return new GetDependencyRiskTransitionsResponse(transitions);
    });
  }

  private void addIssueComment(AddIssueCommentParams params) {
    backendServiceFacade.getBackendService().addIssueComment(params)
      .thenAccept(nothing -> client.showMessage(new MessageParams(MessageType.Info, "New comment was added")));
  }

  @Override
  public CompletableFuture<CheckLocalDetectionSupportedResponse> checkLocalDetectionSupported(UriParams params) {
    var folderUri = params.getUri();
    return backendServiceFacade.getBackendService().checkLocalDetectionSupported(folderUri)
      .thenApply(response -> new CheckLocalDetectionSupportedResponse(response.isSupported(), response.getReason()));
  }

  @Override
  public CompletableFuture<SonarLintExtendedLanguageClient.ShowRuleDescriptionParams> getHotspotDetails(ShowHotspotRuleDescriptionParams params) {
    var fileUri = params.fileUri;
    return commandManager.getFindingDetails(fileUri, params.getHotspotId());
  }

  @Override
  public CompletableFuture<Void> changeHotspotStatus(ChangeHotspotStatusParams params) {
    var workspace = workspaceFoldersManager.findFolderForFile(create(params.getFileUri()))
      .orElseThrow(() -> new IllegalStateException("No workspace found"));
    var workspaceUri = workspace.getUri();
    var hotspotStatus = hotspotStatusOfTitle(params.getNewStatus());
    var coreParams = new org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.ChangeHotspotStatusParams(
      workspaceUri.toString(), params.getHotspotKey(), hotspotStatus);
    return backendServiceFacade.getBackendService().changeHotspotStatus(coreParams).thenAccept(nothing -> {
      var key = params.getHotspotKey();
      if (hotspotStatus != HotspotStatus.TO_REVIEW && hotspotStatus != HotspotStatus.ACKNOWLEDGED) {
        securityHotspotsCache.removeFindingWithServerKey(params.getFileUri(), key);
      } else {
        securityHotspotsCache.updateHotspotStatus(params.getFileUri(), key, hotspotStatus);
      }
      diagnosticPublisher.publishHotspots(create(params.getFileUri()));
      client.showMessage(new MessageParams(MessageType.Info, "Hotspot status was changed"));
    }).exceptionally(t -> {
      lsLogOutput.errorWithStackTrace("Error changing hotspot status", t);
      client.showMessage(new MessageParams(MessageType.Error, "Could not change status for the hotspot. Look at the SonarQube for IDE output for details."));
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
    var connectionId = bindingOptional.get().connectionId();
    var checkStatusChangePermittedParams = new CheckStatusChangePermittedParams(connectionId, params.getHotspotKey());
    return backendServiceFacade.getBackendService().getAllowedHotspotStatuses(checkStatusChangePermittedParams).thenApply(r -> {
      var delegatingHotspot = securityHotspotsCache
        .findHotspotPerId(params.getFileUri(), params.getHotspotKey()).get().getValue();
      var reviewStatus = delegatingHotspot.getStatus();
      var statuses = r.getAllowedStatuses().stream().filter(s -> s != reviewStatus)
        .map(Enum::name).toList();
      return new GetAllowedHotspotStatusesResponse(
        r.isPermitted(),
        r.getNotPermittedReason(),
        statuses);
    }).exceptionally(t -> {
      lsLogOutput.errorWithStackTrace("Error changing hotspot status", t);
      client.showMessage(new MessageParams(MessageType.Error, "Could not change status for the hotspot. Look at the SonarQube for IDE output for details."));
      return null;
    });
  }

  @Override
  public void reopenResolvedLocalIssues(ReopenAllIssuesForFileParams params) {
    var reopenAllIssuesParams = new org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileParams(
      params.getConfigurationScopeId(), Path.of(params.getRelativePath()));
    backendServiceFacade.getBackendService().reopenAllIssuesForFile(reopenAllIssuesParams).thenApply(r -> {
      if (r.isSuccess()) {
        var fullFileUri = getFullFileUriFromFragments(params.getConfigurationScopeId(), Path.of(params.getRelativePath()));
        // re-trigger analysis for the file
        backendServiceFacade.getBackendService().analyzeFilesList(params.getConfigurationScopeId(), List.of(fullFileUri));
        client.showMessage(new MessageParams(MessageType.Info, "Reopened local issues for " + params.getRelativePath()));
      } else {
        client.showMessage(new MessageParams(MessageType.Info, "There are no resolved issues in file " + params.getRelativePath()));
      }
      return r;
    }).exceptionally(e -> {
      lsLogOutput.errorWithStackTrace("Error while reopening resolved local issues", e);
      client.showMessage(new MessageParams(MessageType.Error, "Could not reopen resolved local issues. Look at the SonarQube for IDE output for details."));
      return null;
    });
  }

  @Override
  public CompletableFuture<Void> analyseOpenFileIgnoringExcludes(AnalyseOpenFileIgnoringExcludesParams params) {
    var notebookUriStr = params.notebookUri();
    URI documentUri;
    VersionedOpenFile versionedOpenFile;
    if (notebookUriStr != null) {
      documentUri = create(notebookUriStr);
      var version = params.notebookVersion();
      var notebookUri = create(notebookUriStr);
      requireNonNull(version);
      var cells = requireNonNull(params.notebookCells());
      var notebookFile = VersionedOpenNotebook.create(
        notebookUri, version,
        cells, notebookDiagnosticPublisher);
      versionedOpenFile = notebookFile.asVersionedOpenFile();
      openNotebooksCache.didOpen(notebookUri, version, cells);
    } else {
      var document = requireNonNull(params.textDocument());
      documentUri = create(document.getUri());
      versionedOpenFile = openFilesCache.didOpen(create(document.getUri()), document.getLanguageId(), document.getText(), document.getVersion());
    }
    if (versionedOpenFile != null) {
      var workspaceFolder = workspaceFoldersManager.findFolderForFile(documentUri);
      CompletableFutures.computeAsync(cancelChecker -> {
        moduleEventsProcessor.notifyBackendWithFileLanguageAndContent(versionedOpenFile);
        workspaceFolder.ifPresent(folder -> backendServiceFacade.getBackendService().analyzeFilesList(folder.getUri().toString(), List.of(documentUri)));
        if (params.triggeredByUser()) {
          telemetry.currentFileAnalysisTriggered();
        }
        return null;
      });
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void analyzeVCSChangedFiles(AnalyzeVCSChangedFilesParams params) {
    params.configScopeIds().forEach(configScopeId -> backendServiceFacade.getBackendService().analyzeVCSChangedFiles(configScopeId));
  }

  @Override
  public void dumpThreads() {
    backendServiceFacade.getBackendService().dumpThreads();
  }

  @Override
  public CompletableFuture<JoinIdeLabsProgramResponse> joinIdeLabsProgram(JoinIdeLabsProgramParams params) {
    return backendServiceFacade.getBackendService().joinIdeLabsProgram(params.getEmail(), params.getIde());
  }
}
