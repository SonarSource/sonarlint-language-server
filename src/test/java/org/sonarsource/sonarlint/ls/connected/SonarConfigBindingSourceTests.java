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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.ls.connected.ScopeBinding.Origin;
import org.sonarsource.sonarlint.ls.folders.ResolvedSonarConfig;
import org.sonarsource.sonarlint.ls.folders.SonarConfigProject;
import org.sonarsource.sonarlint.ls.folders.SonarConfigResolver;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarConfigBindingSourceTests {

  private static final URI REPO = URI.create("file:///repo/");
  private static final URI MODULE_A = URI.create("file:///repo/moduleA/");
  private static final WorkspaceFolderWrapper FOLDER = new WorkspaceFolderWrapper(REPO, new WorkspaceFolder(REPO.toString(), "repo"), mock(LanguageClientLogger.class));

  private final SonarConfigResolver resolver = mock(SonarConfigResolver.class);
  private final SettingsManager settingsManager = mock(SettingsManager.class);
  private SonarConfigBindingSource underTest;

  @BeforeEach
  void prepare() {
    underTest = new SonarConfigBindingSource(resolver, settingsManager);
    connections(Map.of("sq", new ServerConnectionSettings("sq", "https://sonarqube.example.com", "token", null, false, null)));
  }

  @Test
  void scopesOf_is_empty_without_a_config() {
    when(resolver.get(REPO)).thenReturn(Optional.empty());

    assertThat(underTest.scopesOf(FOLDER)).isEmpty();
  }

  @Test
  void scopesOf_lists_the_direct_project_and_the_nested_ones() {
    when(resolver.get(REPO)).thenReturn(Optional.of(new ResolvedSonarConfig(server(REPO, "root-project", "."), List.of(server(MODULE_A, "module-a", "moduleA")))));

    assertThat(underTest.scopesOf(FOLDER)).extracting(ScopeBinding::scopeUri, ScopeBinding::projectKey).containsExactly(
      tuple(REPO, "root-project"),
      tuple(MODULE_A, "module-a"));
  }

  @Test
  void scopesOf_binds_each_project_to_the_matching_connection() {
    when(resolver.get(REPO)).thenReturn(Optional.of(new ResolvedSonarConfig(null, List.of(server(MODULE_A, "module-a", "moduleA")))));

    assertThat(underTest.scopesOf(FOLDER)).singleElement().satisfies(scope -> {
      assertThat(scope.scopeUri()).isEqualTo(MODULE_A);
      assertThat(scope.name()).isEqualTo("moduleA");
      assertThat(scope.relativePath()).isEqualTo("moduleA");
      assertThat(scope.projectKey()).isEqualTo("module-a");
      assertThat(scope.connectionId()).isEqualTo("sq");
      assertThat(scope.target()).isEqualTo("serverUrl=https://sonarqube.example.com");
      assertThat(scope.origin()).isEqualTo(Origin.SONAR_CONFIG);
      assertThat(scope.isResolved()).isTrue();
    });
  }

  @Test
  void scopesOf_leaves_the_connection_empty_when_none_matches() {
    var cloudProject = new SonarConfigProject(MODULE_A, "module-a", null, "my-org", SonarCloudRegion.US.name(), "moduleA");
    when(resolver.get(REPO)).thenReturn(Optional.of(new ResolvedSonarConfig(null, List.of(cloudProject))));

    assertThat(underTest.scopesOf(FOLDER)).singleElement().satisfies(scope -> {
      assertThat(scope.connectionId()).isNull();
      assertThat(scope.isResolved()).isFalse();
      assertThat(scope.toProjectBinding()).isEmpty();
      assertThat(scope.target()).isEqualTo("organization=my-org region=US");
    });
  }

  @Test
  void scopesOf_does_not_read_the_settings_until_the_connection_is_asked_for() {
    when(resolver.get(REPO)).thenReturn(Optional.of(new ResolvedSonarConfig(null, List.of(server(MODULE_A, "module-a", "moduleA")))));

    var scope = underTest.scopesOf(FOLDER).get(0);

    verify(settingsManager, never()).getCurrentSettings();
    scope.connectionId();
    scope.connectionId();
    verify(settingsManager, times(1)).getCurrentSettings();
  }

  @Test
  void forget_drops_the_resolved_config_of_the_folder() {
    underTest.forget(REPO);

    verify(resolver).forget(REPO);
  }

  private void connections(Map<String, ServerConnectionSettings> servers) {
    when(settingsManager.getCurrentSettings()).thenReturn(new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(),
      Collections.emptyMap(), false, "", false, true, "", false));
  }

  private static SonarConfigProject server(URI scopeUri, String projectKey, String relativePath) {
    return new SonarConfigProject(scopeUri, projectKey, "https://sonarqube.example.com", null, null, relativePath);
  }
}
