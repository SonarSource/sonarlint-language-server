/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
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

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import mockwebserver3.MockResponse;
import okio.Buffer;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.scanner.protocol.Constants.Severity;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.ProjectBranches;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Qualityprofiles;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Settings;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.GetRemoteProjectsNamesParams;
import org.sonarsource.sonarlint.ls.util.Utils;
import testutils.MockWebServerExtension;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ConnectedModeMediumTests extends AbstractLanguageServerMediumTests {

  private static final String QPROFILE_KEY = "AXDEr5Q7LjElHiH99ZhW";
  private static final String PYTHON_S1481 = "python:S1481";
  private static final String PYTHON_S1313 = "python:S1313";
  private static final String PROJECT_KEY = "myProject";
  public static final String LANGUAGES_LIST = "apex,c,cpp,css,web,java,js,php,plsql,py,secrets,ts,xml,yaml,go,cloudformation,docker,kubernetes,terraform";

  @RegisterExtension
  private final MockWebServerExtension mockWebServerExtension = new MockWebServerExtension();

  private static final String CONNECTION_ID = "mediumTests";

  private static final String PROJECT_KEY1 = "project:key1";
  private static final String PROJECT_NAME1 = "Project One";
  private static final String PROJECT_KEY2 = "project:key2";
  private static final String PROJECT_NAME2 = "Project Two";
  private static final long CURRENT_TIME = System.currentTimeMillis();
  private static Path folder1BaseDir;

  @BeforeAll
  public static void initialize() throws Exception {
    folder1BaseDir = makeStaticTempDir();
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
      "{\"plugins\":[{\"key\": \"python\", \"hash\": \"ignored\", \"filename\": \"sonarpython.jar\", \"sonarLintSupported\": true}]}");
    mockWebServerExtension.addResponse("/api/plugins/download?plugin=python", new MockResponse().setBody(safeGetSonarPython()));
    mockWebServerExtension.addProtobufResponse("/api/settings/values.protobuf?component=myProject", Settings.Values.newBuilder().build());
    mockWebServerExtension.addProtobufResponse("/api/qualityprofiles/search.protobuf?project=myProject", Qualityprofiles.SearchWsResponse.newBuilder()
      .addProfiles(Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder()
        .setKey(QPROFILE_KEY)
        .setLanguage("py")
        .setRulesUpdatedAt("2022-03-14T11:13:26+0000")
        .build())
      .build());
    Rules.Actives.Builder activeBuilder = Rules.Actives.newBuilder();
    activeBuilder.putActives(PYTHON_S1481, Rules.ActiveList.newBuilder().addActiveList(Rules.Active.newBuilder().setSeverity("BLOCKER")).build());
    activeBuilder.putActives(PYTHON_S1313, Rules.ActiveList.newBuilder().addActiveList(Rules.Active.newBuilder().setSeverity("MINOR")).build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/search.protobuf?qprofile=" + QPROFILE_KEY + "&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY,SECURITY_HOTSPOT&s=key&ps=500&p=1",
      Rules.SearchResponse.newBuilder()
        .setActives(activeBuilder.build())
        .setTotal(2)
        .addRules(Rules.Rule.newBuilder()
          .setKey(PYTHON_S1481)
          .setLang("py")
          .build())
        .addRules(Rules.Rule.newBuilder()
          .setKey(PYTHON_S1313)
          .setLang("py")
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

  @NotNull
  private static Buffer safeGetSonarPython() {
    try (var buffer = new Buffer().readFrom(new FileInputStream(fullPathToJar("sonarpython")))) {
      return buffer;
    } catch (IOException ioEx) {
      throw new IllegalStateException(ioEx);
    }
  }

  @Override
  protected void setUpFolderSettings(Map<String, Map<String, Object>> folderSettings) {
    setShowVerboseLogs(client.globalSettings, true);
    setShowAnalyzerLogs(client.globalSettings, true);
    addSonarQubeConnection(client.globalSettings, CONNECTION_ID, mockWebServerExtension.url("/"), "xxxxx");
    String folderUri = folder1BaseDir.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);
    client.readyForTestsLatch = new CountDownLatch(1);
  }

  @Override
  protected void verifyConfigurationChangeOnClient() {
    try {
      assertTrue(client.readyForTestsLatch.await(15, SECONDS));
    } catch (InterruptedException e) {
      fail(e);
    }
  }

  @AfterAll
  public static void cleanUp() {
    FileUtils.deleteQuietly(folder1BaseDir.toFile());
  }

  @Test
  void analysisConnected_find_hotspot() {
    mockNoIssuesNoHotspotsForProject();

    var uriInFolder = folder1BaseDir.resolve("hotspot.py").toUri().toString();
    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "sonarlint", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.", DiagnosticSeverity.Information)));
  }

  @Test
  void analysisConnected_no_matching_server_issues() {
    mockWebServerExtension.addProtobufResponseDelimited(
      "/batch/issues?key=myProject%3AinFolder.py",
      ScannerInput.ServerIssue.newBuilder()
        .setKey("xyz")
        .setRuleRepository("python")
        .setRuleKey("S1482") // Different rule key -> no match
        .setMsg("Remove the declaration of the unused 'toto' variable.")
        .setSeverity(Severity.INFO)
        .setManualSeverity(true)
        .setPath("inFolder.py")
        .build());

    var uriInFolder = folder1BaseDir.resolve("inFolder.py").toUri().toString();
    didOpen(uriInFolder, "python", "def foo():\n  toto = 0\n  plouf = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));
  }

  @Test
  void analysisConnected_matching_server_issues() {
    mockWebServerExtension.addProtobufResponseDelimited(
      "/batch/issues?key=myProject%3AinFolder.py&branch=master",
      ScannerInput.ServerIssue.newBuilder()
        .setKey("xyz")
        .setRuleRepository("python")
        .setRuleKey("S1481")
        .setType(RuleType.BUG.name())
        .setMsg("Remove the unused local variable \"toto\".")
        .setSeverity(Severity.INFO)
        .setManualSeverity(true)
        .setPath("inFolder.py")
        .setLine(2)
        .build());

    var uriInFolder = folder1BaseDir.resolve("inFolder.py").toUri().toString();
    didOpen(uriInFolder, "python", "def foo():\n  toto = 0\n  plouf = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Hint),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));
  }

  @Test
  void analysisConnected_matching_server_issues_on_sq_with_pull_issues() {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"9.6\", \"id\": \"xzy\"}");

    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Issues.IssuesPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(System.currentTimeMillis())
        .build(),
      // no user-overridden severity
      Issues.IssueLite.newBuilder()
        .setKey("xyz")
        .setRuleKey(PYTHON_S1481)
        .setType(Common.RuleType.BUG)
        .setMainLocation(Issues.Location.newBuilder()
          .setFilePath("inFolder.py")
          .setMessage("Remove the declaration of the unused 'toto' variable.")
          .setTextRange(Issues.TextRange.newBuilder()
            .setStartLine(1)
            .setStartLineOffset(2)
            .setEndLine(1)
            .setEndLineOffset(6)
            .setHash(Utils.hash("toto"))
            .build())
          .build())
        .build());
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull_taint?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Issues.TaintVulnerabilityPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(System.currentTimeMillis())
        .build());

    var uriInFolder = folder1BaseDir.resolve("inFolder.py").toUri().toString();
    didOpen(uriInFolder, "python", "def foo():\n  toto = 0\n  plouf = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));
  }

  @Test
  void shouldGetServerNamesForConnection() {
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

  @Test
  void openHotspotInBrowserShouldLogIfBranchNotFound() {
    lsProxy.openHotspotInBrowser(new SonarLintExtendedLanguageServer.OpenHotspotInBrowserLsParams("id", folder1BaseDir.toUri().toString()));

    assertLogContains("Can't find branch for workspace folder " + folder1BaseDir.toUri().getPath()
      + " during attempt to open hotspot in browser.");
  }

  @Test
  void showHotspotLocations() {
    var testParams = new SonarLintExtendedLanguageServer.ShowHotspotLocationsParams("hotspotKey");

    var future = lsProxy.showHotspotLocations(testParams);

    awaitUntilAsserted(() -> assertThat(future.isDone()).isTrue());
  }

  private String stripTrailingSlash(String url) {
    if (url.endsWith("/")) {
      return url.substring(0, url.length() - 1);
    }
    return url;
  }

  private void mockNoIssuesNoHotspotsForProject() {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"9.7\", \"id\": \"xzy\"}");
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Issues.IssuesPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(CURRENT_TIME)
        .build());
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST + "&changedSince=" + CURRENT_TIME,
      Issues.IssuesPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(CURRENT_TIME)
        .build());
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull_taint?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Issues.TaintVulnerabilityPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(CURRENT_TIME)
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=hotspot.py&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build()
    );
  }

}
