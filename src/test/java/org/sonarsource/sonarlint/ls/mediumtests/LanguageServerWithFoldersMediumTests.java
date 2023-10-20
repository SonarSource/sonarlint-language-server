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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LanguageServerWithFoldersMediumTests extends AbstractLanguageServerMediumTests {

  private static final String PYTHON_S1481 = "python:S1481";

  private static Path folder1BaseDir;

  private static Path folder2BaseDir;

  @BeforeAll
  public static void initialize() throws Exception {
    folder1BaseDir = makeStaticTempDir();
    folder2BaseDir = makeStaticTempDir();
    initialize(Map.of(
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1"), new WorkspaceFolder(folder1BaseDir.toUri().toString(), "My Folder 1"));
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
      .extracting(withoutTimestamp())
      .contains("[Info] Found 0 issues"));

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
      .extracting(withoutTimestamp())
      .contains("[Info] Found 0 issues",
        "[Info] Found 0 issues"));

    client.logs.clear();

    // two consecutive changes should be batched
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file1InFolder, 2),
        List.of(new TextDocumentContentChangeEvent("def foo():\n  toto = 0\n  plouf = 0\n"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file2InFolder, 2),
        List.of(new TextDocumentContentChangeEvent("def foo():\n  toto2 = 0\n  plouf2 = 0\n"))));

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .containsSubsequence(
        "[Debug] Queuing analysis of 2 files",
        "[Info] Analyzing 2 files...",
        "[Info] Found 4 issues"));
  }

  @Test
  void shouldNotBatchAnalysisFromDifferentFolders() throws Exception {
    // Simulate opening of a second workspace folder
    lsProxy.getWorkspaceService().didChangeWorkspaceFolders(
      new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(List.of(new WorkspaceFolder(folder2BaseDir.toUri().toString(), "My Folder 2")), List.of())));

    var file1InFolder1 = folder1BaseDir.resolve("file1.py").toUri().toString();
    var file2InFolder2 = folder2BaseDir.resolve("file2.py").toUri().toString();

    didOpen(file1InFolder1, "python", "def foo():\n  toto = 0\n  plouf = 0\n");
    didOpen(file2InFolder2, "python", "def foo():\n  toto2 = 0\n  plouf2 = 0\n");

    client.logs.clear();

    // two consecutive changes on different folders should not be batched
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file1InFolder1, 2),
        List.of(new TextDocumentContentChangeEvent("def foo():\n  toto = 0\n  plouf = 0\n"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file2InFolder2, 2),
        List.of(new TextDocumentContentChangeEvent("def foo():\n  toto2 = 0\n  plouf2 = 0\n"))));

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .containsSubsequence(
        "[Debug] Queuing analysis of 2 files",
        "[Info] Found 2 issues",
        "[Info] Found 2 issues")
      // We don't know the order of analysis for the 2 files, so we can't have a single assertion
      .contains(
        "[Info] Analyzing file \"" + file1InFolder1 + "\"...",
        "[Info] Analyzing file \"" + file2InFolder2 + "\"..."));
  }

  @Test
  void shouldOpenRuleDescFromCodeAction() throws Exception {
    client.showRuleDescriptionLatch = new CountDownLatch(1);

    lsProxy.getWorkspaceService()
      .executeCommand(new ExecuteCommandParams(
        "SonarLint.OpenRuleDescCodeAction",
        List.of(PYTHON_S1481, folder1BaseDir.resolve("foo.py").toUri().toString(), "")))
      .get();
    assertTrue(client.showRuleDescriptionLatch.await(1, TimeUnit.MINUTES));

    assertThat(client.ruleDesc.getKey()).isEqualTo(PYTHON_S1481);
    assertThat(client.ruleDesc.getName()).isEqualTo("Unused local variables should be removed");
    assertThat(client.ruleDesc.getHtmlDescription()).contains("If a local variable is declared but not used, it is dead code and should be removed.");
    assertThat(client.ruleDesc.getType()).isEqualTo("CODE_SMELL");
    assertThat(client.ruleDesc.getSeverity()).isEqualTo("MINOR");
  }

}
