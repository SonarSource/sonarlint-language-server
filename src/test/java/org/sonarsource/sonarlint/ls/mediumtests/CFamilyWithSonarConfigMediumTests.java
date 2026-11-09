/*
 * SonarLint Language Server
 * Copyright (C) SonarSource Sàrl
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

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.eclipse.lsp4j.DiagnosticSeverity.Warning;

class CFamilyWithSonarConfigMediumTests extends AbstractLanguageServerMediumTests {

  private static final String CPP_CODE = """
    int main() {
        int i = 0;
        return 0;
    }
    """;

  private static Path repoDir;
  private static Path moduleDir;
  private static Path cppFile;
  private static Path brokenModuleCppFile;

  @BeforeAll
  static void initialize() throws Exception {
    repoDir = makeStaticTempDir();
    Files.createDirectory(repoDir.resolve(".git"));
    moduleDir = Files.createDirectories(repoDir.resolve("moduleA"));
    cppFile = Files.createFile(moduleDir.resolve("main.cpp"));
    var compilationDatabase = Files.createDirectories(moduleDir.resolve("build")).resolve("compile_commands.json");
    Files.writeString(compilationDatabase, compilationDatabaseContent());
    brokenModuleCppFile = Files.createFile(Files.createDirectories(repoDir.resolve("moduleB")).resolve("main.cpp"));
    Files.writeString(repoDir.resolve(".sonar-config.json"), new Gson().toJson(Map.of("projects", List.of(
      Map.of(
        "projectKey", "module-a",
        "path", "moduleA",
        "serverUrl", "http://no-connection-for-this-server",
        "settings", Map.of("sonar.cfamily.compile-commands", "build/compile_commands.json")),
      Map.of(
        "projectKey", "module-b",
        "path", "moduleB",
        "serverUrl", "http://no-connection-for-this-server",
        "settings", Map.of("sonar.cfamily.compile-commands", "build/missing_compile_commands.json"))))));
    initialize(Map.of(
        "telemetryStorage", "not/exists",
        "productName", "SLCORE tests",
        "productVersion", "0.1",
        "productKey", "productKey"),
      new WorkspaceFolder(repoDir.toUri().toString(), "repo"));
  }

  @Test
  void analyzesACppFileWithThePathToCompileCommandsOfTheSonarConfig() {
    var cppFileUri = cppFile.toUri().toString();

    didOpen(cppFileUri, "cpp", CPP_CODE);

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(cppFileUri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, message(), Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(tuple(1, 8, 1, 9, "cpp:S1481", "sonarqube", "unused variable 'i'", Warning)));
    assertThat(client.needCompilationDatabaseCalls.get()).isZero();
  }

  @Test
  void skipsTheAnalysisOfACppFileWhenThePathToCompileCommandsOfTheSonarConfigIsNotAFile() {
    var cppFileUri = brokenModuleCppFile.toUri().toString();
    setShowVerboseLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    didOpen(cppFileUri, "cpp", CPP_CODE);

    var missingFile = brokenModuleCppFile.getParent().resolve("build/missing_compile_commands.json").toString();
    awaitUntilAsserted(() -> assertLogContains("\"sonar.cfamily.compile-commands\" is not set to a valid file: " + missingFile));
    assertThat(client.getDiagnostics(cppFileUri)).isEmpty();
    assertThat(client.needCompilationDatabaseCalls.get()).isPositive();
  }

  private static String compilationDatabaseContent() {
    var mockClang = Paths.get("src/test/assets/cfamily/clang-test." + (SystemUtils.IS_OS_WINDOWS ? "bat" : "sh")).toAbsolutePath();
    return new Gson().toJson(List.of(Map.of(
      "directory", moduleDir.toString(),
      "command", mockClang + " -x c++ " + cppFile,
      "file", cppFile.toString())));
  }
}
