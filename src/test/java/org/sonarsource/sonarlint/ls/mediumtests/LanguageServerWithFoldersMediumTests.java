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
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class LanguageServerWithFoldersMediumTests extends AbstractLanguageServerMediumTests {

  @TempDir
  public static Path folder1BaseDir;

  @TempDir
  public static Path folder2BaseDir;

  @BeforeAll
  public static void initialize() throws Exception {
    initialize(Map.of(
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1"), new WorkspaceFolder(folder1BaseDir.toUri().toString(), "My Folder 1"));
  }

  @Override
  protected void setUpFolderSettings(Map<String, Map<String, Object>> folderSettings) {
    setTestFilePattern(getFolderSettings(folder1BaseDir.toUri().toString()), "**/*Test.js");
    setTestFilePattern(getFolderSettings(folder2BaseDir.toUri().toString()), "**/*Test.js");
  }

  @Test
  void analysisShouldUseFolderSettings() throws Exception {
    setShowVerboseLogs(client.globalSettings, true);
    // In folder settings, the test pattern is **/*Test.js while in global config we put **/*.js
    setTestFilePattern(client.globalSettings, "**/*.js");
    notifyConfigurationChangeOnClient();

    var uriInFolder = folder1BaseDir.resolve("inFolder.js").toUri().toString();
    didOpen(uriInFolder, "javascript", "function foo() {\n  let toto = 0;\n  let plouf = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInFolder))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 6, 1, 10, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Information),
        tuple(2, 6, 2, 11, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Information)));

    client.logs.clear();

    var uriOutsideFolder = getUri("outsideFolder.js");
    didOpen(uriOutsideFolder, "javascript", "function foo() {\n  let toto = 0;\n  let plouf = 0;\n}");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Info] Found 0 issues"));

    // File is considered as test file
    assertThat(client.getDiagnostics(uriOutsideFolder)).isEmpty();
  }

  @Test
  void shouldBatchAnalysisFromTheSameFolder() throws Exception {
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    var file1InFolder = folder1BaseDir.resolve("file1.js").toUri().toString();
    var file2InFolder = folder1BaseDir.resolve("file2.js").toUri().toString();

    didOpen(file1InFolder, "javascript", "function foo() { /* Empty */ }");
    didOpen(file2InFolder, "javascript", "function foo() { /* Empty */ }");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Info] Found 0 issues",
        "[Info] Found 0 issues"));

    client.logs.clear();

    // two consecute changes should be batched
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file1InFolder, 2),
        List.of(new TextDocumentContentChangeEvent("function foo() {\n  let toto1 = 0;\n  let plouf1 = 0;\n}"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file2InFolder, 2),
        List.of(new TextDocumentContentChangeEvent("function foo() {\n  let toto2 = 0;\n  let plouf2 = 0;\n}"))));

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .containsSubsequence(
        "[Debug] Queuing analysis of 2 files",
        "[Info] Analyzing 2 files...",
        "[Info] Found 4 issues"));
  }

  @Test
  void shouldNotBatchAnalysisFromDifferentFolders() throws Exception {
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    // Simulate opening of a second workspace folder
    lsProxy.getWorkspaceService().didChangeWorkspaceFolders(
      new DidChangeWorkspaceFoldersParams(new WorkspaceFoldersChangeEvent(List.of(new WorkspaceFolder(folder2BaseDir.toUri().toString(), "My Folder 2")), List.of())));

    var file1InFolder1 = folder1BaseDir.resolve("file1.js").toUri().toString();
    var file2InFolder2 = folder2BaseDir.resolve("file2.js").toUri().toString();

    didOpen(file1InFolder1, "javascript", "function foo() {\n  let toto1 = 0;\n  let plouf1 = 0;\n}");
    didOpen(file2InFolder2, "javascript", "function foo() {\n  let toto2 = 0;\n  let plouf2 = 0;\n}");

    client.logs.clear();

    // two consecute changes on different folders should not be batched
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file1InFolder1, 2),
        List.of(new TextDocumentContentChangeEvent("function foo() {\n  let toto1 = 0;\n  let plouf1 = 0;\n}"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file2InFolder2, 2),
        List.of(new TextDocumentContentChangeEvent("function foo() {\n  let toto2 = 0;\n  let plouf2 = 0;\n}"))));

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .containsSubsequence(
        "[Debug] Queuing analysis of 2 files",
        "[Info] Found 2 issues",
        "[Info] Found 2 issues")
      // We don't know the order of analysis for the 2 files, so we can't have a single assertion
      .contains(
        "[Info] Analyzing file '" + file1InFolder1 + "'...",
        "[Info] Analyzing file '" + file2InFolder2 + "'..."));
  }

}
