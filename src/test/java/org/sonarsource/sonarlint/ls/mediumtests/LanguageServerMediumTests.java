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
import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.RuleDescription;
import org.sonarsource.sonarlint.ls.SonarLintLanguageServer;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.fail;

class LanguageServerMediumTests extends AbstractLanguageServerMediumTests {

  @BeforeAll
  public static void initialize() throws Exception {
    Path fakeTypeScriptProjectPath = Paths.get("src/test/resources/fake-ts-project").toAbsolutePath();

    initialize(ImmutableMap.<String, String>builder()
      .put("typeScriptLocation", fakeTypeScriptProjectPath.resolve("node_modules").toString())
      .put("telemetryStorage", "not/exists")
      .put("productName", "SLCORE tests")
      .put("productVersion", "0.1")
      .build());
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
    refreshDiagsCommand.setArguments(Collections.singletonList(new Gson().toJsonTree(new SonarLintLanguageServer.Document(uri))));
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
  public void delayAnalysisOnChange() throws Exception {
    String uri = getUri("foo.js");

    VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(uri, 1);
    // Emulate two quick changes, should only trigger one analysis
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(docId, singletonList(new TextDocumentContentChangeEvent("function foo() {\n  alert('toto');\n}"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(docId, singletonList(new TextDocumentContentChangeEvent("function foo() {\n  alert('toto');\n  alert('toto');\n}"))));
    if (client.diagnosticsLatch.await(1, TimeUnit.MINUTES)) {
      List<Diagnostic> diagnostics = client.getDiagnostics(uri);
      assertThat(diagnostics)
        .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
        .containsExactly(tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Unexpected alert.", DiagnosticSeverity.Information),
          tuple(2, 2, 2, 15, "javascript:S1442", "sonarlint", "Unexpected alert.", DiagnosticSeverity.Information));
    } else {
      throw new AssertionError("No diagnostics received after 1 minute");
    }
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

    assertThat(result.get("HTML"))
      .extracting(RuleDescription::getKey, RuleDescription::getName, RuleDescription::getSeverity, RuleDescription::getType, RuleDescription::getHtmlDescription,
        RuleDescription::isActiveByDefault)
      .contains(tuple("Web:PageWithoutTitleCheck", "\"<title>\" should be present in all pages", "MAJOR", "BUG",
        "<p>Titles are important because they are displayed in search engine results as well as the browser's toolbar.</p>\n" +
          "<p>This rule verifies that the <code>&lt;head&gt;</code> tag contains a <code>&lt;title&gt;</code> one, and the <code>&lt;html&gt;</code> tag a\n" +
          "<code>&lt;head&gt;</code> one.</p>\n" +
          "<h2>Noncompliant Code Example</h2>\n" +
          "<pre>\n" +
          "&lt;html&gt;         &lt;!-- Non-Compliant --&gt;\n" +
          "\n" +
          "&lt;body&gt;\n" +
          "...\n" +
          "&lt;/body&gt;\n" +
          "\n" +
          "&lt;/html&gt;\n" +
          "</pre>\n" +
          "<h2>Compliant Solution</h2>\n" +
          "<pre>\n" +
          "&lt;html&gt;         &lt;!-- Compliant --&gt;\n" +
          "\n" +
          "&lt;head&gt;\n" +
          "  &lt;title&gt;Some relevant title&lt;/title&gt;\n" +
          "&lt;/head&gt;\n" +
          "\n" +
          "&lt;body&gt;\n" +
          "...\n" +
          "&lt;/body&gt;\n" +
          "\n" +
          "&lt;/html&gt;\n" +
          "</pre>",
        true));
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

}
