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

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.shaded.org.apache.commons.io.output.ByteArrayOutputStream;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

public class LanguageServerMediumTests {

  private static final String SOME_FOLDER_URI = "some://uri";

  @TempDir
  Path temp;

  private static ServerSocket serverSocket;
  private static SonarLintExtendedLanguageServer lsProxy;
  private static FakeLanguageClient client;
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

    String js = new File("target/plugins/javascript.jar").getAbsoluteFile().toURI().toURL().toString();
    String php = new File("target/plugins/php.jar").getAbsoluteFile().toURI().toURL().toString();
    String py = new File("target/plugins/python.jar").getAbsoluteFile().toURI().toURL().toString();
    String ts = new File("target/plugins/typescript.jar").getAbsoluteFile().toURI().toURL().toString();
    String html = new File("target/plugins/html.jar").getAbsoluteFile().toURI().toURL().toString();

    Path fakeTypeScriptProjectPath = Paths.get("src/test/resources/fake-ts-project").toAbsolutePath();

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

    InitializeParams initializeParams = new InitializeParams();
    initializeParams.setTrace("messages");
    initializeParams.setInitializationOptions(ImmutableMap.builder()
      .put("typeScriptLocation", fakeTypeScriptProjectPath.resolve("node_modules").toString())
      .put("telemetryStorage", "not/exists")
      .put("productName", "SLCORE tests")
      .put("productVersion", "0.1")
      .build());
    lsProxy.initialize(initializeParams).get();
    lsProxy.initialized(new InitializedParams());
  }

  @AfterAll
  public static void stop() throws Exception {
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

  @Test
  public void analyzeSimpleJsFileOnOpen() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true);

    String uri = getUri("foo.js");
    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "javascript", "function foo() {\n  alert('toto');\n  var plouf = 0;\n}");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Unexpected alert.", DiagnosticSeverity.Information),
        tuple(2, 6, 2, 11, "javascript:UnusedVariable", "sonarlint", "Remove the declaration of the unused 'plouf' variable.",
          DiagnosticSeverity.Information));
  }

  @Test
  public void analyzeSimpleJsFileWithCustomRuleConfig() throws Exception {
    String uri = getUri("foo.js");
    String jsSource = "function foo()\n {\n  alert('toto');\n  var plouf = 0;\n}";

    // Default configuration should result in 2 issues: S1442 and UnusedVariable
    emulateConfigurationChangeOnClient("**/*Test.js", null);

    assertLogContainsInOrder(MessageType.Log,
      "Default settings updated: WorkspaceFolderSettings[analyzerProperties={},testFilePattern=**/*Test.js,serverId=<null>,projectKey=<null>]");

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "javascript", jsSource);
    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactlyInAnyOrder(
        tuple(2, 2, 2, 15, "javascript:S1442", "sonarlint", "Unexpected alert.", DiagnosticSeverity.Information),
        tuple(3, 6, 3, 11, "javascript:UnusedVariable", "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Information));

    client.clear();

    // Update rules configuration: disable UnusedVariable, enable Semicolon
    emulateConfigurationChangeOnClient("**/*Test.js", null,
      "javascript:UnusedVariable", "off",
      "javascript:S1105", "on");

    assertLogContainsInOrder(MessageType.Log,
      "Global settings updated: WorkspaceSettings[disableTelemetry=false,servers={},excludedRules=[javascript:UnusedVariable],includedRules=[javascript:S1105]]");

    // Trigger diagnostics refresh (called by client on config change)
    ExecuteCommandParams refreshDiagsCommand = new ExecuteCommandParams();
    refreshDiagsCommand.setCommand("SonarLint.RefreshDiagnostics");
    refreshDiagsCommand.setArguments(Collections.singletonList(new Gson().toJsonTree(new SonarLintLanguageServer.Document(uri, jsSource))));
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.getWorkspaceService().executeCommand(refreshDiagsCommand);
    client.diagnosticsLatch.await(1, TimeUnit.MINUTES);

    assertThat(client.getDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactlyInAnyOrder(
        tuple(2, 2, 2, 15, "javascript:S1442", "sonarlint", "Unexpected alert.", DiagnosticSeverity.Information),
        tuple(1, 1, 1, 2, "javascript:S1105", "sonarlint", "Move this open curly brace to the end of the previous line.", DiagnosticSeverity.Information)
      // Expected issue on javascript:UnusedVariable is suppressed by rule configuration
      );
  }

  @Test
  public void analyzeSimpleTsFileOnOpen() throws Exception {
    Path tsconfig = temp.resolve("tsconfig.json");
    Files.write(tsconfig, "{}".getBytes(StandardCharsets.UTF_8));
    String uri = getUri("foo.ts");

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "typescript", "function foo() {\n if(bar() && bar()) { return 42; }\n}");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 4, 1, 18, "typescript:S1764", "sonarlint", "Correct one of the identical sub-expressions on both sides of operator \"&&\"",
        DiagnosticSeverity.Warning));
  }

  @Test
  public void analyzeSimplePythonFileOnOpen() throws Exception {
    String uri = getUri("foo.py");

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "python", "def foo():\n  print 'toto'\n");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(
        tuple(1, 2, 1, 7, "python:PrintStatementUsage", "sonarlint", "Replace print statement by built-in function.", DiagnosticSeverity.Warning));
  }

  @Test
  public void analyzeSimplePhpFileOnOpen() throws Exception {
    String uri = getUri("foo.php");

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "php", "<?php\nfunction foo() {\n  echo(\"Hello\");\n}\n?>");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(2, 2, 2, 6, "php:S2041", "sonarlint", "Remove the parentheses from this \"echo\" call.", DiagnosticSeverity.Error));
  }

  @Test
  public void analyzeSimpleHtmlFileOnOpen() throws Exception {
    String uri = getUri("foo.html");

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "html", "<html><body></body></html>");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactlyInAnyOrder(
        tuple(0, 0, 0, 6, "Web:DoctypePresenceCheck", "sonarlint", "Insert a <!DOCTYPE> declaration to before this <html> tag.",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 6, "Web:S5254", "sonarlint", "Add \"lang\" and/or \"xml:lang\" attributes to this \"<html>\" element",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 26, "Web:PageWithoutTitleCheck", "sonarlint", "Add a <title> tag to this page.", DiagnosticSeverity.Warning));
  }

  @Test
  public void analyzeSimpleJspFileOnOpen() throws Exception {
    String uri = getUri("foo.html");

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "jsp", "<html><body></body></html>");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactlyInAnyOrder(
        tuple(0, 0, 0, 6, "Web:DoctypePresenceCheck", "sonarlint", "Insert a <!DOCTYPE> declaration to before this <html> tag.",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 6, "Web:S5254", "sonarlint", "Add \"lang\" and/or \"xml:lang\" attributes to this \"<html>\" element",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 26, "Web:PageWithoutTitleCheck", "sonarlint", "Add a <title> tag to this page.", DiagnosticSeverity.Warning));
  }

  @Test
  public void noIssueOnTestJSFiles() throws Exception {
    emulateConfigurationChangeOnClient("{**/*Test*}", null);
    assertLogContainsInOrder(MessageType.Log,
      "Default settings updated: WorkspaceFolderSettings[analyzerProperties={},testFilePattern={**/*Test*},serverId=<null>,projectKey=<null>]");

    String jsContent = "function foo() {\n  alert('toto');\n}";
    String fooTestUri = getUri("fooTest.js");
    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(fooTestUri, "javascript", jsContent);

    assertThat(diagnostics).isEmpty();
    client.clear();

    emulateConfigurationChangeOnClient("{**/*MyTest*}", null);
    assertLogContainsInOrder(MessageType.Log,
      "Default settings updated: WorkspaceFolderSettings[analyzerProperties={},testFilePattern={**/*MyTest*},serverId=<null>,projectKey=<null>]");

    diagnostics = didChangeAndWaitForDiagnostics(fooTestUri, jsContent);
    assertThat(diagnostics).hasSize(1);

    String fooMyTestUri = getUri("fooMyTest.js");
    List<Diagnostic> diagnosticsOtherFile = didOpenAndWaitForDiagnostics(fooMyTestUri, "javascript", jsContent);

    assertThat(diagnosticsOtherFile).isEmpty();
  }

  @Test
  public void analyzeSimpleJsFileOnChange() throws Exception {
    String uri = getUri("foo.js");

    List<Diagnostic> diagnostics = didChangeAndWaitForDiagnostics(uri, "function foo() {\n  alert('toto');\n}");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Unexpected alert.", DiagnosticSeverity.Information));
  }

  @Test
  public void analyzeSimpleJsFileOnSave() throws Exception {
    String uri = getUri("foo.js");

    List<Diagnostic> diagnostics = didSaveAndWaitForDiagnostics(uri, "function foo() {\n  alert('toto');\n}");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Unexpected alert.", DiagnosticSeverity.Information));
  }

  @Test
  public void diagnosticRelatedInfos() throws Exception {
    String uri = getUri("foo.js");

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "javascript", "function foo(a, b) {  print(a + \" \" + b);\n" +
      "}\n" +
      "foo(\"a\", \"b\", \"c\");\n");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(
        tuple(2, 0, 2, 18, "javascript:S930", "sonarlint", "This function expects 2 arguments, but 3 were provided.", DiagnosticSeverity.Error));

    assertThat(diagnostics.get(0).getRelatedInformation())
      .extracting("location.range.start.line", "location.range.start.character", "location.range.end.line", "location.range.end.character", "location.uri", "message")
      .containsExactly(tuple(0, 13, 0, 17, uri, "Formal parameters"));
  }

  @Test
  public void cleanDiagnosticsOnClose() throws Exception {
    String uri = getUri("foo.js");
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.getTextDocumentService()
      .didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
    client.diagnosticsLatch.await(1, TimeUnit.MINUTES);

    assertThat(client.getDiagnostics(uri)).isEmpty();
  }

  @Test
  public void optOutTelemetry() throws Exception {
    emulateConfigurationChangeOnClient(null, true);

    assertLogContainsInOrder(MessageType.Log,
      "Global settings updated: WorkspaceSettings[disableTelemetry=true,servers={},excludedRules=[],includedRules=[]]");
    // We are using the global system property to disable telemetry in tests, so this assertion do not pass
    // assertLogContainsInOrder(MessageType.Log, "Telemetry disabled");

    emulateConfigurationChangeOnClient(null, false);

    assertLogContainsInOrder(MessageType.Log,
      "Global settings updated: WorkspaceSettings[disableTelemetry=false,servers={},excludedRules=[],includedRules=[]]");
    // We are using the global system property to disable telemetry in tests, so this assertion do not pass
    // assertLogContainsInOrder(MessageType.Log, "Telemetry enabled");
  }

  @Test
  public void testUnknownCommand() throws Exception {
    try {
      lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("unknown", Collections.emptyList())).get();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ResponseErrorException.class);
      ResponseError responseError = ((ResponseErrorException) e.getCause()).getResponseError();
      assertThat(responseError.getCode()).isEqualTo(ResponseErrorCode.InvalidParams.getValue());
      assertThat(responseError.getMessage()).isEqualTo("Unsupported command: unknown");
    }
  }

  @Test
  public void testCodeAction_with_unknown_diagnostic_rule() throws Exception {
    Range range = new Range(new Position(1, 0), new Position(1, 10));
    Diagnostic d = new Diagnostic(range, "An issue");
    d.setSource("sonarlint");
    d.setCode("unknown:rule");
    CodeActionParams codeActionParams = new CodeActionParams(new TextDocumentIdentifier("file://foo.js"), range, new CodeActionContext(Arrays.asList(d)));
    try {
      lsProxy.getTextDocumentService().codeAction(codeActionParams).get();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ResponseErrorException.class);
      assertThat(((ResponseErrorException) e.getCause()).getResponseError().getMessage())
        .isEqualTo("Unknown rule with key: unknown:rule");
    }
  }

  @Test
  public void testCodeAction_with_diagnostic_rule() throws Exception {
    Range range = new Range(new Position(1, 0), new Position(1, 10));
    Diagnostic d = new Diagnostic(range, "An issue");
    d.setSource("sonarlint");
    d.setCode("javascript:S930");
    CodeActionParams codeActionParams = new CodeActionParams(new TextDocumentIdentifier("file://foo.js"), range, new CodeActionContext(Arrays.asList(d)));
    List<Either<Command, CodeAction>> list = lsProxy.getTextDocumentService().codeAction(codeActionParams).get();
    assertThat(list).hasSize(2);
    Command openRuleDesc = list.get(0).getLeft();
    assertThat(openRuleDesc.getCommand()).isEqualTo("SonarLint.OpenRuleDesc");
    assertThat(openRuleDesc.getArguments()).hasSize(5);
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(0)).getAsString()).isEqualTo("javascript:S930");
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(1)).getAsString()).isEqualTo("Function calls should not pass extra arguments");
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(2)).getAsString()).contains("<h2>Noncompliant Code Example");
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(3)).getAsString()).isEqualTo("BUG");
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(4)).getAsString()).isEqualTo("CRITICAL");
    Command disableRule = list.get(1).getLeft();
    assertThat(disableRule.getCommand()).isEqualTo("SonarLint.DeactivateRule");
    assertThat(disableRule.getArguments()).hasSize(1);
    assertThat(((JsonPrimitive) disableRule.getArguments().get(0)).getAsString()).isEqualTo("javascript:S930");
  }

  @Test
  public void testListAllRules() throws Exception {
    Map<String, List<RuleDescription>> result = lsProxy.listAllRules().join();
    assertThat(result).containsOnlyKeys("HTML", "JavaScript", "TypeScript", "PHP", "Python");
  }

  @Test
  public void fetchWorkspaceFolderConfigurationWhenAdded() {
    client.folderSettings.put(SOME_FOLDER_URI, buildSonarLintSettingsSection("some pattern", null));
    lsProxy.getWorkspaceService()
      .didChangeWorkspaceFolders(
        new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(Collections.singletonList(new WorkspaceFolder(SOME_FOLDER_URI, "Added")), Collections.emptyList())));

    assertLogContainsInOrder(MessageType.Log,
      "Workspace workspaceFolderPath 'WorkspaceFolder[uri=some://uri,name=Added]' configuration updated: WorkspaceFolderSettings[analyzerProperties={},testFilePattern=some pattern,serverId=<null>,projectKey=<null>]");
  }

  @Test
  public void logErrorWhenClientFailedToReturnConfiguration() {
    // No workspaceFolderPath settings registered in the client mock, so it should fail when server will request workspaceFolderPath
    // configuration
    lsProxy.getWorkspaceService()
      .didChangeWorkspaceFolders(
        new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(Collections.singletonList(new WorkspaceFolder(SOME_FOLDER_URI, "Added")), Collections.emptyList())));

    assertLogContainsInOrder(MessageType.Error, "Unable to fetch configuration");
    assertLogMatchesInOrder(MessageType.Error, "(?s).*Internal error.*");
  }

  private static void assertLogContainsInOrder(MessageType type, String msg) {
    assertLogMatchesInOrder(type, Pattern.quote(msg));
  }

  private static void assertLogMatchesInOrder(MessageType type, String msgPattern) {
    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(client.logs).isNotEmpty());
    MessageParams params = client.logs.remove();
    assertThat(params.getMessage()).matches(msgPattern);
    assertThat(params.getType()).isEqualTo(type);
  }

  private String getUri(String filename) throws IOException {
    Path file = temp.resolve(filename);
    Files.createFile(file);
    return file.toUri().toString();
  }

  private static class FakeLanguageClient implements SonarLintExtendedLanguageClient {

    Map<String, List<Diagnostic>> diagnostics = new ConcurrentHashMap<>();
    List<RuleDescription> ruleDescs = new ArrayList<>();
    Queue<MessageParams> logs = new ConcurrentLinkedQueue<>();
    Map<String, Object> globalSettings = null;
    Map<String, Map<String, Object>> folderSettings = new HashMap<>();
    CountDownLatch settingsLatch = new CountDownLatch(0);
    CountDownLatch diagnosticsLatch = new CountDownLatch(0);

    void clear() {
      diagnostics.clear();
      ruleDescs.clear();
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
    public void openRuleDescription(RuleDescription notification) {
      ruleDescs.add(notification);
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

  private void emulateConfigurationChangeOnClient(@Nullable String testFilePattern, @Nullable Boolean disableTelemetry, String... ruleConfigs) {
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

  private Map<String, Object> buildSonarLintSettingsSection(@Nullable String testFilePattern, @Nullable Boolean disableTelemetry, String... ruleConfigs) {
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

  private List<Diagnostic> didChangeAndWaitForDiagnostics(String uri, String content) throws InterruptedException {
    VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(uri, 1);
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(docId, singletonList(new TextDocumentContentChangeEvent("function foo() {\n  alert('toto');\n}"))));
    if (client.diagnosticsLatch.await(1, TimeUnit.MINUTES)) {
      return client.getDiagnostics(uri);
    } else {
      throw new AssertionError("No diagnostics received after 1 minute");
    }
  }

  private List<Diagnostic> didOpenAndWaitForDiagnostics(String uri, String languageId, String content) throws InterruptedException {
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, languageId, 1, content)));
    if (client.diagnosticsLatch.await(1, TimeUnit.MINUTES)) {
      return client.getDiagnostics(uri);
    } else {
      throw new AssertionError("No diagnostics received after 1 minute");
    }
  }

  private List<Diagnostic> didSaveAndWaitForDiagnostics(String uri, String content) throws InterruptedException {
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
