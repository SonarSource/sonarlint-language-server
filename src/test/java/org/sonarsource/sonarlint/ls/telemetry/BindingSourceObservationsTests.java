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
package org.sonarsource.sonarlint.ls.telemetry;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.BindingSource;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ObservedBindingSourceDto;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

class BindingSourceObservationsTests {

  private static final Path CONFIG = Path.of("repo", ".sonar-config.json");

  private final BindingSourceObservations underTest = new BindingSourceObservations();
  private final WorkspaceFolderWrapper settings = folder("settings");
  private final WorkspaceFolderWrapper frontend = folder("frontend");
  private final WorkspaceFolderWrapper backend = folder("backend");

  @Test
  void drains_one_source_per_bound_folder_with_the_directory_its_declaration_applies_to() {
    underTest.boundBySettings(settings);
    underTest.boundBySonarConfig(frontend, CONFIG, 3);

    assertThat(underTest.drain(List.of(settings, frontend)))
      .extracting(ObservedBindingSourceDto::getDeclarationRoot, ObservedBindingSourceDto::getBindingSource, ObservedBindingSourceDto::getDeclaredBindingCount)
      .containsExactly(
        tuple(Paths.get(settings.getUri()), BindingSource.IDE_SETTINGS, 1),
        tuple(CONFIG.getParent(), BindingSource.SONAR_CONFIG, 3));
  }

  @Test
  void drains_every_folder_of_a_shared_config_file_with_the_same_root() {
    underTest.boundBySonarConfig(frontend, CONFIG, 2);
    underTest.boundBySonarConfig(backend, CONFIG, 2);

    assertThat(underTest.drain(List.of(frontend, backend))).extracting(ObservedBindingSourceDto::getDeclarationRoot)
      .containsExactly(CONFIG.getParent(), CONFIG.getParent());
  }

  @Test
  void drains_a_settings_folder_without_a_local_path_without_a_root() {
    var remoteUri = URI.create("vscode-vfs://github/org/repo");
    var remote = new WorkspaceFolderWrapper(remoteUri, new WorkspaceFolder(remoteUri.toString(), "repo"), mock(LanguageClientLogger.class));
    underTest.boundBySettings(remote);

    assertThat(underTest.drain(List.of(remote))).extracting(ObservedBindingSourceDto::getDeclarationRoot).containsExactly((Path) null);
  }

  @Test
  void keeps_the_latest_observation_of_a_folder() {
    underTest.boundBySettings(settings);
    underTest.unbound(settings);
    underTest.boundBySonarConfig(frontend, CONFIG, 2);
    underTest.boundBySettings(frontend);

    assertThat(underTest.drain(List.of(settings, frontend))).extracting(ObservedBindingSourceDto::getBindingSource)
      .containsExactly(BindingSource.IDE_SETTINGS);
  }

  @Test
  void drains_only_the_given_folders() {
    underTest.boundBySettings(settings);
    underTest.boundBySettings(frontend);

    assertThat(underTest.drain(List.of(settings))).hasSize(1);
  }

  @Test
  void stops_recording_once_drained() {
    underTest.drain(List.of());

    underTest.boundBySettings(settings);

    assertThat(underTest.drain(List.of(settings))).isEmpty();
  }

  private static WorkspaceFolderWrapper folder(String name) {
    var uri = URI.create("file:///workspace/" + name + "/");
    return new WorkspaceFolderWrapper(uri, new WorkspaceFolder(uri.toString(), name), mock(LanguageClientLogger.class));
  }
}
