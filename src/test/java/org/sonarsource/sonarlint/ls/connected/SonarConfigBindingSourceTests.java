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

    assertThat(underTest.scopesOf(FOLDER)).extracting(Scope::uri, Scope::name, Scope::relativePath, Scope::owner).containsExactly(
      tuple(REPO, "repo", ".", underTest),
      tuple(MODULE_A, "moduleA", "moduleA", underTest));
  }

  @Test
  void bindingOf_binds_the_project_of_the_scope_to_the_matching_connection() {
    when(resolver.get(REPO)).thenReturn(Optional.of(new ResolvedSonarConfig(null, List.of(server(MODULE_A, "module-a", "moduleA")))));

    assertThat(underTest.bindingOf(FOLDER, underTest.scopesOf(FOLDER).get(0))).hasValueSatisfying(binding -> {
      assertThat(binding.projectKey()).isEqualTo("module-a");
      assertThat(binding.connectionId()).isEqualTo("sq");
      assertThat(binding.target()).isEqualTo("serverUrl=https://sonarqube.example.com");
      assertThat(binding.isResolved()).isTrue();
    });
  }

  @Test
  void bindingOf_leaves_the_connection_empty_when_none_matches() {
    var cloudProject = new SonarConfigProject(MODULE_A, "module-a", null, "my-org", SonarCloudRegion.US.name(), "moduleA");
    when(resolver.get(REPO)).thenReturn(Optional.of(new ResolvedSonarConfig(null, List.of(cloudProject))));

    assertThat(underTest.bindingOf(FOLDER, underTest.scopesOf(FOLDER).get(0))).hasValueSatisfying(binding -> {
      assertThat(binding.connectionId()).isNull();
      assertThat(binding.isResolved()).isFalse();
      assertThat(binding.toProjectBinding()).isEmpty();
      assertThat(binding.target()).isEqualTo("organization=my-org region=US");
    });
  }

  @Test
  void bindingOf_is_empty_for_a_scope_the_config_does_not_declare() {
    when(resolver.get(REPO)).thenReturn(Optional.of(new ResolvedSonarConfig(null, List.of(server(MODULE_A, "module-a", "moduleA")))));

    assertThat(underTest.bindingOf(FOLDER, new Scope(REPO, "repo", ".", Origin.SONAR_CONFIG, underTest))).isEmpty();
  }

  @Test
  void bindingOf_does_not_read_the_settings_until_the_connection_is_asked_for() {
    when(resolver.get(REPO)).thenReturn(Optional.of(new ResolvedSonarConfig(null, List.of(server(MODULE_A, "module-a", "moduleA")))));

    var binding = underTest.bindingOf(FOLDER, underTest.scopesOf(FOLDER).get(0)).orElseThrow();

    verify(settingsManager, never()).getCurrentSettings();
    binding.connectionId();
    binding.connectionId();
    verify(settingsManager, times(1)).getCurrentSettings();
  }

  private void connections(Map<String, ServerConnectionSettings> servers) {
    when(settingsManager.getCurrentSettings()).thenReturn(new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(),
      Collections.emptyMap(), false, "", false, true, "", false));
  }

  private static SonarConfigProject server(URI scopeUri, String projectKey, String relativePath) {
    return new SonarConfigProject(scopeUri, projectKey, "https://sonarqube.example.com", null, null, relativePath);
  }
}
