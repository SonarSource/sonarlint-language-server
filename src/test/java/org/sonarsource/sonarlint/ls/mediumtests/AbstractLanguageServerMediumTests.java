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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
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
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.ls.ServerMain;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.SonarLintTelemetry;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

abstract class AbstractLanguageServerMediumTests {

  protected static final String SOME_FOLDER_URI = "some://uri";

  @TempDir
  Path temp;

  private static ServerSocket serverSocket;
  protected static SonarLintExtendedLanguageServer lsProxy;
  protected static FakeLanguageClient client;
  private static ByteArrayOutputStream serverStdOut;
  private static ByteArrayOutputStream serverStdErr;

  @BeforeAll
  public final static void startServer() throws Exception {
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

    String js = new File("target/plugins/javascript.jar").getAbsoluteFile().toURI().toURL().toString();
    String php = new File("target/plugins/php.jar").getAbsoluteFile().toURI().toURL().toString();
    String py = new File("target/plugins/python.jar").getAbsoluteFile().toURI().toURL().toString();
    String ts = new File("target/plugins/typescript.jar").getAbsoluteFile().toURI().toURL().toString();
    String html = new File("target/plugins/html.jar").getAbsoluteFile().toURI().toURL().toString();

    serverStdOut = new ByteArrayOutputStream();
    serverStdErr = new ByteArrayOutputStream();
    try {
      new ServerMain(new PrintStream(serverStdOut), new PrintStream(serverStdErr)).startLanguageServer("" + port, js, php, py, ts, html);
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

  protected static void initialize(Map<String, String> initializeOptions) throws InterruptedException, ExecutionException {
    InitializeParams initializeParams = new InitializeParams();
    initializeParams.setTrace("messages");
    initializeParams.setInitializationOptions(initializeOptions);
    lsProxy.initialize(initializeParams).get();
    lsProxy.initialized(new InitializedParams());
  }

  @AfterAll
  public final static void stop() throws Exception {
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
  public final void cleanup() throws InterruptedException {
    // Reset settings on LS side
    client.clear();
    lsProxy.getWorkspaceService()
      .didChangeWorkspaceFolders(
        new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(Collections.emptyList(), singletonList(new WorkspaceFolder(SOME_FOLDER_URI, "Added")))));
    // Remove a unexisting workspaceFolderPath will log
    lsProxy.getWorkspaceService()
      .didChangeWorkspaceFolders(
        new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(Collections.emptyList(), singletonList(new WorkspaceFolder("another://uri", "Unknown")))));
    await().atMost(5, SECONDS)
      .untilAsserted(() -> assertThat(client.logs).extracting(MessageParams::getMessage).contains("Unregistered workspace folder was missing: another://uri"));

    // Switch telemetry on/off to ensure at least one log will appear
    emulateConfigurationChangeOnClient(null, true);
    emulateConfigurationChangeOnClient(null, false);
    await().atMost(5, SECONDS)
      .untilAsserted(() -> assertThat(client.logs).extracting(MessageParams::getMessage)
        .contains("Global settings updated: WorkspaceSettings[disableTelemetry=false,servers={},excludedRules=[],includedRules=[]]"));

    client.logs.clear();
  }

  protected static void assertLogContainsInOrder(MessageType type, String msg) {
    assertLogMatchesInOrder(type, Pattern.quote(msg));
  }

  protected static void assertLogMatchesInOrder(MessageType type, String msgPattern) {
    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(client.logs).isNotEmpty());
    MessageParams params = client.logs.remove();
    assertThat(params.getMessage()).matches(msgPattern);
    assertThat(params.getType()).isEqualTo(type);
  }

  protected String getUri(String filename) throws IOException {
    Path file = temp.resolve(filename);
    Files.createFile(file);
    return file.toUri().toString();
  }

  protected static class FakeLanguageClient implements SonarLintExtendedLanguageClient {

    Map<String, List<Diagnostic>> diagnostics = new ConcurrentHashMap<>();
    Queue<MessageParams> logs = new ConcurrentLinkedQueue<>();
    Map<String, Object> globalSettings = null;
    Map<String, Map<String, Object>> folderSettings = new HashMap<>();
    CountDownLatch settingsLatch = new CountDownLatch(0);
    CountDownLatch diagnosticsLatch = new CountDownLatch(0);

    void clear() {
      diagnostics.clear();
      logs.clear();
      globalSettings = null;
      folderSettings.clear();
      settingsLatch = new CountDownLatch(0);
      diagnosticsLatch = new CountDownLatch(0);
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
      return null;
    }

    @Override
    public void logMessage(MessageParams message) {
      // SSLRSQBR-72 This log is produced by analyzers ProgressReport, and keeps coming long after the analysis has completed. Just ignore
      // it
      if (!message.getMessage().equals("1/1 source files have been analyzed")) {
        logs.add(message);
      }
      System.out.println(message.getMessage());
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
      return CompletableFutures.computeAsync(cancelToken -> {
        assertThat(configurationParams.getItems()).extracting(ConfigurationItem::getSection).containsExactly("sonarlint");
        List<Object> result = new ArrayList<>(configurationParams.getItems().size());
        for (ConfigurationItem item : configurationParams.getItems()) {
          if (item.getScopeUri() == null) {
            result.add(globalSettings);
          } else {
            result
              .add(Optional.ofNullable(folderSettings.get(item.getScopeUri()))
                .orElseThrow(() -> new IllegalStateException("No settings mocked for workspaceFolderPath " + item.getScopeUri())));
          }
        }
        settingsLatch.countDown();
        return result;
      });
    }
  }

  protected void emulateConfigurationChangeOnClient(@Nullable String testFilePattern, @Nullable Boolean disableTelemetry, String... ruleConfigs) {
    client.globalSettings = buildSonarLintSettingsSection(testFilePattern, disableTelemetry, ruleConfigs);
    client.settingsLatch = new CountDownLatch(1);
    lsProxy.getWorkspaceService().didChangeConfiguration(changedConfiguration(testFilePattern, disableTelemetry, ruleConfigs));
    try {
      client.settingsLatch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  private DidChangeConfigurationParams changedConfiguration(@Nullable String testFilePattern, @Nullable Boolean disableTelemetry, String... ruleConfigs) {
    Map<String, Object> values = buildSonarLintSettingsSection(testFilePattern, disableTelemetry, ruleConfigs);
    return new DidChangeConfigurationParams(ImmutableMap.of("sonarlint", values));
  }

  protected Map<String, Object> buildSonarLintSettingsSection(@Nullable String testFilePattern, @Nullable Boolean disableTelemetry, String... ruleConfigs) {
    Map<String, Object> values = new HashMap<>();
    if (testFilePattern != null) {
      values.put("testFilePattern", testFilePattern);
    }
    if (disableTelemetry != null) {
      values.put("disableTelemetry", disableTelemetry);
    }
    if (ruleConfigs.length > 0) {
      values.put("rules", buildRulesMap(ruleConfigs));
    }
    return values;
  }

  private Map<String, Object> buildRulesMap(String... ruleConfigs) {
    assertThat(ruleConfigs.length % 2).withFailMessage("ruleConfigs must contain 'rule:key', 'level' pairs").isEqualTo(0);
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
    if (client.diagnosticsLatch.await(1, TimeUnit.MINUTES)) {
      return client.getDiagnostics(uri);
    } else {
      throw new AssertionError("No diagnostics received after 1 minute");
    }
  }

  protected List<Diagnostic> didOpenAndWaitForDiagnostics(String uri, String languageId, String content) throws InterruptedException {
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, languageId, 1, content)));
    if (client.diagnosticsLatch.await(1, TimeUnit.MINUTES)) {
      return client.getDiagnostics(uri);
    } else {
      throw new AssertionError("No diagnostics received after 1 minute");
    }
  }

  protected List<Diagnostic> didSaveAndWaitForDiagnostics(String uri, String content) throws InterruptedException {
    VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(uri, 1);
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.getTextDocumentService()
      .didSave(new DidSaveTextDocumentParams(docId, content));
    if (client.diagnosticsLatch.await(1, TimeUnit.MINUTES)) {
      return client.getDiagnostics(uri);
    } else {
      throw new AssertionError("No diagnostics received after 1 minute");
    }
  }

}
