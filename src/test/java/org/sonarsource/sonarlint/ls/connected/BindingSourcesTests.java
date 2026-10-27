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
import java.util.Optional;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.connected.ScopeBinding.Origin;
import org.sonarsource.sonarlint.ls.folders.SonarConfigProject;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingSourcesTests {

  private static final URI REPO = URI.create("file:///repo/");
  private static final URI MODULE_A = URI.create("file:///repo/moduleA/");
  private static final URI NESTED = URI.create("file:///repo/moduleA/nested/");
  private static final WorkspaceFolderWrapper FOLDER = new WorkspaceFolderWrapper(REPO, new WorkspaceFolder(REPO.toString(), "repo"), mock(LanguageClientLogger.class));

  private final SonarConfigBindingSource config = mock(SonarConfigBindingSource.class);
  private final FolderSettingsBindingSource settings = mock(FolderSettingsBindingSource.class);
  private final BindingSources underTest = new BindingSources(config, settings);

  @Test
  void scopesOf_is_what_the_config_declares_when_it_applies() {
    var configScopes = List.of(scope(MODULE_A, "module-a", "connection", Origin.SONAR_CONFIG));
    when(config.scopesOf(FOLDER)).thenReturn(configScopes);
    when(settings.scopesOf(FOLDER)).thenReturn(List.of(scope(REPO, "from-settings", "connection", Origin.SETTINGS)));

    assertThat(underTest.scopesOf(FOLDER)).isEqualTo(configScopes);
  }

  @Test
  void scopesOf_falls_back_to_the_folder_settings_without_a_config() {
    var settingsScopes = List.of(scope(REPO, "from-settings", "connection", Origin.SETTINGS));
    when(config.scopesOf(FOLDER)).thenReturn(List.of());
    when(settings.scopesOf(FOLDER)).thenReturn(settingsScopes);

    assertThat(underTest.scopesOf(FOLDER)).isEqualTo(settingsScopes);
  }

  @Test
  void bindingOf_uses_the_deepest_declared_scope_containing_the_file() {
    when(config.scopesOf(FOLDER)).thenReturn(List.of(
      scope(MODULE_A, "module-a", "connection", Origin.SONAR_CONFIG),
      scope(NESTED, "nested", "connection", Origin.SONAR_CONFIG)));

    assertThat(underTest.bindingOf(FOLDER, URI.create("file:///repo/moduleA/nested/Foo.java"))).contains(new ProjectBinding("connection", "nested"));
    assertThat(underTest.bindingOf(FOLDER, URI.create("file:///repo/moduleA/Foo.java"))).contains(new ProjectBinding("connection", "module-a"));
  }

  @Test
  void bindingOf_is_empty_for_a_file_outside_every_declared_scope() {
    when(config.scopesOf(FOLDER)).thenReturn(List.of(scope(MODULE_A, "module-a", "connection", Origin.SONAR_CONFIG)));
    when(settings.bindingOf(FOLDER)).thenReturn(Optional.of(new ProjectBinding("connection", "from-settings")));

    assertThat(underTest.bindingOf(FOLDER, URI.create("file:///repo/Foo.java"))).isEmpty();
  }

  @Test
  void bindingOf_is_empty_when_the_declared_scope_matches_no_connection() {
    when(config.scopesOf(FOLDER)).thenReturn(List.of(scope(MODULE_A, "module-a", null, Origin.SONAR_CONFIG)));

    assertThat(underTest.bindingOf(FOLDER, URI.create("file:///repo/moduleA/Foo.java"))).isEmpty();
  }

  @Test
  void bindingOf_uses_the_folder_settings_without_a_config() {
    var binding = new ProjectBinding("connection", "from-settings");
    when(config.scopesOf(FOLDER)).thenReturn(List.of());
    when(settings.bindingOf(FOLDER)).thenReturn(Optional.of(binding));

    assertThat(underTest.bindingOf(FOLDER, URI.create("file:///repo/Foo.java"))).contains(binding);
  }

  @Test
  void bindingIfExists_only_reads_what_the_folder_settings_already_cached() {
    var binding = new ProjectBinding("connection", "from-settings");
    when(config.scopesOf(FOLDER)).thenReturn(List.of());
    when(settings.cachedBindingOf(FOLDER)).thenReturn(Optional.of(binding));

    assertThat(underTest.bindingIfExists(FOLDER, URI.create("file:///repo/Foo.java"))).contains(binding);
    verify(settings, never()).bindingOf(FOLDER);
  }

  @Test
  void bindingIfExists_uses_the_declared_scope_when_a_config_applies() {
    when(config.scopesOf(FOLDER)).thenReturn(List.of(scope(MODULE_A, "module-a", "connection", Origin.SONAR_CONFIG)));

    assertThat(underTest.bindingIfExists(FOLDER, URI.create("file:///repo/moduleA/Foo.java"))).contains(new ProjectBinding("connection", "module-a"));
  }

  @Test
  void bindingOfScope_finds_the_declared_scope() {
    when(config.scopesOf(FOLDER)).thenReturn(List.of(
      scope(MODULE_A, "module-a", "connection", Origin.SONAR_CONFIG),
      scope(NESTED, "nested", null, Origin.SONAR_CONFIG)));

    assertThat(underTest.bindingOfScope(FOLDER, MODULE_A)).contains(new ProjectBinding("connection", "module-a"));
    assertThat(underTest.bindingOfScope(FOLDER, NESTED)).isEmpty();
    assertThat(underTest.bindingOfScope(FOLDER, REPO)).isEmpty();
  }

  @Test
  void bindingOfScope_gives_the_folder_settings_only_to_the_folder_itself() {
    var binding = new ProjectBinding("connection", "from-settings");
    when(config.scopesOf(FOLDER)).thenReturn(List.of());
    when(settings.bindingOf(FOLDER)).thenReturn(Optional.of(binding));

    assertThat(underTest.bindingOfScope(FOLDER, REPO)).contains(binding);
    assertThat(underTest.bindingOfScope(FOLDER, MODULE_A)).isEmpty();
  }

  @Test
  void scopeUriOf_is_the_deepest_declared_scope_containing_the_file_or_the_folder() {
    when(config.projectsOf(FOLDER)).thenReturn(List.of(project(MODULE_A), project(NESTED)));

    assertThat(underTest.scopeUriOf(FOLDER, URI.create("file:///repo/moduleA/nested/Foo.java"))).isEqualTo(NESTED);
    assertThat(underTest.scopeUriOf(FOLDER, URI.create("file:///repo/moduleA/Foo.java"))).isEqualTo(MODULE_A);
    assertThat(underTest.scopeUriOf(FOLDER, URI.create("file:///repo/Foo.java"))).isEqualTo(REPO);
  }

  @Test
  void subFolders_are_the_declared_scopes_below_the_folder_named_after_their_directory() {
    when(config.projectsOf(FOLDER)).thenReturn(List.of(project(REPO), project(MODULE_A)));

    assertThat(underTest.subFolders(FOLDER)).extracting(WorkspaceFolder::getUri, WorkspaceFolder::getName)
      .containsExactly(tuple(MODULE_A.toString(), "moduleA"));
  }

  @Test
  void forget_clears_both_sources() {
    underTest.forget(REPO);

    verify(config).forget(REPO);
    verify(settings).forgetFolder(REPO);
  }

  @Test
  void exposes_the_sources() {
    assertThat(underTest.config()).isSameAs(config);
    assertThat(underTest.settings()).isSameAs(settings);
  }

  private static ScopeBinding scope(URI scopeUri, String projectKey, @Nullable String connectionId, Origin origin) {
    return new ScopeBinding(scopeUri, "name", ".", projectKey, connectionId, "target", origin);
  }

  private static SonarConfigProject project(URI scopeUri) {
    return new SonarConfigProject(scopeUri, "key", "https://sonarqube.example.com", null, null, ".");
  }
}
