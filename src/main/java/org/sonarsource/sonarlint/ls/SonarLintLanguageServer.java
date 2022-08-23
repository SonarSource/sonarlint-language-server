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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
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
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
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
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.SecurityHotspotsHandlerServer;
import org.sonarsource.sonarlint.ls.connected.TaintIssuesUpdater;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.connected.notifications.ServerNotifications;
import org.sonarsource.sonarlint.ls.connected.sync.ServerSynchronizer;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.folders.ModuleEventsProcessor;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderBranchManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersProvider;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClientProvider;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.progress.ProgressManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.util.ExitingInputStream;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.net.URI.create;
import static java.util.Optional.ofNullable;
import static org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult.failure;
import static org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult.success;

public class SonarLintLanguageServer implements SonarLintExtendedLanguageServer, WorkspaceService, TextDocumentService {

  private final SonarLintExtendedLanguageClient client;
  private final SonarLintTelemetry telemetry;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private final ServerNotifications serverNotifications;
  private final AnalysisScheduler analysisScheduler;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final OpenFilesCache openFilesCache;
  private final NodeJsRuntime nodeJsRuntime;
  private final EnginesFactory enginesFactory;
  private final StandaloneEngineManager standaloneEngineManager;
  private final CommandManager commandManager;
  private final ProgressManager progressManager;
  private final ExecutorService threadPool;
  private final SecurityHotspotsHandlerServer securityHotspotsHandlerServer;
  private final ApacheHttpClientProvider httpClientProvider;
  private final WorkspaceFolderBranchManager branchManager;
  private final JavaConfigCache javaConfigCache;
  private final IssuesCache issuesCache;
  private final DiagnosticPublisher diagnosticPublisher;
  private final ScmIgnoredCache scmIgnoredCache;
  private ServerSynchronizer serverSynchronizer;
  private final LanguageClientLogger lsLogOutput;

  private final TaintIssuesUpdater taintIssuesUpdater;

  /**
   * Keep track of value 'sonarlint.trace.server' on client side. Not used currently, but keeping it just in case.
   */
  private TraceValue traceLevel;

  private final ModuleEventsProcessor moduleEventsProcessor;

  SonarLintLanguageServer(InputStream inputStream, OutputStream outputStream, Collection<Path> analyzers, Collection<Path> extraAnalyzers) {
    this.threadPool = Executors.newCachedThreadPool(Utils.threadFactory("SonarLint LSP message processor", false));
    var input = new ExitingInputStream(inputStream, this);
    var launcher = new Launcher.Builder<SonarLintExtendedLanguageClient>()
      .setLocalService(this)
      .setRemoteInterface(SonarLintExtendedLanguageClient.class)
      .setInput(input)
      .setOutput(outputStream)
      .setExecutorService(threadPool)
      .create();

    this.client = launcher.getRemoteProxy();
    this.httpClientProvider = new ApacheHttpClientProvider();
    this.lsLogOutput = new LanguageClientLogger(this.client);
    var globalLogOutput = new LanguageClientLogOutput(lsLogOutput, false);
    SonarLintLogger.setTarget(globalLogOutput);
    this.openFilesCache = new OpenFilesCache(lsLogOutput);

    this.issuesCache = new IssuesCache();
    this.taintVulnerabilitiesCache = new TaintVulnerabilitiesCache();
    this.diagnosticPublisher = new DiagnosticPublisher(client, taintVulnerabilitiesCache, issuesCache);
    this.workspaceFoldersManager = new WorkspaceFoldersManager();
    this.progressManager = new ProgressManager(client);
    this.settingsManager = new SettingsManager(this.client, this.workspaceFoldersManager, httpClientProvider);
    this.nodeJsRuntime = new NodeJsRuntime(settingsManager);
    var fileTypeClassifier = new FileTypeClassifier();
    javaConfigCache = new JavaConfigCache(client, openFilesCache, lsLogOutput);
    this.enginesFactory = new EnginesFactory(analyzers, globalLogOutput, nodeJsRuntime,
      new WorkspaceFoldersProvider(workspaceFoldersManager, fileTypeClassifier, javaConfigCache), extraAnalyzers);
    this.standaloneEngineManager = new StandaloneEngineManager(enginesFactory);
    this.settingsManager.addListener(lsLogOutput);
    this.bindingManager = new ProjectBindingManager(enginesFactory, workspaceFoldersManager, settingsManager, client, globalLogOutput);
    this.settingsManager.setBindingManager(bindingManager);
    this.telemetry = new SonarLintTelemetry(httpClientProvider, settingsManager, bindingManager, nodeJsRuntime, standaloneEngineManager);
    this.settingsManager.addListener(telemetry);
    this.settingsManager.addListener((WorkspaceSettingsChangeListener) bindingManager);
    this.settingsManager.addListener((WorkspaceFolderSettingsChangeListener) bindingManager);
    this.workspaceFoldersManager.addListener(settingsManager);
    this.serverNotifications = new ServerNotifications(client, workspaceFoldersManager, telemetry, lsLogOutput);
    this.settingsManager.addListener((WorkspaceSettingsChangeListener) serverNotifications);
    this.settingsManager.addListener((WorkspaceFolderSettingsChangeListener) serverNotifications);
    var skippedPluginsNotifier = new SkippedPluginsNotifier(client);
    this.scmIgnoredCache = new ScmIgnoredCache(client);
    this.moduleEventsProcessor = new ModuleEventsProcessor(standaloneEngineManager, workspaceFoldersManager, bindingManager, fileTypeClassifier, javaConfigCache);
    var analysisTaskExecutor = new AnalysisTaskExecutor(scmIgnoredCache, lsLogOutput, workspaceFoldersManager, bindingManager, javaConfigCache, settingsManager,
      fileTypeClassifier, issuesCache, taintVulnerabilitiesCache, telemetry, skippedPluginsNotifier, standaloneEngineManager, diagnosticPublisher, client);
    this.analysisScheduler = new AnalysisScheduler(lsLogOutput, workspaceFoldersManager, bindingManager, openFilesCache, analysisTaskExecutor);
    this.workspaceFoldersManager.addListener(moduleEventsProcessor);
    bindingManager.setAnalysisManager(analysisScheduler);
    this.settingsManager.addListener((WorkspaceSettingsChangeListener) analysisScheduler);
    this.settingsManager.addListener((WorkspaceFolderSettingsChangeListener) analysisScheduler);
    this.serverSynchronizer = new ServerSynchronizer(client, progressManager, bindingManager, analysisScheduler);
    this.commandManager = new CommandManager(client, settingsManager, bindingManager, serverSynchronizer, telemetry, standaloneEngineManager, taintVulnerabilitiesCache,
      issuesCache);
    this.securityHotspotsHandlerServer = new SecurityHotspotsHandlerServer(lsLogOutput, bindingManager, client, telemetry, settingsManager);
    this.branchManager = new WorkspaceFolderBranchManager(client, bindingManager);
    this.bindingManager.setBranchResolver(branchManager::getReferenceBranchNameForFolder);
    this.workspaceFoldersManager.addListener(this.branchManager);
    this.taintIssuesUpdater = new TaintIssuesUpdater(bindingManager, taintVulnerabilitiesCache, workspaceFoldersManager, settingsManager, diagnosticPublisher);
    launcher.startListening();
  }

  static void bySocket(int port, Collection<Path> analyzers, Collection<Path> extraAnalyzers) throws IOException {
    var socket = new Socket("localhost", port);
    new SonarLintLanguageServer(socket.getInputStream(), socket.getOutputStream(), analyzers, extraAnalyzers);
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      this.traceLevel = parseTraceLevel(params.getTrace());

      progressManager.setWorkDoneProgressSupportedByClient(ofNullable(params.getCapabilities().getWindow().getWorkDoneProgress()).orElse(false));

      workspaceFoldersManager.initialize(params.getWorkspaceFolders());

      var options = Utils.parseToMap(params.getInitializationOptions());

      var productKey = (String) options.get("productKey");
      // deprecated, will be ignored when productKey present
      var telemetryStorage = (String) options.get("telemetryStorage");

      var productName = (String) options.get("productName");
      var productVersion = (String) options.get("productVersion");
      var appName = params.getClientInfo().getName();
      var workspaceName = (String) options.get("workspaceName");
      var clientVersion = params.getClientInfo().getVersion();
      var ideVersion = appName + " " + clientVersion;
      var firstSecretDetected = (boolean) options.getOrDefault("firstSecretDetected", false);
      httpClientProvider.initialize(productName, productVersion);
      var platform = (String) options.get("platform");
      var architecture = (String) options.get("architecture");
      var additionalAttributes = (Map<String, Object>) options.getOrDefault("additionalAttributes", Map.of());
      var showVerboseLogs = (boolean) options.getOrDefault("showVerboseLogs", true);

      lsLogOutput.initialize(showVerboseLogs);
      analysisScheduler.initialize();
      diagnosticPublisher.initialize(firstSecretDetected);

      securityHotspotsHandlerServer.initialize(appName, clientVersion, workspaceName);
      telemetry.initialize(productKey, telemetryStorage, productName, productVersion, ideVersion, platform, architecture, additionalAttributes);

      var c = new ServerCapabilities();
      c.setTextDocumentSync(getTextDocumentSyncOptions());
      c.setCodeActionProvider(true);
      var executeCommandOptions = new ExecuteCommandOptions(CommandManager.SONARLINT_SERVERSIDE_COMMANDS);
      executeCommandOptions.setWorkDoneProgress(true);
      c.setExecuteCommandProvider(executeCommandOptions);
      c.setWorkspace(getWorkspaceServerCapabilities());

      var info = new ServerInfo("SonarLint Language Server", getServerVersion("slls-version.txt"));

      return new InitializeResult(c, info);
    });
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
      securityHotspotsHandlerServer::shutdown,
      telemetry::stop,
      settingsManager::shutdown,
      workspaceFoldersManager::shutdown,
      httpClientProvider::close,
      serverNotifications::shutdown,
      moduleEventsProcessor::shutdown,
      // shutdown engines after the rest so that no operations remain on them, and they won't be recreated accidentally
      bindingManager::shutdown,
      standaloneEngineManager::shutdown)
      // Do last
      .forEach(this::invokeQuietly);
    return CompletableFuture.completedFuture(null);
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
    client.isOpenInEditor(uri.toString()).thenAccept(isOpen -> {
      if (Boolean.TRUE.equals(isOpen)) {
        var file = openFilesCache.didOpen(uri, params.getTextDocument().getLanguageId(), params.getTextDocument().getText(), params.getTextDocument().getVersion());
        analysisScheduler.didOpen(file);
        taintIssuesUpdater.updateTaintIssues(uri);
      } else {
        SonarLintLogger.get().debug("Skipping analysis for preview of file {}", uri);
      }
    });
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    var uri = create(params.getTextDocument().getUri());
    openFilesCache.didChange(uri, params.getContentChanges().get(0).getText(), params.getTextDocument().getVersion());
    analysisScheduler.didChange(uri);
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    var uri = create(params.getTextDocument().getUri());
    analysisScheduler.didClose(uri);
    openFilesCache.didClose(uri);
    javaConfigCache.didClose(uri);
    scmIgnoredCache.didClose(uri);
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
    String connectionId = params.getConnectionId();
    SonarLintLogger.get().debug("Received refresh request for {}", connectionId);
    var config = bindingManager.getServerConfigurationFor(connectionId);
    if (config != null) {
      return config.validateConnection().thenApply(validationResult -> validationResult.success() ? success(connectionId) : failure(connectionId, validationResult.message()));
    }
    return CompletableFuture.completedFuture(failure(connectionId, String.format("Connection '%s' is unknown", connectionId)));
  }

  @Override
  public CompletableFuture<Map<String, String>> getRemoteProjectsForConnection(GetRemoteProjectsForConnectionParams getRemoteProjectsForConnectionParams) {
    return CompletableFuture.completedFuture(
      bindingManager.getRemoteProjects(getRemoteProjectsForConnectionParams.getConnectionId()));
  }

  @Override
  public void onTokenUpdate() {
    SonarLintLogger.get().info("Updating configuration on token change.");
    didChangeConfiguration(new DidChangeConfigurationParams());
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
}
