/*
 * SonarLint Language Server
 * Copyright (C) 2009-2021 SonarSource SA
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

import com.google.common.collect.ImmutableMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
  public static void initialize() throws Exception {
    initialize(ImmutableMap.<String, String>builder()
      // Missing "typeScriptLocation"
      .put("telemetryStorage", "not/exists")
      .put("productName", "SLCORE tests")
      .put("productVersion", "0.1")
      .build());
  }

  @Test
  public void analyzeSimpleJsFileOnOpenWithoutTypescriptCompilerPath() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true);

    String uri = getUri("foo.js");
    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "javascript", "function foo() {\n  alert('toto');\n  var plouf = 0;\n}");

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(2, 6, 2, 11, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Information));
  }

  @Test
  public void noTypeScriptAnalysisWithoutTypescriptCompilerPath() throws Exception {
    // Enable analyzer debug logs for assertions
    emulateConfigurationChangeOnClient("**/*Test.js", null, true, true);

    Path tsconfig = temp.resolve("tsconfig.json");
    Files.write(tsconfig, "{}".getBytes(StandardCharsets.UTF_8));
    String uri = getUri("foo.ts");

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "typescript", "function foo() {\n if(bar() && bar()) { return 42; }\n}");

    assertThat(diagnostics).isEmpty();

    await().atMost(5, SECONDS)
      .untilAsserted(() -> assertThat(client.logs)
        .extracting(withoutTimestamp())
        .contains("[Error] Missing TypeScript dependency"));
  }

}
