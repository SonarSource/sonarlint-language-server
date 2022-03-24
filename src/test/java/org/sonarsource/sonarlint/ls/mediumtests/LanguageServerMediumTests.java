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

import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.Rule;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.LocalBranchNameChangeEvent;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LanguageServerMediumTests extends AbstractLanguageServerMediumTests {

  @BeforeAll
  static void initialize() throws Exception {
    var fakeTypeScriptProjectPath = Paths.get("src/test/resources/fake-ts-project").toAbsolutePath();

    initialize(Map.of(
      "typeScriptLocation", fakeTypeScriptProjectPath.resolve("node_modules").toString(),
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1",
      "showVerboseLogs", false,
      "additionalAttributes", Map.of("extra", "value")));
  }

  @BeforeEach
  void prepare() {
    client.isIgnoredByScm = false;
  }

  @Test
  void analyzeSimpleJsFileOnOpen() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true);

    var uri = getUri("analyzeSimpleJsFileOnOpen.js");
    didOpen(uri, "javascript", "function foo() {\n  var toto = 0;\n  var plouf = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 6, 1, 10, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Information),
        tuple(2, 6, 2, 11, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Information)));
  }

  @Test
  void analyzeSimpleJsFileWithCustomRuleConfig() throws Exception {
    var uri = getUri("analyzeSimpleJsFileWithCustomRuleConfig.js");
    var jsSource = "function foo()\n {\n  var toto = 0;\n  var plouf = 0;\n}";

    // Default configuration should result in 2 issues: S1442 and UnusedVariable
    emulateConfigurationChangeOnClient("**/*Test.js", null, false, true);

    assertLogContains(
      "Default settings updated: WorkspaceFolderSettings[analyzerProperties={},connectionId=<null>,pathToCompileCommands=<null>,projectKey=<null>,testFilePattern=**/*Test.js]");

    didOpen(uri, "javascript", jsSource);
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(2, 6, 2, 10, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Information),
        tuple(3, 6, 3, 11, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Information)));

    client.clear();

    // Update rules configuration: disable UnusedVariable, enable Semicolon
    emulateConfigurationChangeOnClient("**/*Test.js", null,
      "javascript:S1481", "off",
      "javascript:S1105", "on");

    assertLogContains(
      "Global settings updated: WorkspaceSettings[connections={},disableTelemetry=false,excludedRules=[javascript:S1481],includedRules=[javascript:S1105],pathToNodeExecutable=<null>,ruleParameters={},showAnalyzerLogs=false,showVerboseLogs=false]");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 1, 1, 2, "javascript:S1105", "sonarlint", "Opening curly brace does not appear on the same line as controlling statement.", DiagnosticSeverity.Information)
      // Expected issues on javascript:S1481 are suppressed by rule configuration
      ));
  }

  @Test
  void analyzeSimpleTsFileOnOpen() throws Exception {
    var tsconfig = temp.resolve("tsconfig.json");
    Files.write(tsconfig, "{}".getBytes(StandardCharsets.UTF_8));
    var uri = getUri("analyzeSimpleTsFileOnOpen.ts");

    didOpen(uri, "typescript", "function foo() {\n if(bar() && bar()) { return 42; }\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(tuple(1, 13, 1, 18, "typescript:S1764", "sonarlint", "Correct one of the identical sub-expressions on both sides of operator \"&&\" [+1 location]",
        DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimplePythonFileOnOpen() throws Exception {
    var uri = getUri("analyzeSimplePythonFileOnOpen.py");

    didOpen(uri, "python", "def foo():\n  print 'toto'\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 2, 1, 7, "python:PrintStatementUsage", "sonarlint", "Replace print statement by built-in function.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzePythonFileWithDuplicatedStringOnOpen() throws Exception {
    var uri = getUri("analyzePythonFileWithDuplicatedStringOnOpen.py");

    didOpen(uri, "python", "def foo():\n  print('/toto')\n  print('/toto')\n  print('/toto')\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 8, 1, 15, "python:S1192", "sonarlint", "Define a constant instead of duplicating this literal '/toto' 3 times. [+2 locations]", DiagnosticSeverity.Warning)));

    var d = client.getDiagnostics(uri).get(0);
    var codeActionParams = new CodeActionParams(new TextDocumentIdentifier(uri), d.getRange(), new CodeActionContext(List.of(d)));
    var codeActions = lsProxy.getTextDocumentService().codeAction(codeActionParams).get();
    assertThat(codeActions).hasSize(3);
    var allLocationsAction = codeActions.get(1).getRight();
    assertThat(allLocationsAction.getCommand().getCommand()).isEqualTo(ShowAllLocationsCommand.ID);
    assertThat(allLocationsAction.getCommand().getArguments()).hasSize(1);
  }

  @Test
  void analyzeSimplePhpFileOnOpen() throws Exception {
    var uri = getUri("foo.php");

    didOpen(uri, "php", "<?php\nfunction foo() {\n  echo(\"Hello\");\n}\n?>");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(tuple(2, 2, 2, 6, "php:S2041", "sonarlint", "Remove the parentheses from this \"echo\" call.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleHtmlFileOnOpen() throws Exception {
    var uri = getUri("foo.html");

    didOpen(uri, "html", "<html><body></body></html>");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 0, 0, 6, "Web:DoctypePresenceCheck", "sonarlint", "Insert a <!DOCTYPE> declaration to before this <html> tag.",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 6, "Web:S5254", "sonarlint", "Add \"lang\" and/or \"xml:lang\" attributes to this \"<html>\" element",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 26, "Web:PageWithoutTitleCheck", "sonarlint", "Add a <title> tag to this page.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleJspFileOnOpen() throws Exception {
    var uri = getUri("foo.html");

    didOpen(uri, "jsp", "<html><body></body></html>");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 0, 0, 6, "Web:DoctypePresenceCheck", "sonarlint", "Insert a <!DOCTYPE> declaration to before this <html> tag.",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 6, "Web:S5254", "sonarlint", "Add \"lang\" and/or \"xml:lang\" attributes to this \"<html>\" element",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 26, "Web:PageWithoutTitleCheck", "sonarlint", "Add a <title> tag to this page.", DiagnosticSeverity.Warning)));
  }

  @Test
  void noIssueOnTestJSFiles() throws Exception {
    emulateConfigurationChangeOnClient("{**/*Test*}", null, null, true);
    assertLogContains(
      "Default settings updated: WorkspaceFolderSettings[analyzerProperties={},connectionId=<null>,pathToCompileCommands=<null>,projectKey=<null>,testFilePattern={**/*Test*}]");

    var jsContent = "function foo() {\n  var toto = 0;\n}";
    var fooTestUri = getUri("fooTest.js");
    didOpen(fooTestUri, "javascript", jsContent);

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Info] Found 0 issues"));
    assertThat(client.getDiagnostics(fooTestUri)).isEmpty();
    client.clear();

    emulateConfigurationChangeOnClient("{**/*MyTest*}", null, null, true);
    assertLogContains(
      "Default settings updated: WorkspaceFolderSettings[analyzerProperties={},connectionId=<null>,pathToCompileCommands=<null>,projectKey=<null>,testFilePattern={**/*MyTest*}]");

    didChange(fooTestUri, jsContent);
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fooTestUri)).hasSize(1));

    client.logs.clear();

    var fooMyTestUri = getUri("fooMyTest.js");
    didOpen(fooMyTestUri, "javascript", jsContent);

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Info] Found 0 issues"));

    assertThat(client.getDiagnostics(fooMyTestUri)).isEmpty();
  }

  @Test
  void analyzeSimpleJsFileOnChange() throws Exception {
    var uri = getUri("analyzeSimpleJsFileOnChange.js");

    didOpen(uri, "javascript", "function foo() {}");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Info] Found 0 issues"));

    didChange(uri, "function foo() {\n  var toto = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(tuple(1, 6, 1, 10, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Information)));
  }

  @Test
  void analyzeSimpleXmlFileOnOpen() throws Exception {
    var uri = getUri("analyzeSimpleXmlFileOnOpen.xml");

    didOpen(uri, "xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<root>\n" +
      "  <!-- TODO Add content -->\n" +
      "</root>\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(2, 2, 2, 27, "xml:S1135", "sonarlint", "Complete the task associated to this \"TODO\" comment.", DiagnosticSeverity.Hint)));
  }

  @Test
  void delayAnalysisOnChange() throws Exception {
    var uri = getUri("foo.js");

    didOpen(uri, "javascript", "function foo() {}");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Info] Found 0 issues"));

    client.logs.clear();

    // Emulate two quick changes, should only trigger one analysis
    lsProxy.getTextDocumentService()
      .didChange(
        new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, 2), List.of(new TextDocumentContentChangeEvent("function foo() {\n  var toto = 0;\n}"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, 3),
        List.of(new TextDocumentContentChangeEvent("function foo() {\n  var toto = 0;\n  var plouf = 0;\n}"))));

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Debug] Queuing analysis of file '" + uri + "' (version 3)"));

    assertThat(client.logs)
      .extracting(withoutTimestamp())
      .doesNotContain("[Debug] Queuing analysis of file '" + uri + "' (version 2)");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 6, 1, 10, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Information),
        tuple(2, 6, 2, 11, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Information)));
  }

  @Test
  void cleanDiagnosticsOnClose() throws Exception {
    var uri = getUri("foo.js");
    didOpen(uri, "javascript", "function foo() {\n  var toto = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(tuple(1, 6, 1, 10, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Information)));

    lsProxy.getTextDocumentService().didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri)).isEmpty());
  }

  @Test
  void noAnalysisOnNullContent() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true, true, true);

    var uri = getUri("foo.py");
    // SLVSCODE-157 - Open/Close/Open/Close triggers a race condition that nullifies content
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "python", 1, "# Nothing to see here\n")));
    lsProxy.getTextDocumentService()
      .didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "python", 1, "# Nothing to see here\n")));
    lsProxy.getTextDocumentService()
      .didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Info] Found 0 issues",
        "[Info] Found 0 issues"));
    assertThat(client.getDiagnostics(uri)).isEmpty();
  }

  @Test
  void vcsIgnoredShouldNotAnalyzed() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true, true, true);
    client.logs.clear();

    var uri = getUri("foo.py");
    client.isIgnoredByScm = true;

    didOpen(uri, "python", "# Nothing to see here\n");

    awaitUntilAsserted(() -> assertThat(client.logs).extracting(withoutTimestamp())
      .contains("[Debug] Skip analysis for SCM ignored file: '" + uri + "'"));
    assertThat(client.getDiagnostics(uri)).isEmpty();
  }

  @Test
  void optOutTelemetry() throws Exception {
    // Ensure telemetry is disabled and enable verbose logs
    emulateConfigurationChangeOnClient(null, true, false, true);

    // We are using the global system property to disable telemetry in tests, so this assertion do not pass
    // assertLogContainsInOrder( "Telemetry disabled");

    // Enable telemetry
    emulateConfigurationChangeOnClient(null, false, false, true);

    assertLogContains(
      "Global settings updated: WorkspaceSettings[connections={},disableTelemetry=false,excludedRules=[],includedRules=[],pathToNodeExecutable=<null>,ruleParameters={},showAnalyzerLogs=false,showVerboseLogs=true]");
    // We are using the global system property to disable telemetry in tests, so this assertion do not pass
    // assertLogContainsInOrder( "Telemetry enabled");
  }

  @Test
  void testUnknownCommand() throws Exception {
    try {
      lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("unknown", Collections.emptyList())).get();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ResponseErrorException.class);
      var responseError = ((ResponseErrorException) e.getCause()).getResponseError();
      assertThat(responseError.getCode()).isEqualTo(ResponseErrorCode.InvalidParams.getValue());
      assertThat(responseError.getMessage()).isEqualTo("Unsupported command: unknown");
    }
  }

  @Test
  void test_command_open_standalone_rule_desc_with_unknown_diagnostic_rule() throws Exception {
    try {
      lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("SonarLint.OpenStandaloneRuleDesc", List.of("unknown:rule"))).get();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ResponseErrorException.class);
      assertThat(((ResponseErrorException) e.getCause()).getResponseError().getMessage())
        .isEqualTo("Unknown rule with key: unknown:rule");
    }
  }

  @Test
  void test_command_open_standalone_rule_desc() throws Exception {
    client.showRuleDescriptionLatch = new CountDownLatch(1);
    lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("SonarLint.OpenStandaloneRuleDesc", List.of("javascript:S930"))).get();
    assertTrue(client.showRuleDescriptionLatch.await(1, TimeUnit.MINUTES));

    assertThat(client.ruleDesc.getKey()).isEqualTo("javascript:S930");
    assertThat(client.ruleDesc.getName()).isEqualTo("Function calls should not pass extra arguments");
    assertThat(client.ruleDesc.getHtmlDescription()).contains("You can easily call a JavaScript function with more arguments than the function needs");
    assertThat(client.ruleDesc.getType()).isEqualTo("BUG");
    assertThat(client.ruleDesc.getSeverity()).isEqualTo("CRITICAL");
    assertThat(client.ruleDesc.getParameters()).isEmpty();
  }

  @Test
  void test_command_open_standalone_rule_desc_with_params() throws Exception {
    client.showRuleDescriptionLatch = new CountDownLatch(1);
    lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("SonarLint.OpenStandaloneRuleDesc", List.of("javascript:S103"))).get();
    assertTrue(client.showRuleDescriptionLatch.await(1, TimeUnit.MINUTES));

    assertThat(client.ruleDesc.getKey()).isEqualTo("javascript:S103");
    assertThat(client.ruleDesc.getName()).isEqualTo("Lines should not be too long");
    assertThat(client.ruleDesc.getHtmlDescription()).contains("Having to scroll horizontally makes it harder to get a quick overview and understanding of any piece of code");
    assertThat(client.ruleDesc.getType()).isEqualTo("CODE_SMELL");
    assertThat(client.ruleDesc.getSeverity()).isEqualTo("MAJOR");
    assertThat(client.ruleDesc.getParameters()).hasSize(1)
      .extracting(SonarLintExtendedLanguageClient.RuleParameter::getName, SonarLintExtendedLanguageClient.RuleParameter::getDescription,
        SonarLintExtendedLanguageClient.RuleParameter::getDefaultValue)
      .containsExactly(tuple("maximumLineLength", "The maximum authorized line length.", "180"));
  }

  @Test
  void test_command_open_rule_desc_from_code_action() throws Exception {
    client.showRuleDescriptionLatch = new CountDownLatch(1);
    lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("SonarLint.OpenRuleDescCodeAction", List.of("javascript:S930", "file://foo.js"))).get();
    assertTrue(client.showRuleDescriptionLatch.await(1, TimeUnit.MINUTES));

    assertThat(client.ruleDesc.getKey()).isEqualTo("javascript:S930");
    assertThat(client.ruleDesc.getName()).isEqualTo("Function calls should not pass extra arguments");
    assertThat(client.ruleDesc.getHtmlDescription()).contains("You can easily call a JavaScript function with more arguments than the function needs");
    assertThat(client.ruleDesc.getType()).isEqualTo("BUG");
    assertThat(client.ruleDesc.getSeverity()).isEqualTo("CRITICAL");
  }

  @Test
  void testCodeAction_with_diagnostic_rule() throws Exception {
    var range = new Range(new Position(1, 0), new Position(1, 10));
    var d = new Diagnostic(range, "An issue");
    d.setSource("sonarlint");
    d.setCode("javascript:S930");
    d.setData("uuid");
    var codeActionParams = new CodeActionParams(new TextDocumentIdentifier("file://foo.js"), range, new CodeActionContext(List.of(d)));
    var list = lsProxy.getTextDocumentService().codeAction(codeActionParams).get();
    assertThat(list).hasSize(2);
    var codeAction = list.get(0).getRight();
    assertThat(codeAction.getTitle()).isEqualTo("SonarLint: Open description of rule 'javascript:S930'");
    var openRuleDesc = codeAction.getCommand();
    assertThat(openRuleDesc.getCommand()).isEqualTo("SonarLint.OpenRuleDescCodeAction");
    assertThat(openRuleDesc.getArguments()).hasSize(2);
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(0)).getAsString()).isEqualTo("javascript:S930");
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(1)).getAsString()).isEqualTo("file://foo.js");
    var disableRuleCodeAction = list.get(1).getRight();
    assertThat(disableRuleCodeAction.getTitle()).isEqualTo("SonarLint: Deactivate rule 'javascript:S930'");
    var disableRule = disableRuleCodeAction.getCommand();
    assertThat(disableRule.getCommand()).isEqualTo("SonarLint.DeactivateRule");
    assertThat(disableRule.getArguments()).hasSize(1);
    assertThat(((JsonPrimitive) disableRule.getArguments().get(0)).getAsString()).isEqualTo("javascript:S930");
  }

  @Test
  void testListAllRules() throws Exception {
    var result = lsProxy.listAllRules().join();
    String[] commercialLanguages = new String[] {"C", "C++"};
    String[] freeLanguages = new String[] {"HTML", "JavaScript", "TypeScript", "PHP", "Python", "Java", "XML"};
    if (COMMERCIAL_ENABLED) {
      assertThat(result).containsOnlyKeys(ArrayUtils.addAll(commercialLanguages, freeLanguages));
    } else {
      assertThat(result).containsOnlyKeys(freeLanguages);
    }

    assertThat(result.get("HTML"))
      .extracting(Rule::getKey, Rule::getName, Rule::isActiveByDefault)
      .contains(tuple("Web:PageWithoutTitleCheck", "\"<title>\" should be present in all pages", true));
  }

  @Test
  void logErrorWhenClientFailedToReturnConfiguration() {
    // No workspaceFolderPath settings registered in the client mock, so it should fail when server will request workspaceFolderPath
    // configuration
    var folderUri = "some://noconfig_uri";
    try {
      lsProxy.getWorkspaceService()
        .didChangeWorkspaceFolders(
          new DidChangeWorkspaceFoldersParams(
            new WorkspaceFoldersChangeEvent(List.of(new WorkspaceFolder(folderUri, "No config")), Collections.emptyList())));

      assertLogContainsPattern("\\[Error.*\\] Unable to fetch configuration of folder " + folderUri);
      assertLogContainsPattern("(?s).*Internal error.*");
    } finally {
      lsProxy.getWorkspaceService()
        .didChangeWorkspaceFolders(
          new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(Collections.emptyList(), List.of(new WorkspaceFolder(folderUri, "No config")))));
    }
  }

  @Test
  void fetchWorkspaceFolderConfigurationWhenAdded() throws Exception {
    client.settingsLatch = new CountDownLatch(1);
    var folderUri = "file:///added_uri";
    client.folderSettings.put(folderUri, buildSonarLintSettingsSection("another pattern", null, null, true));

    try {
      lsProxy.getWorkspaceService()
        .didChangeWorkspaceFolders(
          new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(List.of(new WorkspaceFolder(folderUri, "Added")), Collections.emptyList())));
      awaitLatch(client.settingsLatch);

      assertLogContains(
        "Workspace folder 'WorkspaceFolder[name=Added,uri=file:///added_uri]' configuration updated: WorkspaceFolderSettings[analyzerProperties={},connectionId=<null>,pathToCompileCommands=<null>,projectKey=<null>,testFilePattern=another pattern]");
    } finally {
      lsProxy.getWorkspaceService()
        .didChangeWorkspaceFolders(
          new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(Collections.emptyList(), List.of(new WorkspaceFolder(folderUri, "Added")))));

    }
  }

  @Test
  void test_analysis_logs_disabled() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true, false, false);
    Thread.sleep(1000);
    client.logs.clear();

    var uri = getUri("testAnalysisLogsDisabled.js");
    didOpen(uri, "javascript", "function foo() {\n  alert('toto');\n  var plouf = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .filteredOn(notFromContextualTSserver())
      .extracting(withoutTimestamp())
      .containsExactly(
        "[Info] Analyzing file '" + uri + "'...",
        "[Info] Found 1 issue"));
  }

  @Test
  void test_debug_logs_enabled() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true, false, true);
    Thread.sleep(1000);
    client.logs.clear();

    var uri = getUri("testAnalysisLogsDebugEnabled.js");
    didOpen(uri, "javascript", "function foo() {\n  alert('toto');\n  var plouf = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .filteredOn(notFromContextualTSserver())
      .extracting(withoutTimestamp())
      .containsSubsequence(
        "[Debug] Queuing analysis of file '" + uri + "' (version 1)",
        "[Info] Analyzing file '" + uri + "'...",
        "[Info] Found 1 issue"));
  }

  @Test
  void test_analysis_logs_enabled() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true, true, false);
    Thread.sleep(1000);
    client.logs.clear();

    var uri = getUri("testAnalysisLogsEnabled.js");
    didOpen(uri, "javascript", "function foo() {\n  alert('toto');\n  var plouf = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .filteredOn(notFromContextualTSserver())
      .extracting(withoutTimestamp())
      .contains(
        "[Info] Analyzing file '" + uri + "'...",
        "[Info] Index files",
        "[Info] 1 file indexed",
        "[Info] 1 source files to be analyzed",
        "[Info] Found 1 issue"));
  }

  @Test
  void test_analysis_with_debug_logs_enabled() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true, true, true);
    Thread.sleep(1000);
    client.logs.clear();

    var uri = getUri("testAnalysisLogsWithDebugEnabled.js");
    didOpen(uri, "javascript", "function foo() {\n  alert('toto');\n  var plouf = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .filteredOn(notFromContextualTSserver())
      .extracting(withoutTimestamp())
      .contains(
        "[Info] Analyzing file '" + uri + "'...",
        "[Info] Index files",
        "[Debug] Language of file '" + uri + "' is set to 'JavaScript'",
        "[Info] 1 file indexed",
        "[Debug] Execute Sensor: JavaScript analysis",
        "[Info] Found 1 issue"));
  }

  @Test
  void preservePreviousDiagnosticsWhenFileHasParsingErrors() throws Exception {
    emulateConfigurationChangeOnClient("", true, true, true);
    var uri = getUri("parsingError.py");

    didOpen(uri, "python", "def foo():\n  print 'toto'\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 2, 1, 7, "python:PrintStatementUsage", "sonarlint", "Replace print statement by built-in function.", DiagnosticSeverity.Warning)));

    client.logs.clear();

    didChange(uri, "def foo()\n  print 'toto'\n");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Error] Unable to parse file: [uri=" + uri + "]"));

    Thread.sleep(1000);

    assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 2, 1, 7, "python:PrintStatementUsage", "sonarlint", "Replace print statement by built-in function.", DiagnosticSeverity.Warning));
  }

  @Test
  void updateBranchNameShouldLogAMessage() throws Exception {
    lsProxy.didLocalBranchNameChange(new LocalBranchNameChangeEvent("file:///some_folder", "some/branch/name"));

    assertLogContains("Folder file:///some_folder is now on branch some/branch/name.");

    assertThat(client.referenceBranchNameByFolder.get("file:///some_folder")).isNull();
  }

  @Test
  void updateBranchNameWithNullBranchShouldLogAnotherMessage() throws Exception {
    lsProxy.didLocalBranchNameChange(new LocalBranchNameChangeEvent("file:///some_folder", null));

    assertLogContains("Folder file:///some_folder is now on an unknown branch.");

    assertThat(client.referenceBranchNameByFolder.get("file:///some_folder")).isNull();
  }

  private Predicate<? super MessageParams> notFromContextualTSserver() {
    return m -> !m.getMessage().contains("SonarTS") && !m.getMessage().contains("Using typescript at");
  }

}
