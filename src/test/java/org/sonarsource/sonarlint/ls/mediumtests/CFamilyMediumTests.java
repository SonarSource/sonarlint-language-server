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

import java.io.FileWriter;
import java.io.IOException;
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

class CFamilyMediumTests extends AbstractLanguageServerMediumTests {

  @Test
  @EnabledIfSystemProperty(named = "commercial", matches = ".*", disabledReason = "Commercial plugin not available")
  void analyzeSimpleCppTestFileOnOpen(@TempDir Path cppProjectBaseDir) throws IOException, InterruptedException {
    var cppFile = cppProjectBaseDir.resolve("analyzeSimpleCppTestFileOnOpen.cpp");
    Files.createFile(cppFile);
    var cppFileUri = cppFile.toUri().toString();

    var compilationDatabaseFile = cppProjectBaseDir.resolve("compile_commands.json");

    var compilationDatabaseContent = "[\n" +
      "{\n" +
      "  \"directory\": \""+ cppProjectBaseDir + "\",\n" +
      "  \"command\": \"/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/c++ -g -arch arm64 -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX12.0.sdk -std=gnu++14 -o CMakeFiles/T.dir/main.cpp.o -c"  + cppFile + "\",\n" +
      "  \"file\": \"" + cppFile + "\"\n" +
      "}\n" +
      "]";

    FileUtils.write(compilationDatabaseFile.toFile(), compilationDatabaseContent, StandardCharsets.UTF_8);

    Map<String, String> analyserProperties = Map.of("sonar.cfamily.compile-commands", compilationDatabaseFile.toString());
    emulateConfigurationChangeOnClient(null, true, true, true,
      analyserProperties);

    var diagnostics = didOpenAndWaitForDiagnostics(cppFileUri, "cpp", "#include <iostream>\n" +
      "\n" +
      "int main() {\n" +
      "    std::cout << \"Hello, World!\" << std::endl;\n" +
      "    return 0;\n" +
      "}\n");

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder();
  }

}
