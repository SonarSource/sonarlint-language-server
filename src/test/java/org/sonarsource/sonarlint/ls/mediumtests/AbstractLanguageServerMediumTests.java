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
package org.sonarsource.sonarlint.ls.mediumtests;

import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.eclipse.lsp4j.InitializeResult;
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
import org.sonarsource.sonarlint.ls.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
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
  private static ByteArrayOutputStream serverStdOut;
  private static ByteArrayOutputStream serverStdErr;

  @BeforeAll
  public static void startServer() throws Exception {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    serverSocket = new ServerSocket(0);
    int port = serverSocket.getLocalPort();

    client = new FakeLanguageClient();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Callable<SonarLintExtendedLanguageServer> callable = () -> {
      Socket socket = serverSocket.accept();
      Launcher<SonarLintExtendedLanguageServer> clientSideLauncher = new LSPLauncher.Builder<SonarLintExtendedLanguageServer>()
        .setLocalService(client)
        .setRemoteInterface(SonarLintExtendedLanguageServer.class)
        .setInput(socket.getInputStream())
        .setOutput(socket.getOutputStream())
        .create();
      clientSideLauncher.startListening();
      return clientSideLauncher.getRemoteProxy();
    };
    Future<SonarLintExtendedLanguageServer> future = executor.submit(callable);
    executor.shutdown();

    String java = new File("target/plugins/java.jar").getAbsoluteFile().toURI().toURL().toString();
    String js = new File("target/plugins/javascript.jar").getAbsoluteFile().toURI().toURL().toString();
    String php = new File("target/plugins/php.jar").getAbsoluteFile().toURI().toURL().toString();
    String py = new File("target/plugins/python.jar").getAbsoluteFile().toURI().toURL().toString();
    String html = new File("target/plugins/html.jar").getAbsoluteFile().toURI().toURL().toString();

    serverStdOut = new ByteArrayOutputStream();
    serverStdErr = new ByteArrayOutputStream();
    try {
      new ServerMain(new PrintStream(serverStdOut), new PrintStream(serverStdErr)).startLanguageServer("" + port, "-analyzers", java, js, php, py, html);
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

  protected static void initialize(Map<String, Object> initializeOptions, WorkspaceFolder... initFolders) throws InterruptedException, ExecutionException {
    InitializeParams initializeParams = new InitializeParams();
    initializeParams.setTrace("messages");
    initializeParams.setInitializationOptions(initializeOptions);
    initializeParams.setWorkspaceFolders(asList(initFolders));
    initializeParams.setClientInfo(new ClientInfo("SonarLint LS Medium tests", "1.0"));
    initializeParams.setCapabilities(new ClientCapabilities());
    initializeParams.getCapabilities().setWindow(new WindowClientCapabilities());
    InitializeResult initializeResult = lsProxy.initialize(initializeParams).get();
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
  public void cleanup() throws InterruptedException {
    // Reset state on LS side
    client.clear();

    emulateConfigurationChangeOnClient(null, false, false, true);

    // Wait for logs to stop being produced
    await().during(1, SECONDS).atMost(5, SECONDS).until(() -> {
      int count = client.logs.size();
      client.logs.clear();
      return count;
    }, equalTo(0));
  }

  @AfterEach
  public final void closeFiles() throws InterruptedException {
    // Close all opened files
    for (String uri : toBeClosed) {
      client.diagnosticsLatch = new CountDownLatch(1);
      lsProxy.getTextDocumentService().didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
      if (!client.diagnosticsLatch.await(1, TimeUnit.MINUTES)) {
        throw new AssertionError("No empty diagnostics received after 1 minute");
      }
    }
    toBeClosed.clear();
  }

  protected static void assertLogContains(String msg) {
    assertLogContainsPattern("\\[.*\\] " + Pattern.quote(msg));
  }

  protected static void assertLogContainsPattern(String msgPattern) {
    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(client.logs).anyMatch(p -> p.getMessage().matches(msgPattern)));
  }

  protected String getUri(String filename) throws IOException {
    Path file = temp.resolve(filename);
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
    CountDownLatch settingsLatch = new CountDownLatch(0);
    CountDownLatch diagnosticsLatch = new CountDownLatch(0);
    CountDownLatch showRuleDescriptionLatch = new CountDownLatch(0);
    ShowRuleDescriptionParams ruleDesc;
    boolean isIgnoredByScm = false;

    void clear() {
      diagnostics.clear();
      logs.clear();
      globalSettings = null;
      folderSettings.clear();
      settingsLatch = new CountDownLatch(0);
      diagnosticsLatch = new CountDownLatch(0);
      showRuleDescriptionLatch = new CountDownLatch(0);
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
      diagnosticsLatch.countDown();
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
          for (ConfigurationItem item : configurationParams.getItems()) {
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
    Map<String, Object> values = buildSonarLintSettingsSection(testFilePattern, disableTelemetry, showAnalyzerLogs, showVerboseLogs, ruleConfigs);
    return new DidChangeConfigurationParams(ImmutableMap.of("sonarlint", values));
  }

  protected static Map<String, Object> buildSonarLintSettingsSection(@Nullable String testFilePattern, @Nullable Boolean disableTelemetry, @Nullable Boolean showAnalyzerLogs,
    @Nullable Boolean showVerboseLogs, String... ruleConfigs) {
    Map<String, Object> values = new HashMap<>();
    if (testFilePattern != null) {
      values.put("testFilePattern", testFilePattern);
    }
    if (disableTelemetry != null) {
      values.put("disableTelemetry", disableTelemetry);
    }
    if (showAnalyzerLogs != null || showVerboseLogs != null) {
      Map<String, Object> output = new HashMap<>();
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
    ImmutableMap.Builder<String, Object> rules = ImmutableMap.builder();
    for (int i = 0; i < ruleConfigs.length; i += 2) {
      rules.put(ruleConfigs[i], ImmutableMap.of("level", ruleConfigs[i + 1]));
    }
    return rules.build();
  }

  protected List<Diagnostic> didChangeAndWaitForDiagnostics(String uri, String content) throws InterruptedException {
    VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(uri, 1);
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(docId, singletonList(new TextDocumentContentChangeEvent(content))));
    toBeClosed.add(uri);
    return awaitDiagnosticsForOneMinute(uri);
  }

  protected List<Diagnostic> didOpenAndWaitForDiagnostics(String uri, String languageId, String content) throws InterruptedException {
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, languageId, 1, content)));
    toBeClosed.add(uri);
    return awaitDiagnosticsForOneMinute(uri);
  }

  protected List<Diagnostic> didSaveAndWaitForDiagnostics(String uri, String content) throws InterruptedException {
    VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(uri, 1);
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.getTextDocumentService()
      .didSave(new DidSaveTextDocumentParams(docId, content));
    return awaitDiagnosticsForOneMinute(uri);
  }

  private List<Diagnostic> awaitDiagnosticsForOneMinute(String uri) throws InterruptedException {
    if (client.diagnosticsLatch.await(1, TimeUnit.MINUTES)) {
      return client.getDiagnostics(uri);
    } else {
      throw new AssertionError("No diagnostics received after 1 minute");
    }
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
