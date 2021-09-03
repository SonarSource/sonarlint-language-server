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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageServerWithFoldersMediumTests extends AbstractLanguageServerMediumTests {

  @BeforeAll
  public static void initialize() throws Exception {
    Path fakeTypeScriptProjectPath = Paths.get("src/test/resources/fake-ts-project").toAbsolutePath();

    client.settingsLatch = new CountDownLatch(1);
    String folderUri = "file:///init_uri";
    client.folderSettings.put(folderUri, buildSonarLintSettingsSection("some pattern", null, null, true));

    initialize(ImmutableMap.<String, Object>builder()
      .put("typeScriptLocation", fakeTypeScriptProjectPath.resolve("node_modules").toString())
      .put("telemetryStorage", "not/exists")
      .put("productName", "SLCORE tests")
      .put("productVersion", "0.1")
      .build(), new WorkspaceFolder(folderUri, "My Folder"));

    emulateConfigurationChangeOnClient(null, false, false, false);

    assertTrue(client.settingsLatch.await(5, SECONDS));
  }

  // Override parent to not clear folderSettings
  @Override
  public void cleanup() throws InterruptedException {
    // Wait for logs to stop being produced
    await().during(1, SECONDS).atMost(5, SECONDS).until(() -> {
      int count = client.logs.size();
      client.logs.clear();
      return count;
    }, equalTo(0));
  }

  @Test
  public void analyzeSimpleJsFileOnOpen() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true);

    String uri = getUri("analyzeSimpleJsFileOnOpen.js");
    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "javascript", "function foo() {\n  var toto = 0;\n  var plouf = 0;\n}");

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(1, 6, 1, 10, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'toto' variable.", DiagnosticSeverity.Information),
        tuple(2, 6, 2, 11, "javascript:S1481", "sonarlint", "Remove the declaration of the unused 'plouf' variable.", DiagnosticSeverity.Information));
  }

}
