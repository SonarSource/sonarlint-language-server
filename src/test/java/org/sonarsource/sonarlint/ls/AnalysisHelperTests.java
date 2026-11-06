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
package org.sonarsource.sonarlint.ls;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.ls.connected.Origin;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.notebooks.NotebookDiagnosticPublisher;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScope;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScopeManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisHelperTests {

  private static final String COMPILE_COMMANDS_PROPERTY = "sonar.cfamily.compile-commands";

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path basedir;

  private final SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  private final WorkspaceFoldersManager workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
  private final OpenFilesCache openFilesCache = mock(OpenFilesCache.class);
  private final ConfigurationScopeManager scopeManager = mock(ConfigurationScopeManager.class);
  private WorkspaceFolderWrapper folder;
  private AnalysisHelper underTest;

  @BeforeEach
  void prepare() {
    folder = new WorkspaceFolderWrapper(basedir.toUri(), new WorkspaceFolder(basedir.toUri().toString(), "folder"), logTester.getLogger());
    when(workspaceFoldersManager.findFolderForFile(any())).thenReturn(Optional.of(folder));
    underTest = new AnalysisHelper(client, logTester.getLogger(), workspaceFoldersManager, mock(JavaConfigCache.class), mock(SettingsManager.class),
      mock(IssuesCache.class), mock(HotspotsCache.class), mock(DiagnosticPublisher.class), mock(OpenNotebooksCache.class),
      mock(NotebookDiagnosticPublisher.class), openFilesCache, scopeManager);
  }

  @Test
  void the_path_to_compile_commands_of_the_scope_wins_over_the_one_of_the_folder() {
    folder.setSettings(settingsWithCompileCommands("/folder/compile_commands.json"));
    var scope = scopeWithCompileCommands("/project/build/compile_commands.json");

    var properties = underTest.getInferredAnalysisProperties(scope.uri().toString(), List.of());

    assertThat(properties).containsEntry(COMPILE_COMMANDS_PROPERTY, "/project/build/compile_commands.json");
  }

  @Test
  void the_path_to_compile_commands_of_the_folder_is_used_when_the_scope_has_none() {
    folder.setSettings(settingsWithCompileCommands("/folder/compile_commands.json"));
    var scope = scopeWithCompileCommands(null);

    var properties = underTest.getInferredAnalysisProperties(scope.uri().toString(), List.of());

    assertThat(properties).containsEntry(COMPILE_COMMANDS_PROPERTY, "/folder/compile_commands.json");
  }

  @Test
  void there_is_no_path_to_compile_commands_when_neither_the_scope_nor_the_folder_has_one() {
    folder.setSettings(settingsWithCompileCommands(null));
    var scope = scopeWithCompileCommands(null);

    var properties = underTest.getInferredAnalysisProperties(scope.uri().toString(), List.of());

    assertThat(properties).doesNotContainKey(COMPILE_COMMANDS_PROPERTY);
  }

  @Test
  void a_c_file_needs_no_compilation_database_when_the_scope_has_one() throws IOException {
    folder.setSettings(settingsWithCompileCommands(null));
    var compileCommands = Files.writeString(basedir.resolve("compile_commands.json"), "[]");
    var scope = scopeWithCompileCommands(compileCommands.toString());
    var cFile = openCFile();

    underTest.getInferredAnalysisProperties(scope.uri().toString(), List.of(cFile));

    verify(client, never()).needCompilationDatabase();
  }

  @Test
  void a_c_file_needs_a_compilation_database_when_the_one_of_the_scope_does_not_exist() {
    folder.setSettings(settingsWithCompileCommands(null));
    var scope = scopeWithCompileCommands(basedir.resolve("missing/compile_commands.json").toString());
    var cFile = openCFile();

    underTest.getInferredAnalysisProperties(scope.uri().toString(), List.of(cFile));

    verify(client).needCompilationDatabase();
  }

  private ConfigurationScope scopeWithCompileCommands(String pathToCompileCommands) {
    var scope = new ConfigurationScope(basedir.resolve("moduleA").toUri(), "moduleA", "moduleA", Origin.SONAR_CONFIG, null, null, pathToCompileCommands);
    when(scopeManager.scopeById(scope.uri().toString())).thenReturn(Optional.of(scope));
    return scope;
  }

  private URI openCFile() {
    var uri = basedir.resolve("moduleA/main.c").toUri();
    when(openFilesCache.getFile(uri)).thenReturn(Optional.of(new VersionedOpenFile(uri, "c", 1, "int main() { return 0; }")));
    return uri;
  }

  private static WorkspaceFolderSettings settingsWithCompileCommands(String pathToCompileCommands) {
    return new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null, pathToCompileCommands);
  }
}
