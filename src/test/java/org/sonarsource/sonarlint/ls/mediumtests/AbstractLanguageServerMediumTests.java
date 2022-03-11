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
package org.sonarsource.sonarlint.ls.mediumtests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.assertj.core.api.iterable.ThrowingExtractor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WindowClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.ls.ServerMain;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class AbstractLanguageServerMediumTests {

  @TempDir
  Path temp;

  protected Set<String> toBeClosed = new HashSet<>();

  private static ServerSocket serverSocket;
  protected static SonarLintExtendedLanguageServer lsProxy;
  protected static FakeLanguageClient client;

  @BeforeAll
  static void startServer() throws Exception {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    serverSocket = new ServerSocket(0);
    var port = serverSocket.getLocalPort();

    client = new FakeLanguageClient();

    var executor = Executors.newSingleThreadExecutor();
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
    executor.shutdown();

    var java = fullPathToJar("java");
    var js = fullPathToJar("javascript");
    var php = fullPathToJar("php");
    var py = fullPathToJar("python");
    var html = fullPathToJar("html");
    var xml = fullPathToJar("xml");

    var serverStdOut = new ByteArrayOutputStream();
    var serverStdErr = new ByteArrayOutputStream();
    try {
      new ServerMain(new PrintStream(serverStdOut), new PrintStream(serverStdErr)).startLanguageServer("" + port, "-analyzers", java, js, php, py, html, xml);
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

  private static String fullPathToJar(String jarName) {
    return Paths.get("target/plugins").resolve(jarName + ".jar").toAbsolutePath().toString();
  }

  protected static void initialize(Map<String, Object> initializeOptions, WorkspaceFolder... initFolders) throws InterruptedException, ExecutionException {
    var initializeParams = new InitializeParams();
    initializeParams.setTrace("messages");
    initializeParams.setInitializationOptions(initializeOptions);
    initializeParams.setWorkspaceFolders(List.of(initFolders));
    initializeParams.setClientInfo(new ClientInfo("SonarLint LS Medium tests", "1.0"));
    initializeParams.setCapabilities(new ClientCapabilities());
    initializeParams.getCapabilities().setWindow(new WindowClientCapabilities());
    var initializeResult = lsProxy.initialize(initializeParams).get();
    assertThat(initializeResult.getServerInfo().getName()).isEqualTo("SonarLint Language Server");
    assertThat(initializeResult.getServerInfo().getVersion()).isNotBlank();
    lsProxy.initialized(new InitializedParams());
  }

  @AfterAll
  public static void stopServer() throws Exception {
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
  void cleanup() throws InterruptedException {
    // Reset state on LS side
    client.clear();
    toBeClosed.clear();

    setUpFolderSettings(client.folderSettings);

    emulateConfigurationChangeOnClient(null, false, false, true);

    // Wait for logs to stop being produced
    await().during(1, SECONDS).atMost(5, SECONDS).until(() -> {
      int count = client.logs.size();
      client.logs.clear();
      return count;
    }, equalTo(0));
  }

  protected void setUpFolderSettings(Map<String, Map<String, Object>> folderSettings) {
    // do nothing by default
  }

  @AfterEach
  final void closeFiles() throws InterruptedException {
    // Close all opened files
    for (var uri : toBeClosed) {
      client.doAndWaitForDiagnostics(uri, () -> {
        lsProxy.getTextDocumentService().didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
      });
    }
  }

  protected static void assertLogContains(String msg) {
    assertLogContainsPattern("\\[.*\\] " + Pattern.quote(msg));
  }

  protected static void assertLogContainsPattern(String msgPattern) {
    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(client.logs).anyMatch(p -> p.getMessage().matches(msgPattern)));
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
    Queue<MessageParams> logs = new ConcurrentLinkedQueue<>();
    Map<String, Object> globalSettings = null;
    Map<String, Map<String, Object>> folderSettings = new HashMap<>();
    Map<String, GetJavaConfigResponse> javaConfigs = new HashMap<>();
    Map<String, String> branchNameByFolder = new HashMap<>();
    Map<String, String> referenceBranchNameByFolder = new HashMap<>();
    CountDownLatch settingsLatch = new CountDownLatch(0);
    private final Map<String, CountDownLatch> diagnosticsLatches = new HashMap<>();
    CountDownLatch showRuleDescriptionLatch = new CountDownLatch(0);
    ShowRuleDescriptionParams ruleDesc;
    boolean isIgnoredByScm = false;

    void clear() {
      diagnostics.clear();
      logs.clear();
      globalSettings = null;
      folderSettings.clear();
      settingsLatch = new CountDownLatch(0);
      diagnosticsLatches.clear();
      showRuleDescriptionLatch = new CountDownLatch(0);
    }

    public void doAndWaitForDiagnostics(String uri, Runnable action) {
      if (diagnosticsLatches.containsKey(uri)) {
        fail("There is already something waiting for diagnostics of uri: " + uri);
      }
      var latch = new CountDownLatch(1);
      diagnosticsLatches.put(uri, latch);
      action.run();
      try {
        if (!latch.await(1, TimeUnit.MINUTES)) {
          fail("No diagnostics received after 1 minute");
        }
      } catch (InterruptedException e) {
        throw new IllegalStateException("interrupted", e);
      }
    }

    @Override
    public void telemetryEvent(Object object) {
    }

    List<Diagnostic> getDiagnostics(String uri) {
      return diagnostics.get(uri);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
      this.diagnostics.put(diagnostics.getUri(), diagnostics.getDiagnostics());
      var latch = diagnosticsLatches.remove(diagnostics.getUri());
      if (latch != null) {
        latch.countDown();
      } else {
        fail("Unexpected diagnostics: " + diagnostics);
      }
    }

    @Override
    public void showMessage(MessageParams messageParams) {

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
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
      return CompletableFutures.computeAsync(cancelToken -> {
        List<Object> result;
        try {
          assertThat(configurationParams.getItems()).extracting(ConfigurationItem::getSection).containsExactly("sonarlint");
          result = new ArrayList<>(configurationParams.getItems().size());
          for (var item : configurationParams.getItems()) {
            if (item.getScopeUri() == null) {
              result.add(globalSettings);
            } else {
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
    public CompletableFuture<Void> showSonarLintOutput() {
      return CompletableFutures.computeAsync(null);
    }

    @Override
    public CompletableFuture<Void> openJavaHomeSettings() {
      return CompletableFutures.computeAsync(null);
    }

    @Override
    public CompletableFuture<Void> openPathToNodeSettings() {
      return CompletableFutures.computeAsync(null);
    }

    @Override
    public CompletableFuture<Void> showRuleDescription(ShowRuleDescriptionParams params) {
      return CompletableFutures.computeAsync(cancelToken -> {
        this.ruleDesc = params;
        showRuleDescriptionLatch.countDown();
        return null;
      });
    }

    @Override
    public CompletableFuture<Void> showHotspot(ServerHotspot h) {
      return CompletableFutures.computeAsync(null);
    }

    @Override
    public CompletableFuture<Void> showTaintVulnerability(ShowAllLocationsCommand.Param params) {
      return CompletableFutures.computeAsync(null);
    }

    @Override
    public CompletableFuture<Boolean> isIgnoredByScm(String fileUri) {
      return CompletableFutures.computeAsync(cancelToken -> isIgnoredByScm);
    }

    @Override
    public CompletableFuture<Void> showFirstSecretDetectionNotification() {
      return CompletableFutures.computeAsync(null);
    }

    @Override
    public CompletableFuture<GetJavaConfigResponse> getJavaConfig(String fileUri) {
      return CompletableFutures.computeAsync(cancelToken -> {
        return javaConfigs.get(fileUri);
      });
    }

    @Override
    public CompletableFuture<Void> browseTo(String link) {
      return CompletableFutures.computeAsync(null);
    }

    @Override
    public CompletableFuture<Void> openConnectionSettings(boolean isSonarCloud) {
      return CompletableFutures.computeAsync(null);
    }

    @Override
    public CompletableFuture<String> getBranchNameForFolder(String folderUri) {
      return CompletableFutures.computeAsync(cancelToken -> branchNameByFolder.get(folderUri));
    }

    @Override
    public CompletableFuture<Void> setReferenceBranchNameForFolder(ReferenceBranchForFolder newReferenceBranch) {
      referenceBranchNameByFolder.put(newReferenceBranch.getFolderUri(), newReferenceBranch.getBranchName());
      return CompletableFuture.completedFuture(null);
    }
  }

  protected void emulateConfigurationChangeOnClient(@Nullable String testFilePattern, @Nullable Boolean disableTelemetry, String... ruleConfigs) {
    emulateConfigurationChangeOnClient(testFilePattern, disableTelemetry, null, null, ruleConfigs);
  }

  protected static void emulateConfigurationChangeOnClient(@Nullable String testFilePattern, @Nullable Boolean disableTelemetry, @Nullable Boolean showAnalyzerLogs,
    @Nullable Boolean showVerboseLogs, String... ruleConfigs) {
    client.globalSettings = buildSonarLintSettingsSection(testFilePattern, disableTelemetry, showAnalyzerLogs, showVerboseLogs, ruleConfigs);
    client.settingsLatch = new CountDownLatch(1);
    lsProxy.getWorkspaceService().didChangeConfiguration(changedConfiguration(testFilePattern, disableTelemetry, showAnalyzerLogs, showVerboseLogs, ruleConfigs));
    awaitLatch(client.settingsLatch);
  }

  private static DidChangeConfigurationParams changedConfiguration(@Nullable String testFilePattern, @Nullable Boolean disableTelemetry, @Nullable Boolean showAnalyzerLogs,
    @Nullable Boolean showVerboseLogs, String... ruleConfigs) {
    var values = buildSonarLintSettingsSection(testFilePattern, disableTelemetry, showAnalyzerLogs, showVerboseLogs, ruleConfigs);
    return new DidChangeConfigurationParams(Map.of("sonarlint", values));
  }

  protected static Map<String, Object> buildSonarLintSettingsSection(@Nullable String testFilePattern, @Nullable Boolean disableTelemetry, @Nullable Boolean showAnalyzerLogs,
    @Nullable Boolean showVerboseLogs, String... ruleConfigs) {
    var values = new HashMap<String, Object>();
    if (testFilePattern != null) {
      values.put("testFilePattern", testFilePattern);
    }
    if (disableTelemetry != null) {
      values.put("disableTelemetry", disableTelemetry);
    }
    if (showAnalyzerLogs != null || showVerboseLogs != null) {
      var output = new HashMap<String, Object>();
      if (showAnalyzerLogs != null) {
        output.put("showAnalyzerLogs", showAnalyzerLogs);
      }
      if (showVerboseLogs != null) {
        output.put("showVerboseLogs", showVerboseLogs);
      }
      values.put("output", output);
    }
    if (ruleConfigs.length > 0) {
      values.put("rules", buildRulesMap(ruleConfigs));
    }
    return values;
  }

  private static Map<String, Object> buildRulesMap(String... ruleConfigs) {
    assertThat(ruleConfigs.length % 2).withFailMessage("ruleConfigs must contain 'rule:key', 'level' pairs").isZero();
    var rules = new Map.Entry[ruleConfigs.length / 2];
    for (var i = 0; i < ruleConfigs.length; i += 2) {
      rules[i / 2] = Map.entry(ruleConfigs[i], Map.of("level", ruleConfigs[i + 1]));
    }
    return Map.ofEntries(rules);
  }

  protected List<Diagnostic> didChangeAndWaitForDiagnostics(String uri, String content) throws InterruptedException {
    var docId = new VersionedTextDocumentIdentifier(uri, 1);
    client.doAndWaitForDiagnostics(uri, () -> {
      lsProxy.getTextDocumentService()
        .didChange(new DidChangeTextDocumentParams(docId, List.of(new TextDocumentContentChangeEvent(content))));
      toBeClosed.add(uri);
    });
    return client.getDiagnostics(uri);
  }

  protected List<Diagnostic> didOpenAndWaitForDiagnostics(String uri, String languageId, String content) throws InterruptedException {
    client.doAndWaitForDiagnostics(uri, () -> {
      lsProxy.getTextDocumentService()
        .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, languageId, 1, content)));
      toBeClosed.add(uri);
    });
    return client.getDiagnostics(uri);
  }

  protected List<Diagnostic> didSaveAndWaitForDiagnostics(String uri, String content) throws InterruptedException {
    var docId = new VersionedTextDocumentIdentifier(uri, 1);
    client.doAndWaitForDiagnostics(uri, () -> {
      lsProxy.getTextDocumentService()
        .didSave(new DidSaveTextDocumentParams(docId, content));
      toBeClosed.add(uri);
    });
    return client.getDiagnostics(uri);
  }

  protected ThrowingExtractor<? super MessageParams, String, RuntimeException> withoutTimestamp() {
    return p -> p.getMessage().replaceAll("\\[(\\w*)\\s*-(.*)\\]", "[$1]");
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
}
