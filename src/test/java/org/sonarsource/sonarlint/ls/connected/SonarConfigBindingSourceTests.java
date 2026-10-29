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
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.ls.folders.ResolvedSonarConfig;
import org.sonarsource.sonarlint.ls.folders.SonarConfigProject;
import org.sonarsource.sonarlint.ls.folders.SonarConfigResolver;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScope;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarConfigBindingSourceTests {

  private static final URI REPO = URI.create("file:///repo/");
  private static final URI MODULE_A = URI.create("file:///repo/moduleA/");

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private final WorkspaceFolderWrapper folder = new WorkspaceFolderWrapper(REPO, new WorkspaceFolder(REPO.toString(), "my repo"), logTester.getLogger());
  private final SonarConfigResolver resolver = mock(SonarConfigResolver.class);
  private final SettingsManager settingsManager = mock(SettingsManager.class);
  private SonarConfigBindingSource underTest;

  @BeforeEach
  void prepare() {
    underTest = new SonarConfigBindingSource(resolver, settingsManager, logTester.getLogger());
    connections(Map.of("sq", new ServerConnectionSettings("sq", "https://sonarqube.example.com", "token", null, false, null)));
  }

  @Test
  void scopesOf_is_empty_without_a_config() {
    when(resolver.get(REPO)).thenReturn(Optional.empty());

    assertThat(underTest.scopesOf(folder)).isEmpty();
  }

  @Test
  void scopesOf_lists_the_direct_project_and_the_nested_ones_bound_to_the_matching_connection() {
    when(resolver.get(REPO)).thenReturn(Optional.of(new ResolvedSonarConfig(server(REPO, "root-project", "."), List.of(server(MODULE_A, "module-a", "moduleA")))));

    assertThat(underTest.scopesOf(folder)).containsExactly(
      new ConfigurationScope(REPO, "my repo", ".", Origin.SONAR_CONFIG, new ProjectBinding("sq", "root-project")),
      new ConfigurationScope(MODULE_A, "moduleA", "moduleA", Origin.SONAR_CONFIG, new ProjectBinding("sq", "module-a")));
  }

  @Test
  void scopesOf_leaves_a_scope_unbound_and_says_so_when_no_connection_matches() {
    var cloudProject = new SonarConfigProject(MODULE_A, "module-a", null, "my-org", SonarCloudRegion.US.name(), "moduleA");
    when(resolver.get(REPO)).thenReturn(Optional.of(new ResolvedSonarConfig(null, List.of(cloudProject))));

    assertThat(underTest.scopesOf(folder)).containsExactly(new ConfigurationScope(MODULE_A, "moduleA", "moduleA", Origin.SONAR_CONFIG, null));
    assertThat(logTester.logs()).anyMatch(log -> log.contains("No connection configured for organization=my-org region=US, scope " + MODULE_A + " is unbound"));
  }

  private void connections(Map<String, ServerConnectionSettings> servers) {
    when(settingsManager.getCurrentSettings()).thenReturn(new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(),
      Collections.emptyMap(), false, "", false, true, "", false));
  }

  private static SonarConfigProject server(URI scopeUri, String projectKey, String relativePath) {
    return new SonarConfigProject(scopeUri, projectKey, "https://sonarqube.example.com", null, null, relativePath);
  }
}
