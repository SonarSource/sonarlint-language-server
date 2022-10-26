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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.scanner.protocol.Constants.Severity;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.ProjectBranches;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Qualityprofiles;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Settings;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.GetRemoteProjectsNamesParams;
import org.sonarsource.sonarlint.ls.util.Utils;
import testutils.MockWebServerExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class ConnectedModeMediumTests extends AbstractLanguageServerMediumTests {

  private static final String QPROFILE_KEY = "AXDEr5Q7LjElHiH99ZhW";
  private static final String JAVASCRIPT_S1481 = "javascript:S1481";

  @RegisterExtension
  private final MockWebServerExtension mockWebServerExtension = new MockWebServerExtension();

  private static final String CONNECTION_ID = "mediumTests";

  private static final String PROJECT_KEY1 = "project:key1";
  private static final String PROJECT_NAME1 = "Project One";
  private static final String PROJECT_KEY2 = "project:key2";
  private static final String PROJECT_NAME2 = "Project Two";
  @TempDir
  public static Path folder1BaseDir;

  @BeforeAll
  public static void initialize() throws Exception {

    initialize(Map.of(
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1"), new WorkspaceFolder(folder1BaseDir.toUri().toString(), "My Folder 1"));
  }

  @BeforeEach
  public void mockSonarQube() {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"9.3\", \"id\": \"xzy\"}");
    mockWebServerExtension.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1", Components.SearchWsResponse.newBuilder()
        .addComponents(Components.Component.newBuilder().setKey(PROJECT_KEY1).setName(PROJECT_NAME1).build())
        .addComponents(Components.Component.newBuilder().setKey(PROJECT_KEY2).setName(PROJECT_NAME2).build())
        .setPaging(Common.Paging.newBuilder().setTotal(2).build())
      .build());
    mockWebServerExtension.addProtobufResponse("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=myProject&ps=500&p=1", Components.TreeWsResponse.newBuilder().build());
    mockWebServerExtension.addStringResponse("/api/plugins/installed",
      "{\"plugins\":[{\"key\": \"javascript\", \"hash\": \"not_used\", \"filename\": \"not_used\", \"sonarLintSupported\": true}]}");
    mockWebServerExtension.addProtobufResponse("/api/settings/values.protobuf?component=myProject", Settings.Values.newBuilder().build());
    mockWebServerExtension.addProtobufResponse("/api/qualityprofiles/search.protobuf?project=myProject", Qualityprofiles.SearchWsResponse.newBuilder()
      .addProfiles(Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder()
        .setKey(QPROFILE_KEY)
        .setLanguage("js")
        .setRulesUpdatedAt("2022-03-14T11:13:26+0000")
        .build())
      .build());
    Rules.Actives.Builder activeBuilder = Rules.Actives.newBuilder();
    activeBuilder.putActives(JAVASCRIPT_S1481, Rules.ActiveList.newBuilder().addActiveList(Rules.Active.newBuilder().setSeverity("BLOCKER")).build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/search.protobuf?qprofile=" + QPROFILE_KEY + "&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY&s=key&ps=500&p=1",
      Rules.SearchResponse.newBuilder()
        .setActives(activeBuilder.build())
        .setTotal(1)
        .addRules(Rules.Rule.newBuilder()
          .setKey(JAVASCRIPT_S1481)
          .setLang("js")
          .build())
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/project_branches/list.protobuf?project=myProject",
      ProjectBranches.ListWsResponse.newBuilder()
        .addBranches(ProjectBranches.Branch.newBuilder()
          .setName("master")
          .setIsMain(true)
          .setType(Common.BranchType.BRANCH)
          .build())
        .build());
  }

  @Override
  protected void setUpFolderSettings(Map<String, Map<String, Object>> folderSettings) {
    setShowVerboseLogs(client.globalSettings, true);
    setShowAnalyzerLogs(client.globalSettings, true);
    addSonarQubeConnection(client.globalSettings, CONNECTION_ID, mockWebServerExtension.url("/"), "xxxxx");
    bindProject(getFolderSettings(folder1BaseDir.toUri().toString()), CONNECTION_ID, "myProject");
  }

  @Test
  void analysisConnected_no_matching_server_issues() throws Exception {
    assertLogContains("Enabling notifications for project 'myProject' on connection 'mediumTests'");

    mockWebServerExtension.addProtobufResponseDelimited(
      "/batch/issues?key=myProject%3AinFolder.js",
      ScannerInput.ServerIssue.newBuilder()
        .setKey("xyz")
        .setRuleRepository("javascript")
        .setRuleKey("S1482") // Different rule key -> no match
        .setMsg("Remove the declaration of the unused 'toto' variable.")
        .setSeverity(Severity.INFO)
        .setManualSeverity(true)
        .setPath("inFolder.js")
        .build());

    var uriInFolder = folder1BaseDir.resolve("inFolder.js").toUri().toString();
    didOpen(uriInFolder, "javascript", "function foo() {\n  var toto = 0;\n  var plouf = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 6, 1, 10, JAVASCRIPT_S1481, "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Warning),
        tuple(2, 6, 2, 11, JAVASCRIPT_S1481, "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analysisConnected_matching_server_issues() throws Exception {
    assertLogContains("Enabling notifications for project 'myProject' on connection 'mediumTests'");

    mockWebServerExtension.addProtobufResponseDelimited(
      "/batch/issues?key=myProject%3AinFolder.js&branch=master",
      ScannerInput.ServerIssue.newBuilder()
        .setKey("xyz")
        .setRuleRepository("javascript")
        .setRuleKey("S1481")
        .setType(RuleType.BUG.name())
        .setMsg("Remove the declaration of the unused 'toto' variable.")
        .setSeverity(Severity.INFO)
        .setManualSeverity(true)
        .setPath("inFolder.js")
        .build());

    var uriInFolder = folder1BaseDir.resolve("inFolder.js").toUri().toString();
    didOpen(uriInFolder, "javascript", "function foo() {\n  var toto = 0;\n  var plouf = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 6, 1, 10, JAVASCRIPT_S1481, "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Hint),
        tuple(2, 6, 2, 11, JAVASCRIPT_S1481, "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analysisConnected_matching_server_issues_on_sq_with_pull_issues() throws Exception {
    assertLogContains("Enabling notifications for project 'myProject' on connection 'mediumTests'");
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"9.6\", \"id\": \"xzy\"}");

    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull?projectKey=myProject&branchName=master&languages=apex,c,cpp,web,java,js,php,plsql,py,secrets,ts,xml,yaml",
      Issues.IssuesPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(System.currentTimeMillis())
        .build(),
      // no user-overridden severity
      Issues.IssueLite.newBuilder()
        .setKey("xyz")
        .setRuleKey(JAVASCRIPT_S1481)
        .setType(Common.RuleType.BUG)
        .setMainLocation(Issues.Location.newBuilder()
          .setFilePath("inFolder.js")
          .setMessage("Remove the declaration of the unused 'toto' variable.")
          .setTextRange(Issues.TextRange.newBuilder()
            .setStartLine(1)
            .setStartLineOffset(6)
            .setEndLine(1)
            .setEndLineOffset(10)
            .setHash(Utils.hash("toto"))
            .build())
          .build())
        .build());
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull_taint?projectKey=myProject&branchName=master&languages=apex,c,cpp,web,java,js,php,plsql,py,secrets,ts,xml,yaml",
      Issues.TaintVulnerabilityPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(System.currentTimeMillis())
        .build());

    var uriInFolder = folder1BaseDir.resolve("inFolder.js").toUri().toString();
    didOpen(uriInFolder, "javascript", "function foo() {\n  var toto = 0;\n  var plouf = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 6, 1, 10, JAVASCRIPT_S1481, "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Warning),
        tuple(2, 6, 2, 11, JAVASCRIPT_S1481, "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Warning)));
  }

  @Test
  void shouldGetServerNamesForConnection() {
    assertLogContains("Enabling notifications for project 'myProject' on connection 'mediumTests'");

    // Trigger a binding update to fetch projects in connected mode storage
    ExecuteCommandParams updateBindings = new ExecuteCommandParams();
    updateBindings.setCommand("SonarLint.UpdateAllBindings");
    lsProxy.getWorkspaceService().executeCommand(updateBindings);

    var params = new GetRemoteProjectsNamesParams(CONNECTION_ID, List.of(PROJECT_KEY1, "unknown"));

    // Update of storage is asynchronous, eventually we get the right data in the storage :)
    awaitUntilAsserted(() -> assertThat(lsProxy.getRemoteProjectNames(params).get()).containsExactly(Map.entry(PROJECT_KEY1, PROJECT_NAME1)));
  }

  @Test
  void shouldThrowGettingServerNamesForUnknownConnection() {
    assertLogContains("Enabling notifications for project 'myProject' on connection 'mediumTests'");

    var params = new GetRemoteProjectsNamesParams("unknown connection", List.of("unknown-project"));

    var future = lsProxy.getRemoteProjectNames(params);
    awaitUntilAsserted(() -> assertThat(future).isCompletedExceptionally());
  }

  @Test
  void shouldReturnRemoteProjectsForKnownConnection() throws ExecutionException, InterruptedException {
    SonarLintExtendedLanguageServer.GetRemoteProjectsForConnectionParams testParams = new SonarLintExtendedLanguageServer.GetRemoteProjectsForConnectionParams(CONNECTION_ID);
    var result = lsProxy.getRemoteProjectsForConnection(testParams);

    var actual = result.get();
    awaitUntilAsserted(() -> assertThat(actual)
                                      .isNotNull()
                                      .hasSize(2)
                                      .containsKey(PROJECT_KEY1)
                                      .containsKey(PROJECT_KEY2)
                                      .containsValue(PROJECT_NAME1)
                                      .containsValue(PROJECT_NAME2));
  }

  @Test
  void shouldThrowExceptionForUnknownConnection() {
    SonarLintExtendedLanguageServer.GetRemoteProjectsForConnectionParams testParams = new SonarLintExtendedLanguageServer.GetRemoteProjectsForConnectionParams("random_string");
    var future = lsProxy.getRemoteProjectsForConnection(testParams);

    awaitUntilAsserted(() -> assertThat(future).isCompletedExceptionally());
  }

  @Test
  void shouldGetTokenGenerationServerPath() throws ExecutionException, InterruptedException {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"9.7\", \"id\": \"xzy\"}");
    var serverUrl = mockWebServerExtension.url("");
    var cleanUrl = stripTrailingSlash(serverUrl);
    var params = new SonarLintExtendedLanguageServer.GetServerPathForTokenGenerationParams(cleanUrl);

    var result = lsProxy.getServerPathForTokenGeneration(params);
    var actual = result.get();

    assertThat(actual.getServerUrl())
      // Local port range is dynamic in range [64120-64130]
      .startsWith(cleanUrl + "/sonarlint/auth?ideName=SonarLint+LS+Medium+tests&port=641");
  }

  @Test
  void shouldGetTokenGenerationServerPathOld() throws ExecutionException, InterruptedException {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"9.6\", \"id\": \"xzy\"}");
    var serverUrl = mockWebServerExtension.url("");
    var cleanUrl = stripTrailingSlash(serverUrl);
    var params = new SonarLintExtendedLanguageServer.GetServerPathForTokenGenerationParams(cleanUrl);

    var result = lsProxy.getServerPathForTokenGeneration(params);
    var actual = result.get();

    assertThat(actual.getServerUrl()).isEqualTo(cleanUrl + "/account/security");
  }

  @Test
  void shouldReturnErrorForInvalidUrl() {
    var params = new SonarLintExtendedLanguageServer.GetServerPathForTokenGenerationParams("invalid/url");

    var result = lsProxy.getServerPathForTokenGeneration(params);

    assertThatThrownBy(result::get).hasMessage("org.eclipse.lsp4j.jsonrpc.ResponseErrorException: Internal error.");
  }

  private String stripTrailingSlash(String url) {
    if (url.endsWith("/")) {
      return url.substring(0, url.length() - 1);
    }
    return url;
  }

}
