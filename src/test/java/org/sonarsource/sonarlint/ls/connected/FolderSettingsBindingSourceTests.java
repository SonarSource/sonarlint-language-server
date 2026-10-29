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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FolderSettingsBindingSourceTests {

  private static final String CONNECTION_ID = "myServer";
  private static final WorkspaceFolderSettings UNBOUND = new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null, null);
  private static final WorkspaceFolderSettings BOUND = new WorkspaceFolderSettings(CONNECTION_ID, "myProject", Collections.emptyMap(), null, null);

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path basedir;

  private final SettingsManager settingsManager = mock(SettingsManager.class);
  private final Map<String, ServerConnectionSettings> servers = new HashMap<>();
  private FolderSettingsBindingSource underTest;
  private WorkspaceFolderWrapper folder;

  @BeforeEach
  void prepare() {
    underTest = new FolderSettingsBindingSource(settingsManager, logTester.getLogger());
    when(settingsManager.connectionOf(any())).thenAnswer(invocation -> servers.get(SettingsManager.connectionIdOrDefault(invocation.getArgument(0))));
    folder = spy(newFolder("folder"));
  }

  @Test
  void bindingOf_is_empty_for_a_folder_outside_the_local_filesystem() {
    var remoteUri = URI.create("vscode-vfs://github/org/repo");
    var remoteFolder = new WorkspaceFolderWrapper(remoteUri, new WorkspaceFolder(remoteUri.toString(), "repo"), logTester.getLogger());
    remoteFolder.setSettings(BOUND);

    assertThat(underTest.bindingOf(remoteFolder, scopeOf(remoteFolder))).isEmpty();
  }


  @Test
  void bindingOf_is_empty_without_binding() {
    folder.setSettings(UNBOUND);

    assertThat(underTest.bindingOf(folder, scopeOf(folder))).isEmpty();
  }

  @Test
  void bindingOf_describes_the_binding_of_the_folder_with_its_connection() {
    connection();
    folder.setSettings(BOUND);

    assertThat(underTest.bindingOf(folder, scopeOf(folder))).hasValueSatisfying(binding -> {
      assertThat(binding.projectKey()).isEqualTo("myProject");
      assertThat(binding.connectionId()).isEqualTo(CONNECTION_ID);
      assertThat(binding.target()).isEqualTo("connection 'myServer'");
    });
  }

  @Test
  void bindingOf_keeps_the_binding_but_not_the_connection_when_the_connection_is_unknown() {
    folder.setSettings(BOUND);

    assertThat(underTest.bindingOf(folder, scopeOf(folder))).hasValueSatisfying(binding -> {
      assertThat(binding.connectionId()).isNull();
      assertThat(binding.projectKey()).isEqualTo("myProject");
    });
    assertThat(logTester.logs())
      .anyMatch(log -> log.contains("Invalid binding for"));
  }

  @Test
  void bindingOf_does_not_resolve_the_connection_until_it_is_asked_for() {
    connection();
    folder.setSettings(BOUND);

    var scope = binding(folder);

    verify(settingsManager, never()).connectionOf(any());
    assertThat(scope.connectionId()).isEqualTo(CONNECTION_ID);
    assertThat(scope.connectionId()).isEqualTo(CONNECTION_ID);
    verify(settingsManager, times(1)).connectionOf(any());
  }

  private static Scope scopeOf(WorkspaceFolderWrapper folder) {
    return new Scope(folder.getUri(), "folder", ".", Origin.SETTINGS);
  }

  private WorkspaceFolderWrapper newFolder(String name) {
    var path = basedir.resolve(name);
    return new WorkspaceFolderWrapper(path.toUri(), new WorkspaceFolder(path.toUri().toString(), name), logTester.getLogger());
  }

  private ScopeBinding binding(WorkspaceFolderWrapper boundFolder) {
    return binding(underTest, boundFolder);
  }

  private static ScopeBinding binding(FolderSettingsBindingSource source, WorkspaceFolderWrapper boundFolder) {
    return source.bindingOf(boundFolder, scopeOf(boundFolder)).orElseThrow();
  }

  private void connection() {
    servers.put(CONNECTION_ID, server(CONNECTION_ID, "http://foo"));
  }

  private static ServerConnectionSettings server(String id, String url) {
    return new ServerConnectionSettings(id, url, "token", null, true, SonarCloudRegion.EU);
  }
}
