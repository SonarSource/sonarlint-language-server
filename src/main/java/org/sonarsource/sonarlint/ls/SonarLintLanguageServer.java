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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.SecurityHotspotsHandlerServer;
import org.sonarsource.sonarlint.ls.connected.notifications.ServerNotifications;
import org.sonarsource.sonarlint.ls.file.FileLanguageCache;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersProvider;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClient;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.progress.ProgressManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

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
  private final AnalysisManager analysisManager;
  private final NodeJsRuntime nodeJsRuntime;
  private final EnginesFactory enginesFactory;
  private final CommandManager commandManager;
  private final ProgressManager progressManager;
  private final ExecutorService threadPool;
  private final SecurityHotspotsHandlerServer securityHotspotsHandlerServer;
  private final ApacheHttpClient httpClient;
  private final FileLanguageCache fileLanguageCache = new FileLanguageCache();

  /**
   * Keep track of value 'sonarlint.trace.server' on client side. Not used currently, but keeping it just in case.
   */
  private TraceValues traceLevel;

  SonarLintLanguageServer(InputStream inputStream, OutputStream outputStream, Collection<URL> analyzers) {
    this.threadPool = Executors.newCachedThreadPool(Utils.threadFactory("SonarLint LSP message processor", false));
    Launcher<SonarLintExtendedLanguageClient> launcher = new Launcher.Builder<SonarLintExtendedLanguageClient>()
      .setLocalService(this)
      .setRemoteInterface(SonarLintExtendedLanguageClient.class)
      .setInput(inputStream)
      .setOutput(outputStream)
      .setExecutorService(threadPool)
      .create();

    this.client = launcher.getRemoteProxy();
    this.httpClient = ApacheHttpClient.create();
    LanguageClientLogOutput lsLogOutput = new LanguageClientLogOutput(this.client);
    Loggers.setTarget(lsLogOutput);
    this.telemetry = new SonarLintTelemetry(httpClient);
    this.workspaceFoldersManager = new WorkspaceFoldersManager();
    this.progressManager = new ProgressManager(client);
    this.settingsManager = new SettingsManager(this.client, this.workspaceFoldersManager, httpClient);
    this.nodeJsRuntime = new NodeJsRuntime(settingsManager);
    FileTypeClassifier fileTypeClassifier = new FileTypeClassifier(fileLanguageCache);
    JavaConfigCache javaConfigCache = new JavaConfigCache(client, fileLanguageCache);
    this.enginesFactory = new EnginesFactory(analyzers, lsLogOutput, nodeJsRuntime,
      new WorkspaceFoldersProvider(workspaceFoldersManager, fileTypeClassifier, javaConfigCache));
    this.settingsManager.addListener(telemetry);
    this.settingsManager.addListener(lsLogOutput);
    this.bindingManager = new ProjectBindingManager(enginesFactory, workspaceFoldersManager, settingsManager, client, progressManager);
    this.settingsManager.addListener((WorkspaceSettingsChangeListener) bindingManager);
    this.settingsManager.addListener((WorkspaceFolderSettingsChangeListener) bindingManager);
    this.workspaceFoldersManager.addListener(settingsManager);
    this.serverNotifications = new ServerNotifications(client, workspaceFoldersManager, telemetry, lsLogOutput);
    this.settingsManager.addListener((WorkspaceSettingsChangeListener) serverNotifications);
    this.settingsManager.addListener((WorkspaceFolderSettingsChangeListener) serverNotifications);
    this.analysisManager = new AnalysisManager(lsLogOutput, enginesFactory, client, telemetry, workspaceFoldersManager, settingsManager, bindingManager, fileTypeClassifier,
      fileLanguageCache, javaConfigCache);
    this.workspaceFoldersManager.addListener(analysisManager);
    bindingManager.setAnalysisManager(analysisManager);
    this.settingsManager.addListener(analysisManager);
    this.commandManager = new CommandManager(client, settingsManager, bindingManager, analysisManager, telemetry);
    this.securityHotspotsHandlerServer = new SecurityHotspotsHandlerServer(lsLogOutput, bindingManager, client, telemetry);
    launcher.startListening();
  }

  static void bySocket(int port, Collection<URL> analyzers) throws IOException {
    Socket socket = new Socket("localhost", port);
    new SonarLintLanguageServer(socket.getInputStream(), socket.getOutputStream(), analyzers);
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      this.traceLevel = parseTraceLevel(params.getTrace());

      progressManager.setWorkDoneProgressSupportedByClient(ofNullable(params.getCapabilities().getWindow().getWorkDoneProgress()).orElse(false));

      workspaceFoldersManager.initialize(params.getWorkspaceFolders());

      Map<String, Object> options = Utils.parseToMap(params.getInitializationOptions());

      String productKey = (String) options.get("productKey");
      // deprecated, will be ignored when productKey present
      String telemetryStorage = (String) options.get("telemetryStorage");

      String productName = (String) options.get("productName");
      String productVersion = (String) options.get("productVersion");
      // Don't use params.getClientInfo().getName() because it is currently hardcoded to 'vscode'
      // until https://github.com/microsoft/vscode-languageserver-node/pull/697 is released
      // params.getClientInfo().getName()
      String appName = (String) options.get("appName");
      String workspaceName = (String) options.get("workspaceName");
      String clientVersion = params.getClientInfo().getVersion();
      String ideVersion = appName + " " + clientVersion;

      Optional<String> typeScriptPath = ofNullable((String) options.get(TYPESCRIPT_LOCATION));

      enginesFactory.initialize(typeScriptPath.map(Paths::get).orElse(null));
      analysisManager.initialize();

      securityHotspotsHandlerServer.init(appName, clientVersion, workspaceName);
      telemetry.init(productKey, telemetryStorage, productName, productVersion, ideVersion,
        bindingManager::usesConnectedMode, bindingManager::usesSonarCloud, bindingManager::devNotificationsDisabled, nodeJsRuntime::nodeVersion);

      ServerCapabilities c = new ServerCapabilities();
      c.setTextDocumentSync(getTextDocumentSyncOptions());
      c.setCodeActionProvider(true);
      ExecuteCommandOptions executeCommandOptions = new ExecuteCommandOptions(CommandManager.SONARLINT_SERVERSIDE_COMMANDS);
      executeCommandOptions.setWorkDoneProgress(true);
      c.setExecuteCommandProvider(executeCommandOptions);
      c.setWorkspace(getWorkspaceServerCapabilities());

      ServerInfo info = new ServerInfo("SonarLint Language Server", getServerVersion("slls-version.txt"));

      return new InitializeResult(c, info);
    });
  }

  @CheckForNull
  static String getServerVersion(String fileName) {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    try (InputStream is = classLoader.getResourceAsStream(fileName)) {
      try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(isr)) {
        return reader.lines().findFirst().orElse(null);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read server version", e);
    }
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
      securityHotspotsHandlerServer.shutdown();
      analysisManager.shutdown();
      bindingManager.shutdown();
      telemetry.stop();
      settingsManager.shutdown();
      threadPool.shutdown();
      httpClient.close();
      serverNotifications.shutdown();
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
    URI uri = create(params.getTextDocument().getUri());
    analysisManager.didOpen(uri, params.getTextDocument().getLanguageId(), params.getTextDocument().getText());
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    URI uri = create(params.getTextDocument().getUri());
    analysisManager.didChange(uri, params.getContentChanges().get(0).getText());
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    URI uri = create(params.getTextDocument().getUri());
    analysisManager.didClose(uri);
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    URI uri = create(params.getTextDocument().getUri());
    analysisManager.didSave(uri, params.getText());
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
    analysisManager.didChangeWatchedFiles(params.getChanges());
  }

  @Override
  public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
    WorkspaceFoldersChangeEvent event = params.getEvent();
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
  public void didClasspathUpdate(String projectUri) {
    analysisManager.didClasspathUpdate(create(projectUri));
  }

  @Override
  public void didJavaServerModeChange(String serverMode) {
    analysisManager.didServerModeChange(ServerMode.of(serverMode));
  }

  @Override
  public void cancelProgress(WorkDoneProgressCancelParams params) {
    progressManager.cancelProgress(params);
  }
}
