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
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarcloud.ws.Organizations;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import testutils.MockWebServerExtension;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LanguageServerWithFoldersMediumTests extends AbstractLanguageServerMediumTests {

  private static final String PYTHON_S1481 = "python:S1481";

  private static Path folder1BaseDir;

  private static Path folder2BaseDir;

  private static final int SONAR_CLOUD_PORT = findAvailablePort();
  @RegisterExtension
  private static final MockWebServerExtension sonarCloudWebServer = new MockWebServerExtension(SONAR_CLOUD_PORT);

  private static int findAvailablePort() {
    try {
      ServerSocket socket = new ServerSocket(0);
      int port = socket.getLocalPort();
      socket.close();
      return port;
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  @BeforeAll
  public static void initialize() throws Exception {
    System.setProperty("sonarlint.internal.sonarcloud.url", "http://localhost:" + SONAR_CLOUD_PORT);
    System.setProperty("sonarlint.internal.sonarcloud.websocket.url", "http://localhost:40000" + SONAR_CLOUD_PORT);
    folder1BaseDir = makeStaticTempDir();
    folder2BaseDir = makeStaticTempDir();
    initialize(Map.of(
        "telemetryStorage", "not/exists",
        "productName", "SLCORE tests",
        "productVersion", "0.1",
        "productKey", "productKey"),
      new WorkspaceFolder(folder1BaseDir.toUri().toString(), "My Folder 1"));
  }

  @AfterAll
  public static void resetSonarCloud() {
    System.clearProperty("sonarlint.internal.sonarcloud.websocket.url");
    System.clearProperty("sonarlint.internal.sonarcloud.url");
  }

  @Override
  protected void setupGlobalSettings(Map<String, Object> globalSettings) {
    setShowVerboseLogs(client.globalSettings, true);
  }

  @Override
  protected void verifyConfigurationChangeOnClient() {
    try {
      assertTrue(client.readyForTestsLatch.await(15, SECONDS));
    } catch (InterruptedException e) {
      fail(e);
    }
  }

  @Override
  protected void setUpFolderSettings(Map<String, Map<String, Object>> folderSettings) {
    setTestFilePattern(getFolderSettings(folder1BaseDir.toUri().toString()), "**/*Test.py");
    setTestFilePattern(getFolderSettings(folder2BaseDir.toUri().toString()), "**/*Test.py");
  }

  @Test
  void analysisShouldUseFolderSettings() throws Exception {
    // In folder settings, the test pattern is **/*Test.py while in global config we put **/*.py
    setTestFilePattern(client.globalSettings, "**/*.py");
    notifyConfigurationChangeOnClient();

    var uriInFolder = folder1BaseDir.resolve("inFolder.py").toUri().toString();
    didOpen(uriInFolder, "python", "def foo():\n  toto = 0\n  plouf = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 2, 1, 6, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 7, PYTHON_S1481, "sonarlint", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning)));

    client.logs.clear();

    var uriOutsideFolder = getUri("outsideFolder.py");
    didOpen(uriOutsideFolder, "python", "def foo():\n  toto = 0\n  plouf = 0\n");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestampAndMillis())
      .contains("[Debug] Classified as test by configured 'testFilePattern' setting"));

    // File is considered as test file
    assertThat(client.getDiagnostics(uriOutsideFolder)).isEmpty();
  }

  @Test
  void shouldBatchAnalysisFromTheSameFolder() {
    var file1InFolder = folder1BaseDir.resolve("file1.py").toUri().toString();
    var file2InFolder = folder1BaseDir.resolve("file2.py").toUri().toString();

    didOpen(file1InFolder, "python", "def foo():\n  return\n");
    didOpen(file2InFolder, "python", "def foo():\n  return\n");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestampAndMillis())
      .contains("[Info] Analysis detected 0 issues and 0 Security Hotspots in XXXms",
        "[Info] Analysis detected 0 issues and 0 Security Hotspots in XXXms"));

    client.logs.clear();

    // two consecutive changes should be batched
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file1InFolder, 2),
        List.of(new TextDocumentContentChangeEvent("def foo():\n  toto = 0\n  plouf = 0\n"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file2InFolder, 2),
        List.of(new TextDocumentContentChangeEvent("def foo():\n  toto2 = 0\n  plouf2 = 0\n"))));

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestampAndMillis())
      .containsSubsequence(
        "[Info] Analysis detected 2 issues and 0 Security Hotspots in XXXms",
        "[Info] Analysis detected 2 issues and 0 Security Hotspots in XXXms"));
  }

  @Test
  void shouldNotBatchAnalysisFromDifferentFolders() {
    // Simulate opening of a second workspace folder
    lsProxy.getWorkspaceService().didChangeWorkspaceFolders(
      new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(List.of(new WorkspaceFolder(folder2BaseDir.toUri().toString(), "My Folder 2")), List.of())));

    var file1InFolder1 = folder1BaseDir.resolve("file1.py").toUri().toString();
    var file2InFolder2 = folder2BaseDir.resolve("file2.py").toUri().toString();

    didOpen(file1InFolder1, "python", "def foo():\n  toto = 0\n");
    didOpen(file2InFolder2, "python", "def foo():\n  toto2 = 0\n");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestampAndMillis())
      .containsSubsequence(
        "[Info] Analysis detected 1 issue and 0 Security Hotspots in XXXms",
        "[Info] Analysis detected 1 issue and 0 Security Hotspots in XXXms"));

    client.logs.clear();

    // two consecutive changes on different folders should not be batched
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file1InFolder1, 2),
        List.of(new TextDocumentContentChangeEvent("def foo():\n  toto = 0\n  plouf = 0\n"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file2InFolder2, 2),
        List.of(new TextDocumentContentChangeEvent("def foo():\n  toto2 = 0\n  plouf2 = 0\n"))));

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestampAndMillis())
      .containsSubsequence(
        "[Info] Analysis detected 2 issues and 0 Security Hotspots in XXXms",
        "[Info] Analysis detected 2 issues and 0 Security Hotspots in XXXms"));
  }

  @Test
  void shouldOpenRuleDescFromCodeAction() throws Exception {
    var file1InFolder = folder1BaseDir.resolve("file1.py").toUri().toString();

    didOpen(file1InFolder, "python", "def foo():\n  toto = 0\n  plouf = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(file1InFolder))
      .hasSize(2));
    var issueId = ((JsonObject) client.getDiagnostics(file1InFolder).get(0).getData()).get("entryKey").getAsString();

    client.showRuleDescriptionLatch = new CountDownLatch(1);

    lsProxy.getWorkspaceService()
      .executeCommand(new ExecuteCommandParams(
        "SonarLint.ShowIssueDetailsCodeAction",
        List.of(issueId, file1InFolder)))
      .get();

    assertTrue(client.showRuleDescriptionLatch.await(1, TimeUnit.MINUTES));

    var ruleDescriptionTabNonContextual = client.ruleDesc.getHtmlDescriptionTabs()[0].getRuleDescriptionTabNonContextual();
    var htmlContent = ruleDescriptionTabNonContextual != null ? ruleDescriptionTabNonContextual.getHtmlContent() : "";

    assertThat(client.ruleDesc.getKey()).isEqualTo(PYTHON_S1481);
    assertThat(client.ruleDesc.getName()).isEqualTo("Unused local variables should be removed");
    assertThat(htmlContent).contains("It is dead code,\n" +
      "contributing to unnecessary complexity and leading to confusion when reading the code.");
    assertThat(client.ruleDesc.getType()).isNull();
    assertThat(client.ruleDesc.getSeverity()).isNull();
    assertThat(client.ruleDesc.getCleanCodeAttribute()).isEqualTo("Not clear");
    assertThat(client.ruleDesc.getCleanCodeAttributeCategory()).isEqualTo("Intentionality");
    assertThat(client.ruleDesc.getImpacts()).containsEntry("Maintainability", "Low");

  }

  @Test
  void list_user_organizations() {
    var paging = Common.Paging.newBuilder()
      .setPageSize(500)
      .setTotal(1)
      .setPageIndex(1)
      .build();
    var organization = Organizations.Organization.newBuilder()
      .setKey("key")
      .setName("name")
      .build();
    sonarCloudWebServer.addProtobufResponse("/api/organizations/search.protobuf?member=true&ps=500&p=1",
      Organizations.SearchWsResponse.newBuilder()
        .addAllOrganizations(List.of(organization))
        .setPaging(paging)
        .build());
    var token = "123456";
    var result = lsProxy.listUserOrganizations(token).join();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKey()).isEqualTo("key");
  }

}
