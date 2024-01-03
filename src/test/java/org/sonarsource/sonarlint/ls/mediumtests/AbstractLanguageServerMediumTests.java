/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.sonarlint.ls.mediumtests;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.assertj.core.api.iterable.ThrowingExtractor;
import org.awaitility.core.ThrowingRunnable;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeNotebookDocumentParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseNotebookDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenNotebookDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.NotebookDocument;
import org.eclipse.lsp4j.NotebookDocumentChangeEvent;
import org.eclipse.lsp4j.NotebookDocumentClientCapabilities;
import org.eclipse.lsp4j.NotebookDocumentIdentifier;
import org.eclipse.lsp4j.NotebookDocumentSyncClientCapabilities;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedNotebookDocumentIdentifier;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WindowClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.ServerMain;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.SonarLintLanguageServer;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;
import picocli.CommandLine;
import testutils.LogTestStartAndEnd;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.sonarsource.sonarlint.ls.SonarLintLanguageServer.JUPYTER_NOTEBOOK_TYPE;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.DOTNET_DEFAULT_SOLUTION_PATH;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.OMNISHARP_LOAD_PROJECT_ON_DEMAND;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.OMNISHARP_PROJECT_LOAD_TIMEOUT;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.OMNISHARP_USE_MODERN_NET;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.SONARLINT_CONFIGURATION_NAMESPACE;

@ExtendWith(LogTestStartAndEnd.class)
public abstract class AbstractLanguageServerMediumTests {

  protected final static boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;

  private static final Set<Path> staticTempDirs = new HashSet<>();
  private final Set<Path> instanceTempDirs = new HashSet<>();
  Path temp;
  protected Set<String> toBeClosed = new HashSet<>();
  protected Set<String> notebooksToBeClosed = new HashSet<>();
  protected Set<String> foldersToRemove = new HashSet<>();
  private static ServerSocket serverSocket;
  protected static SonarLintExtendedLanguageServer lsProxy;
  protected static FakeLanguageClient client;

  @BeforeAll
  static void startServer() throws Exception {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    EnginesFactory.sonarLintUserHomeOverride = makeStaticTempDir();
    serverSocket = new ServerSocket(0);
    var port = serverSocket.getLocalPort();

    client = new FakeLanguageClient();

    var executor = Executors.newFixedThreadPool(2);
    var future = executor.submit(() -> {
      Socket socket = serverSocket.accept();
      Launcher<SonarLintExtendedLanguageServer> clientSideLauncher = new LSPLauncher.Builder<SonarLintExtendedLanguageServer>()
        .setLocalService(client)
        .setRemoteInterface(SonarLintExtendedLanguageServer.class)
        .setInput(socket.getInputStream())
        .setOutput(socket.getOutputStream())
        .create();
      clientSideLauncher.startListening();
      return clientSideLauncher.getRemoteProxy();
    });

    var go = fullPathToJar("sonargo");
    var iac = fullPathToJar("sonariac");
    var html = fullPathToJar("sonarhtml");
    var java = fullPathToJar("sonarjava");
    var js = fullPathToJar("sonarjs");
    var php = fullPathToJar("sonarphp");
    var py = fullPathToJar("sonarpython");
    var text = fullPathToJar("sonartext");
    var xml = fullPathToJar("sonarxml");
    var omnisharp = fullPathToJar("sonarlintomnisharp");
    String[] languageServerArgs = new String[]{"-port", "" + port, "-analyzers", go, java, js, php, py, html, xml, text, iac, omnisharp};
    if (COMMERCIAL_ENABLED) {
      var cfamily = fullPathToJar("cfamily");
      languageServerArgs = ArrayUtils.add(languageServerArgs, cfamily);
    }

    try {
      var cmd = new CommandLine(new ServerMain());
      var cmdOutput = new StringWriter();
      cmd.setErr(new PrintWriter(cmdOutput));
      cmd.setOut(new PrintWriter(cmdOutput));

      var clonedArgs = ArrayUtils.clone(languageServerArgs);
      executor.submit(() -> cmd.execute(clonedArgs));
      executor.shutdown();
    } catch (Exception e) {
      e.printStackTrace();
      future.get(1, TimeUnit.SECONDS);
      if (!future.isDone()) {
        future.cancel(true);
      }
      throw e;
    }

    lsProxy = future.get();
  }

  protected static String fullPathToJar(String jarName) {
    return Paths.get("target/plugins").resolve(jarName + ".jar").toAbsolutePath().toString();
  }

  protected static void initialize(Map<String, Object> initializeOptions, WorkspaceFolder... initFolders) throws InterruptedException, ExecutionException {
    var initializeParams = new InitializeParams();
    initializeParams.setTrace("messages");
    initializeParams.setInitializationOptions(initializeOptions);
    initializeParams.setWorkspaceFolders(List.of(initFolders));
    initializeParams.setClientInfo(new ClientInfo("SonarLint LS Medium tests", "1.0"));
    var clientCapabilities = new ClientCapabilities();
    var notebookDocument = new NotebookDocumentClientCapabilities();
    var synchronization = new NotebookDocumentSyncClientCapabilities();
    synchronization.setDynamicRegistration(true);
    synchronization.setExecutionSummarySupport(true);
    notebookDocument.setSynchronization(synchronization);
    clientCapabilities.setNotebookDocument(notebookDocument);
    initializeParams.setCapabilities(clientCapabilities);
    initializeParams.getCapabilities().setWindow(new WindowClientCapabilities());
    var initializeResult = lsProxy.initialize(initializeParams).get();
    assertThat(initializeResult.getServerInfo().getName()).isEqualTo("SonarLint Language Server");
    assertThat(initializeResult.getServerInfo().getVersion()).isNotBlank();
    if (SonarLintLanguageServer.isEnableNotebooks(initializeOptions)) {
      assertThat(initializeResult.getCapabilities().getNotebookDocumentSync()).isNotNull();
    } else {
      assertThat(initializeResult.getCapabilities().getNotebookDocumentSync()).isNull();
    }
    lsProxy.initialized(new InitializedParams());
  }

  @AfterAll
  public static void stopServer() throws Exception {
    staticTempDirs.forEach(tempDirPath -> FileUtils.deleteQuietly(tempDirPath.toFile()));
    staticTempDirs.clear();
    System.clearProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY);
    try {
      if (lsProxy != null) {
        lsProxy.shutdown().join();
        lsProxy.exit();
      }
    } finally {
      serverSocket.close();
    }
  }

  @BeforeEach
  void cleanup() throws Exception {
    temp = makeInstanceTempDir();
    // Reset state on LS side
    client.clear();
    toBeClosed.clear();
    notebooksToBeClosed.clear();

    setupGlobalSettings(client.globalSettings);
    setUpFolderSettings(client.folderSettings);

    notifyConfigurationChangeOnClient();
    verifyConfigurationChangeOnClient();
  }

  protected void setupGlobalSettings(Map<String, Object> globalSettings) {
    // do nothing by default
  }

  protected void setUpFolderSettings(Map<String, Map<String, Object>> folderSettings) {
    // do nothing by default
  }

  protected void verifyConfigurationChangeOnClient() {
    // do nothing by default
  }

  @AfterEach
  final void closeFiles() {
    // Close all opened files
    for (var uri : toBeClosed) {
      lsProxy.getTextDocumentService().didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
    }
    for (var uri : notebooksToBeClosed) {
      lsProxy.getNotebookDocumentService().didClose(new DidCloseNotebookDocumentParams(new NotebookDocumentIdentifier(uri), List.of()));
    }
    var changeWorkspaceFoldersParams = new DidChangeWorkspaceFoldersParams();
    var event = new WorkspaceFoldersChangeEvent();
    event.setRemoved(foldersToRemove.stream().map(WorkspaceFolder::new).collect(Collectors.toList()));
    changeWorkspaceFoldersParams.setEvent(event);
    lsProxy.getWorkspaceService().didChangeWorkspaceFolders(changeWorkspaceFoldersParams);
    instanceTempDirs.forEach(tempDirPath -> FileUtils.deleteQuietly(tempDirPath.toFile()));
    instanceTempDirs.clear();
  }

  protected static void assertLogContains(String msg) {
    assertLogContainsPattern("\\[.*\\] " + Pattern.quote(msg));
  }

  protected static void assertLogContainsPattern(String msgPattern) {
    await().atMost(10, SECONDS).untilAsserted(() -> assertThat(client.logs).anyMatch(p -> p.getMessage().matches(msgPattern)));
  }

  protected String getUri(String filename) throws IOException {
    var file = temp.resolve(filename);
    Files.createFile(file);
    return file.toUri().toString();
  }

  protected static void awaitLatch(CountDownLatch latch) {
    try {
      assertTrue(latch.await(15, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      fail(e);
    }
  }

  protected static class FakeLanguageClient implements SonarLintExtendedLanguageClient {

    Map<String, List<Diagnostic>> diagnostics = new ConcurrentHashMap<>();
    Map<String, List<Diagnostic>> hotspots = new ConcurrentHashMap<>();
    Queue<MessageParams> logs = new ConcurrentLinkedQueue<>();
    Map<String, Object> globalSettings = new HashMap<>();
    Map<String, Map<String, Object>> folderSettings = new HashMap<>();
    Map<String, GetJavaConfigResponse> javaConfigs = new HashMap<>();
    Map<String, String> branchNameByFolder = new HashMap<>();
    Map<String, String> referenceBranchNameByFolder = new HashMap<>();
    CountDownLatch settingsLatch = new CountDownLatch(0);
    CountDownLatch showRuleDescriptionLatch = new CountDownLatch(0);
    CountDownLatch suggestBindingLatch = new CountDownLatch(0);
    CountDownLatch readyForTestsLatch = new CountDownLatch(0);
    ShowAllLocationsCommand.Param showIssueParams;
    SuggestBindingParams suggestedBindings;
    ShowRuleDescriptionParams ruleDesc;
    boolean isIgnoredByScm = false;
    boolean shouldAnalyseFile = true;
    final AtomicInteger needCompilationDatabaseCalls = new AtomicInteger();
    final Set<String> openedLinks = new HashSet<>();
    final Set<MessageParams> shownMessages = new HashSet<>();
    final Map<String, NewCodeDefinitionDto> newCodeDefinitionCache = new HashMap<>();

    void clear() {
      diagnostics.clear();
      hotspots.clear();
      logs.clear();
      shownMessages.clear();
      globalSettings = new HashMap<>();
      setDisableTelemetry(globalSettings, true);
      folderSettings.clear();
      settingsLatch = new CountDownLatch(0);
      showRuleDescriptionLatch = new CountDownLatch(0);
      suggestBindingLatch = new CountDownLatch(0);
      readyForTestsLatch = new CountDownLatch(0);
      needCompilationDatabaseCalls.set(0);
      shouldAnalyseFile = true;
    }

    @Override
    public void telemetryEvent(Object object) {
    }

    List<Diagnostic> getDiagnostics(String uri) {
      return diagnostics.getOrDefault(uri, List.of());
    }

    List<Diagnostic> getHotspots(String uri) {
      return hotspots.getOrDefault(uri, List.of());
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
      this.diagnostics.put(diagnostics.getUri(), diagnostics.getDiagnostics());
    }

    @Override
    public void publishSecurityHotspots(PublishDiagnosticsParams diagnostics) {
      this.hotspots.put(diagnostics.getUri(), diagnostics.getDiagnostics());
    }

    @Override
    public void showMessage(MessageParams messageParams) {
      shownMessages.add(messageParams);
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {
      // SSLRSQBR-72 This log is produced by analyzers ProgressReport, and keeps coming long after the analysis has completed. Just ignore
      // it
      if (!message.getMessage().contains("1/1 source files have been analyzed")) {
        logs.add(message);
      }
      System.out.println(message.getMessage());
    }

    @Override
    public void notifyProgress(ProgressParams params) {
      System.out.println(params);
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
      return CompletableFutures.computeAsync(cancelToken -> {
        List<Object> result;
        try {
          assertThat(configurationParams.getItems()).extracting(ConfigurationItem::getSection).containsExactly(SONARLINT_CONFIGURATION_NAMESPACE,
            DOTNET_DEFAULT_SOLUTION_PATH, OMNISHARP_USE_MODERN_NET, OMNISHARP_LOAD_PROJECT_ON_DEMAND, OMNISHARP_PROJECT_LOAD_TIMEOUT);
          result = new ArrayList<>(configurationParams.getItems().size());
          for (var item : configurationParams.getItems()) {
            if (item.getScopeUri() == null && item.getSection().equals(SONARLINT_CONFIGURATION_NAMESPACE)) {
              result.add(globalSettings);
            } else if (item.getScopeUri() != null && !item.getSection().equals(SONARLINT_CONFIGURATION_NAMESPACE)) {
              result
                .add(Optional.ofNullable(folderSettings.get(item.getScopeUri()))
                  .orElseThrow(() -> new IllegalStateException("No settings mocked for workspaceFolderPath " + item.getScopeUri())));
            }
          }
        } finally {
          settingsLatch.countDown();
        }
        return result;
      });
    }

    @Override
    public void readyForTests() {
      readyForTestsLatch.countDown();
    }

    @Override
    public CompletableFuture<Boolean> askSslCertificateConfirmation(SslCertificateConfirmationParams params) {
      return null;
    }

    @Override
    public void showSoonUnsupportedVersionMessage(ShowSoonUnsupportedVersionMessageParams messageParams) {
    }

    @Override
    public void submitNewCodeDefinition(SubmitNewCodeDefinitionParams params) {
      newCodeDefinitionCache.put(params.getFolderUri(),
        new NewCodeDefinitionDto(params.getNewCodeDefinitionOrMessage(), params.isSupported()));
    }

    @Override
    public void suggestBinding(SuggestBindingParams binding) {
      this.suggestedBindings = binding;
      suggestBindingLatch.countDown();
    }

    @Override
    public CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInFolder(FindFileByNamesInFolder params) {
      return CompletableFuture.completedFuture(new FindFileByNamesInScopeResponse(Collections.emptyList()));
    }

    @Override
    public void showSonarLintOutput() {
    }

    @Override
    public void openJavaHomeSettings() {
    }

    @Override
    public void openPathToNodeSettings() {
    }

    @Override
    public void doNotShowMissingRequirementsMessageAgain() {

    }

    @Override
    public CompletableFuture<Boolean> canShowMissingRequirementsNotification() {
      return CompletableFuture.completedFuture(false);
    }

    @Override
    public void showRuleDescription(ShowRuleDescriptionParams params) {
      this.ruleDesc = params;
      showRuleDescriptionLatch.countDown();
    }

    @Override
    public void showHotspot(HotspotDetailsDto h) {
    }

    @Override
    public void showIssue(ShowAllLocationsCommand.Param issue) {
      this.showIssueParams = issue;
    }

    @Override
    public void showIssueOrHotspot(ShowAllLocationsCommand.Param params) {
    }

    @Override
    public CompletableFuture<Boolean> isIgnoredByScm(String fileUri) {
      return CompletableFutures.computeAsync(cancelToken -> isIgnoredByScm);
    }

    @Override
    public CompletableFuture<ShouldAnalyseFileCheckResult> shouldAnalyseFile(SonarLintExtendedLanguageServer.UriParams fileUri) {
      return CompletableFutures.computeAsync(cancelToken -> new ShouldAnalyseFileCheckResult(shouldAnalyseFile, "reason"));

    }

    @Override
    public CompletableFuture<FileUrisResult> filterOutExcludedFiles(FileUrisParams params) {
      return CompletableFutures.computeAsync(cancelToken -> new FileUrisResult(params.getFileUris()));
    }

    @Override
    public void showFirstSecretDetectionNotification() {
    }

    @Override
    public CompletableFuture<GetJavaConfigResponse> getJavaConfig(String fileUri) {
      return CompletableFutures.computeAsync(cancelToken -> {
        return javaConfigs.get(fileUri);
      });
    }

    @Override
    public void browseTo(String link) {
      openedLinks.add(link);
    }

    @Override
    public void openConnectionSettings(boolean isSonarCloud) {
    }

    @Override
    public void assistCreatingConnection(CreateConnectionParams params) {
    }

    @Override
    public void assistBinding(AssistBindingParams params) {
    }

    @Override
    public CompletableFuture<String> getBranchNameForFolder(String folderUri) {
      return CompletableFutures.computeAsync(cancelToken -> branchNameByFolder.get(folderUri));
    }

    @Override
    public void setReferenceBranchNameForFolder(ReferenceBranchForFolder newReferenceBranch) {
      referenceBranchNameByFolder.put(newReferenceBranch.getFolderUri(), newReferenceBranch.getBranchName());
    }

    @Override
    public void needCompilationDatabase() {
      this.needCompilationDatabaseCalls.incrementAndGet();
    }

    @Override
    public void reportConnectionCheckResult(ConnectionCheckResult result) {
      // NOP
    }

    @Override
    public CompletableFuture<String> getTokenForServer(String serverId) {
      return CompletableFutures.computeAsync(server -> "token");
    }
  }

  protected static void notifyConfigurationChangeOnClient() {
    client.settingsLatch = new CountDownLatch(1);
    lsProxy.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(Map.of("sonarlint", client.globalSettings)));
    awaitLatch(client.settingsLatch);
    // workspace/configuration has been called by server, but give some time for the response to be processed (settings change listeners)
    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  protected static void setTestFilePattern(Map<String, Object> config, @Nullable String testFilePattern) {
    if (testFilePattern != null) {
      config.put("testFilePattern", testFilePattern);
    } else {
      config.remove("testFilePattern");
    }
  }

  protected static void setPathToCompileCommands(Map<String, Object> config, @Nullable String pathToCompileCommands) {
    if (pathToCompileCommands != null) {
      config.put("pathToCompileCommands", pathToCompileCommands);
    } else {
      config.remove("pathToCompileCommands");
    }
  }

  protected static void setDisableTelemetry(Map<String, Object> config, @Nullable Boolean disableTelemetry) {
    if (disableTelemetry != null) {
      config.put("disableTelemetry", disableTelemetry);
    } else {
      config.remove("disableTelemetry");
    }
  }

  protected static void setShowAnalyzerLogs(Map<String, Object> config, @Nullable Boolean showAnalyzerLogs) {
    if (showAnalyzerLogs != null) {
      ((HashMap<String, Object>) config.computeIfAbsent("output", k -> new HashMap<String, Object>())).put("showAnalyzerLogs", showAnalyzerLogs);
    } else {
      config.remove("showAnalyzerLogs");
    }
  }

  protected static void setShowVerboseLogs(Map<String, Object> config, @Nullable Boolean showVerboseLogs) {
    if (showVerboseLogs != null) {
      ((HashMap<String, Object>) config.computeIfAbsent("output", k -> new HashMap<String, Object>())).put("showVerboseLogs", showVerboseLogs);
    } else {
      config.remove("showVerboseLogs");
    }
  }

  protected static void setAnalyzerProperties(Map<String, Object> config, Map<String, String> analyzerProperties) {
    if (analyzerProperties.isEmpty()) {
      config.put("analyzerProperties", analyzerProperties);
    } else {
      config.remove("analyzerProperties");
    }
  }

  protected static void addSonarQubeConnection(Map<String, Object> config, String connectionId, String url, String token) {
    var connectedMode = (Map<String, Object>) config.computeIfAbsent("connectedMode", k -> new HashMap<String, Object>());
    var connections = (Map<String, Object>) connectedMode.computeIfAbsent("connections", k -> new HashMap<String, Object>());
    var sonarqubeConnections = (List) connections.computeIfAbsent("sonarqube", k -> new ArrayList<>());
    sonarqubeConnections.add(Map.of("connectionId", connectionId, "serverUrl", url, "token", token));
  }

  protected static void bindProject(Map<String, Object> config, String connectionId, String projectKey) {
    var connectedMode = (Map<String, Object>) config.computeIfAbsent("connectedMode", k -> new HashMap<String, Object>());
    connectedMode.put("project", Map.of("connectionId", connectionId, "projectKey", projectKey));
  }

  protected static void setRulesConfig(Map<String, Object> config, String... ruleConfigs) {
    if (ruleConfigs.length > 0) {
      config.put("rules", buildRulesMap(ruleConfigs));
    } else {
      config.remove("rules");
    }
  }

  private static Map<String, Object> buildRulesMap(String... ruleConfigs) {
    assertThat(ruleConfigs.length % 2).withFailMessage("ruleConfigs must contain 'rule:key', 'level' pairs").isZero();
    var rules = new Map.Entry[ruleConfigs.length / 2];
    for (var i = 0; i < ruleConfigs.length; i += 2) {
      rules[i / 2] = Map.entry(ruleConfigs[i], Map.of("level", ruleConfigs[i + 1]));
    }
    return Map.ofEntries(rules);
  }

  protected void didChange(String uri, String content) {
    var docId = new VersionedTextDocumentIdentifier(uri, 1);
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(docId, List.of(new TextDocumentContentChangeEvent(content))));
  }

  protected void didChangeNotebook(String uri, String content) {
    var docId = new VersionedNotebookDocumentIdentifier(1, uri);
    lsProxy.getNotebookDocumentService()
      .didChange(new DidChangeNotebookDocumentParams(docId, new NotebookDocumentChangeEvent()));
  }

  protected void didOpen(String uri, String languageId, String content) {
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, languageId, 1, content)));
    toBeClosed.add(uri);
  }

  protected void didClose(String uri) {
    lsProxy.getTextDocumentService()
      .didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
    toBeClosed.remove(uri);
  }

  protected void didOpenNotebook(String uri, String... cellContents) {
    var notebookDocument = new NotebookDocument();
    notebookDocument.setUri(uri);
    notebookDocument.setNotebookType(JUPYTER_NOTEBOOK_TYPE);
    notebookDocument.setVersion(1);

    var cellDocuments = new ArrayList<TextDocumentItem>();
    var cellIndex = new AtomicInteger();
    Stream.of(cellContents).forEach(cellContent -> {
      var newCellDocument = new TextDocumentItem();
      newCellDocument.setText(cellContent);
      newCellDocument.setUri(uri + "#" + cellIndex.incrementAndGet());
      newCellDocument.setLanguageId("python");
      newCellDocument.setVersion(1);
      cellDocuments.add(newCellDocument);
    });

    lsProxy.getNotebookDocumentService().didOpen(new DidOpenNotebookDocumentParams(notebookDocument, cellDocuments));
    notebooksToBeClosed.add(uri);
  }

  protected ThrowingExtractor<? super MessageParams, String, RuntimeException> withoutTimestamp() {
    return p -> p.getMessage().replaceAll("\\[(\\w*)\\s+-\\s[\\d:.]*\\]", "[$1]");
  }

  protected Function<? super Diagnostic, ?> code() {
    return d -> d.getCode().getLeft();
  }

  protected Function<? super Diagnostic, ?> endCharacter() {
    return d -> d.getRange().getEnd().getCharacter();
  }

  protected Function<? super Diagnostic, ?> endLine() {
    return d -> d.getRange().getEnd().getLine();
  }

  protected Function<? super Diagnostic, ?> startCharacter() {
    return d -> d.getRange().getStart().getCharacter();
  }

  protected Function<? super Diagnostic, ?> startLine() {
    return d -> d.getRange().getStart().getLine();
  }

  protected void awaitUntilAsserted(ThrowingRunnable assertion) {
    await().atMost(2, MINUTES).untilAsserted(assertion);
  }

  protected Map<String, Object> getFolderSettings(String folderUri) {
    return client.folderSettings.computeIfAbsent(folderUri, f -> new HashMap<>());
  }

  /**
   * Use this instead of {@link org.junit.jupiter.api.io.TempDir} that has issues on Windows
   */
  protected Path makeInstanceTempDir() throws Exception {
    var newTempDir = Files.createTempDirectory(null);
    instanceTempDirs.add(newTempDir);
    return newTempDir;
  }

  /**
   * Use this instead of {@link org.junit.jupiter.api.io.TempDir} that has issues on Windows
   */
  protected static Path makeStaticTempDir() throws IOException {
    var newTempDir = Files.createTempDirectory(null);
    staticTempDirs.add(newTempDir);
    return newTempDir;
  }

  static class NewCodeDefinitionDto {
    String newCodeDefinitionOrMessage;
    boolean isSupported;

    public NewCodeDefinitionDto(String newCodeDefinitionOrMessage, boolean isSupported) {
      this.newCodeDefinitionOrMessage = newCodeDefinitionOrMessage;
      this.isSupported = isSupported;
    }

    public String getNewCodeDefinitionOrMessage() {
      return newCodeDefinitionOrMessage;
    }

    public boolean isSupported() {
      return isSupported;
    }
  }
}
