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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.SubProjectScopeInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class LanguageServerWithSonarConfigMediumTests extends AbstractLanguageServerMediumTests {

  private static Path repoDir;

  @BeforeAll
  static void initialize() throws Exception {
    repoDir = makeStaticTempDir();
    Files.createDirectory(repoDir.resolve(".git"));
    Files.createDirectory(repoDir.resolve("moduleA"));
    Files.writeString(repoDir.resolve(".sonar-config.json"),
      "{\"projects\":[{\"projectKey\":\"module-a\",\"path\":\"moduleA\",\"serverUrl\":\"https://sonarqube.example.com\"}]}");
    initialize(Map.of(
        "telemetryStorage", "not/exists",
        "productName", "SLCORE tests",
        "productVersion", "0.1",
        "productKey", "productKey"),
      new WorkspaceFolder(repoDir.toUri().toString(), "repo"));
  }

  @Override
  protected void setUpFolderSettings(Map<String, Map<String, Object>> folderSettings) {
    setTestFilePattern(getFolderSettings(repoDir.toUri().toString()), "**/*Test.py");
  }

  @Test
  void declaresTheSubProjectsOfTheSonarConfigAsChildScopes() {
    awaitUntilAsserted(() -> assertThat(client.subProjectScopes).isNotEmpty().allSatisfy(params -> {
      assertThat(params.parentFolderUri()).isEqualTo(repoDir.toUri().toString());
      assertThat(params.scopes())
        .extracting(SubProjectScopeInfo::scopeUri, SubProjectScopeInfo::projectKey, SubProjectScopeInfo::connectionId, SubProjectScopeInfo::relativePath)
        .containsExactly(tuple(repoDir.resolve("moduleA").toUri().toString(), "module-a", null, "moduleA"));
    }));
  }

  @Test
  void analyzesAFileInASubProject() {
    var uriInSubProject = repoDir.resolve("moduleA").resolve("inSubProject.py").toUri().toString();

    didOpen(uriInSubProject, "python", "def foo():\n  toto = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInSubProject)).extracting(startLine(), code()).containsExactly(tuple(1, "python:S1481")));
  }
}
