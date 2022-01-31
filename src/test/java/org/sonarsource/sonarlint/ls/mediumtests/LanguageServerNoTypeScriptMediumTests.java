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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;

class LanguageServerNoTypeScriptMediumTests extends AbstractLanguageServerMediumTests {

  @BeforeAll
  static void initialize() throws Exception {
    initialize(Map.ofEntries(
      // Missing "typeScriptLocation"
      Map.entry("telemetryStorage", "not/exists"),
      Map.entry("productName", "SLCORE tests"),
      Map.entry("productVersion", "0.1")));
  }

  @Test
  void analyzeSimpleJsFileOnOpenWithoutTypescriptCompilerPath() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true);

    var uri = getUri("foo.js");
    var diagnostics = didOpenAndWaitForDiagnostics(uri, "javascript", "function foo() {\n  alert('toto');\n  var plouf = 0;\n}");

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(2, 6, 2, 11, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Information));
  }

  @Test
  void noTypeScriptAnalysisWithoutTypescriptCompilerPath() throws Exception {
    // Enable analyzer debug logs for assertions
    emulateConfigurationChangeOnClient("**/*Test.js", null, true, true);

    var tsconfig = temp.resolve("tsconfig.json");
    Files.write(tsconfig, "{}".getBytes(StandardCharsets.UTF_8));
    var uri = getUri("foo.ts");

    var diagnostics = didOpenAndWaitForDiagnostics(uri, "typescript", "function foo() {\n if(bar() && bar()) { return 42; }\n}");

    assertThat(diagnostics).isEmpty();

    await().atMost(5, SECONDS)
      .untilAsserted(() -> assertThat(client.logs)
        .extracting(withoutTimestamp())
        .contains("[Error] Missing TypeScript dependency"));
  }

}
