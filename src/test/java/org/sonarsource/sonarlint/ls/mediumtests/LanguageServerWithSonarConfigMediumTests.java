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
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.BindingInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class LanguageServerWithSonarConfigMediumTests extends AbstractLanguageServerMediumTests {

  private static final String CONNECTION_ID = "sq";
  private static final String SERVER_URL = "http://localhost:1";

  private static Path repoDir;

  @BeforeAll
  static void initialize() throws Exception {
    repoDir = makeStaticTempDir();
    Files.createDirectory(repoDir.resolve(".git"));
    Files.createDirectory(repoDir.resolve("moduleA"));
    Files.createDirectory(repoDir.resolve("moduleB"));
    Files.writeString(repoDir.resolve(".sonar-config.json"),
      "{\"projects\":[{\"projectKey\":\"module-a\",\"path\":\"moduleA\",\"serverUrl\":\"" + SERVER_URL + "\"},"
        + "{\"projectKey\":\"module-b\",\"path\":\"moduleB\",\"serverUrl\":\"http://no-connection-for-this-server\"}]}");
    initialize(Map.of(
        "telemetryStorage", "not/exists",
        "productName", "SLCORE tests",
        "productVersion", "0.1",
        "productKey", "productKey"),
      new WorkspaceFolder(repoDir.toUri().toString(), "repo"));
  }

  @Override
  protected void setupGlobalSettings(Map<String, Object> globalSettings) {
    addSonarQubeConnection(globalSettings, CONNECTION_ID, SERVER_URL, "token");
  }

  @Override
  protected void setUpFolderSettings(Map<String, Map<String, Object>> folderSettings) {
    setTestFilePattern(getFolderSettings(repoDir.toUri().toString()), "**/*Test.py");
  }

  @Test
  void tellsTheClientToRequestTheBindingsOnceTheScopesAreDeclared() {
    awaitUntilAsserted(() -> assertThat(client.bindingsChangedCount.get()).isPositive());
  }

  @Test
  void listsOnlyTheBindingsWithAConnectionThroughTheRequest() {
    awaitUntilAsserted(() -> assertThat(lsProxy.getBindings().join())
      .extracting(BindingInfo::scopeUri, BindingInfo::projectKey, BindingInfo::connectionId, BindingInfo::relativePath, BindingInfo::source)
      .containsExactly(tuple(repoDir.resolve("moduleA").toUri().toString(), "module-a", CONNECTION_ID, "moduleA", "SONAR_CONFIG")));
  }

  @Test
  void analyzesAFileInAnUnboundSubProject() {
    var uriInSubProject = repoDir.resolve("moduleB").resolve("inSubProject.py").toUri().toString();

    didOpen(uriInSubProject, "python", "def foo():\n  toto = 0\n");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uriInSubProject)).extracting(startLine(), code()).containsExactly(tuple(1, "python:S1481")));
  }
}
