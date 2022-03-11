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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.eclipse.lsp4j.DiagnosticSeverity.Information;

class CFamilyMediumTests extends AbstractLanguageServerMediumTests {

  @Test
  @EnabledIfSystemProperty(named = "commercial", matches = ".*", disabledReason = "Commercial plugin not available")
  void analyzeSimpleCppTestFileOnOpen(@TempDir Path cppProjectBaseDir) throws IOException, InterruptedException, URISyntaxException {
    var mockClang = CFamilyMediumTests.class.getResource("/clang");
    assertThat(mockClang).isNotNull();


    var cppFile = cppProjectBaseDir.resolve("analyzeSimpleCppTestFileOnOpen.cpp");
    Files.createFile(cppFile);
    var cppFileUri = cppFile.toUri().toString();


    var compilationDatabaseContent = "[\n" +
      "{\n" +
      "  \"directory\": \""+ cppProjectBaseDir + "\",\n" +
      "  \"command\": \"" + mockClang.toURI().getPath() + " -x c++ " + cppFile + "\",\n" +
      "  \"file\": \"" + cppFile + "\"\n" +
      "}\n" +
      "]";

    var compilationDatabaseFile = cppProjectBaseDir.resolve("compile_commands.json");
    FileUtils.write(compilationDatabaseFile.toFile(), compilationDatabaseContent, StandardCharsets.UTF_8);

    Map<String, String> analyserProperties = Map.of("sonar.cfamily.compile-commands", compilationDatabaseFile.toString());
    emulateConfigurationChangeOnClient(null, true, true, true,
      analyserProperties);

    var diagnostics = didOpenAndWaitForDiagnostics(cppFileUri, "cpp",
      "int main() {\n" +
      "    int i = 0;\n" +
      "    return 0;\n" +
      "}\n");

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(tuple(1, 8, 1, 9, "cpp:S1481", "sonarlint", "unused variable 'i'", Information));
  }

}
