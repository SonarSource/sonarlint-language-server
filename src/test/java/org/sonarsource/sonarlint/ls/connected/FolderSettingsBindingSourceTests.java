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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.ls.connected.ScopeBinding.Origin;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
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
    when(settingsManager.getCurrentSettings()).thenReturn(new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(),
      Collections.emptyMap(), false, "", false, true, "", false));
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(UNBOUND);
    folder = spy(newFolder("folder"));
  }

  @Test
  void scopesOf_is_empty_without_settings_or_binding() {
    assertThat(underTest.scopesOf(folder)).isEmpty();

    folder.setSettings(UNBOUND);
    assertThat(underTest.scopesOf(folder)).isEmpty();
  }

  @Test
  void scopesOf_is_empty_for_a_folder_outside_the_local_filesystem() {
    var remoteUri = URI.create("vscode-vfs://github/org/repo");
    var remoteFolder = new WorkspaceFolderWrapper(remoteUri, new WorkspaceFolder(remoteUri.toString(), "repo"), logTester.getLogger());
    remoteFolder.setSettings(BOUND);

    assertThat(underTest.scopesOf(remoteFolder)).isEmpty();
  }

  @Test
  void scopesOf_describes_the_folder_as_one_scope_with_its_connection() {
    connection();
    folder.setSettings(BOUND);

    assertThat(underTest.scopesOf(folder)).singleElement().satisfies(scope -> {
      assertThat(scope.scopeUri()).isEqualTo(folder.getUri());
      assertThat(scope.name()).isEqualTo("folder");
      assertThat(scope.relativePath()).isEqualTo(".");
      assertThat(scope.projectKey()).isEqualTo("myProject");
      assertThat(scope.connectionId()).isEqualTo(CONNECTION_ID);
      assertThat(scope.target()).isEqualTo("connection 'myServer'");
      assertThat(scope.origin()).isEqualTo(Origin.SETTINGS);
    });
  }

  @Test
  void scopesOf_keeps_the_scope_but_not_the_connection_when_the_connection_is_unknown() {
    folder.setSettings(BOUND);

    assertThat(underTest.scopesOf(folder)).singleElement().satisfies(scope -> {
      assertThat(scope.connectionId()).isNull();
      assertThat(scope.projectKey()).isEqualTo("myProject");
    });
    assertThat(logTester.logs())
      .anyMatch(log -> log.contains("The specified connection id 'myServer' doesn't exist."))
      .anyMatch(log -> log.contains("Invalid binding for"));
  }

  @Test
  void scopesOf_does_not_resolve_the_connection_until_it_is_asked_for() {
    connection();
    folder.setSettings(BOUND);

    var scope = underTest.scopesOf(folder).get(0);

    verify(folder, never()).getSettings();
    assertThat(scope.connectionId()).isEqualTo(CONNECTION_ID);
    assertThat(scope.connectionId()).isEqualTo(CONNECTION_ID);
    verify(folder, times(1)).getSettings();
  }

  @Test
  void awaitSettings_waits_for_the_settings_of_the_folder() {
    folder.setSettings(BOUND);

    underTest.awaitSettings(folder);

    verify(folder).getSettings();
  }

  @Test
  void the_resolved_binding_is_kept_until_the_folder_settings_change() {
    connection();
    folder.setSettings(BOUND);
    underTest.scopesOf(folder).get(0).connectionId();
    underTest.scopesOf(folder).get(0).connectionId();
    verify(folder, times(1)).getSettings();

    underTest.onFolderSettingsChanged(folder.getUri());
    underTest.scopesOf(folder).get(0).connectionId();

    verify(folder, times(2)).getSettings();
  }

  @Test
  void forget_drops_the_resolved_binding_of_the_folder() {
    connection();
    folder.setSettings(BOUND);
    underTest.scopesOf(folder).get(0).connectionId();

    underTest.forget(folder.getUri());
    underTest.scopesOf(folder).get(0).connectionId();

    verify(folder, times(2)).getSettings();
  }

  @Test
  void bindingOfFile_uses_the_default_folder_settings_and_keeps_the_result() {
    connection();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND);
    var file = basedir.resolve("Foo.java").toUri();

    assertThat(underTest.bindingOfFile(file)).contains(new ProjectBinding(CONNECTION_ID, "myProject"));
    assertThat(underTest.bindingOfFile(file)).isPresent();

    verify(settingsManager, times(1)).getCurrentDefaultFolderSettings();
  }

  @Test
  void a_change_of_the_default_folder_settings_drops_the_bindings_of_the_files() {
    connection();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND);
    var file = basedir.resolve("Foo.java").toUri();
    underTest.bindingOfFile(file);

    underTest.onDefaultFolderSettingsChanged();
    underTest.bindingOfFile(file);

    verify(settingsManager, times(2)).getCurrentDefaultFolderSettings();
  }

  @Test
  void a_changed_or_removed_connection_drops_the_bindings_that_used_it_and_keeps_the_others() {
    var other = spy(newFolder("other"));
    other.setSettings(new WorkspaceFolderSettings("otherServer", "otherProject", Collections.emptyMap(), null, null));
    folder.setSettings(BOUND);
    var changed = server(CONNECTION_ID, "http://foo");
    var unchanged = server("otherServer", "http://bar");
    servers.put(CONNECTION_ID, changed);
    servers.put("otherServer", unchanged);
    underTest.scopesOf(folder).get(0).connectionId();
    underTest.scopesOf(other).get(0).connectionId();

    underTest.onConnectionsChanged(Map.of(CONNECTION_ID, changed, "otherServer", unchanged), Map.of(CONNECTION_ID, server(CONNECTION_ID, "http://moved"), "otherServer", unchanged));

    assertThat(underTest.cachedBindings()).containsExactly(new ProjectBinding("otherServer", "otherProject"));
  }

  @Test
  void a_removed_connection_drops_the_bindings_that_used_it() {
    folder.setSettings(BOUND);
    var existing = server(CONNECTION_ID, "http://foo");
    servers.put(CONNECTION_ID, existing);
    underTest.scopesOf(folder).get(0).connectionId();

    underTest.onConnectionsChanged(Map.of(CONNECTION_ID, existing), Map.of());

    assertThat(underTest.cachedBindings()).isEmpty();
  }

  @Test
  void a_new_connection_makes_the_bindings_that_were_waiting_for_it_resolve() {
    folder.setSettings(BOUND);
    assertThat(underTest.scopesOf(folder).get(0).connectionId()).isNull();

    connection();
    underTest.onConnectionsChanged(Map.of(), servers);

    assertThat(underTest.scopesOf(folder).get(0).connectionId()).isEqualTo(CONNECTION_ID);
  }

  @Test
  void cachedFolderBoundTo_finds_the_folder_with_that_binding() {
    connection();
    folder.setSettings(BOUND);
    underTest.scopesOf(folder).get(0).connectionId();

    assertThat(underTest.cachedFolderBoundTo(CONNECTION_ID, "myProject")).contains(folder.getUri());
    assertThat(underTest.cachedFolderBoundTo(CONNECTION_ID, "unknown")).isEmpty();
    assertThat(underTest.cachedFolderBoundTo("other", "myProject")).isEmpty();
    assertThat(underTest.cachedBindings()).containsExactly(new ProjectBinding(CONNECTION_ID, "myProject"));
  }

  @Test
  void connectionOf_logs_when_the_connection_does_not_exist() {
    assertThat(underTest.connectionOf(null)).isNull();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("doesn't exist"));
  }

  @Test
  void the_cache_can_be_provided() {
    var cache = new ConcurrentHashMap<URI, Optional<ProjectBinding>>();
    cache.put(folder.getUri(), Optional.of(new ProjectBinding(CONNECTION_ID, "cached")));
    var source = new FolderSettingsBindingSource(settingsManager, logTester.getLogger(), cache);
    folder.setSettings(BOUND);

    assertThat(source.scopesOf(folder).get(0).connectionId()).isEqualTo(CONNECTION_ID);
    assertThat(source.cachedBindings()).containsExactly(new ProjectBinding(CONNECTION_ID, "cached"));
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
