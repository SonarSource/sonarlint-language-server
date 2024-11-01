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


import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jgit.api.Git;
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
import org.eclipse.lsp4j.MessageType;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttributeCategory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;
import org.sonarsource.sonarlint.ls.Rule;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.util.EnumLabelsMapper;
import testutils.MockWebServerExtension;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LanguageServerMediumTests extends AbstractLanguageServerMediumTests {

  private static final String CONNECTION_ID = "known";
  private static final String TOKEN = "token";
  private static final String PYTHON_S1481 = "python:S1481";
  private static final String PYTHON_S139 = "python:S139";
  private static final String JAVA_S2095 = "java:S2095";
  private static final String GO_S1862 = "go:S1862";
  private static final String GO_S108 = "go:S108";
  @RegisterExtension
  private static final MockWebServerExtension mockWebServerExtension = new MockWebServerExtension();
  public static final String CLOUDFORMATION_S6273 = "cloudformation:S6273";
  public static final String DOCKER_S6476 = "docker:S6476";
  public static final String TERRAFORM_S6273 = "terraform:S6273";
  public static final String ARM_S4423 = "azureresourcemanager:S4423";
  private static Path omnisharpDir;
  private static Path analysisDir;

  @BeforeAll
  static void initialize() throws Exception {
    omnisharpDir = makeStaticTempDir();
    analysisDir = makeStaticTempDir();
    initialize(Map.of(
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1",
      "showVerboseLogs", false,
      "productKey", "productKey",
      "additionalAttributes", Map.of(
        "extra", "value",
        "omnisharpDirectory", omnisharpDir.toString()
      )
    ), new WorkspaceFolder(analysisDir.toUri().toString(), "AnalysisDir"));
  }

  @BeforeEach
  void prepare() throws IOException {
    client.isIgnoredByScm = false;
    org.apache.commons.io.FileUtils.cleanDirectory(analysisDir.toFile());
  }

  @BeforeEach
  public void mockSonarQube() {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"9.9\", \"id\": \"xzy\"}");
    mockWebServerExtension.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": true}");
    mockWebServerExtension.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1", Components.SearchWsResponse.newBuilder().build());
  }

  @Test
  void analyzeSimpleJsFileOnOpen() throws Exception {
    setShowVerboseLogs(client.globalSettings, true);
    var uri = getUri("analyzeSimpleJsFileOnOpen.js", analysisDir);
    didOpen(uri, "javascript", "function foo() {\n  let toto = 0;\n  let plouf = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 6, 1, 10, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Warning),
        tuple(2, 6, 2, 11, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleTsFileOnOpen() throws Exception {
    var tsconfig = temp.resolve("tsconfig.json");
    Files.write(tsconfig, "{}".getBytes(StandardCharsets.UTF_8));
    var uri = getUri("analyzeSimpleTsFileOnOpen.ts", analysisDir);

    didOpen(uri, "typescript", "function foo() {\n if(bar() && bar()) { return 42; }\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(tuple(1, 13, 1, 18, "typescript:S1764", "sonarlint", "Correct one of the identical sub-expressions on both sides of operator \"&&\" [+1 location]",
        DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleGoFileOnOpen() throws Exception {
    setRulesConfig(client.globalSettings, GO_S1862, "on"); //NB: make sure the tested rule is enabled
    notifyConfigurationChangeOnClient();

    var uri = getUri("analyzeSimpleGoFileOnOpen.go", analysisDir);

    didOpen(uri, "go", "package main\n" +
      "import \"fmt\"\n" +
      "func main() {\n" +
      "\tif condition1 {\n" +
      "\t} else if condition1 { // Noncompliant\n" +
      "\t}\n" +
      "}");
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(3, 15, 4, 2, GO_S108, "sonarlint", "Either remove or fill this block of code.", DiagnosticSeverity.Warning),
        tuple(4, 11, 4, 21, GO_S1862, "sonarlint", "This condition duplicates the one on line 4. [+1 location]", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleCloudFormationFileOnOpen() throws Exception {
    setRulesConfig(client.globalSettings, CLOUDFORMATION_S6273, "on"); //NB: make sure the tested rule is enabled
    notifyConfigurationChangeOnClient();

    var uri = getUri("sampleCloudFormation.yaml", analysisDir);

    didOpen(uri, "yaml", "AWSTemplateFormatVersion: 2010-09-09\n" +
      "Resources:\n" +
      "  S3Bucket:\n" +
      "    Type: 'AWS::S3::Bucket'\n" +
      "    Properties:\n" +
      "      BucketName: \"mybucketname\"\n" +
      "      Tags:\n" +
      "        - Key: \"anycompany:cost-center\" # Noncompliant\n" +
      "          Value: \"Accounting\"\n" +
      "        - Key: \"anycompany:EnvironmentType\" # Noncompliant\n" +
      "          Value: \"PROD\"\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(7, 15, 7, 39, CLOUDFORMATION_S6273,
          "sonarlint", "Rename tag key \"anycompany:cost-center\" to match the regular expression \"^([A-Z][A-Za-z]*:)*([A-Z][A-Za-z]*)$\".",
          DiagnosticSeverity.Warning),
        tuple(9, 15, 9, 43, CLOUDFORMATION_S6273, "sonarlint",
          "Rename tag key \"anycompany:EnvironmentType\" to match the regular expression \"^([A-Z][A-Za-z]*:)*([A-Z][A-Za-z]*)$\".",
          DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleDockerFileOnOpen() throws Exception {
    setRulesConfig(client.globalSettings, DOCKER_S6476, "on"); //NB: make sure the tested rule is enabled
    notifyConfigurationChangeOnClient();

    var uri = getUri("Dockerfile", analysisDir);

    didOpen(uri, "docker", "from ubuntu:22.04 as jammy\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 18, 0, 20, DOCKER_S6476, "sonarlint", "Replace `as` with upper case format `AS`.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 4, DOCKER_S6476, "sonarlint", "Replace `from` with upper case format `FROM`.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleTerraformFileOnOpen() throws Exception {
    setRulesConfig(client.globalSettings, TERRAFORM_S6273, "on"); //NB: make sure the tested rule is enabled
    notifyConfigurationChangeOnClient();

    var uri = getUri("sampleTerraform.tf", analysisDir);

    didOpen(uri, "terraform", "resource \"aws_s3_bucket\" \"mynoncompliantbucket\" {\n" +
      "  bucket = \"mybucketname\"\n" +
      "\n" +
      "  tags = {\n" +
      "    \"anycompany:cost-center\" = \"Accounting\" # Noncompliant\n" +
      "  }\n" +
      "}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(4, 4, 4, 28, TERRAFORM_S6273, "sonarlint",
          "Rename tag key \"anycompany:cost-center\" to match the regular expression \"^([A-Z][A-Za-z]*:)*([A-Z][A-Za-z]*)$\".", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleBicepFileOnOpen() throws Exception {
    setRulesConfig(client.globalSettings, ARM_S4423, "on");
    notifyConfigurationChangeOnClient();

    var uri = getUri("sampleBicep.bicep", analysisDir);

    didOpen(uri, "bicep", """
      resource mysqlDbServer 'Microsoft.DBforMySQL/servers@2017-12-01' = {
        name: 'example'
        properties: {
          minimalTlsVersion: 'TLS1_0'
        }
      }
      """);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(3, 4, 3, 31, ARM_S4423, "sonarlint",
          "Change this code to disable support of older TLS versions.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleArmJsonFileOnOpen() throws Exception {
    setRulesConfig(client.globalSettings, ARM_S4423, "on");
    notifyConfigurationChangeOnClient();

    var uri = getUri("sampleArm.json", analysisDir);

    didOpen(uri, "json", """
      {
        "$schema": "https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#",
        "contentVersion": "1.0.0.0",
        "resources": [
          {
            "type": "Microsoft.DBforMySQL/servers",
            "apiVersion": "2017-12-01",
            "name": "Raise an issue: older TLS versions shouldn't be allowed",
            "properties": {
              "minimalTlsVersion": "TLS1_0"
            }
          }
        ]
      }
      """);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(9, 8, 9, 37, ARM_S4423, "sonarlint",
          "Change this code to disable support of older TLS versions.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimplePythonFileOnOpen() throws Exception {
    var uri = getUri("analyzeSimplePythonFileOnOpen.py", analysisDir);

    didOpen(uri, "python", "def foo():\n  print 'toto'\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 2, 1, 7, "python:PrintStatementUsage", "sonarlint", "Replace print statement by built-in function.", DiagnosticSeverity.Warning)));
  }

  @Test
  @Disabled("We lost the ability to exclude preview files")
  void doNotAnalyzePythonFileOnPreview() throws Exception {
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    var uri = getUri("analyzeSimplePythonFileOnOpen.py", analysisDir);

    client.shouldAnalyseFile = false;
    didOpen(uri, "python", "def foo():\n  print 'toto'\n");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Info] reason \"" + uri + "\""));
    assertThat(client.getDiagnostics(uri)).isEmpty();
  }

  @Test
  void analyzeSimplePythonFileWithCustomRuleConfig() throws Exception {
    var uri = getUri("analyzeSimplePyFileWithCustomRuleConfig.py", analysisDir);
    var pySource = "def foo():\n  toto = 0\n  plouf = 0   # This is a trailing comment that can be very very long\n";

    // Default configuration should result in 2 issues for rule S1481

    didOpen(uri, "python", pySource);
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));

    client.clear();

    // Update rules configuration: disable S1481, enable S139
    setRulesConfig(client.globalSettings, PYTHON_S1481, "off", PYTHON_S139, "on");
    notifyConfigurationChangeOnClient();

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(2, 14, 2, 69, PYTHON_S139, "sonarlint", "Move this trailing comment on the previous empty line.", DiagnosticSeverity.Warning)
        // Expected issues on python:S1481 are suppressed by rule configuration
      ));
  }

  @Test
  void analyzePythonFileWithDuplicatedStringOnOpen() throws Exception {
    var uri = getUri("analyzePythonFileWithDuplicatedStringOnOpen.py", analysisDir);

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
    var uri = getUri("foo.php", analysisDir);

    didOpen(uri, "php", "<?php\nfunction foo() {\n  echo(\"Hello\");\n}\n?>");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 0, 0, 0, "php:S113", "sonarlint", "Add a new line at the end of this file.", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 15, "php:S6600", "sonarlint", "Remove the parentheses from this \"echo\" call.", DiagnosticSeverity.Warning),
        tuple(4, 0, 4, 2, "php:S1780", "sonarlint", "Remove this closing tag \"?>\".", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleHtmlFileOnOpen() throws Exception {
    var uri = getUri("foo.html", analysisDir);

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
    var uri = getUri("foo1.html", analysisDir);

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
    setTestFilePattern(getFolderSettings(analysisDir.toUri().toString()), "{**/*Test*}");
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    var jsContent = "function foo() {\n  let toto = 0;\n}";
    var fooTestUri = getUri("fooTest.js", analysisDir);
    didOpen(fooTestUri, "javascript", jsContent);

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestampAndMillis())
      .contains("[Info] Analysis detected 0 issues and 0 Security Hotspots in XXXms"));
    assertThat(client.getDiagnostics(fooTestUri)).isEmpty();
    client.clear();

    setTestFilePattern(getFolderSettings(analysisDir.toUri().toString()), "{**/*MyTest*}");
    notifyConfigurationChangeOnClient();

    didChange(fooTestUri, jsContent);
    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestampAndMillis())
      .contains("[Info] Analysis detected 1 issue and 0 Security Hotspots in XXXms"));
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fooTestUri)).hasSize(1));

    client.logs.clear();

    var fooMyTestUri = getUri("fooMyTest.js", analysisDir);
    didOpen(fooMyTestUri, "javascript", jsContent);

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestampAndMillis())
      .contains("[Info] Analysis detected 0 issues and 0 Security Hotspots in XXXms"));

    assertThat(client.getDiagnostics(fooMyTestUri)).isEmpty();
  }

  @Test
  void analyzeSimplePythonFileOnChange() throws Exception {
    var uri = getUri("analyzeSimplePythonFileOnChange.py", analysisDir);

    didOpen(uri, "python", "def foo():\n  # Empty\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri).isEmpty()));

    didChange(uri, "def foo():\n  toto = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning)));
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void cleanUpDiagnosticsOnFileClose() throws IOException {
    var uri = getUri("foo2.html", analysisDir);

    didOpen(uri, "html", "<html><body></body></html>");
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 0, 0, 6, "Web:DoctypePresenceCheck", "sonarlint", "Insert a <!DOCTYPE> declaration to before this <html> tag.",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 6, "Web:S5254", "sonarlint", "Add \"lang\" and/or \"xml:lang\" attributes to this \"<html>\" element",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 26, "Web:PageWithoutTitleCheck", "sonarlint", "Add a <title> tag to this page.", DiagnosticSeverity.Warning)));

    didClose(uri);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri)).isEmpty());
  }

  @Test
  void analyzeSimpleXmlFileOnOpen() throws Exception {
    var uri = getUri("analyzeSimpleXmlFileOnOpen.xml", analysisDir);

    didOpen(uri, "xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<root>\n" +
      "  <!-- TODO Add content -->\n" +
      "</root>\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(2, 2, 2, 27, "xml:S1135", "sonarlint", "Complete the task associated to this \"TODO\" comment.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleCssFileOnOpen() throws Exception {
    var uri = getUri("analyzeSimpleCssFileOnOpen.css", analysisDir);

    didOpen(uri, "css", "* {}\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 0, 0, 4, "css:S4658", "sonarlint", "Unexpected empty block", DiagnosticSeverity.Warning)));
  }

  @Test
  void delayAnalysisOnChange() throws Exception {
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    var uri = getUri("delayAnalysisOnChange.py", analysisDir);

    didOpen(uri, "python", "def foo():\n  print('Error code %d' % '42')\n");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestampAndMillis())
      .contains("[Info] Analysis detected 1 issue and 0 Security Hotspots in XXXms"));

    client.logs.clear();

    // Emulate two quick changes, should only trigger one analysis
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, 2),
        List.of(new TextDocumentContentChangeEvent("def foo():\n  toto = 0\n"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, 3),
        List.of(new TextDocumentContentChangeEvent("def foo():\n  toto = 0\n  plouf = 0\n"))));

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));
  }

  @Test
  void noAnalysisOnNullContent() throws Exception {
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    var uri = getUri("foo.py", analysisDir);
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
      .extracting(withoutTimestampAndMillis())
      .contains("[Info] Analysis detected 0 issues and 0 Security Hotspots in XXXms",
        "[Info] Analysis detected 0 issues and 0 Security Hotspots in XXXms"));
    assertThat(client.getDiagnostics(uri)).isEmpty();
  }

  @Test
  void vcsIgnoredShouldNotAnalyzed() throws Exception {
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();
    client.logs.clear();

    // Initialize git repository
    Git git = Git.init().setDirectory(analysisDir.toFile()).call();
    // avoid NoHeadException
    git.commit().setMessage("First snow").call();

    var uri = getUri("foo.py", analysisDir);
    var gitignorePath = analysisDir.resolve(".gitignore");
    var gitignoreContent = "foo.py";

    Files.writeString(gitignorePath, gitignoreContent, StandardOpenOption.CREATE);

    didOpen(uri, "python", "# Nothing to see here\n");

    awaitUntilAsserted(() -> assertThat(client.logs).extracting(withoutTimestamp())
      .contains("[Error] No file to analyze"));
    assertThat(client.getDiagnostics(uri)).isEmpty();
  }

  @Test
  void optOutTelemetry() {
    // Ensure telemetry is disabled and enable verbose logs
    setDisableTelemetry(client.globalSettings, true);
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    // We are using the global system property to disable telemetry in tests, so this assertion do not pass
    // assertLogContainsInOrder( "Telemetry disabled");

    // Enable telemetry
    setDisableTelemetry(client.globalSettings, false);
    notifyConfigurationChangeOnClient();

    assertLogContains(
      String.format("Global settings updated: WorkspaceSettings[analysisExcludes=,connections={%s=ServerConnectionSettings[connectionId=%s,disableNotifications=false,organizationKey=<null>,serverUrl=%s]},disableTelemetry=false,excludedRules=[],focusOnNewCode=false,includedRules=[],pathToNodeExecutable=<null>,ruleParameters={},showAnalyzerLogs=false,showVerboseLogs=true]",
        CONNECTION_ID, CONNECTION_ID, mockWebServerExtension.url("/")));
    // We are using the global system property to disable telemetry in tests, so this assertion do not pass
    // assertLogContainsInOrder( "Telemetry enabled");
  }

  @Test
  void testUnknownCommand() {
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
  void test_command_open_standalone_rule_desc_with_unknown_diagnostic_rule() throws ExecutionException, InterruptedException {
    lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("SonarLint.OpenStandaloneRuleDesc", List.of("unknown:rule"))).get();
    await().atMost(10, SECONDS).untilAsserted(() -> assertThat(client.shownMessages)
      .contains(new MessageParams(MessageType.Error, "Can't show rule details for unknown rule with key: unknown:rule")));
  }

  @Test
  void test_command_open_standalone_rule_desc() throws Exception {
    client.showRuleDescriptionLatch = new CountDownLatch(1);
    lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("SonarLint.OpenStandaloneRuleDesc", List.of("javascript:S930"))).get();
    assertTrue(client.showRuleDescriptionLatch.await(1, TimeUnit.MINUTES));

    var firstTabNonContextualInfo = client.ruleDesc.getHtmlDescriptionTabs()[0].getRuleDescriptionTabNonContextual();
    var htmlContent = firstTabNonContextualInfo != null ? firstTabNonContextualInfo.getHtmlContent() : "";

    assertThat(client.ruleDesc.getKey()).isEqualTo("javascript:S930");
    assertThat(client.ruleDesc.getName()).isEqualTo("Function calls should not pass extra arguments");
    assertThat(htmlContent).contains("When you call a function in JavaScript and provide more arguments than the function expects, the extra arguments are simply ignored by the\n" +
      "function.");
    assertThat(client.ruleDesc.getType()).isNull();
    assertThat(client.ruleDesc.getSeverity()).isNull();
    assertThat(client.ruleDesc.getCleanCodeAttributeCategory()).isEqualTo("Intentionality");
    assertThat(client.ruleDesc.getCleanCodeAttribute()).isEqualTo("Not logical");
    assertThat(client.ruleDesc.getImpacts()).containsExactly(Map.entry("Reliability", "High"));
    assertThat(client.ruleDesc.getParameters()).isEmpty();
    assertThat(client.ruleDesc.getHtmlDescriptionTabs()).hasSize(2);
  }

  @Test
  void test_clean_code_taxonomy_fields_are_present() throws Exception {
    client.showRuleDescriptionLatch = new CountDownLatch(1);
    lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("SonarLint.OpenStandaloneRuleDesc", List.of(JAVA_S2095))).get();
    assertTrue(client.showRuleDescriptionLatch.await(1, TimeUnit.MINUTES));

    assertThat(client.ruleDesc.getKey()).isEqualTo(JAVA_S2095);
    assertThat(client.ruleDesc.getCleanCodeAttribute()).isEqualTo(EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.COMPLETE));
    assertThat(client.ruleDesc.getCleanCodeAttributeCategory()).isEqualTo(EnumLabelsMapper.cleanCodeAttributeCategoryToLabel(CleanCodeAttributeCategory.INTENTIONAL));
    assertThat(client.ruleDesc.getImpacts()).containsExactly(Map.entry(EnumLabelsMapper.softwareQualityToLabel(SoftwareQuality.RELIABILITY), EnumLabelsMapper.impactSeverityToLabel(ImpactSeverity.HIGH)));
  }

  @Test
  void test_command_open_standalone_rule_desc_with_params() throws Exception {
    client.showRuleDescriptionLatch = new CountDownLatch(1);
    lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("SonarLint.OpenStandaloneRuleDesc", List.of(PYTHON_S139))).get();
    assertTrue(client.showRuleDescriptionLatch.await(1, TimeUnit.MINUTES));

    var firstTabNonContextualInfo = client.ruleDesc.getHtmlDescriptionTabs()[0].getRuleDescriptionTabNonContextual();
    var htmlContent = firstTabNonContextualInfo != null ? firstTabNonContextualInfo.getHtmlContent() : "";

    assertThat(client.ruleDesc.getKey()).isEqualTo(PYTHON_S139);
    assertThat(client.ruleDesc.getName()).isEqualTo("Comments should not be located at the end of lines of code");
    assertThat(htmlContent).contains("This rule verifies that single-line comments are not located at the ends of lines of code.");
    assertThat(client.ruleDesc.getCleanCodeAttribute()).isEqualTo("Not formatted");
    assertThat(client.ruleDesc.getImpacts()).containsEntry("Maintainability", "Low");
    assertThat(client.ruleDesc.getParameters()).hasSize(1)
      .extracting(SonarLintExtendedLanguageClient.RuleParameter::getName, SonarLintExtendedLanguageClient.RuleParameter::getDescription,
        SonarLintExtendedLanguageClient.RuleParameter::getDefaultValue)
      .containsExactly(
        tuple("legalTrailingCommentPattern",
          "Pattern for text of trailing comments that are allowed. By default, Mypy and Black pragma comments as well as comments containing only one word.",
          "^#\\s*+([^\\s]++|fmt.*|type.*|noqa.*)$"));
  }

  @Test
  void testCodeAction_with_diagnostic_rule() throws Exception {
    var uri = getUri("analyzeSimpleJsFileOnOpen.js", analysisDir);
    didOpen(uri, "javascript", "function sum(a, b) {\n" +
      "  return a + b;\n" +
      "}\n" +
      "\n" +
      "sum(1, 2, 3);");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .hasSize(1));

    var range = new Range(new Position(1, 0), new Position(1, 10));
    var issueId = ((JsonObject) client.getDiagnostics(uri).get(0).getData()).get("entryKey").getAsString();

    var codeActionParams = new CodeActionParams(new TextDocumentIdentifier(uri), range, new CodeActionContext(List.of(client.getDiagnostics(uri).get(0))));
    var list = lsProxy.getTextDocumentService().codeAction(codeActionParams).get();
    assertThat(list).hasSize(3);
    var codeAction = list.get(0).getRight();
    assertThat(codeAction.getTitle()).isEqualTo("SonarLint: Show issue details for 'javascript:S930'");
    var openRuleDesc = codeAction.getCommand();
    assertThat(openRuleDesc.getCommand()).isEqualTo("SonarLint.ShowIssueDetailsCodeAction");
    assertThat(openRuleDesc.getArguments()).hasSize(2);
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(0)).getAsString()).isEqualTo(issueId);
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(1)).getAsString()).isEqualTo(uri);
    var showAllLocationsCodeAction = list.get(1).getRight();
    assertThat(showAllLocationsCodeAction.getTitle()).isEqualTo("SonarLint: Show all locations for issue 'javascript:S930'");
    var disableRuleCodeAction = list.get(2).getRight();
    assertThat(disableRuleCodeAction.getTitle()).isEqualTo("SonarLint: Deactivate rule 'javascript:S930'");
    var disableRule = disableRuleCodeAction.getCommand();
    assertThat(disableRule.getCommand()).isEqualTo("SonarLint.DeactivateRule");
    assertThat(disableRule.getArguments()).hasSize(1);
    assertThat(((JsonPrimitive) disableRule.getArguments().get(0)).getAsString()).isEqualTo("javascript:S930");
  }

  @Test
  void testListAllRules() {
    var result = lsProxy.listAllRules().join();
    String[] commercialLanguages = new String[]{"C", "C++"};
    String[] freeLanguages = new String[]{"AzureResourceManager", "CSS", "C#", "CloudFormation", "Docker", "Go", "HTML", "IPython Notebooks", "Java",
      "JavaScript", "Kubernetes", "PHP", "Python", "Secrets", "Terraform", "TypeScript", "XML"};
    if (COMMERCIAL_ENABLED) {
      awaitUntilAsserted(() -> assertThat(result).containsOnlyKeys(ArrayUtils.addAll(commercialLanguages, freeLanguages)));
    } else {
      awaitUntilAsserted(() -> assertThat(result).containsOnlyKeys(freeLanguages));
    }

    awaitUntilAsserted(() -> assertThat(result.get("HTML"))
      .extracting(Rule::getKey, Rule::getName, Rule::isActiveByDefault)
      .contains(tuple("Web:PageWithoutTitleCheck", "\"<title>\" should be present in all pages", true)));
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

      assertLogContainsPattern("\\[Error.*\\] Unable to fetch configuration of folder " + folderUri + ".*");
      assertLogContainsPattern("(?s).*Internal error.*");
    } finally {
      lsProxy.getWorkspaceService()
        .didChangeWorkspaceFolders(
          new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(Collections.emptyList(), List.of(new WorkspaceFolder(folderUri, "No config")))));
    }
  }

  @Test
  void fetchWorkspaceFolderConfigurationWhenAdded() {
    client.settingsLatch = new CountDownLatch(1);
    var folderUri = "file:///added_uri";
    setShowVerboseLogs(client.globalSettings, true);
    setTestFilePattern(getFolderSettings(folderUri), "another pattern");
    notifyConfigurationChangeOnClient();

    try {
      lsProxy.getWorkspaceService()
        .didChangeWorkspaceFolders(
          new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(List.of(new WorkspaceFolder(folderUri, "Added")), Collections.emptyList())));
      awaitLatch(client.settingsLatch);

      assertLogContains(
        "Workspace folder 'WorkspaceFolder[name=Added,uri=file:///added_uri]' configuration updated: WorkspaceFolderSettings[analyzerProperties={sonar.cs.file.suffixes=.cs, sonar.cs.internal.loadProjectsTimeout=60, sonar.cs.internal.useNet6=true, sonar.cs.internal.loadProjectOnDemand=false},connectionId=<null>,pathToCompileCommands=<null>,projectKey=<null>,testFilePattern=another pattern]");
    } finally {
      lsProxy.getWorkspaceService()
        .didChangeWorkspaceFolders(
          new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(Collections.emptyList(), List.of(new WorkspaceFolder(folderUri, "Added")))));

    }
  }

  @Test
  void test_analysis_logs_disabled() throws Exception {
    client.folderSettings.computeIfAbsent(analysisDir.toUri().toString(), f -> new HashMap<>());
    Thread.sleep(1000);
    client.logs.clear();

    var uri = getUri("testAnalysisLogsDisabled.py", analysisDir);
    didOpen(uri, "python", "def foo():\n  toto = 0\n");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .filteredOn(notFromContextualTSserver())
      .extracting(withoutTimestampAndMillis())
      .contains(
        "[Info] Analysis detected 1 issue and 0 Security Hotspots in XXXms"));
  }

  @Test
  void test_debug_logs_enabled() throws Exception {
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();
    Thread.sleep(1000);
    client.logs.clear();

    var uri = getUri("testAnalysisLogsDebugEnabled.py", analysisDir);
    didOpen(uri, "python", "def foo():\n  toto = 0\n");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .filteredOn(notFromContextualTSserver())
      .extracting(withoutTimestampAndMillis())
      .containsSubsequence(
        "[Info] Analysis detected 1 issue and 0 Security Hotspots in XXXms"));
  }

  @Test
  void test_analysis_logs_enabled() throws Exception {
    setShowAnalyzerLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();
    Thread.sleep(1000);
    client.logs.clear();

    var uri = getUri("testAnalysisLogsEnabled.py", analysisDir);
    didOpen(uri, "python", "def foo():\n  toto = 0\n");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .filteredOn(notFromContextualTSserver())
      .extracting(withoutTimestampAndMillis())
      .contains(
        "[Info] Index files",
        "[Info] 1 file indexed",
        "[Info] 1 source file to be analyzed",
        "[Info] Analysis detected 1 issue and 0 Security Hotspots in XXXms"));
  }

  @Test
  void test_analysis_with_debug_logs_enabled() throws Exception {
    setShowVerboseLogs(client.globalSettings, true);
    setShowAnalyzerLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();
    Thread.sleep(1000);
    client.logs.clear();

    var uri = getUri("testAnalysisLogsWithDebugEnabled.py", analysisDir);
    didOpen(uri, "python", "def foo():\n  toto = 0\n");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .filteredOn(notFromContextualTSserver())
      .extracting(withoutTimestampAndMillis())
      .contains(
        "[Info] Index files",
        "[Debug] Language of file \"" + uri + "\" is set to \"PYTHON\"",
        "[Info] 1 file indexed",
        "[Debug] Execute Sensor: Python Sensor",
        "[Info] Analysis detected 1 issue and 0 Security Hotspots in XXXms"));
  }

  @Test
  @Disabled("Disabled until we can tell apart 0 issues from failed analysis")
  void preservePreviousDiagnosticsWhenFileHasParsingErrors() throws Exception {
    setShowVerboseLogs(client.globalSettings, true);
    setShowAnalyzerLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();
    var uri = getUri("parsingError.py", analysisDir);

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
  void updateBranchNameShouldLogAMessage() {
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    lsProxy.didLocalBranchNameChange(new DidLocalBranchNameChangeParams("file:///some_folder", "some/branch/name"));

    assertLogContains("Folder file:///some_folder is now on branch some/branch/name.");

    assertThat(client.referenceBranchNameByFolder.get("file:///some_folder")).isNull();
  }

  @Test
  void updateBranchNameWithNullBranchShouldLogAnotherMessage() {
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    lsProxy.didLocalBranchNameChange(new DidLocalBranchNameChangeParams("file:///some_folder", null));

    assertLogContains("Folder file:///some_folder is now on an unknown branch.");

    assertThat(client.referenceBranchNameByFolder.get("file:///some_folder")).isNull();
  }

  @Test
  void testCheckConnectionWithUnknownConnection() throws ExecutionException, InterruptedException {
    String unknownConnectionId = "unknown";
    SonarLintExtendedLanguageServer.ConnectionCheckParams testParams = new SonarLintExtendedLanguageServer.ConnectionCheckParams(unknownConnectionId);
    CompletableFuture<SonarLintExtendedLanguageClient.ConnectionCheckResult> result = lsProxy.checkConnection(testParams);

    SonarLintExtendedLanguageClient.ConnectionCheckResult actual = result.get();
    assertThat(actual).isNotNull();
    assertThat(actual.getConnectionId()).isEqualTo(unknownConnectionId);
    assertThat(actual.getReason()).isEqualTo("Connection 'unknown' is unknown");
  }

  @Test
  void testCheckNewSqConnection() throws ExecutionException, InterruptedException {
    var serverUrl = mockWebServerExtension.url("/");
    SonarLintExtendedLanguageServer.ConnectionCheckParams testParams = new SonarLintExtendedLanguageServer.ConnectionCheckParams(TOKEN, null, serverUrl);
    CompletableFuture<SonarLintExtendedLanguageClient.ConnectionCheckResult> result = lsProxy.checkConnection(testParams);

    SonarLintExtendedLanguageClient.ConnectionCheckResult actual = result.get();
    assertThat(actual).isNotNull();
    assertThat(actual.getConnectionId()).isEqualTo(serverUrl);
    assertThat(actual.isSuccess()).isTrue();
  }

  @Test
  void testCheckConnectionWithKnownConnection() throws ExecutionException, InterruptedException {
    SonarLintExtendedLanguageServer.ConnectionCheckParams testParams = new SonarLintExtendedLanguageServer.ConnectionCheckParams(CONNECTION_ID);
    CompletableFuture<SonarLintExtendedLanguageClient.ConnectionCheckResult> result = lsProxy.checkConnection(testParams);

    SonarLintExtendedLanguageClient.ConnectionCheckResult actual = result.get();
    assertThat(actual).isNotNull();
    assertThat(actual.getConnectionId()).isEqualTo(CONNECTION_ID);
    assertThat(actual.isSuccess()).isTrue();
  }

  @Test
  void testSetConnectionIdInCheckConnectionParams() {
    String OLD = "old";
    String NEW = "new";
    SonarLintExtendedLanguageServer.ConnectionCheckParams connectionCheckParams = new SonarLintExtendedLanguageServer.ConnectionCheckParams(OLD);
    connectionCheckParams.setConnectionId(NEW);

    assertThat(connectionCheckParams.getConnectionId()).isEqualTo(NEW);
  }

  @Test
  void shouldUpdateConfigurationOnTokenChange() {
    lsProxy.onTokenUpdate(new SonarLintExtendedLanguageServer.OnTokenUpdateNotificationParams("connectionId", "123456"));

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Info] Updating credentials on token change."));
    client.logs.clear();
  }

  @Test
  void shouldSetConnectionIdForGetRemoteProjectsParams() {
    String OLD = "old";
    String NEW = "new";

    SonarLintExtendedLanguageServer.GetRemoteProjectsForConnectionParams testParams = new SonarLintExtendedLanguageServer.GetRemoteProjectsForConnectionParams(OLD);
    testParams.setConnectionId(NEW);

    assertThat(testParams.getConnectionId()).isEqualTo(NEW);
  }

  @Test
  void test_open_rule_desc_for_file_without_workspace() throws Exception {
    var uri = getUri("analyzeSimplePythonFileOnOpen.py", analysisDir);

    didOpen(uri, "python", "def foo():\n  print 'toto'\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .hasSize(1));
    var issueId = ((JsonObject) client.getDiagnostics(uri).get(0).getData()).get("entryKey");

    client.showRuleDescriptionLatch = new CountDownLatch(1);
    lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("SonarLint.ShowIssueDetailsCodeAction", List.of(issueId, uri))).get();

    assertTrue(client.showRuleDescriptionLatch.await(1, TimeUnit.MINUTES));

    var ruleDescriptionTabNonContextual = client.ruleDesc.getHtmlDescriptionTabs()[0].getRuleDescriptionTabNonContextual();
    var htmlContent = ruleDescriptionTabNonContextual != null ? ruleDescriptionTabNonContextual.getHtmlContent() : "";
    assertThat(client.ruleDesc.getKey()).isEqualTo("python:PrintStatementUsage");
    assertThat(client.ruleDesc.getName()).isEqualTo("The \"print\" statement should not be used");
    assertThat(htmlContent).contains("<p>The <code>print</code> statement was removed in Python 3.0.");
    assertThat(client.ruleDesc.getCleanCodeAttribute()).isEqualTo("Not conventional");
    assertThat(client.ruleDesc.getCleanCodeAttributeCategory()).isEqualTo("Consistency");
    assertThat(client.ruleDesc.getImpacts()).containsEntry("Maintainability", "Medium");
  }

  @Test
  void openHotspotInBrowserShouldLogIfWorkspaceNotFound() {
    lsProxy.openHotspotInBrowser(new SonarLintExtendedLanguageServer.OpenHotspotInBrowserLsParams("id", "/workspace"));

    assertLogContains("Can't find workspace folder for file /workspace during attempt to open hotspot in browser.");
  }

  @Test
  void helpAndFeedbackLinkClickedNotificationShouldCallTelemetry() {
    SonarLintExtendedLanguageServer.HelpAndFeedbackLinkClickedNotificationParams params = new SonarLintExtendedLanguageServer.HelpAndFeedbackLinkClickedNotificationParams("faq");
    var result = lsProxy.helpAndFeedbackLinkClicked(params);

    assertThat(result).isNull();
  }

  @Test
  void fixSuggestionResolvedShouldCallTelemetry() {
    var params = new SonarLintExtendedLanguageServer.FixSuggestionResolvedParams(UUID.randomUUID().toString(), true);
    var result = lsProxy.fixSuggestionResolved(params);

    assertThat(result).isNull();
  }

  @Test
  void getFilePatternsForAnalysis() throws ExecutionException, InterruptedException {
    var result = lsProxy.getFilePatternsForAnalysis(new SonarLintExtendedLanguageServer.UriParams("notBound")).get();

    assertThat(result.getPatterns()).containsExactlyInAnyOrder("**/*.c",
      "**/*.h",
      "**/*.cc",
      "**/*.cpp",
      "**/*.cxx",
      "**/*.c++",
      "**/*.hh",
      "**/*.hpp",
      "**/*.hxx",
      "**/*.h++",
      "**/*.ipp",
      "**/*.cs",
      "**/*.css",
      "**/*.less",
      "**/*.scss",
      "**/*.html",
      "**/*.xhtml",
      "**/*.cshtml",
      "**/*.vbhtml",
      "**/*.aspx",
      "**/*.ascx",
      "**/*.rhtml",
      "**/*.erb",
      "**/*.shtm",
      "**/*.shtml",
      "**/*.ipynb",
      "**/*.java",
      "**/*.jav",
      "**/*.js",
      "**/*.jsx",
      "**/*.vue",
      "**/*.php",
      "**/*.php3",
      "**/*.php4",
      "**/*.php5",
      "**/*.phtml",
      "**/*.inc",
      "**/*.py",
      "**/*.ts",
      "**/*.tsx",
      "**/*.xml",
      "**/*.xsd",
      "**/*.xsl",
      "**/*.yml",
      "**/*.yaml",
      "**/*.go",
      "**/*.tf",
      "**/*.bicep",
      "**/*.json");
  }

  @Test
  void shouldRespectAnalysisExcludes() {
    var fileName = "analyseOpenFileIgnoringExcludes.py";
    var fileUri = analysisDir.resolve(fileName).toUri().toString();
    client.shouldAnalyseFile = false;

    didOpen(fileUri, "py", "def foo():\n  toto = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri)).isEmpty());
  }

  @Test
  void analyseOpenFileIgnoringExcludes() {
    var fileName = "analyseOpenFileIgnoringExcludes.py";
    var fileUri = analysisDir.resolve(fileName).toUri().toString();

    lsProxy.analyseOpenFileIgnoringExcludes(new SonarLintExtendedLanguageServer.AnalyseOpenFileIgnoringExcludesParams(
      new TextDocumentItem(fileUri, "py", 1, "def foo():\n  toto = 0\n"), null, null, Collections.emptyList()));

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning)));
  }

  @Test
  void change_issue_status_permission_check_not_bound() throws ExecutionException, InterruptedException {
    var response = lsProxy.checkIssueStatusChangePermitted(new SonarLintExtendedLanguageServer.CheckIssueStatusChangePermittedParams(temp.toUri().toString(), "key")).get();

    awaitUntilAsserted(() -> {
      assertFalse(response.isPermitted());
      assertThat(response.getNotPermittedReason()).isEqualTo("There is no binding for the folder: " + temp.toUri());
      assertThat(response.getAllowedStatuses()).isEmpty();
    });
  }

  @Override
  protected void setUpFolderSettings(Map<String, Map<String, Object>> folderSettings) {
    addSonarQubeConnection(client.globalSettings, CONNECTION_ID, mockWebServerExtension.url("/"), TOKEN);
  }

  private Predicate<? super MessageParams> notFromContextualTSserver() {
    return m -> !m.getMessage().contains("SonarTS") && !m.getMessage().contains("Using typescript at");
  }

}
