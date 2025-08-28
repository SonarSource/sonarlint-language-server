/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.eclipse.lsp4j.DiagnosticSeverity.Warning;

@EnabledIfSystemProperty(named = "commercial", matches = ".*", disabledReason = "Commercial plugin not available")
class CFamilyMediumTests extends AbstractLanguageServerMediumTests {
  @BeforeAll
  static void initialize() throws Exception {
    initialize(Map.of(
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1",
      "productKey", "productKey"));
  }

  @BeforeEach
  void clean() {
    setPathToCompileCommands(client.globalSettings, "");
  }

  @Test
  void analyzeSimpleCppFileOnOpen(@TempDir Path cppProjectBaseDir) throws IOException {
    var mockClang = mockClangCompiler();

    var cppFile = cppProjectBaseDir.resolve("analyzeSimpleCppFileOnOpen.cpp");
    Files.createFile(cppFile);
    var cppFileUri = cppFile.toUri().toString();

    var compilationDatabaseFile = prepareCompilationDatabase(cppProjectBaseDir, mockClang, cppFile);

    setPathToCompileCommands(client.globalSettings, compilationDatabaseFile.toString());
    notifyConfigurationChangeOnClient();

    didOpen(cppFileUri, "cpp",
      """
        int main() {
            int i = 0;
            return 0;
        }
        """);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(cppFileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(tuple(1, 8, 1, 9, "cpp:S1481", "sonarqube", "unused variable 'i'", Warning)));

    assertThat(client.needCompilationDatabaseCalls.get()).isZero();
  }

  @Test
  void skipCppAnalysisIfMissingCompilationCommand(@TempDir Path cppProjectBaseDir) throws IOException {
    var cppFile = cppProjectBaseDir.resolve("skipCppAnalysisIfMissingCompilationCommand.cpp");
    Files.createFile(cppFile);
    var cppFileUri = cppFile.toUri().toString();

    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    didOpen(cppFileUri, "cpp",
      """
        int main() {
            int i = 0;
            return 0;
        }
        """);

    awaitUntilAsserted(() -> waitForLogToContain("Specify the \"sonar.cfamily.compile-commands\" option to configure the C and C++ analysis"));
    assertThat(client.getDiagnostics(cppFileUri)).isEmpty();
  }

  @Test
  void skipCppAnalysisIfInvalidCompilationCommand(@TempDir Path cppProjectBaseDir) throws IOException {
    var cppFile = cppProjectBaseDir.resolve("skipCppAnalysisIfInvalidCompilationCommand.cpp");
    Files.createFile(cppFile);
    var cppFileUri = cppFile.toUri().toString();

    setShowVerboseLogs(client.globalSettings, true);
    setPathToCompileCommands(client.globalSettings, "non/existing/file");
    notifyConfigurationChangeOnClient();

    didOpen(cppFileUri, "cpp",
      """
        int main() {
            int i = 0;
            return 0;
        }
        """);

    awaitUntilAsserted(() -> assertLogContains("\"sonar.cfamily.compile-commands\" is not set to a valid file: non/existing/file"));
    assertThat(client.getDiagnostics(cppFileUri)).isEmpty();
    assertThat(client.needCompilationDatabaseCalls.get()).isEqualTo(1);
  }

  @Test
  void analyzeCppFileOnCompileCommandsSettingChanged(@TempDir Path cppProjectBaseDir) throws IOException {
    var mockClang = mockClangCompiler();

    var cppFile = cppProjectBaseDir.resolve("analyzeCppFileOnCompileCommandsSettingChanged.cpp");
    Files.createFile(cppFile);
    var cppFileUri = cppFile.toUri().toString();

    var compilationDatabaseFile = prepareCompilationDatabase(cppProjectBaseDir, mockClang, cppFile);

    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    didOpen(cppFileUri, "cpp",
      """
        int main() {
            int i = 0;
            return 0;
        }
        """);

    awaitUntilAsserted(() -> waitForLogToContain("Specify the \"sonar.cfamily.compile-commands\" option to configure the C and C++ analysis"));
    assertThat(client.getDiagnostics(cppFileUri)).isEmpty();

    setPathToCompileCommands(client.globalSettings, compilationDatabaseFile.toString());
    notifyConfigurationChangeOnClient();

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(cppFileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(tuple(1, 8, 1, 9, "cpp:S1481", "sonarqube", "unused variable 'i'", Warning)));

    assertThat(client.needCompilationDatabaseCalls.get()).isEqualTo(1);

  }

  private Path prepareCompilationDatabase(Path cppProjectBaseDir, Path mockClang, Path cppFile) throws IOException {
    var compilationDatabaseContent = """
      [
        {
          "directory": "%s",
          "command": "%s -x c++ %s",
          "file": "%s"
        }
      ]""".formatted(
      escapeJsonString(cppProjectBaseDir.toString()),
      escapeJsonString(mockClang.toString()),
      escapeJsonString(cppFile.toString()),
      escapeJsonString(cppFile.toString()));

    var compilationDatabaseFile = cppProjectBaseDir.resolve("compile_commands.json");
    FileUtils.write(compilationDatabaseFile.toFile(), compilationDatabaseContent, StandardCharsets.UTF_8);
    return compilationDatabaseFile;
  }

  private static String escapeJsonString(String input) {
    if (input == null) {
      return "null";
    }
    return input
      .replace("\\", "\\\\")  // Escape backslashes first
      .replace("\"", "\\\"")  // Escape double quotes
      .replace("\b", "\\b")   // Escape backspace
      .replace("\f", "\\f")   // Escape form feed
      .replace("\n", "\\n")   // Escape newline
      .replace("\r", "\\r")   // Escape carriage return
      .replace("\t", "\\t");  // Escape tab
  }

  private Path mockClangCompiler() {
    String fileExt;
    if (SystemUtils.IS_OS_WINDOWS) {
      fileExt = "bat";
    } else {
      fileExt = "sh";
    }
    return Paths.get("src/test/assets/cfamily/clang-test." + fileExt).toAbsolutePath();
  }
}
