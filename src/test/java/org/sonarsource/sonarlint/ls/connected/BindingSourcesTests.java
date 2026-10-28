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
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.connected.ScopeBinding.Origin;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingSourcesTests {

  private static final URI REPO = URI.create("file:///repo/");
  private static final URI MODULE_A = URI.create("file:///repo/moduleA/");
  private static final URI NESTED = URI.create("file:///repo/moduleA/nested/");
  private static final WorkspaceFolderWrapper FOLDER = new WorkspaceFolderWrapper(REPO, new WorkspaceFolder(REPO.toString(), "repo"), mock(LanguageClientLogger.class));

  private final BindingSource first = mock(BindingSource.class);
  private final BindingSource second = mock(BindingSource.class);
  private final BindingSources underTest = new BindingSources(List.of(first, second));

  @Test
  void scopesOf_is_what_the_first_source_that_defines_scopes_says() {
    var firstScopes = List.of(scope(MODULE_A, "from-first", "connection"));
    when(first.scopesOf(FOLDER)).thenReturn(firstScopes);
    when(second.scopesOf(FOLDER)).thenReturn(List.of(scope(REPO, "from-second", "connection")));

    assertThat(underTest.scopesOf(FOLDER)).isEqualTo(firstScopes);
  }

  @Test
  void scopesOf_falls_through_to_the_next_source_when_the_first_defines_none() {
    var secondScopes = List.of(scope(REPO, "from-second", "connection"));
    when(first.scopesOf(FOLDER)).thenReturn(List.of());
    when(second.scopesOf(FOLDER)).thenReturn(secondScopes);

    assertThat(underTest.scopesOf(FOLDER)).isEqualTo(secondScopes);
  }

  @Test
  void scopesOf_is_empty_when_no_source_defines_scopes() {
    assertThat(underTest.scopesOf(FOLDER)).isEmpty();
  }

  @Test
  void bindingOf_uses_the_deepest_scope_containing_the_file() {
    when(first.scopesOf(FOLDER)).thenReturn(List.of(scope(MODULE_A, "module-a", "connection"), scope(NESTED, "nested", "connection")));

    assertThat(underTest.bindingOf(FOLDER, URI.create("file:///repo/moduleA/nested/Foo.java"))).contains(new ProjectBinding("connection", "nested"));
    assertThat(underTest.bindingOf(FOLDER, URI.create("file:///repo/moduleA/Foo.java"))).contains(new ProjectBinding("connection", "module-a"));
  }

  @Test
  void bindingOf_is_empty_for_a_file_outside_every_scope() {
    when(first.scopesOf(FOLDER)).thenReturn(List.of(scope(MODULE_A, "module-a", "connection")));
    when(second.scopesOf(FOLDER)).thenReturn(List.of(scope(REPO, "from-second", "connection")));

    assertThat(underTest.bindingOf(FOLDER, URI.create("file:///repo/Foo.java"))).isEmpty();
  }

  @Test
  void bindingOf_is_empty_when_the_scope_has_no_connection() {
    when(first.scopesOf(FOLDER)).thenReturn(List.of(scope(MODULE_A, "module-a", null)));

    assertThat(underTest.bindingOf(FOLDER, URI.create("file:///repo/moduleA/Foo.java"))).isEmpty();
  }

  @Test
  void bindingOfScope_finds_the_scope_with_that_uri() {
    when(first.scopesOf(FOLDER)).thenReturn(List.of(scope(MODULE_A, "module-a", "connection"), scope(NESTED, "nested", null)));

    assertThat(underTest.bindingOfScope(FOLDER, MODULE_A)).contains(new ProjectBinding("connection", "module-a"));
    assertThat(underTest.bindingOfScope(FOLDER, NESTED)).isEmpty();
    assertThat(underTest.bindingOfScope(FOLDER, REPO)).isEmpty();
  }

  @Test
  void scopeUriOf_is_the_deepest_scope_containing_the_file_or_the_folder() {
    when(first.scopesOf(FOLDER)).thenReturn(List.of(scope(MODULE_A, "module-a", "connection"), scope(NESTED, "nested", "connection")));

    assertThat(underTest.scopeUriOf(FOLDER, URI.create("file:///repo/moduleA/nested/Foo.java"))).isEqualTo(NESTED);
    assertThat(underTest.scopeUriOf(FOLDER, URI.create("file:///repo/moduleA/Foo.java"))).isEqualTo(MODULE_A);
    assertThat(underTest.scopeUriOf(FOLDER, URI.create("file:///repo/Foo.java"))).isEqualTo(REPO);
  }

  @Test
  void finding_a_scope_does_not_wait_for_the_settings() {
    when(first.scopesOf(FOLDER)).thenReturn(List.of(scope(MODULE_A, "module-a", "connection")));

    underTest.scopesOf(FOLDER);
    underTest.scopeUriOf(FOLDER, URI.create("file:///repo/moduleA/Foo.java"));

    verify(first, never()).awaitSettings(FOLDER);
    verify(second, never()).awaitSettings(FOLDER);
  }

  @Test
  void resolving_a_binding_waits_only_for_the_sources_it_asks() {
    when(first.scopesOf(FOLDER)).thenReturn(List.of(scope(MODULE_A, "module-a", "connection")));

    underTest.bindingOf(FOLDER, URI.create("file:///repo/moduleA/Foo.java"));
    underTest.bindingOfScope(FOLDER, MODULE_A);

    verify(first, times(2)).awaitSettings(FOLDER);
    verify(second, never()).awaitSettings(FOLDER);
  }

  @Test
  void resolving_a_binding_waits_for_the_next_source_when_the_first_defines_no_scope() {
    when(second.scopesOf(FOLDER)).thenReturn(List.of(scope(REPO, "from-second", "connection")));

    assertThat(underTest.loadedScopesOf(FOLDER)).hasSize(1);

    verify(first).awaitSettings(FOLDER);
    verify(second).awaitSettings(FOLDER);
  }

  @Test
  void scopeUriOf_does_not_resolve_any_connection() {
    var scopeWhoseConnectionMustNotBeAsked = new ScopeBinding(MODULE_A, "name", ".", "module-a", () -> {
      throw new AssertionError("the connection must stay unresolved");
    }, "target", Origin.SONAR_CONFIG);
    when(first.scopesOf(FOLDER)).thenReturn(List.of(scopeWhoseConnectionMustNotBeAsked));

    assertThat(underTest.scopeUriOf(FOLDER, URI.create("file:///repo/moduleA/Foo.java"))).isEqualTo(MODULE_A);
  }

  @Test
  void the_hooks_reach_every_source() {
    Map<String, ServerConnectionSettings> previous = Map.of();
    Map<String, ServerConnectionSettings> current = Map.of();

    underTest.onConnectionsChanged(previous, current);
    underTest.onFolderSettingsChanged(REPO);
    underTest.forget(REPO);

    verify(first).onConnectionsChanged(previous, current);
    verify(second).onConnectionsChanged(previous, current);
    verify(first).onFolderSettingsChanged(REPO);
    verify(second).onFolderSettingsChanged(REPO);
    verify(first).forget(REPO);
    verify(second).forget(REPO);
  }

  private static ScopeBinding scope(URI scopeUri, String projectKey, @Nullable String connectionId) {
    return ScopeBinding.of(scopeUri, "name", ".", projectKey, connectionId, "target", Origin.SONAR_CONFIG);
  }
}
