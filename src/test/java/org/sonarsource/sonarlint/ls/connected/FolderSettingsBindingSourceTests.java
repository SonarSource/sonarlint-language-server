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
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScope;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
  void scopesOf_is_the_unbound_folder_scope_outside_the_local_filesystem() {
    var remoteUri = URI.create("vscode-vfs://github/org/repo");
    var remoteFolder = new WorkspaceFolderWrapper(remoteUri, new WorkspaceFolder(remoteUri.toString(), "repo"), logTester.getLogger());
    remoteFolder.setSettings(BOUND);

    assertThat(underTest.scopesOf(remoteFolder)).containsExactly(new ConfigurationScope(remoteUri, "repo", ".", Origin.SETTINGS, null));
  }

  @Test
  void scopesOf_is_the_unbound_folder_scope_without_binding() {
    folder.setSettings(UNBOUND);

    assertThat(underTest.scopesOf(folder)).containsExactly(new ConfigurationScope(folder.getUri(), "folder", ".", Origin.SETTINGS, null));
  }

  @Test
  void scopesOf_binds_the_folder_scope_to_the_project_of_its_connection() {
    connection();
    folder.setSettings(BOUND);

    assertThat(underTest.scopesOf(folder)).containsExactly(new ConfigurationScope(folder.getUri(), "folder", ".", Origin.SETTINGS, new ProjectBinding(CONNECTION_ID, "myProject")));
  }

  @Test
  void scopesOf_leaves_the_folder_scope_unbound_when_the_connection_is_unknown() {
    folder.setSettings(BOUND);

    assertThat(underTest.scopesOf(folder)).extracting(ConfigurationScope::binding).containsExactly((ProjectBinding) null);
    assertThat(logTester.logs()).anyMatch(log -> log.contains("Invalid binding for"));
  }

  private WorkspaceFolderWrapper newFolder(String name) {
    var path = basedir.resolve(name);
    return new WorkspaceFolderWrapper(path.toUri(), new WorkspaceFolder(path.toUri().toString(), name), logTester.getLogger());
  }

  private void connection() {
    servers.put(CONNECTION_ID, server(CONNECTION_ID, "http://foo"));
  }

  private static ServerConnectionSettings server(String id, String url) {
    return new ServerConnectionSettings(id, url, "token", null, true, SonarCloudRegion.EU);
  }
}
