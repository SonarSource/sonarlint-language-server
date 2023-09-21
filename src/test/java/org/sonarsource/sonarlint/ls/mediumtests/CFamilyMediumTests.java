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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.eclipse.lsp4j.DiagnosticSeverity.Warning;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfSystemProperty(named = "commercial", matches = ".*", disabledReason = "Commercial plugin not available")
class CFamilyMediumTests extends AbstractLanguageServerMediumTests {
  @BeforeAll
  static void initialize() throws Exception {
    initialize(Map.of(
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1"));
  }

  @Override
  protected void setUpFolderSettings(Map<String, Map<String, Object>> folderSettings) {
    super.setUpFolderSettings(folderSettings);
    client.readyForTestsLatch = new CountDownLatch(1);
  }

  @Override
  protected void verifyConfigurationChangeOnClient() {
    try {
      assertTrue(client.readyForTestsLatch.await(15, SECONDS));
    } catch (InterruptedException e) {
      fail(e);
    }
  }

  @Test
  void analyzeSimpleCppFileOnOpen(@TempDir Path cppProjectBaseDir) throws IOException, InterruptedException {
    var mockClang = mockClangCompiler();

    var cppFile = cppProjectBaseDir.resolve("analyzeSimpleCppFileOnOpen.cpp");
    Files.createFile(cppFile);
    var cppFileUri = cppFile.toUri().toString();

    var compilationDatabaseFile = prepareCompilationDatabase(cppProjectBaseDir, mockClang, cppFile);

    setPathToCompileCommands(client.globalSettings, compilationDatabaseFile.toString());
    notifyConfigurationChangeOnClient();

    didOpen(cppFileUri, "cpp",
      "int main() {\n" +
        "    int i = 0;\n" +
        "    return 0;\n" +
        "}\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(cppFileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(tuple(1, 8, 1, 9, "cpp:S1481", "sonarlint", "unused variable 'i'", Warning)));

    assertThat(client.needCompilationDatabaseCalls.get()).isZero();
  }

  @Test
  void skipCppAnalysisIfMissingCompilationCommand(@TempDir Path cppProjectBaseDir) throws IOException, InterruptedException {
    var cppFile = cppProjectBaseDir.resolve("skipCppAnalysisIfMissingCompilationCommand.cpp");
    Files.createFile(cppFile);
    var cppFileUri = cppFile.toUri().toString();

    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    didOpen(cppFileUri, "cpp",
      "int main() {\n" +
        "    int i = 0;\n" +
        "    return 0;\n" +
        "}\n");

    awaitUntilAsserted(() -> assertLogContains("Skipping analysis of C and C++ file(s) because no compilation database was configured"));
    awaitUntilAsserted(() -> assertThat(client.needCompilationDatabaseCalls.get()).isEqualTo(1));
    assertThat(client.getDiagnostics(cppFileUri)).isEmpty();
  }

  @Test
  void skipCppAnalysisIfInvalidCompilationCommand(@TempDir Path cppProjectBaseDir) throws IOException, InterruptedException {
    var cppFile = cppProjectBaseDir.resolve("skipCppAnalysisIfInvalidCompilationCommand.cpp");
    Files.createFile(cppFile);
    var cppFileUri = cppFile.toUri().toString();

    setShowVerboseLogs(client.globalSettings, true);
    setPathToCompileCommands(client.globalSettings, "non/existing/file");
    notifyConfigurationChangeOnClient();

    didOpen(cppFileUri, "cpp",
      "int main() {\n" +
        "    int i = 0;\n" +
        "    return 0;\n" +
        "}\n");

    awaitUntilAsserted(() -> assertLogContains("Skipping analysis of C and C++ file(s) because configured compilation database does not exist: non/existing/file"));
    awaitUntilAsserted(() -> assertThat(client.needCompilationDatabaseCalls.get()).isEqualTo(1));
    assertThat(client.getDiagnostics(cppFileUri)).isEmpty();
  }

  @Test
  void analyzeCppFileOnCompileCommandsSettingChanged(@TempDir Path cppProjectBaseDir) throws IOException, InterruptedException {
    var mockClang = mockClangCompiler();

    var cppFile = cppProjectBaseDir.resolve("analyzeCppFileOnCompileCommandsSettingChanged.cpp");
    Files.createFile(cppFile);
    var cppFileUri = cppFile.toUri().toString();

    var compilationDatabaseFile = prepareCompilationDatabase(cppProjectBaseDir, mockClang, cppFile);

    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    didOpen(cppFileUri, "cpp",
      "int main() {\n" +
        "    int i = 0;\n" +
        "    return 0;\n" +
        "}\n");

    awaitUntilAsserted(() -> assertLogContains("Skipping analysis of C and C++ file(s) because no compilation database was configured"));
    awaitUntilAsserted(() -> assertThat(client.needCompilationDatabaseCalls.getAndSet(0)).isEqualTo(1));
    assertThat(client.getDiagnostics(cppFileUri)).isEmpty();

    setPathToCompileCommands(client.globalSettings, compilationDatabaseFile.toString());
    notifyConfigurationChangeOnClient();

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(cppFileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(tuple(1, 8, 1, 9, "cpp:S1481", "sonarlint", "unused variable 'i'", Warning)));

    assertThat(client.needCompilationDatabaseCalls.get()).isZero();

  }

  private Path prepareCompilationDatabase(Path cppProjectBaseDir, Path mockClang, Path cppFile) throws IOException {
    var compilationDatabaseContent = "[\n" +
      "{\n" +
      "  \"directory\": \"" + StringEscapeUtils.escapeJson(cppProjectBaseDir.toString()) + "\",\n" +
      "  \"command\": \"" + StringEscapeUtils.escapeJson(mockClang.toString()) + " -x c++ " + StringEscapeUtils.escapeJson(cppFile.toString()) + "\",\n" +
      "  \"file\": \"" + StringEscapeUtils.escapeJson(cppFile.toString()) + "\"\n" +
      "}\n" +
      "]";

    var compilationDatabaseFile = cppProjectBaseDir.resolve("compile_commands.json");
    FileUtils.write(compilationDatabaseFile.toFile(), compilationDatabaseContent, StandardCharsets.UTF_8);
    return compilationDatabaseFile;
  }

  private Path mockClangCompiler() {
    String fileExt;
    if (SystemUtils.IS_OS_WINDOWS) {
      fileExt = "bat";
    } else {
      fileExt = "sh";
    }
    var mockClang = Paths.get("src/test/assets/cfamily/clang-test." + fileExt).toAbsolutePath();
    return mockClang;
  }
}
