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

import java.util.List;
import java.net.URI;
import java.util.Map;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

class NotebookMediumTests extends AbstractLanguageServerMediumTests {

  @BeforeAll
  static void initialize() throws Exception {
    initialize(Map.of(
      "telemetryStorage", "not/exists",
      "productName", "SLCORE notebook tests",
      "productVersion", "0.1",
      "showVerboseLogs", false,
      "enableNotebooks", true
    ));
  }

  @Override
  protected void setupGlobalSettings(Map<String, Object> globalSettings) {
    setShowVerboseLogs(globalSettings, true);
  }

  @Test
  void analyzeNotebookOnOpen() throws Exception {
    var uri = getUri("analyzeNotebookOnOpen.ipynb");

    didOpenNotebook(uri,
      // First cell has no issue
      "def no_issue():\n  print('Hello')\n",
      // Second cell has an issue
      "def foo():\n  print 'toto'\n"
    );

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri + "#2"))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 2, 1, 7, "ipython:PrintStatementUsage", "sonarlint", "Replace print statement by built-in function.", DiagnosticSeverity.Warning)));
    assertThat(client.getDiagnostics(uri + "#1")).isEmpty();
    assertThat(client.getDiagnostics(uri)).isEmpty();

    didOpen(uri, "ignored", "ignored");
    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains(
        String.format("[Debug] Skipping text document analysis of notebook \"%s\"", uri)));
    assertThat(client.getDiagnostics(uri)).isEmpty();
  }

  @Test
  void shouldLogIllegalStateErrorWhenDidChangeReceivedAndNotebookIsNotOpen() {
    var notebookUri = URI.create("file:///some/notebook.ipynb");

    var cell1 = new TextDocumentItem();
    cell1.setUri(notebookUri + "#cell1");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var cell2 = new TextDocumentItem();
    cell2.setUri(notebookUri + "#cell2");
    cell2.setText("cell2 line1\ncell2 line2\n");

    setShowVerboseLogs(client.globalSettings, true);
    var logger = mock(LanguageClientLogger.class);
    doCallRealMethod().when(logger).warn(any());

    didChangeNotebook(notebookUri.toString(), "newContent");

    awaitUntilAsserted(() -> assertLogContains("Illegal state. File \"file:///some/notebook.ipynb\" is reported changed but we missed the open notification"));
  }

  @Test
  void analyseOpenNotebookIgnoringExcludes() {
    var fileName = "analyseOpenNotebookIgnoringExcludes.ipynb";
    var fileUri = temp.resolve(fileName).toUri().toString();

    lsProxy.analyseOpenFileIgnoringExcludes(new SonarLintExtendedLanguageServer.AnalyseOpenFileIgnoringExcludesParams(
      null, fileUri, 0,
      List.of(new TextDocumentItem(fileUri + "#1", "python", 1, "def foo():\n  print 'toto'\n"))));

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(fileUri + "#1"))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage,
        Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(1, 2, 1, 7, "ipython:PrintStatementUsage", "sonarlint", "Replace print statement by built-in function.", DiagnosticSeverity.Warning)));
  }
}
