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
    folder = spy(new WorkspaceFolderWrapper(basedir.toUri(), new WorkspaceFolder(basedir.toUri().toString(), "folder"), logTester.getLogger()));
  }

  @Test
  void bindingOf_resolves_the_binding_of_a_bound_folder_and_caches_it() {
    connection();
    folder.setSettings(BOUND);

    assertThat(underTest.bindingOf(folder)).contains(new ProjectBinding(CONNECTION_ID, "myProject"));
    assertThat(underTest.bindingOf(folder)).contains(new ProjectBinding(CONNECTION_ID, "myProject"));

    verify(folder, times(1)).getSettings();
    assertThat(underTest.hasCachedFolder(basedir.toUri())).isTrue();
    assertThat(underTest.cachedBindingOf(folder)).isPresent();
  }

  @Test
  void bindingOf_is_empty_for_a_folder_without_binding() {
    folder.setSettings(UNBOUND);

    assertThat(underTest.bindingOf(folder)).isEmpty();
  }

  @Test
  void bindingOf_is_empty_and_logs_when_the_connection_is_unknown() {
    folder.setSettings(BOUND);

    assertThat(underTest.bindingOf(folder)).isEmpty();
    assertThat(logTester.logs())
      .anyMatch(log -> log.contains("The specified connection id 'myServer' doesn't exist."))
      .anyMatch(log -> log.contains("Invalid binding for"));
  }

  @Test
  void bindingOfFile_uses_the_default_folder_settings_and_caches_the_result() {
    connection();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND);
    var file = basedir.resolve("Foo.java").toUri();

    assertThat(underTest.bindingOfFile(file)).contains(new ProjectBinding(CONNECTION_ID, "myProject"));
    assertThat(underTest.bindingOfFile(file)).isPresent();

    verify(settingsManager, times(1)).getCurrentDefaultFolderSettings();
    assertThat(underTest.hasCachedFiles()).isTrue();
    assertThat(underTest.cachedBindingOfFile(file)).isPresent();
  }

  @Test
  void scopesOf_is_empty_without_settings_or_binding() {
    assertThat(underTest.scopesOf(folder)).isEmpty();

    folder.setSettings(UNBOUND);
    assertThat(underTest.scopesOf(folder)).isEmpty();
  }

  @Test
  void scopesOf_describes_the_folder_as_one_scope_with_its_connection() {
    connection();
    folder.setSettings(BOUND);

    assertThat(underTest.scopesOf(folder)).singleElement().satisfies(scope -> {
      assertThat(scope.scopeUri()).isEqualTo(basedir.toUri());
      assertThat(scope.name()).isEqualTo(basedir.getFileName().toString());
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
  }

  @Test
  void a_cached_folder_can_be_forgotten_or_marked_unbound() {
    connection();
    folder.setSettings(BOUND);
    underTest.bindingOf(folder);

    underTest.markFolderUnbound(basedir.toUri());
    assertThat(underTest.cachedBindingOf(folder)).isEmpty();
    assertThat(underTest.hasCachedFolder(basedir.toUri())).isTrue();

    underTest.forgetFolder(basedir.toUri());
    assertThat(underTest.hasCachedFolder(basedir.toUri())).isFalse();
  }

  @Test
  void cached_files_can_be_forgotten_or_marked_unbound() {
    connection();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND);
    var file = basedir.resolve("Foo.java").toUri();
    underTest.bindingOfFile(file);

    underTest.markFilesUnbound();
    assertThat(underTest.cachedBindingOfFile(file)).isEmpty();
    assertThat(underTest.hasCachedFiles()).isTrue();

    underTest.forgetFiles();
    assertThat(underTest.hasCachedFiles()).isFalse();
  }

  @Test
  void forgetConnection_drops_only_the_bindings_of_that_connection() {
    connection();
    folder.setSettings(BOUND);
    underTest.bindingOf(folder);
    var other = new WorkspaceFolderWrapper(basedir.resolve("other").toUri(), new WorkspaceFolder(basedir.resolve("other").toUri().toString(), "other"), logTester.getLogger());
    other.setSettings(UNBOUND);
    underTest.bindingOf(other);

    underTest.forgetConnection(CONNECTION_ID);

    assertThat(underTest.hasCachedFolder(basedir.toUri())).isFalse();
    assertThat(underTest.hasCachedFolder(other.getUri())).isTrue();
  }

  @Test
  void forgetUnresolved_drops_the_results_that_found_no_binding_so_that_a_new_connection_counts() {
    folder.setSettings(BOUND);
    assertThat(underTest.bindingOf(folder)).isEmpty();

    connection();
    underTest.forgetUnresolved();

    assertThat(underTest.bindingOf(folder)).contains(new ProjectBinding(CONNECTION_ID, "myProject"));
  }

  @Test
  void cachedFolderBoundTo_finds_the_folder_with_that_binding() {
    connection();
    folder.setSettings(BOUND);
    underTest.bindingOf(folder);

    assertThat(underTest.cachedFolderBoundTo(CONNECTION_ID, "myProject")).contains(basedir.toUri());
    assertThat(underTest.cachedFolderBoundTo(CONNECTION_ID, "unknown")).isEmpty();
    assertThat(underTest.cachedFolderBoundTo("other", "myProject")).isEmpty();
    assertThat(underTest.cachedBindings()).containsExactly(new ProjectBinding(CONNECTION_ID, "myProject"));
  }

  @Test
  void connectionOf_defaults_to_the_default_connection_id() {
    assertThat(underTest.connectionOf(null)).isNull();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("doesn't exist"));
  }

  @Test
  void the_cache_can_be_provided() {
    var cache = new java.util.concurrent.ConcurrentHashMap<URI, Optional<ProjectBinding>>();
    cache.put(basedir.toUri(), Optional.of(new ProjectBinding(CONNECTION_ID, "cached")));
    var source = new FolderSettingsBindingSource(settingsManager, logTester.getLogger(), cache);

    assertThat(source.bindingOf(folder)).contains(new ProjectBinding(CONNECTION_ID, "cached"));
  }

  private void connection() {
    servers.put(CONNECTION_ID, new ServerConnectionSettings(CONNECTION_ID, "http://foo", "token", null, true, SonarCloudRegion.EU));
  }
}
