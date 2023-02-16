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

import java.util.Map;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

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

  @Test
  void analyzeNotebookOnOpen() throws Exception {
    var uri = getUri("analyzeNotebookOnOpen.ipynb");

    didOpenNotebook(uri,
      // First cell has no issue
      "def no_issue():\n  print('Hello')\n",
      // Second cell has an issue
      "def foo():\n  print 'toto'\n"
    );
    didOpen(uri, "ignored", "ignored");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri + "#2"))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 2, 1, 7, "ipython:PrintStatementUsage", "sonarlint", "Replace print statement by built-in function.", DiagnosticSeverity.Warning)));
    assertThat(client.getDiagnostics(uri + "#1")).isEmpty();
    assertThat(client.getDiagnostics(uri)).isEmpty();
  }

}
