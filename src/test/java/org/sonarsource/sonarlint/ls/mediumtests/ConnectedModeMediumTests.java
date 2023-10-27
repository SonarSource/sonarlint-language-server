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

import com.google.gson.JsonObject;
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
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.DateUtils;
import org.sonar.scanner.protocol.Constants.Severity;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.commons.Language;
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
import static org.sonar.api.rules.RuleType.SECURITY_HOTSPOT;

class ConnectedModeMediumTests extends AbstractLanguageServerMediumTests {

  private static final String QPROFILE_KEY = "AXDEr5Q7LjElHiH99ZhW";
  private static final String PYTHON_S1481 = "python:S1481";
  private static final String PYTHON_S1313 = "python:S1313";
  private static final String PROJECT_KEY = "myProject";
  public static final String LANGUAGES_LIST = "apex,c,cpp,cs,css,cobol,web,java,js,php,plsql,py,secrets,ts,xml,yaml,go,cloudformation,docker,kubernetes,terraform";

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
    mockWebServerExtension.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1",
      Components.SearchWsResponse.newBuilder()
        .addComponents(Components.Component.newBuilder().setKey(PROJECT_KEY1).setName(PROJECT_NAME1).build())
        .addComponents(Components.Component.newBuilder().setKey(PROJECT_KEY2).setName(PROJECT_NAME2).build())
        .setPaging(Common.Paging.newBuilder().setTotal(2).build())
        .build());
    mockWebServerExtension.addProtobufResponse("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=myProject&ps=500&p=1",
      Components.TreeWsResponse.newBuilder().build());
    mockWebServerExtension.addStringResponse("/api/plugins/installed",
      "{\"plugins\":[{\"key\": \"python\", \"hash\": \"ignored\", \"filename\": \"sonarpython.jar\", \"sonarLintSupported\": true}]}");
    mockWebServerExtension.addResponse("/api/plugins/download?plugin=python", new MockResponse().setBody(safeGetSonarPython()));
    mockWebServerExtension.addProtobufResponse("/api/settings/values.protobuf?component=myProject", Settings.ValuesWsResponse.newBuilder()
      .addSettings(Settings.Setting.newBuilder()
        .setKey("sonar.cs.file.suffixes")
        .setValue(".cs,.razor"))
      .build());
    mockWebServerExtension.addProtobufResponse("/api/rules/search.protobuf?repositories=roslyn.sonaranalyzer.security.cs,javasecurity," +
        "jssecurity,phpsecurity,pythonsecurity,tssecurity&f=repo&s=key&ps=500&p=1",
      Rules.SearchResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponse("/api/qualityprofiles/search.protobuf?project=myProject",
      Qualityprofiles.SearchWsResponse.newBuilder()
        .addProfiles(Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder()
          .setKey(QPROFILE_KEY)
          .setLanguage("py")
          .setRulesUpdatedAt("2022-03-14T11:13:26+0000")
          .build())
        .build());
    Rules.Actives.Builder activeBuilder = Rules.Actives.newBuilder();
    activeBuilder.putActives(PYTHON_S1481,
      Rules.ActiveList.newBuilder().addActiveList(Rules.Active.newBuilder().setSeverity("BLOCKER")).build());
    activeBuilder.putActives(PYTHON_S1313,
      Rules.ActiveList.newBuilder().addActiveList(Rules.Active.newBuilder().setSeverity("MINOR")).build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/search.protobuf?qprofile=" + QPROFILE_KEY + "&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY," +
        "SECURITY_HOTSPOT&s=key&ps=500&p=1",
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
    var folderUri = folder1BaseDir.toUri().toString();
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
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "sonarlint", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.",
          DiagnosticSeverity.Warning)));
  }

  @Test
  void analysisConnected_find_tracked_hotspot_before_sq_10_1() {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"10.0\", \"id\": \"xzy\"}");
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
      "/api/rules/show.protobuf?key=python:S1313",
      Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder()
          .setSeverity("MINOR")
          .setType(Common.RuleType.SECURITY_HOTSPOT)
          .setLang(Language.PYTHON.getLanguageKey())
          .build())
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=hotspot.py&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder()
        .addHotspots(Hotspots.SearchWsResponse.Hotspot.newBuilder()
          .setKey("myhotspotkey")
          .setComponent("someComponentKey")
          .setCreationDate(DateUtils.formatDateTime(System.currentTimeMillis()))
          .setStatus("TO_REVIEW")
          .setVulnerabilityProbability("LOW")
          .setTextRange(Common.TextRange.newBuilder()
            .setStartLine(1)
            .setStartOffset(13)
            .setEndLine(1)
            .setEndOffset(26)
            .build()
          )
          .setRuleKey(PYTHON_S1313)
          .build()
        )
        .addComponents(Hotspots.Component.newBuilder()
          .setKey("someComponentKey")
          .setPath("hotspot.py")
          .build()
        )
        .setPaging(Common.Paging.newBuilder().setTotal(1).build())
        .build()
    );

    var uriInFolder = folder1BaseDir.resolve("hotspot.py").toUri().toString();
    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "remote", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.",
          DiagnosticSeverity.Warning)));
  }

  @Test
  void analysisConnected_find_tracked_hotspot_after_sq_10_1() {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"10.1\", \"id\": \"xzy\"}");
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
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull_taint?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST + "&changedSince=" + CURRENT_TIME,
      Issues.TaintVulnerabilityPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(CURRENT_TIME)
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=hotspot.py&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/show.protobuf?key=python:S1313",
      Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder()
          .setSeverity("MINOR")
          .setType(Common.RuleType.SECURITY_HOTSPOT)
          .setLang(Language.PYTHON.getLanguageKey())
          .build())
        .build());
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build(),
      Hotspots.HotspotLite.newBuilder()
        .setKey("myhotspotkey")
        .setFilePath("hotspot.py")
        .setCreationDate(System.currentTimeMillis())
        .setStatus("TO_REVIEW")
        .setVulnerabilityProbability("LOW")
        .setTextRange(Hotspots.TextRange.newBuilder()
          .setStartLine(1)
          .setStartLineOffset(13)
          .setEndLine(1)
          .setEndLineOffset(26)
          .setHash(Utils.hash("'12.34.56.78'"))
          .build()
        )
        .setRuleKey(PYTHON_S1313)
        .build()
    );

    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST + "&changedSince=" + CURRENT_TIME,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build(),
      Hotspots.HotspotLite.newBuilder()
        .setKey("myhotspotkey")
        .setFilePath("hotspot.py")
        .setCreationDate(System.currentTimeMillis())
        .setStatus("TO_REVIEW")
        .setVulnerabilityProbability("LOW")
        .setTextRange(Hotspots.TextRange.newBuilder()
          .setStartLine(1)
          .setStartLineOffset(13)
          .setEndLine(1)
          .setEndLineOffset(26)
          .setHash(Utils.hash("'12.34.56.78'"))
          .build()
        )
        .setRuleKey(PYTHON_S1313)
        .build()
    );

    var uriInFolder = folder1BaseDir.resolve("hotspot.py").toUri().toString();
    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "remote", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.",
          DiagnosticSeverity.Warning)));
  }

  @Test
  void analysisConnected_scan_all_hotspot_then_forget() {
    mockNoIssuesNoHotspotsForProject();

    var uri1InFolder = folder1BaseDir.resolve("hotspot1.py").toUri().toString();
    var doc1 = new TextDocumentItem();
    doc1.setUri(uri1InFolder);
    doc1.setText("def foo():\n  id_address = '12.34.56.78'\n");
    doc1.setVersion(0);
    doc1.setLanguageId("[unknown]");

    var uri2InFolder = folder1BaseDir.resolve("hotspot2.py").toUri().toString();
    var doc2 = new TextDocumentItem();
    doc2.setUri(uri2InFolder);
    doc2.setText("def foo():\n  id_address = '23.45.67.89'\n");
    doc2.setVersion(0);
    doc2.setLanguageId("[unknown]");

    List<TextDocumentItem> documents = List.of(doc1, doc2);
    var scanParams = new SonarLintExtendedLanguageServer.ScanFolderForHotspotsParams(folder1BaseDir.toUri().toString(), documents);

    lsProxy.scanFolderForHotspots(scanParams);

    awaitUntilAsserted(() -> {
      assertThat(client.getHotspots(uri1InFolder))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
          Diagnostic::getSeverity)
        .containsExactly(
          tuple(1, 15, 1, 28, PYTHON_S1313, "sonarlint", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.",
            DiagnosticSeverity.Warning));
      assertThat(client.getDiagnostics(uri1InFolder)).isEmpty();

      assertThat(client.getHotspots(uri2InFolder))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
          Diagnostic::getSeverity)
        .containsExactly(
          tuple(1, 15, 1, 28, PYTHON_S1313, "sonarlint", "Make sure using this hardcoded IP address \"23.45.67.89\" is safe here.",
            DiagnosticSeverity.Warning));
      assertThat(client.getDiagnostics(uri2InFolder)).isEmpty();
    });

    // Simulate that file 1 is open, should not be cleaned
    didOpen(uri1InFolder, "python", "def foo():\n  id_address = '12.34.56.78'\n");

    lsProxy.forgetFolderHotspots();

    awaitUntilAsserted(() -> {
      // File 1 is still open, keeping hotspots
      assertThat(client.getHotspots(uri1InFolder))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
          Diagnostic::getSeverity)
        .containsExactly(
          tuple(1, 15, 1, 28, PYTHON_S1313, "sonarlint", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.",
            DiagnosticSeverity.Warning));

      // File 2 is not open, cleaning hotspots
      assertThat(client.getHotspots(uri2InFolder)).isEmpty();
    });
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
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));
  }

  @Test
  void analysisConnected_matching_server_issues() throws Exception {
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
    mockWebServerExtension.addProtobufResponse(
      "/api/issues/search.protobuf?issues=xyz&additionalFields=transitions&ps=1&p=1",
      Issues.SearchWsResponse.newBuilder()
        .addIssues(Issues.Issue.newBuilder()
          .setKey("xyz")
          .setTransitions(Issues.Transitions.newBuilder()
            .addAllTransitions(List.of("wontfix", "falsepositive"))
            .build())
          .build())
        .build());
    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folder1BaseDir.toUri().toString()
      , "master"));

    var uriInFolder = folder1BaseDir.resolve("inFolder.py").toUri().toString();
    didOpen(uriInFolder, "python", "def foo():\n  toto = 0\n  plouf = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));

    var firstDiagnostic = client.getDiagnostics(uriInFolder).get(0);
    var codeActionParams = new CodeActionParams();
    codeActionParams.setTextDocument(new TextDocumentIdentifier(uriInFolder));
    codeActionParams.setRange(firstDiagnostic.getRange());
    codeActionParams.setContext(new CodeActionContext(List.of(firstDiagnostic)));

    var codeActions = lsProxy.getTextDocumentService().codeAction(codeActionParams).get();
    assertThat(codeActions).hasSize(2)
      .extracting(Either::getRight)
      .extracting(CodeAction::getCommand)
      .extracting(Command::getCommand)
      .containsExactlyInAnyOrder("SonarLint.ResolveIssue", "SonarLint.OpenRuleDescCodeAction");
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
          .setFilePath("pythonFile.py")
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

    var uriInFolder = folder1BaseDir.resolve("pythonFile.py").toUri().toString();
    didOpen(uriInFolder, "python", "def foo():\n  toto = 0\n  plouf = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
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
    awaitUntilAsserted(() -> assertThat(lsProxy.getRemoteProjectNames(params).get()).containsExactly(Map.entry(PROJECT_KEY1,
      PROJECT_NAME1)));
  }

  @Test
  void shouldThrowGettingServerNamesForUnknownConnection() {
    var params = new GetRemoteProjectsNamesParams("unknown connection", List.of("unknown-project"));

    var future = lsProxy.getRemoteProjectNames(params);
    awaitUntilAsserted(() -> assertThat(future).isCompletedExceptionally());
  }

  @Test
  void shouldReturnRemoteProjectsForKnownConnection() throws ExecutionException, InterruptedException {
    SonarLintExtendedLanguageServer.GetRemoteProjectsForConnectionParams testParams =
      new SonarLintExtendedLanguageServer.GetRemoteProjectsForConnectionParams(CONNECTION_ID);
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
    SonarLintExtendedLanguageServer.GetRemoteProjectsForConnectionParams testParams =
      new SonarLintExtendedLanguageServer.GetRemoteProjectsForConnectionParams("random_string");
    var future = lsProxy.getRemoteProjectsForConnection(testParams);

    awaitUntilAsserted(() -> assertThat(future).isCompletedExceptionally());
  }

  @Test
  void shouldReturnErrorForInvalidUrl() {
    var params = new SonarLintExtendedLanguageServer.GenerateTokenParams("invalid/url");

    var result = lsProxy.generateToken(params);

    assertThatThrownBy(result::get).hasMessage("org.eclipse.lsp4j.jsonrpc.ResponseErrorException: Internal error.");
  }

  @Test
  void openHotspotInBrowserShouldLogIfBranchNotFound() {
    lsProxy.openHotspotInBrowser(new SonarLintExtendedLanguageServer.OpenHotspotInBrowserLsParams("id", folder1BaseDir.toUri().toString()));

    assertLogContains("Can't find branch for workspace folder " + folder1BaseDir.toUri().getPath()
      + " during attempt to open hotspot in browser.");
  }

  @Test
  void shouldOpenHotspotDescription() {
    mockNoIssuesNoHotspotsForProject();
    var uriInFolder = folder1BaseDir.resolve("hotspot.py").toUri().toString();
    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");
    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder)).hasSize(1));

    var diagnostic = client.getHotspots(uriInFolder).get(0);
    var hotspotId = ((JsonObject) diagnostic.getData()).get("entryKey").getAsString();
    var ruleKey = diagnostic.getCode().getLeft();
    var params = new SonarLintExtendedLanguageServer.ShowHotspotRuleDescriptionParams(ruleKey, hotspotId);
    params.setFileUri(uriInFolder);

    assertThat(lsProxy.showHotspotRuleDescription(params)).isNull();
    awaitUntilAsserted(() -> {
      assertThat(client.ruleDesc).isNotNull();
      assertThat(client.ruleDesc.getType()).isEqualTo(SECURITY_HOTSPOT.toString());
      assertThat(client.ruleDesc.getKey()).isEqualTo("python:S1313");
      assertThat(client.ruleDesc.getName()).isEqualTo("Using hardcoded IP addresses is security-sensitive");
    });
  }

  @Test
  void showHotspotLocations() {
    var testParams = new SonarLintExtendedLanguageServer.ShowHotspotLocationsParams("hotspotKey");

    var future = lsProxy.showHotspotLocations(testParams);

    awaitUntilAsserted(() -> assertThat(future.isDone()).isTrue());
  }

  @Test
  void shouldReturnHotspotDetails() {
    var testParams = new SonarLintExtendedLanguageServer.ShowHotspotRuleDescriptionParams("python:S930", "hotspotKey");
    testParams.setFileUri(folder1BaseDir.resolve("hotspot.py").toUri().toString());

    var result = lsProxy.getHotspotDetails(testParams);

    awaitUntilAsserted(() -> {
      assertTrue(result.isDone());
      assertThat(result.get().getLanguageKey()).isEqualTo("py");
      assertThat(result.get().getName()).isEqualTo("The number and name of arguments passed to a function should match its parameters");
    });
  }


  @Test
  void checkLocalDetectionSupportedNotBound() throws ExecutionException, InterruptedException {
    var result = lsProxy.checkLocalDetectionSupported(new SonarLintExtendedLanguageServer.UriParams("notBound")).get();

    assertThat(result.isSupported()).isFalse();
    assertThat(result.getReason()).isEqualTo("The provided configuration scope does not exist: notBound");
  }

  @Test
  void shouldChangeIssueStatus() {
    var issueKey = "qwerty";
    mockWebServerExtension.addProtobufResponseDelimited(
      "/batch/issues?key=myProject%3AchangeIssueStatus.py&branch=master",
      ScannerInput.ServerIssue.newBuilder()
        .setKey(issueKey)
        .setRuleRepository("python")
        .setRuleKey("S1481")
        .setType(RuleType.BUG.name())
        .setMsg("Remove the unused local variable \"toto\".")
        .setSeverity(Severity.INFO)
        .setManualSeverity(true)
        .setPath("changeIssueStatus.py")
        .setLine(2)
        .build());

    mockWebServerExtension.addResponse("/api/issues/do_transition", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addResponse("/api/issues/add_comment", new MockResponse().setResponseCode(200));

    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folder1BaseDir.toUri().toString(), "some/branch/name"));
    var fileUri = folder1BaseDir.resolve("changeIssueStatus.py").toUri().toString();
    var content = "def foo():\n  toto = 0\n  plouf = 0\n";
    didOpen(fileUri, "python", content);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));

    lsProxy.changeIssueStatus(new SonarLintExtendedLanguageServer.ChangeIssueStatusParams(folder1BaseDir.toUri().toString(), issueKey,
      ResolutionStatus.FALSE_POSITIVE, fileUri, "clever comment", false));

    //Now we expect that one issue is resolved
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));
    assertThat(client.shownMessages).contains(new MessageParams(MessageType.Info, "New comment was added"));
  }

  @Test
  void shouldNotChangeStatusWhenServerIsDown() throws IOException {
    var issueKey = "qwerty";
    mockWebServerExtension.addProtobufResponseDelimited(
      "/batch/issues?key=myProject%3AchangeIssueStatus.py&branch=master",
      ScannerInput.ServerIssue.newBuilder()
        .setKey(issueKey)
        .setRuleRepository("python")
        .setRuleKey("S1481")
        .setType(RuleType.BUG.name())
        .setMsg("Remove the unused local variable \"toto\".")
        .setSeverity(Severity.INFO)
        .setManualSeverity(true)
        .setPath("changeIssueStatus.py")
        .setLine(2)
        .build());
    mockWebServerExtension.addResponse("/api/issues/do_transition", new MockResponse().setResponseCode(200));

    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folder1BaseDir.toUri().toString(), "some/branch/name"));
    var fileUri = folder1BaseDir.resolve("changeIssueStatus.py").toUri().toString();
    var content = "def foo():\n  toto = 0\n  plouf = 0\n";
    didOpen(fileUri, "python", content);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri)).isNotEmpty());
    mockWebServerExtension.stopServer();
    lsProxy.changeIssueStatus(new SonarLintExtendedLanguageServer.ChangeIssueStatusParams(folder1BaseDir.toUri().toString(), issueKey,
      ResolutionStatus.FALSE_POSITIVE, fileUri, "comment", false));

    awaitUntilAsserted(() -> assertThat(client.shownMessages).isNotEmpty());
    assertThat(client.shownMessages)
      .contains(new MessageParams(MessageType.Error, "Could not change status for the issue. Look at the SonarLint output for details."));
  }

  @Test
  void change_hotspot_status_to_resolved() {
    var analyzedFileName = "hotspot_resolved.py";

    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"10.1\", \"id\": \"xzy\"}");
    mockWebServerExtension.addResponse("/api/hotspots/change_status", new MockResponse().setResponseCode(200));
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
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull_taint?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST + "&changedSince=" + CURRENT_TIME,
      Issues.TaintVulnerabilityPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(CURRENT_TIME)
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=" + analyzedFileName + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/show.protobuf?key=python:S1313",
      Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder()
          .setSeverity("MINOR")
          .setType(Common.RuleType.SECURITY_HOTSPOT)
          .setLang(Language.PYTHON.getLanguageKey())
          .build())
        .build());
    var hotspotKey = "myhotspotkey";
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build(),
      Hotspots.HotspotLite.newBuilder()
        .setKey(hotspotKey)
        .setFilePath(analyzedFileName)
        .setCreationDate(System.currentTimeMillis())
        .setStatus("TO_REVIEW")
        .setVulnerabilityProbability("LOW")
        .setTextRange(Hotspots.TextRange.newBuilder()
          .setStartLine(1)
          .setStartLineOffset(13)
          .setEndLine(1)
          .setEndLineOffset(26)
          .setHash(Utils.hash("'12.34.56.78'"))
          .build()
        )
        .setRuleKey(PYTHON_S1313)
        .build()
    );
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST + "&changedSince=" + CURRENT_TIME,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build(),
      Hotspots.HotspotLite.newBuilder()
        .setKey(hotspotKey)
        .setFilePath(analyzedFileName)
        .setCreationDate(System.currentTimeMillis())
        .setStatus("TO_REVIEW")
        .setVulnerabilityProbability("LOW")
        .setTextRange(Hotspots.TextRange.newBuilder()
          .setStartLine(1)
          .setStartLineOffset(13)
          .setEndLine(1)
          .setEndLineOffset(26)
          .setHash(Utils.hash("'12.34.56.78'"))
          .build()
        )
        .setRuleKey(PYTHON_S1313)
        .build()
    );


    var uriInFolder = folder1BaseDir.resolve(analyzedFileName).toUri().toString();
    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "remote", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.", DiagnosticSeverity.Warning)));
    assertThat(client.getHotspots(uriInFolder).get(0).getData().toString()).contains("\"status\":0");

    lsProxy.changeHotspotStatus(new SonarLintExtendedLanguageServer.ChangeHotspotStatusParams(hotspotKey, HotspotStatus.SAFE.getTitle(), uriInFolder));

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder)).isEmpty());
  }

  @Test
  void change_hotspot_status_to_acknowledged() {
    var analyzedFileName = "hotspot_acknowledged.py";

    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"10.1\", \"id\": \"xzy\"}");
    mockWebServerExtension.addResponse("/api/hotspots/change_status", new MockResponse().setResponseCode(200));
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
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull_taint?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST + "&changedSince=" + CURRENT_TIME,
      Issues.TaintVulnerabilityPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(CURRENT_TIME)
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=" + analyzedFileName + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/show.protobuf?key=python:S1313",
      Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder()
          .setSeverity("MINOR")
          .setType(Common.RuleType.SECURITY_HOTSPOT)
          .setLang(Language.PYTHON.getLanguageKey())
          .build())
        .build());
    var hotspotKey = "myhotspotkey";
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build(),
      Hotspots.HotspotLite.newBuilder()
        .setKey(hotspotKey)
        .setFilePath(analyzedFileName)
        .setCreationDate(System.currentTimeMillis())
        .setStatus("TO_REVIEW")
        .setVulnerabilityProbability("LOW")
        .setTextRange(Hotspots.TextRange.newBuilder()
          .setStartLine(1)
          .setStartLineOffset(13)
          .setEndLine(1)
          .setEndLineOffset(26)
          .setHash(Utils.hash("'12.34.56.78'"))
          .build()
        )
        .setRuleKey(PYTHON_S1313)
        .build()
    );


    var uriInFolder = folder1BaseDir.resolve(analyzedFileName).toUri().toString();
    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "remote", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.", DiagnosticSeverity.Warning)));
    assertThat(client.getHotspots(uriInFolder).get(0).getData().toString()).contains("\"status\":0");

    lsProxy.changeHotspotStatus(new SonarLintExtendedLanguageServer.ChangeHotspotStatusParams(hotspotKey, HotspotStatus.ACKNOWLEDGED.getTitle(), uriInFolder));

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder).get(0).getData().toString()).contains("\"status\":3"));
  }

  @Test
  void change_hotspot_status_permission_check() throws ExecutionException, InterruptedException {
    var analyzedFileName = "hotspot_permissions.py";
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"10.1\", \"id\": \"xzy\"}");
    mockWebServerExtension.addResponse("/api/hotspots/change_status", new MockResponse().setResponseCode(200));
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
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull_taint?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST + "&changedSince=" + CURRENT_TIME,
      Issues.TaintVulnerabilityPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(CURRENT_TIME)
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=" + analyzedFileName + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/show.protobuf?key=python:S1313",
      Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder()
          .setSeverity("MINOR")
          .setType(Common.RuleType.SECURITY_HOTSPOT)
          .setLang(Language.PYTHON.getLanguageKey())
          .build())
        .build());
    var hotspotKey = "myhotspotkey";
    mockWebServerExtension.addProtobufResponse("/api/hotspots/show.protobuf?hotspot=" + hotspotKey,
      Hotspots.ShowWsResponse.newBuilder()
        .setMessage("message")
        .setComponent(Hotspots.Component.newBuilder().setPath("path").build())
        .setTextRange(Common.TextRange.newBuilder()
          .setStartLine(1)
          .setEndLine(1)
          .build())
        .setAuthor("Author")
        .setStatus("TO_REVIEW")
        .setRule(Hotspots.Rule.newBuilder().setVulnerabilityProbability("HIGH").build())
        .setCanChangeStatus(true)
        .build()
    );
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build(),
      Hotspots.HotspotLite.newBuilder()
        .setKey(hotspotKey)
        .setFilePath(analyzedFileName)
        .setCreationDate(System.currentTimeMillis())
        .setStatus("TO_REVIEW")
        .setVulnerabilityProbability("LOW")
        .setTextRange(Hotspots.TextRange.newBuilder()
          .setStartLine(1)
          .setStartLineOffset(13)
          .setEndLine(1)
          .setEndLineOffset(26)
          .setHash(Utils.hash("'12.34.56.78'"))
          .build()
        )
        .setRuleKey(PYTHON_S1313)
        .build()
    );
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST + "&changedSince=" + CURRENT_TIME,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build(),
      Hotspots.HotspotLite.newBuilder()
        .setKey(hotspotKey)
        .setFilePath(analyzedFileName)
        .setCreationDate(System.currentTimeMillis())
        .setStatus("TO_REVIEW")
        .setVulnerabilityProbability("LOW")
        .setTextRange(Hotspots.TextRange.newBuilder()
          .setStartLine(1)
          .setStartLineOffset(13)
          .setEndLine(1)
          .setEndLineOffset(26)
          .setHash(Utils.hash("'12.34.56.78'"))
          .build()
        )
        .setRuleKey(PYTHON_S1313)
        .build()
    );


    var uriInFolder = folder1BaseDir.resolve(analyzedFileName).toUri().toString();
    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "remote", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.", DiagnosticSeverity.Warning)));


    var response = lsProxy.getAllowedHotspotStatuses(
      new SonarLintExtendedLanguageServer.GetAllowedHotspotStatusesParams(hotspotKey, folder1BaseDir.toUri().toString(), uriInFolder)).get();

    assertThat(response.isPermitted()).isTrue();
    assertThat(response.getNotPermittedReason()).isNull();
    assertThat(response.getAllowedStatuses()).containsExactly("Acknowledged", "Fixed", "Safe");
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
      "/api/rules/show.protobuf?key=python:S1313",
      Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder()
          .setSeverity("MINOR")
          .setType(Common.RuleType.SECURITY_HOTSPOT)
          .setLang(Language.PYTHON.getLanguageKey())
          .build())
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=hotspot.py&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build()
    );
  }

  @Test
  void shouldChangeLocalIssueStatus() {
    var fileUri = folder1BaseDir.resolve("changeLocalIssueStatus.py").toUri().toString();
    assertLocalIssuesStatusChanged(fileUri);
  }

  @Test
  void shouldReopenResolvedLocalIssues() {
    var fileName = "changeAndReopenLocalIssueStatus.py";
    var fileUri = folder1BaseDir.resolve(fileName).toUri().toString();
    assertLocalIssuesStatusChanged(fileUri);

    lsProxy.reopenResolvedLocalIssues(new SonarLintExtendedLanguageServer.ReopenAllIssuesForFileParams(fileName, fileUri, folder1BaseDir.toUri().toString()));

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));
  }

  @Test
  void shouldIgnoreRazorFile() {
    var uriInFolder = folder1BaseDir.resolve("shouldIgnore.razor").toUri().toString();
    didOpen(uriInFolder, "csharp", "@using System");

    awaitUntilAsserted(() -> assertLogContains("Found 0 issues"));
    assertLogContains("'OmniSharp' skipped because there is no related files in the current project");
  }

  private void assertLocalIssuesStatusChanged(String fileUri) {
    mockWebServerExtension.addResponse("/api/issues/anticipated_transitions?projectKey=myProject", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addResponse("/api/issues/add_comment", new MockResponse().setResponseCode(200));

    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folder1BaseDir.toUri().toString(), "some/branch/name"));
    var content = "def foo():\n  toto = 0\n  plouf = 0\n";
    didOpen(fileUri, "python", content);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));

    var diagnostics = client.getDiagnostics(fileUri);
    var issueKey = ((JsonObject) diagnostics.stream().filter(it -> it.getMessage().equals("Remove the unused local variable \"plouf\"."))
      .findFirst().get().getData()).get("entryKey").getAsString();
    lsProxy.changeIssueStatus(new SonarLintExtendedLanguageServer.ChangeIssueStatusParams(folder1BaseDir.toUri().toString(), issueKey,
      ResolutionStatus.FALSE_POSITIVE, fileUri, "", false));

    //Now we expect that one issue is resolved
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning)));
  }
}
