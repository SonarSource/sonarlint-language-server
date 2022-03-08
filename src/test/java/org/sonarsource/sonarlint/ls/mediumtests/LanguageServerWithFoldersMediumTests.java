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
import java.nio.file.Paths;
import java.util.Map;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class LanguageServerWithFoldersMediumTests extends AbstractLanguageServerMediumTests {

  @TempDir
  public static Path folderBaseDir;

  @BeforeAll
  public static void initialize() throws Exception {
    var fakeTypeScriptProjectPath = Paths.get("src/test/resources/fake-ts-project").toAbsolutePath();

    client.folderSettings.put(folderBaseDir.toUri().toString(), buildSonarLintSettingsSection("**/*Test.js", null, null, true));

    initialize(Map.of(
      "typeScriptLocation", fakeTypeScriptProjectPath.resolve("node_modules").toString(),
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1"), new WorkspaceFolder(folderBaseDir.toUri().toString(), "My Folder"));

  }

  @Test
  void analysisShouldUseFolderSettings() throws Exception {
    // In folder settings, the test pattern is **/*Test.js while in global config we put **/*.js
    emulateConfigurationChangeOnClient("**/*.js", true);

    var uriInFolder = folderBaseDir.resolve("inFolder.js").toUri().toString();
    var diagnosticsInFolder = didOpenAndWaitForDiagnostics(uriInFolder, "javascript", "function foo() {\n  var toto = 0;\n  var plouf = 0;\n}");

    assertThat(diagnosticsInFolder)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 6, 1, 10, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Information),
        tuple(2, 6, 2, 11, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Information));

    var uriOutsideFolder = getUri("outsideFolder.js");
    var diagnosticsOutsideFolder = didOpenAndWaitForDiagnostics(uriOutsideFolder, "javascript", "function foo() {\n  var toto = 0;\n  var plouf = 0;\n}");

    // File is considered as test file
    assertThat(diagnosticsOutsideFolder).isEmpty();
  }

}
