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
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.SecurityHotspotsHandlerServer;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.connected.notifications.ServerNotifications;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.folders.ModuleEventsProcessor;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderBranchManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersProvider;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClient;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.progress.ProgressManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

import static java.net.URI.create;
import static java.util.Optional.ofNullable;

public class SonarLintLanguageServer implements SonarLintExtendedLanguageServer, WorkspaceService, TextDocumentService {

  private static final String TYPESCRIPT_LOCATION = "typeScriptLocation";

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
  private final ApacheHttpClient httpClient;
  private final WorkspaceFolderBranchManager branchManager;
  private final JavaConfigCache javaConfigCache;
  private final IssuesCache issuesCache;
  private final DiagnosticPublisher diagnosticPublisher;
  private final ScmIgnoredCache scmIgnoredCache;

  /**
   * Keep track of value 'sonarlint.trace.server' on client side. Not used currently, but keeping it just in case.
   */
  private TraceValues traceLevel;

  private final ModuleEventsProcessor moduleEventsProcessor;

  SonarLintLanguageServer(InputStream inputStream, OutputStream outputStream, Collection<Path> analyzers, Collection<Path> extraAnalyzers) {
    this.threadPool = Executors.newCachedThreadPool(Utils.threadFactory("SonarLint LSP message processor", false));
    var launcher = new Launcher.Builder<SonarLintExtendedLanguageClient>()
      .setLocalService(this)
      .setRemoteInterface(SonarLintExtendedLanguageClient.class)
      .setInput(inputStream)
      .setOutput(outputStream)
      .setExecutorService(threadPool)
      .create();

    this.client = launcher.getRemoteProxy();
    this.httpClient = ApacheHttpClient.create();
    var lsLogOutput = new LanguageClientLogger(this.client);
    var globalLogOutput = new LanguageClientLogOutput(lsLogOutput, false);
    SonarLintLogger.setTarget(globalLogOutput);
    this.openFilesCache = new OpenFilesCache(lsLogOutput);

    this.issuesCache = new IssuesCache();
    this.taintVulnerabilitiesCache = new TaintVulnerabilitiesCache();
    this.diagnosticPublisher = new DiagnosticPublisher(lsLogOutput, client, taintVulnerabilitiesCache, issuesCache);
    this.workspaceFoldersManager = new WorkspaceFoldersManager();
    this.progressManager = new ProgressManager(client);
    this.settingsManager = new SettingsManager(this.client, this.workspaceFoldersManager, httpClient);
    this.nodeJsRuntime = new NodeJsRuntime(settingsManager);
    var fileTypeClassifier = new FileTypeClassifier();
    javaConfigCache = new JavaConfigCache(client, openFilesCache, lsLogOutput);
    this.enginesFactory = new EnginesFactory(analyzers, globalLogOutput, nodeJsRuntime,
      new WorkspaceFoldersProvider(workspaceFoldersManager, fileTypeClassifier, javaConfigCache), extraAnalyzers);
    this.standaloneEngineManager = new StandaloneEngineManager(enginesFactory);
    this.settingsManager.addListener(lsLogOutput);
    this.bindingManager = new ProjectBindingManager(enginesFactory, workspaceFoldersManager, settingsManager, client, progressManager);
    this.telemetry = new SonarLintTelemetry(httpClient, settingsManager, bindingManager, nodeJsRuntime, standaloneEngineManager);
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
      fileTypeClassifier, issuesCache, taintVulnerabilitiesCache, telemetry, skippedPluginsNotifier, standaloneEngineManager, diagnosticPublisher);
    this.analysisScheduler = new AnalysisScheduler(lsLogOutput, workspaceFoldersManager, bindingManager, openFilesCache, analysisTaskExecutor);
    this.workspaceFoldersManager.addListener(moduleEventsProcessor);
    bindingManager.setAnalysisManager(analysisScheduler);
    this.settingsManager.addListener(analysisScheduler);
    this.commandManager = new CommandManager(client, settingsManager, bindingManager, telemetry, standaloneEngineManager, taintVulnerabilitiesCache, issuesCache);
    this.securityHotspotsHandlerServer = new SecurityHotspotsHandlerServer(lsLogOutput, bindingManager, client, telemetry, settingsManager);
    this.branchManager = new WorkspaceFolderBranchManager(client, bindingManager);
    this.bindingManager.setBranchResolver(branchManager::getReferenceBranchNameForFolder);
    this.workspaceFoldersManager.addListener(this.branchManager);
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
      var firstSecretDetected = Boolean.parseBoolean((String) options.get("firstSecretDetected"));
      var typeScriptPath = ofNullable((String) options.get(TYPESCRIPT_LOCATION));
      var additionalAttributes = ofNullable((Map<String, Object>) options.get("additionalAttributes")).orElse(Collections.emptyMap());

      enginesFactory.initialize(typeScriptPath.map(Paths::get).orElse(null));
      analysisScheduler.initialize();
      diagnosticPublisher.initialize(firstSecretDetected);

      securityHotspotsHandlerServer.initialize(appName, clientVersion, workspaceName);
      telemetry.initialize(productKey, telemetryStorage, productName, productVersion, ideVersion, additionalAttributes);

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
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      securityHotspotsHandlerServer.shutdown();
      analysisScheduler.shutdown();
      bindingManager.shutdown();
      telemetry.stop();
      settingsManager.shutdown();
      threadPool.shutdown();
      httpClient.close();
      serverNotifications.shutdown();
      standaloneEngineManager.shutdown();
      moduleEventsProcessor.shutdown();
      return new Object();
    });
  }

  @Override
  public void exit() {
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
    var file = openFilesCache.didOpen(uri, params.getTextDocument().getLanguageId(), params.getTextDocument().getText(), params.getTextDocument().getVersion());
    analysisScheduler.didOpen(file);
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
    taintVulnerabilitiesCache.didClose(uri);
    javaConfigCache.didClose(uri);
    diagnosticPublisher.didClose(uri);
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
  public void setTraceNotification(SetTraceNotificationParams params) {
    this.traceLevel = parseTraceLevel(params.getValue());
  }

  private static TraceValues parseTraceLevel(@Nullable String trace) {
    return ofNullable(trace)
      .map(String::toUpperCase)
      .map(TraceValues::valueOf)
      .orElse(TraceValues.OFF);
  }

  @Override
  public void didClasspathUpdate(String projectUriStr) {
    var projectUri = create(projectUriStr);
    javaConfigCache.didClasspathUpdate(projectUri);
    analysisScheduler.didClasspathUpdate();
  }

  @Override
  public void didJavaServerModeChange(String serverMode) {
    var serverModeEnum = ServerMode.of(serverMode);
    javaConfigCache.didServerModeChange(serverModeEnum);
    analysisScheduler.didServerModeChange(serverModeEnum);
  }

  @Override
  public void didLocalBranchNameChange(LocalBranchNameChangeEvent event) {
    branchManager.didBranchNameChange(create(event.getFolderUri()), event.getBranchName());
  }

  @Override
  public void cancelProgress(WorkDoneProgressCancelParams params) {
    progressManager.cancelProgress(params);
  }
}
