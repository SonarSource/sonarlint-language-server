/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
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
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.DateUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetBindingSuggestionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.GetMCPServerConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.ChangeDependencyRiskStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetConnectionSuggestionsParams;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Measures;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.ProjectBranches;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Qualityprofiles;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Settings;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.util.Utils;
import testutils.MockWebServerExtension;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonar.api.rules.RuleType.SECURITY_HOTSPOT;

class ConnectedModeMediumTests extends AbstractLanguageServerMediumTests {

  private static final String QPROFILE_KEY = "AXDEr5Q7LjElHiH99ZhW";
  private static final String PYTHON_S1481 = "python:S1481";
  private static final String PYTHON_S1313 = "python:S1313";
  private static final String PROJECT_KEY = "myProject";
  public static final String LANGUAGES_LIST = "apex,c,cpp,cs,css,cobol,web,java,js,php,plsql,py,secrets,text,tsql,ts,xml,yaml,json,go,cloudformation,docker,kubernetes,terraform," +
    "azureresourcemanager,ansible,githubactions";

  @RegisterExtension
  private static final MockWebServerExtension mockWebServerExtension = MockWebServerExtension.onRandomPort();

  private static final String CONNECTION_ID = "mediumTests";

  private static final String PROJECT_KEY1 = "project:key1";
  private static final String PROJECT_NAME1 = "Project One";
  private static final String PROJECT_KEY2 = "project:key2";
  private static final String PROJECT_NAME2 = "Project Two";
  private static final long CURRENT_TIME = System.currentTimeMillis();
  private static Path folder1BaseDir;
  private static Path bindingSuggestionBaseDir;

  @BeforeAll
  static void initialize() throws Exception {
    Path omnisharpDir = makeStaticTempDir();
    folder1BaseDir = makeStaticTempDir();
    initialize(Map.of(
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1",
      "productKey", "productKey",
      "omnisharpDirectory", omnisharpDir.toString(),
      "connections", Map.of(
        "sonarqube", List.of(Map.of(
          "connectionId", CONNECTION_ID,
          "serverUrl", "/")))));

    var fileName1 = "analysisConnected_scan_all_hotspot_then_forget_hotspot1.py";
    var fileName2 = "analysisConnected_scan_all_hotspot_then_forget_hotspot2.py";
    var file1 = new SonarLintExtendedLanguageClient.FoundFileDto(fileName1, folder1BaseDir.resolve(fileName1).toFile().getAbsolutePath(),
      "def foo():\n  id_address = '12.34.56.78'\n");
    var file2 = new SonarLintExtendedLanguageClient.FoundFileDto(fileName2, folder1BaseDir.resolve(fileName2).toFile().getAbsolutePath(),
      "def foo():\n  id_address = '23.45.67.89'\n");

    setUpFindFilesInFolderResponse(folder1BaseDir.toUri().toString(), List.of(file1, file2));
  }

  void mockSonarQube() {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"10.7\", \"id\": \"xzy\"}");
    mockWebServerExtension.addStringResponse("/api/features/list", "[]");
    mockWebServerExtension.addProtobufResponse("/api/settings/values.protobuf", Settings.Values.newBuilder().build());
    mockWebServerExtension.addResponse("/api/authentication/validate?format=json", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1",
      Components.SearchWsResponse.newBuilder()
        .addComponents(Components.Component.newBuilder().setKey(PROJECT_KEY1).setName(PROJECT_NAME1).build())
        .addComponents(Components.Component.newBuilder().setKey(PROJECT_KEY2).setName(PROJECT_NAME2).build())
        .setPaging(Common.Paging.newBuilder().setTotal(2).build())
        .build());
    mockWebServerExtension.addProtobufResponse("/api/components/show.protobuf?component=project%3Akey1",
      Components.ShowWsResponse.newBuilder()
        .setComponent(Components.Component.newBuilder()
          .setKey(PROJECT_KEY1)
          .setName(PROJECT_NAME1)
          .build())
        .build());
    mockWebServerExtension.addProtobufResponse("/api/components/tree.protobuf?qualifiers=FIL,UTS&component=myProject&ps=500&p=1",
      Components.TreeWsResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponse("/api/measures/component.protobuf?additionalFields=period&metricKeys=projects&component=" + PROJECT_KEY,
      Measures.ComponentWsResponse.newBuilder()
        .setComponent(Measures.Component.newBuilder()
          .setKey(PROJECT_KEY)
          .setQualifier("TRK")
          .build())
        .setPeriod(Measures.Period.newBuilder()
          .setMode("PREVIOUS_VERSION")
          .setParameter("0.1")
          .build())
        .build());
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
    try (var inputStream = new FileInputStream(fullPathToJar("sonarpython"))) {
      var buffer = new Buffer();
      buffer.readFrom(inputStream);
      return buffer;
    } catch (IOException ioEx) {
      throw new IllegalStateException(ioEx);
    }
  }

  @Override
  protected void setUpFolderSettings(Map<String, Map<String, Object>> folderSettings) {
    mockSonarQube();
    setShowVerboseLogs(client.globalSettings, true);
    addSonarQubeConnection(client.globalSettings, CONNECTION_ID, mockWebServerExtension.url("/"), "xxxxx");
    var folder1Uri = folder1BaseDir.toUri().toString();
    bindProject(getFolderSettings(folder1Uri), CONNECTION_ID, PROJECT_KEY);
  }

  @AfterAll
  static void cleanUp() {
    FileUtils.deleteQuietly(folder1BaseDir.toFile());
    clearFilesInFolder();
  }

  @Test
  void should_export_binding_settings() {
    var configScopeId = folder1BaseDir.toUri().toString();
    addConfigScope(configScopeId);
    var connectedModeConfigContents = lsProxy.getSharedConnectedModeConfigFileContents(new GetSharedConnectedModeConfigFileParams(configScopeId)).join();
    assertThat(connectedModeConfigContents.getJsonFileContent())
      .isNotEmpty()
      .contains(PROJECT_KEY);
  }

  @Test
  void should_get_mcp_server_settings() {
    var configScopeId = folder1BaseDir.toUri().toString();
    var baseServerUrl = mockWebServerExtension.url("/").substring(0, mockWebServerExtension.url("/").length() - 1);
    addConfigScope(configScopeId);

    var mcpServerConfig = lsProxy.getMCPServerConfiguration(new GetMCPServerConfigurationParams(CONNECTION_ID, "xxxxx")).join();

    assertThat(mcpServerConfig.getJsonConfiguration())
      .isNotEmpty()
      .contains(baseServerUrl);
  }

  @Test
  void should_get_mcp_rule_file_content() {
    var aiAgent = "cursor";

    var ruleFileContent = lsProxy.getMCPRuleFileContent(aiAgent).join();

    assertThat(ruleFileContent.getContent())
      .isNotEmpty()
      .contains("SonarQube MCP Server");
  }

  @Test
  void should_show_warning_notification_for_unsupported_ide_when_requesting_rule_file_content() {
    var aiAgent = "unsupported-agent";

    var future = lsProxy.getMCPRuleFileContent(aiAgent);

    assertThrows(CompletionException.class, future::join);

    assertThat(client.shownMessages)
      .isNotEmpty()
      .contains(new MessageParams(MessageType.Warning,
        "Rule file creation is not yet supported for AI agent 'unsupported-agent'."));
  }

  @Test
  void should_get_ai_agent_hook_script_content() {
    var aiAgent = "windsurf";

    var hookScriptContent = lsProxy.getAiAgentHookScriptContent(aiAgent).join();

    assertThat(hookScriptContent.getScriptContent())
      .isNotEmpty()
      .contains("SonarQube for IDE")
      .contains("sonarqube_analysis_hook");
    assertThat(hookScriptContent.getScriptFileName())
      .isNotEmpty()
      .matches(".*\\.(js|py|sh)$");
    assertThat(hookScriptContent.getConfigContent())
      .isNotEmpty()
      .contains("\"post_write_code\"")
      .contains("{{SCRIPT_PATH}}");
    assertThat(hookScriptContent.getConfigFileName())
      .isEqualTo("hooks.json");
  }

  @Test
  void should_show_warning_notification_for_unsupported_ide_when_requesting_ai_agent_hook_script() {
    var aiAgent = "unsupported-agent";

    var future = lsProxy.getAiAgentHookScriptContent(aiAgent);

    assertThrows(CompletionException.class, future::join);

    assertThat(client.shownMessages)
      .isNotEmpty()
      .contains(new MessageParams(MessageType.Warning,
        "Hook script creation is not yet supported for AI agent 'unsupported-agent'."));
  }

  @Test
  void analysisConnected_find_hotspot() throws IOException {
    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);
    var analyzedFileName = "analysisConnected_find_hotspot.py";
    mockNoIssuesNoHotspotsForProject(analyzedFileName);

    addConfigScope(folderUri);
    var uriInFolder = folder.resolve(analyzedFileName).toUri().toString();
    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "local-hotspot", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.",
          DiagnosticSeverity.Information)));
  }

  @Test
  void analysisConnected_find_tracked_hotspot_before_sq_10_1() throws IOException {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"10.0\", \"id\": \"xzy\"}");
    mockNoIssueAndNoTaintInIncrementalSync();

    var analyzedFileName = "analysisConnected_find_tracked_hotspot_before_sq_10_1.py";
    var hotspotKey = UUID.randomUUID().toString();
    mockWebServerExtension.addProtobufResponse("/api/measures/component.protobuf?additionalFields=period&metricKeys=projects&component=myProject",
      Measures.ComponentWsResponse.newBuilder()
        .setComponent(Measures.Component.newBuilder()
          .setKey("myProject")
          .setQualifier("TRK")
          .build())
        .setPeriod(Measures.Period.newBuilder()
          .setMode("PREVIOUS_VERSION")
          .setDate("2023-08-29T09:37:59+0000")
          .setParameter("9.2")
          .build())
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/show.protobuf?key=python:S1313",
      Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder()
          .setSeverity("MINOR")
          .setType(Common.RuleType.SECURITY_HOTSPOT)
          .setLang("py")
          .build())
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=" + analyzedFileName + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder()
        .addHotspots(Hotspots.SearchWsResponse.Hotspot.newBuilder()
          .setKey(hotspotKey)
          .setMessage("Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.")
          .setComponent("someComponentKey")
          .setCreationDate(DateUtils.formatDateTime(System.currentTimeMillis()))
          .setStatus("TO_REVIEW")
          .setVulnerabilityProbability("LOW")
          .setTextRange(Common.TextRange.newBuilder()
            .setStartLine(1)
            .setStartOffset(13)
            .setEndLine(1)
            .setEndOffset(26)
            .build())
          .setRuleKey(PYTHON_S1313)
          .build())
        .addComponents(Hotspots.Component.newBuilder()
          .setKey("someComponentKey")
          .setPath(analyzedFileName)
          .build())
        .setPaging(Common.Paging.newBuilder().setTotal(1).build())
        .build());

    notifyConfigurationChangeOnClient();

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    addConfigScope(folderUri);
    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folderUri, "master"));

    var uriInFolder = folder.resolve(analyzedFileName).toUri().toString();
    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "remote-hotspot", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.",
          DiagnosticSeverity.Information)));
  }

  @Test
  void analysisConnected_find_tracked_hotspot_after_sq_10_1() throws IOException {
    var analyzedFileName = "analysisConnected_find_tracked_hotspot_after_sq_10_1.py";
    var hotspotKey = UUID.randomUUID().toString();
    mockNoIssueAndNoTaintInIncrementalSync();
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=" + analyzedFileName + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/show.protobuf?key=python:S1313",
      Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder()
          .setSeverity("MINOR")
          .setType(Common.RuleType.SECURITY_HOTSPOT)
          .setLang("py")
          .build())
        .build());
    // Initial sync returns empty to establish baseline
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build());

    // Incremental sync returns the actual findings
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST + "&changedSince=" + CURRENT_TIME,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build(),
      Hotspots.HotspotLite.newBuilder()
        .setKey(hotspotKey)
        .setFilePath(analyzedFileName)
        .setCreationDate(System.currentTimeMillis())
        .setStatus("TO_REVIEW")
        .setVulnerabilityProbability("LOW")
        .setMessage("Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.")
        .setTextRange(Hotspots.TextRange.newBuilder()
          .setStartLine(1)
          .setStartLineOffset(13)
          .setEndLine(1)
          .setEndLineOffset(26)
          .setHash(Utils.hash("'12.34.56.78'"))
          .build())
        .setRuleKey(PYTHON_S1313)
        .build());

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    var uriInFolder = folder.resolve(analyzedFileName).toUri().toString();
    addConfigScope(folderUri);
    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folderUri, "master"));

    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "remote-hotspot", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.",
          DiagnosticSeverity.Information)));
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
    // whole folder scan does not work on Windows - SLLS-250
  void analysisConnected_scan_all_hotspot_then_forget() throws IOException {
    var file1 = "analysisConnected_scan_all_hotspot_then_forget_hotspot1.py";
    var file2 = "analysisConnected_scan_all_hotspot_then_forget_hotspot2.py";
    mockNoIssuesNoHotspotsForProject(file1);
    mockWebServerExtension.addProtobufResponse("/api/hotspots/search.protobuf?projectKey=" + PROJECT_KEY + "&files=" + file2 + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());

    var uri1InFolder = folder1BaseDir.resolve(file1).toUri().toString();
    var doc1 = new TextDocumentItem();
    doc1.setUri(uri1InFolder);
    String doc1Content = "def foo():\n  id_address = '12.34.56.78'\n";
    doc1.setText(doc1Content);
    doc1.setVersion(0);
    doc1.setLanguageId("python");
    Path doc1Path = Path.of(URI.create(uri1InFolder).getPath());
    Files.createFile(doc1Path);
    Files.writeString(doc1Path, doc1Content);

    var uri2InFolder = folder1BaseDir.resolve(file2).toUri().toString();
    var doc2 = new TextDocumentItem();
    doc2.setUri(uri2InFolder);
    String doc2Content = "def foo():\n  id_address = '23.45.67.89'\n";
    doc2.setText(doc2Content);
    doc2.setVersion(0);
    doc2.setLanguageId("python");
    Path doc2Path = Path.of(URI.create(uri2InFolder).getPath());
    Files.createFile(doc2Path);
    Files.writeString(doc2Path, doc2Content);

    List<TextDocumentItem> documents = List.of(doc1, doc2);
    var scanParams = new SonarLintExtendedLanguageServer.ScanFolderForHotspotsParams(folder1BaseDir.toUri().toString(), documents);

    addConfigScope(folder1BaseDir.toUri().toString());
    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folder1BaseDir.toUri().toString(), "master"));

    lsProxy.scanFolderForHotspots(scanParams);

    awaitUntilAsserted(() -> {
      assertThat(client.getHotspots(uri1InFolder))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
          Diagnostic::getSeverity)
        .containsExactly(
          tuple(1, 15, 1, 28, PYTHON_S1313, "local-hotspot", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.",
            DiagnosticSeverity.Information));
      assertThat(client.getDiagnostics(uri1InFolder)).isEmpty();

      assertThat(client.getHotspots(uri2InFolder))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
          Diagnostic::getSeverity)
        .containsExactly(
          tuple(1, 15, 1, 28, PYTHON_S1313, "local-hotspot", "Make sure using this hardcoded IP address \"23.45.67.89\" is safe here.",
            DiagnosticSeverity.Information));
      assertThat(client.getDiagnostics(uri2InFolder)).isEmpty();
    });

    // Simulate that file 1 is open, should not be cleaned
    didOpen(uri1InFolder, "python", doc1Content);
    // allow enough time for the file opening to be reflected
    awaitUntilAsserted(
      () -> assertThat(client.logs).anyMatch(messageParams -> messageParams.getMessage().contains("Language of file \"" + doc1.getUri() + "\" is set to \"PYTHON\"")));

    lsProxy.forgetFolderHotspots();

    awaitUntilAsserted(() -> {
      // File 1 is still open, keeping hotspots
      assertThat(client.getHotspots(uri1InFolder))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
          Diagnostic::getSeverity)
        .containsExactly(
          tuple(1, 15, 1, 28, PYTHON_S1313, "local-hotspot", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.",
            DiagnosticSeverity.Information));

      // File 2 is not open, cleaning hotspots
      assertThat(client.getHotspots(uri2InFolder)).isEmpty();
    });
  }

  @Test
  void analysisConnected_no_matching_server_issues() throws IOException {
    var fileName = "analysisConnected_no_matching_server_issues.py";
    mockNoIssuesNoHotspotsForProject(fileName);
    mockWebServerExtension.addStringResponse("/api/authentication/validate?format=json", "{\"valid\": true}");
    mockWebServerExtension.addProtobufResponse("/api/measures/component.protobuf?additionalFields=period&metricKeys=projects&component=myProject",
      Measures.ComponentWsResponse.newBuilder()
        .setComponent(Measures.Component.newBuilder()
          .setKey("myProject")
          .setQualifier("TRK")
          .build())
        .setPeriod(Measures.Period.newBuilder()
          .setMode("PREVIOUS_VERSION")
          .setDate("2023-08-29T09:37:59+0000")
          .setParameter("9.2")
          .build())
        .build());

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    addConfigScope(folderUri);
    var uriInFolder = folder.resolve(fileName).toUri().toString();
    didOpen(uriInFolder, "python", "def foo():\n  toto = 0\n  plouf = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));
  }

  private void addConfigScope(String configScopeId) {
    addFolder(configScopeId, Path.of(URI.create(configScopeId)).getFileName().toString());
    awaitUntilAsserted(() -> assertThat(client)
      .satisfiesAnyOf(c -> assertThat(c.newCodeDefinitionCache).containsKey(configScopeId)));
  }

  @Test
  void analysisConnected_matching_server_issues() throws Exception {
    var issueKey = UUID.randomUUID().toString();
    mockWebServerExtension.addProtobufResponse(
      "/api/issues/search.protobuf?issues=" + issueKey + "&additionalFields=transitions&ps=1&p=1",
      Issues.SearchWsResponse.newBuilder()
        .addIssues(Issues.Issue.newBuilder()
          .setKey(issueKey)
          .setTransitions(Issues.Transitions.newBuilder()
            .addAllTransitions(List.of("wontfix", "falsepositive"))
            .build())
          .build())
        .build());
    mockWebServerExtension.addProtobufResponse("/api/measures/component.protobuf?additionalFields=period&metricKeys=projects&component=myProject",
      Measures.ComponentWsResponse.newBuilder()
        .setComponent(Measures.Component.newBuilder()
          .setKey("myProject")
          .setQualifier("TRK")
          .build())
        .setPeriod(Measures.Period.newBuilder()
          .setMode("PREVIOUS_VERSION")
          .setDate("2023-08-29T09:37:59+0000")
          .setParameter("9.2")
          .build())
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=" + PROJECT_KEY + "&files=analysisConnected_matching_server_issues.py&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Issues.IssuesPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(CURRENT_TIME)
        .build(),
      Issues.IssueLite.newBuilder()
        .setKey(issueKey)
        .setRuleKey(PYTHON_S1481)
        .setType(Common.RuleType.CODE_SMELL)
        .setUserSeverity(Common.Severity.BLOCKER)
        .setMainLocation(Issues.Location.newBuilder()
          .setFilePath("analysisConnected_matching_server_issues.py")
          .setMessage("Remove the unused local variable \"toto\".")
          .setTextRange(Issues.TextRange.newBuilder()
            .setStartLine(1)
            .setStartLineOffset(2)
            .setEndLine(1)
            .setEndLineOffset(6)
            .setHash("f71dbe52628a3f83a77ab494817525c6")
            .build())
          .build())
        .setClosed(false)
        .build());

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    addConfigScope(folderUri);
    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folderUri, "master"));

    var uriInFolder = folder.resolve("analysisConnected_matching_server_issues.py").toUri().toString();
    didOpen(uriInFolder, "python", "def foo():\n  toto = 0\n  plouf = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));

    var firstDiagnostic = client.getDiagnostics(uriInFolder).get(0);
    var codeActionParams = new CodeActionParams();
    codeActionParams.setTextDocument(new TextDocumentIdentifier(uriInFolder));
    codeActionParams.setRange(firstDiagnostic.getRange());
    codeActionParams.setContext(new CodeActionContext(List.of(firstDiagnostic)));
    var codeActions = lsProxy.getTextDocumentService().codeAction(codeActionParams).get();
    assertThat(codeActions).hasSize(3)
      .extracting(Either::getRight)
      .extracting(CodeAction::getCommand)
      .extracting(Command::getCommand)
      .containsExactlyInAnyOrder("SonarLint.QuickFixApplied", "SonarLint.ResolveIssue", "SonarLint.ShowIssueDetailsCodeAction");
  }

  @Test
  void shouldGetServerNamesForConnection() throws ExecutionException, InterruptedException {
    var params = new SonarLintExtendedLanguageServer.GetRemoteProjectNamesByKeysParams(CONNECTION_ID, List.of(PROJECT_KEY1, "unknown"));

    assertThat(lsProxy.getRemoteProjectNamesByProjectKeys(params).get()).containsExactly(Map.entry(PROJECT_KEY1,
      PROJECT_NAME1));
  }

  @Test
  void shouldThrowGettingServerNamesForUnknownConnection() {
    var params = new SonarLintExtendedLanguageServer.GetRemoteProjectsForConnectionParams("unknown connection");

    var future = lsProxy.getRemoteProjectsForConnection(params);
    awaitUntilAsserted(() -> assertThat(future).isCompletedExceptionally());
  }

  @Test
  void shouldReturnRemoteProjectsForKnownConnection() throws ExecutionException, InterruptedException {
    mockNoIssuesNoHotspotsForProject("shouldReturnRemoteProjectsForKnownConnection.py");

    mockWebServerExtension.addProtobufResponse("/api/measures/component.protobuf?additionalFields=period&metricKeys=projects&component=myProject",
      Measures.ComponentWsResponse.newBuilder()
        .setComponent(Measures.Component.newBuilder()
          .setKey("myProject")
          .setQualifier("TRK")
          .build())
        .setPeriod(Measures.Period.newBuilder()
          .setMode("PREVIOUS_VERSION")
          .setDate("2023-08-29T09:37:59+0000")
          .setParameter("9.2")
          .build())
        .build());

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
  void shouldThrowExceptionForUnknownProjectKey() {
    SonarLintExtendedLanguageServer.GetRemoteProjectNamesByKeysParams testParams = new SonarLintExtendedLanguageServer.GetRemoteProjectNamesByKeysParams("random_string",
      List.of("unknown_project_key"));
    var future = lsProxy.getRemoteProjectNamesByProjectKeys(testParams);

    awaitUntilAsserted(() -> assertThat(future).isCompletedExceptionally());
  }

  @Test
  void openHotspotInBrowserShouldLogIfBranchNotFound() throws IOException {
    var folder2BaseDir = makeStaticTempDir();
    var folder2Uri = folder2BaseDir.toUri().toString();
    bindProject(getFolderSettings(folder2Uri), CONNECTION_ID, PROJECT_KEY2);
    addFolder(folder2BaseDir.toUri().toString(), folder2BaseDir.getFileName().toString());

    lsProxy.openHotspotInBrowser(new SonarLintExtendedLanguageServer.OpenHotspotInBrowserLsParams("id", folder2BaseDir.toUri().toString()));

    awaitUntilAsserted(
      () -> assertThat(client.shownMessages).contains(new MessageParams(MessageType.Error, "Can't find branch for workspace folder " + folder2BaseDir.toUri().getPath()
        + " during attempt to open hotspot in browser.")));
    FileUtils.deleteQuietly(folder2BaseDir.toFile());
  }

  @Test
  void shouldOpenHotspotDescription() throws IOException {
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=" + PYTHON_S1313, Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder()
        .setKey(PYTHON_S1313)
        .setName("fakeName")
        .setLang("java")
        .setHtmlNote("htmlNote")
        .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder().build())
        .setCleanCodeAttribute(Common.CleanCodeAttribute.CONVENTIONAL)
        .setEducationPrinciples(Rules.Rule.EducationPrinciples.newBuilder().build())
        .setSeverity("BLOCKER")
        .setType(Common.RuleType.BUG)
        .setHtmlDesc("htmlDesc")
        .setImpacts(Rules.Rule.Impacts.newBuilder().build())
        .build())
      .build());

    var analyzedFileName = "shouldOpenHotspotDescription.py";
    mockNoIssuesNoHotspotsForProject(analyzedFileName);

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    addConfigScope(folderUri);
    var uriInFolder = folder.resolve(analyzedFileName).toUri().toString();
    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");
    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder)).hasSize(1));

    var diagnostic = client.getHotspots(uriInFolder).get(0);
    var hotspotId = ((JsonObject) diagnostic.getData()).get("entryKey").getAsString();
    var params = new SonarLintExtendedLanguageServer.ShowHotspotRuleDescriptionParams(hotspotId, uriInFolder);
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
  @DisabledOnOs(OS.WINDOWS)
  void shouldReturnHotspotDetails() {
    addConfigScope(folder1BaseDir.toUri().toString());

    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=" + PYTHON_S1313, Rules.ShowResponse.newBuilder()
      .setRule(Rules.Rule.newBuilder()
        .setKey(PYTHON_S1313)
        .setName("fakeName")
        .setLang("java")
        .setHtmlNote("htmlNote")
        .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder().build())
        .setCleanCodeAttribute(Common.CleanCodeAttribute.CONVENTIONAL)
        .setEducationPrinciples(Rules.Rule.EducationPrinciples.newBuilder().build())
        .setSeverity("BLOCKER")
        .setType(Common.RuleType.SECURITY_HOTSPOT)
        .setHtmlDesc("htmlDesc")
        .setImpacts(Rules.Rule.Impacts.newBuilder().build())
        .build())
      .build());

    var uriInFolder = folder1BaseDir.resolve("shouldReturnHotspotDetails.py").toUri().toString();
    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder)).hasSizeGreaterThanOrEqualTo(1));

    var hotspotId = ((JsonObject) client.getHotspots(uriInFolder).get(0).getData()).get("entryKey").getAsString();
    var testParams = new SonarLintExtendedLanguageServer.ShowHotspotRuleDescriptionParams(hotspotId, uriInFolder);
    testParams.setFileUri(folder1BaseDir.resolve("shouldReturnHotspotDetails.py").toUri().toString());

    var result = lsProxy.getHotspotDetails(testParams);

    awaitUntilAsserted(() -> {
      assertTrue(result.isDone());
      assertThat(result.get().getLanguageKey()).isEqualTo("py");
      assertThat(result.get().getName()).isEqualTo("Using hardcoded IP addresses is security-sensitive");
    });
  }

  @Test
  void checkLocalDetectionSupportedNotBound() throws ExecutionException, InterruptedException {
    var result = lsProxy.checkLocalDetectionSupported(new SonarLintExtendedLanguageServer.UriParams("notBound")).get();

    assertThat(result.isSupported()).isFalse();
    assertThat(result.getReason()).isEqualTo("The provided configuration scope does not exist: notBound");
  }

  @Test
  void shouldChangeIssueStatus() throws IOException {
    var analyzedFileName = "shouldChangeIssueStatus.py";
    mockWebServerExtension.addResponse("/api/issues/do_transition", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addResponse("/api/issues/anticipated_transitions?projectKey=myProject", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addResponse("/api/issues/add_comment", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=" + analyzedFileName + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    addConfigScope(folderUri);
    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folderUri, "some/branch/name"));

    var fileUri = folder.resolve(analyzedFileName).toUri().toString();
    var content = "def foo():\n  toto = 0\n  plouf = 0\n";
    didOpen(fileUri, "python", content);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));

    var issueKey = ((JsonObject) client.getDiagnostics(fileUri).get(0).getData()).get("entryKey").getAsString();

    lsProxy.changeIssueStatus(new SonarLintExtendedLanguageServer.ChangeIssueStatusParams(folderUri, issueKey,
      "False positive", fileUri, "clever comment", false));

    // Now we expect that one issue is resolved
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));
    assertThat(client.shownMessages).contains(new MessageParams(MessageType.Info, "New comment was added"));
  }

  @Test
  void shouldNotChangeIssueStatus() throws IOException {
    var issueKey = UUID.randomUUID().toString();

    mockWebServerExtension.addResponse("/api/issues/do_transition", new MockResponse().setResponseCode(400));
    mockWebServerExtension.addResponse("/api/issues/add_comment", new MockResponse().setResponseCode(400));
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=shouldNotChangeIssueStatus.py&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    addConfigScope(folderUri);
    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folderUri, "some/branch/name"));

    var fileUri = folder.resolve("shouldNotChangeIssueStatus.py").toUri().toString();
    var content = "def foo():\n  toto = 0\n  plouf = 0\n";
    didOpen(fileUri, "python", content);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));

    lsProxy.changeIssueStatus(new SonarLintExtendedLanguageServer.ChangeIssueStatusParams(folderUri, issueKey,
      "False positive", fileUri, "clever comment", false));

    // Now we expect that one issue is resolved
    awaitUntilAsserted(() -> assertThat(client.shownMessages)
      .contains(new MessageParams(MessageType.Error, "Could not change status for the issue. Look at the SonarQube for IDE output for details.")));
  }

  @Test
  void change_hotspot_status_to_resolved() throws IOException {
    var analyzedFileName = "hotspot_resolved.py";
    mockWebServerExtension.addResponse("/api/hotspots/change_status", new MockResponse().setResponseCode(200));
    mockNoIssueAndNoTaintInIncrementalSync();
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=" + analyzedFileName + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/show.protobuf?key=python:S1313",
      Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder()
          .setSeverity("MINOR")
          .setType(Common.RuleType.SECURITY_HOTSPOT)
          .setLang("py")
          .build())
        .build());
    var hotspotKey = UUID.randomUUID().toString();
    // Initial sync returns empty to establish baseline
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build());
    // Incremental sync returns the actual findings
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST + "&changedSince=" + CURRENT_TIME,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build(),
      Hotspots.HotspotLite.newBuilder()
        .setKey(hotspotKey)
        .setFilePath(analyzedFileName)
        .setCreationDate(System.currentTimeMillis())
        .setStatus("TO_REVIEW")
        .setVulnerabilityProbability("LOW")
        .setMessage("Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.")
        .setTextRange(Hotspots.TextRange.newBuilder()
          .setStartLine(1)
          .setStartLineOffset(13)
          .setEndLine(1)
          .setEndLineOffset(26)
          .setHash(Utils.hash("'12.34.56.78'"))
          .build())
        .setRuleKey(PYTHON_S1313)
        .build());

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    var uriInFolder = folder.resolve(analyzedFileName).toUri().toString();
    addConfigScope(folderUri);
    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folderUri, "master"));

    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "remote-hotspot", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.", DiagnosticSeverity.Information)));
    assertThat(client.getHotspots(uriInFolder).get(0).getData().toString()).contains("\"status\":0");

    lsProxy.changeHotspotStatus(new SonarLintExtendedLanguageServer.ChangeHotspotStatusParams(hotspotKey, HotspotStatus.SAFE.name(), uriInFolder));

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder)).isEmpty());
  }

  @Test
  void should_not_change_hotspot_status_to_resolved() throws IOException {
    var analyzedFileName = "hotspot_not_resolved.py";
    mockWebServerExtension.addResponse("/api/hotspots/change_status", new MockResponse().setResponseCode(400));
    mockNoIssueAndNoTaintInIncrementalSync();
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=" + analyzedFileName + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/show.protobuf?key=python:S1313",
      Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder()
          .setSeverity("MINOR")
          .setType(Common.RuleType.SECURITY_HOTSPOT)
          .setLang("py")
          .build())
        .build());
    var hotspotKey = UUID.randomUUID().toString();
    // Initial sync returns empty to establish baseline
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build(),
      Hotspots.HotspotLite.newBuilder()
        .setKey(hotspotKey)
        .setFilePath(analyzedFileName)
        .setCreationDate(System.currentTimeMillis())
        .setStatus("TO_REVIEW")
        .setVulnerabilityProbability("LOW")
        .setMessage("Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.")
        .setTextRange(Hotspots.TextRange.newBuilder()
          .setStartLine(1)
          .setStartLineOffset(13)
          .setEndLine(1)
          .setEndLineOffset(26)
          .setHash(Utils.hash("'12.34.56.78'"))
          .build())
        .setRuleKey(PYTHON_S1313)
        .build());

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    var uriInFolder = folder.resolve(analyzedFileName).toUri().toString();
    addConfigScope(folderUri);
    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folderUri, "master"));

    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "remote-hotspot", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.", DiagnosticSeverity.Information)));
    assertThat(client.getHotspots(uriInFolder).get(0).getData().toString()).contains("\"status\":0");

    lsProxy.changeHotspotStatus(new SonarLintExtendedLanguageServer.ChangeHotspotStatusParams(hotspotKey, HotspotStatus.SAFE.name(), uriInFolder));

    awaitUntilAsserted(() -> assertThat(client.shownMessages)
      .contains(new MessageParams(MessageType.Error, "Could not change status for the hotspot. Look at the SonarQube for IDE output for details.")));
  }

  @Test
  void change_hotspot_status_permission_check() throws ExecutionException, InterruptedException, IOException {
    var analyzedFileName = "hotspot_permissions.py";
    mockWebServerExtension.addResponse("/api/hotspots/change_status", new MockResponse().setResponseCode(200));
    mockNoIssueAndNoTaintInIncrementalSync();
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=" + analyzedFileName + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponse(
      "/api/rules/show.protobuf?key=python:S1313",
      Rules.ShowResponse.newBuilder()
        .setRule(Rules.Rule.newBuilder()
          .setSeverity("MINOR")
          .setType(Common.RuleType.SECURITY_HOTSPOT)
          .setLang("py")
          .build())
        .build());
    var hotspotKey = UUID.randomUUID().toString();
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
        .build());
    // Initial sync returns empty to establish baseline
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build());
    // Incremental sync returns the actual findings
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/hotspots/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST + "&changedSince=" + CURRENT_TIME,
      Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(CURRENT_TIME).build(),
      Hotspots.HotspotLite.newBuilder()
        .setKey(hotspotKey)
        .setFilePath(analyzedFileName)
        .setCreationDate(System.currentTimeMillis())
        .setStatus("TO_REVIEW")
        .setVulnerabilityProbability("LOW")
        .setMessage("Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.")
        .setTextRange(Hotspots.TextRange.newBuilder()
          .setStartLine(1)
          .setStartLineOffset(13)
          .setEndLine(1)
          .setEndLineOffset(26)
          .setHash(Utils.hash("'12.34.56.78'"))
          .build())
        .setRuleKey(PYTHON_S1313)
        .build());

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    addConfigScope(folderUri);

    var uriInFolder = folder.resolve(analyzedFileName).toUri().toString();

    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "remote-hotspot", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.", DiagnosticSeverity.Information)));

    var response = lsProxy.getAllowedHotspotStatuses(
      new SonarLintExtendedLanguageServer.GetAllowedHotspotStatusesParams(hotspotKey, folderUri, uriInFolder)).get();

    assertThat(response.isPermitted()).isTrue();
    assertThat(response.getNotPermittedReason()).isNull();
    assertThat(response.getAllowedStatuses()).containsExactly("ACKNOWLEDGED", "FIXED", "SAFE");
  }

  @Test
  void change_hotspot_status_permission_check_fail() throws ExecutionException, InterruptedException, IOException {
    var analyzedFileName = "hotspot_no_permissions.py";
    mockWebServerExtension.addResponse("/api/hotspots/change_status", new MockResponse().setResponseCode(400));

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    var uriInFolder = folder.resolve(analyzedFileName).toUri().toString();
    addConfigScope(folderUri);
    var hotspotKey = "myHotspotKey";

    didOpen(uriInFolder, "python", "IP_ADDRESS = '12.34.56.78'\n");

    awaitUntilAsserted(() -> assertThat(client.getHotspots(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(0, 13, 0, 26, PYTHON_S1313, "local-hotspot", "Make sure using this hardcoded IP address \"12.34.56.78\" is safe here.", DiagnosticSeverity.Information)));

    lsProxy.getAllowedHotspotStatuses(
      new SonarLintExtendedLanguageServer.GetAllowedHotspotStatusesParams(hotspotKey, folderUri, uriInFolder)).get();

    awaitUntilAsserted(() -> assertThat(client.shownMessages)
      .contains(new MessageParams(MessageType.Error, "Could not change status for the hotspot. Look at the SonarQube for IDE output for details.")));
  }

  @Test
  void change_issue_status_permission_check() throws ExecutionException, InterruptedException, IOException {
    var issueKey = UUID.randomUUID().toString();
    mockWebServerExtension.addProtobufResponse(
      "/api/issues/search.protobuf?issues=" + issueKey + "&additionalFields=transitions&ps=1&p=1",
      Issues.SearchWsResponse.newBuilder()
        .addIssues(Issues.Issue.newBuilder()
          .setKey(issueKey)
          .setTransitions(Issues.Transitions.newBuilder()
            .addAllTransitions(List.of("wontfix", "falsepositive"))
            .build())
          .build())
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=" + PROJECT_KEY + "&files=change_issue_status_permission_check.py&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Issues.IssuesPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(CURRENT_TIME)
        .build(),
      Issues.IssueLite.newBuilder()
        .setKey(issueKey)
        .setRuleKey(PYTHON_S1481)
        .setType(Common.RuleType.CODE_SMELL)
        .setUserSeverity(Common.Severity.BLOCKER)
        .setMainLocation(Issues.Location.newBuilder()
          .setFilePath("change_issue_status_permission_check.py")
          .setMessage("Remove the unused local variable \"toto\".")
          .setTextRange(Issues.TextRange.newBuilder()
            .setStartLine(1)
            .setStartLineOffset(2)
            .setEndLine(1)
            .setEndLineOffset(6)
            .setHash("f71dbe52628a3f83a77ab494817525c6")
            .build())
          .build())
        .setClosed(false)
        .build());

    mockWebServerExtension.addResponse("/api/issues/do_transition", new MockResponse().setResponseCode(200));
    mockWebServerExtension.addResponse("/api/issues/add_comment", new MockResponse().setResponseCode(200));

    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    addConfigScope(folderUri);
    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(folderUri, "some/branch/name"));
    var fileUri = folder.resolve("change_issue_status_permission_check.py").toUri().toString();
    var content = "def foo():\n  toto = 0\n  plouf = 0\n";
    didOpen(fileUri, "python", content);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));

    var result = lsProxy.checkIssueStatusChangePermitted(new SonarLintExtendedLanguageServer.CheckIssueStatusChangePermittedParams(folderUri, issueKey))
      .get();

    awaitUntilAsserted(() -> {
      assertTrue(result.isPermitted());
      assertThat(result.getNotPermittedReason()).isNull();
      assertThat(result.getAllowedStatuses()).containsExactly("Won't fix", "False positive");
    });
  }

  @Test
  void change_issue_status_permission_check_exceptionally() throws ExecutionException, InterruptedException {
    addConfigScope(folder1BaseDir.toUri().toString());

    var issueKey = "malformed issue UUID";
    var result = lsProxy.checkIssueStatusChangePermitted(new SonarLintExtendedLanguageServer.CheckIssueStatusChangePermittedParams(folder1BaseDir.toUri().toString(), issueKey))
      .get();

    awaitUntilAsserted(() -> {
      assertNull(result);
      assertThat(client.logs)
        .extracting(withoutTimestamp())
        .contains("Could not get issue status change for issue \""
          + issueKey + "\". Look at the SonarQube for IDE output for details.");
    });
  }

  @Test
  void should_notify_client_when_change_dependency_risk_status_fails() {
    var riskId = UUID.randomUUID();

    lsProxy.changeDependencyRiskStatus(new ChangeDependencyRiskStatusParams(
      folder1BaseDir.toUri().toString(),
      riskId,
      DependencyRiskTransition.ACCEPT,
      "some comment"));

    awaitUntilAsserted(() -> {
      assertThat(client.shownMessages)
        .extracting(MessageParams::getType, MessageParams::getMessage)
        .containsExactlyInAnyOrder(
          tuple(MessageType.Error, "Could not change status for the dependency risk. Check SonarQube for IDE output for details."));
    });
  }

  @Test
  void should_return_empty_dependency_risk_transitions() {
    var params = new SonarLintExtendedLanguageServer.GetDependencyRiskTransitionsParams(UUID.randomUUID());

    var result = lsProxy.getDependencyRiskTransitions(params).join();

    assertThat(result.transitions()).isEmpty();
  }

  @Test
  void should_show_error_notification_when_open_risk_in_browser_fails() {
    var riskId = UUID.randomUUID();
    var folderUri = folder1BaseDir.toUri().toString();
    var params = new SonarLintExtendedLanguageServer.OpenDependencyRiskInBrowserParams(riskId, folderUri);

    lsProxy.openDependencyRiskInBrowser(params);

    awaitUntilAsserted(() -> {
      assertThat(client.shownMessages)
        .extracting(MessageParams::getType, MessageParams::getMessage)
        .containsExactlyInAnyOrder(
          tuple(MessageType.Error,
            String.format("Failed to open dependency risk in browser: Configuration scope '%s' is not bound properly, unable to open dependency risk", folderUri)));
    });
  }

  private void mockNoIssueAndNoTaintInIncrementalSync() {
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
  }

  private void mockNoIssuesNoHotspotsForProject(String fileName) {
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
          .setLang("py")
          .build())
        .build());
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=myProject&files=" + fileName + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());
  }

  @Test
  void shouldChangeLocalIssueStatus() throws URISyntaxException, IOException {
    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    var fileUri = folder.resolve("changeLocalIssueStatus.py").toUri().toString();
    assertLocalIssuesStatusChanged(folderUri, fileUri);
  }

  @Test
  void shouldReopenResolvedLocalIssues() throws URISyntaxException, IOException {
    var folder = makeStaticTempDir();
    var folderUri = folder.toUri().toString();
    bindProject(getFolderSettings(folderUri), CONNECTION_ID, PROJECT_KEY);

    var fileName = "changeAndReopenLocalIssueStatus.py";
    var fileUri = folder.resolve(fileName).toUri().toString();
    assertLocalIssuesStatusChanged(folderUri, fileUri);

    lsProxy.reopenResolvedLocalIssues(new SonarLintExtendedLanguageServer.ReopenAllIssuesForFileParams(fileName, fileUri, folderUri));
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));
  }

  @Test
  void shouldIgnoreRazorFile() {
    var configScopeId = folder1BaseDir.toUri().toString();
    addConfigScope(configScopeId);
    var uriInFolder = folder1BaseDir.resolve("shouldIgnore.razor").toUri().toString();
    didOpen(uriInFolder, "csharp", "@using System");

    awaitUntilAsserted(() -> waitForLogToContain("Analysis detected 0 issues and 0 Security Hotspots"));
    waitForLogToContain("'OmniSharp' skipped because there are no related files in the current project");
  }

  private void assertLocalIssuesStatusChanged(String configScope, String fileUri) throws URISyntaxException {
    mockWebServerExtension.addResponse("/api/issues/anticipated_transitions?projectKey=" + PROJECT_KEY, new MockResponse().setResponseCode(202));
    mockWebServerExtension.addResponse("/api/issues/add_comment", new MockResponse().setResponseCode(200));
    mockNoIssueAndNoTaintInIncrementalSync();
    mockWebServerExtension.addProtobufResponse(
      "/api/hotspots/search.protobuf?projectKey=" + PROJECT_KEY + "&files=" + getFileNameFromFileUri(fileUri) + "&branch=master&ps=500&p=1",
      Hotspots.SearchWsResponse.newBuilder().build());

    addConfigScope(configScope);
    lsProxy.didLocalBranchNameChange(new SonarLintExtendedLanguageServer.DidLocalBranchNameChangeParams(configScope, "some/branch/name"));

    var content = "def foo():\n  toto = 0\n  plouf = 0\n";
    didOpen(fileUri, "python", content);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));

    var diagnostics = client.getDiagnostics(fileUri);
    var issueKey = ((JsonObject) diagnostics.stream().filter(it -> it.getMessage().equals("Remove the unused local variable \"plouf\"."))
      .findFirst().get().getData()).get("entryKey").getAsString();
    lsProxy.changeIssueStatus(new SonarLintExtendedLanguageServer.ChangeIssueStatusParams(configScope, issueKey,
      "False positive", fileUri, "", false));

    // Now we expect that one issue is resolved
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarqube", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning)));
  }

  private String getFileNameFromFileUri(String fileUri) throws URISyntaxException {
    return Paths.get(new URI(fileUri)).getFileName().toString();
  }

  @Test
  void shouldReportTaintIssues() {
    var analyzedFileName = "shouldReportTaintIssues.py";
    var issueKey = UUID.randomUUID().toString();
    mockNoIssuesNoHotspotsForProject(analyzedFileName);
    var fileUri = folder1BaseDir.resolve(analyzedFileName).toUri().toString();
    // Full sync returns the taint
    mockWebServerExtension.addProtobufResponseDelimited(
      "/api/issues/pull_taint?projectKey=myProject&branchName=master&languages=" + LANGUAGES_LIST,
      Issues.TaintVulnerabilityPullQueryTimestamp.newBuilder()
        .setQueryTimestamp(CURRENT_TIME)
        .build(),
      Issues.TaintVulnerabilityLite.newBuilder()
        .setKey(issueKey)
        .setRuleKey("ruleKey")
        .setType(Common.RuleType.BUG)
        .setSeverity(Common.Severity.MAJOR)
        .setMainLocation(Issues.Location.newBuilder().setFilePath(analyzedFileName).setMessage("message")
          .setTextRange(Issues.TextRange.newBuilder()
            .setStartLine(1)
            .setStartLineOffset(1)
            .setEndLine(1)
            .setEndLineOffset(2)
            .setHash("hash")))
        .setCreationDate(Instant.now().toEpochMilli())
        .build());

    addConfigScope(folder1BaseDir.toUri().toString());
    var content = "def foo():\n  toto = 0\n  plouf = 0\n";
    didOpen(fileUri, "python", content);

    awaitUntilAsserted(() -> assertThat(client.getTaints(fileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .contains(tuple(0, 1, 0, 2, "ruleKey", "Latest SonarQube Server Analysis", "message", DiagnosticSeverity.Error)));
  }

  @Test
  void should_automatically_suggest_connection_to_client() throws Exception {
    client.suggestConnectionLatch = new CountDownLatch(1);

    var serverUrl = "https://random.sqs.url.com";

    bindingSuggestionBaseDir = makeStaticTempDir();
    var bindingClueFileName = "connectedMode.json";
    var bindingClueFile = new SonarLintExtendedLanguageClient.FoundFileDto(
      bindingClueFileName, bindingSuggestionBaseDir.resolve(".sonarlint").resolve(bindingClueFileName).toFile().getAbsolutePath(),
      "{\"sonarQubeUri\": \"" + serverUrl + "\", \"projectKey\": \"" + PROJECT_KEY + "\"}");
    setUpFindFilesInFolderResponse(bindingSuggestionBaseDir.toUri().toString(), List.of(bindingClueFile));

    addFolder(bindingSuggestionBaseDir.toUri().toString(), bindingSuggestionBaseDir.getFileName().toString());

    assertTrue(client.suggestConnectionLatch.await(10, SECONDS));

    assertThat(client.suggestConnections).isNotNull();
    assertThat(client.suggestConnections.getSuggestionsByConfigScopeId()).isNotEmpty();
    assertThat(client.suggestConnections.getSuggestionsByConfigScopeId().get(bindingSuggestionBaseDir.toUri().toString())).isNotNull();
  }

  @Test
  void should_allow_client_to_explicitly_ask_for_binding_suggestions() {
    var workspaceUri = folder1BaseDir.resolve("foo-bar").toUri().toString();
    client.folderSettings = new HashMap<>();
    client.folderSettings.put(workspaceUri, new HashMap<>());
    addFolder(workspaceUri, "foo-bar");

    // Availability of binding suggestions for the added folder can take some time
    awaitUntilAsserted(() -> {
      var result = lsProxy.getBindingSuggestion(new GetBindingSuggestionParams(workspaceUri, CONNECTION_ID)).get();
      assertThat(result).isNotNull();
      assertThat(result.getSuggestions()).isEmpty();
    });
  }

  @Test
  void should_allow_client_to_explicitly_ask_for_connection_suggestions() throws InterruptedException, IOException {
    client.suggestConnectionLatch = new CountDownLatch(1);

    var serverUrl = "https://random.sqs.url.com";

    bindingSuggestionBaseDir = makeStaticTempDir();
    var bindingClueFileName = "connectedMode.json";
    var bindingClueFile = new SonarLintExtendedLanguageClient.FoundFileDto(
      bindingClueFileName, bindingSuggestionBaseDir.resolve(".sonarlint").resolve(bindingClueFileName).toFile().getAbsolutePath(),
      "{\"sonarQubeUri\": \"" + serverUrl + "\", \"projectKey\": \"" + PROJECT_KEY + "\"}");
    setUpFindFilesInFolderResponse(bindingSuggestionBaseDir.toUri().toString(), List.of(bindingClueFile));

    addFolder(bindingSuggestionBaseDir.toUri().toString(), bindingSuggestionBaseDir.getFileName().toString());

    assertTrue(client.suggestConnectionLatch.await(10, SECONDS));

    awaitUntilAsserted(() -> {
      var result = lsProxy.getConnectionSuggestions(new GetConnectionSuggestionsParams(bindingSuggestionBaseDir.toUri().toString())).get();
      var connectionSuggestions = result.getConnectionSuggestions();
      assertThat(connectionSuggestions).isNotNull();
      assertThat(connectionSuggestions).isNotEmpty();
      assertTrue(connectionSuggestions.get(0).getConnectionSuggestion().isLeft());
      assertThat(connectionSuggestions.get(0).getConnectionSuggestion().getLeft().getServerUrl()).isEqualTo(serverUrl);
      assertThat(connectionSuggestions.get(0).getConnectionSuggestion().getLeft().getProjectKey()).isEqualTo(PROJECT_KEY);
    });
  }

  @Test
  void should_generate_token_for_sonarqube_server() {
    var generateTokenParams = new SonarLintExtendedLanguageServer.GenerateTokenParams(mockWebServerExtension.url("/"));

    lsProxy.generateToken(generateTokenParams);

    awaitUntilAsserted(() -> assertThat(client.openedLinks).isNotEmpty());

    assertThat(client.openedLinks)
      .hasSize(1)
      .allMatch(url -> url.startsWith(mockWebServerExtension.url("/sonarlint/auth"))
        && !url.contains("utm"));
  }

  @Test
  void should_generate_token_for_sonarqube_cloud_with_link_tracking() {
    var generateTokenParams = new SonarLintExtendedLanguageServer.GenerateTokenParams("https://sonarcloud.io");

    lsProxy.generateToken(generateTokenParams);

    awaitUntilAsserted(() -> assertThat(client.openedLinks).isNotEmpty());

    assertThat(client.openedLinks)
      .hasSize(1)
      .allMatch(url -> url.startsWith("https://sonarcloud.io/sonarlint/auth")
        && url.contains("utm_medium=referral")
        && url.contains("utm_source=sq-ide-product-vscode")
        && url.contains("utm_content=create-new-sqc-connection")
        && url.contains("utm_term=generate-token"));
  }
}
