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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SonarConfigResolverTests {

  private static final String SERVER_URL = "https://sonarqube.example.com";
  private static final Path REPO = Path.of("repo").toAbsolutePath().normalize();
  private static final URI REPO_URI = REPO.toUri();
  private static final Path MODULE_A = REPO.resolve("moduleA");

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private final SonarConfigLoader loader = mock(SonarConfigLoader.class);
  private SonarConfigResolver underTest;

  @BeforeEach
  void prepare() {
    underTest = new SonarConfigResolver(logTester.getLogger(), loader);
  }

  @Test
  void get_is_empty_for_a_non_file_uri_without_loading() {
    assertThat(underTest.get(URI.create("ftp://example.com/folder"))).isEmpty();

    verifyNoInteractions(loader);
  }

  @Test
  void get_is_empty_when_there_is_no_config_file() {
    when(loader.load(REPO)).thenReturn(Optional.empty());

    assertThat(underTest.get(REPO_URI)).isEmpty();
  }

  @Test
  void get_binds_the_folder_directly_when_an_entry_points_at_it() {
    loaded(REPO, entry("root-project", "."));

    var config = underTest.get(REPO_URI).orElseThrow();

    assertThat(config.children()).isEmpty();
    assertThat(config.direct().scopeUri()).isEqualTo(REPO_URI);
    assertThat(config.direct().projectKey()).isEqualTo("root-project");
    assertThat(config.direct().relativePath()).isEqualTo(".");
  }

  @Test
  void get_binds_a_folder_below_the_config_directly() {
    when(loader.load(MODULE_A)).thenReturn(Optional.of(fileIn(REPO, entry("module-a", "moduleA"))));

    var config = underTest.get(MODULE_A.toUri()).orElseThrow();

    assertThat(config.direct().projectKey()).isEqualTo("module-a");
    assertThat(config.children()).isEmpty();
  }

  @Test
  void get_prefers_the_deepest_entry_containing_the_folder() {
    var folder = MODULE_A.resolve("src");
    when(loader.load(folder)).thenReturn(Optional.of(fileIn(REPO, entry("root-project", "."), entry("module-a", "moduleA"))));

    assertThat(underTest.get(folder.toUri()).orElseThrow().direct().projectKey()).isEqualTo("module-a");
  }

  @Test
  void get_declares_nested_entries_as_children() {
    loaded(REPO, entry("module-a", "moduleA"), entry("module-b-api", "moduleB/api"));

    var config = underTest.get(REPO_URI).orElseThrow();

    assertThat(config.direct()).isNull();
    assertThat(config.children()).extracting(SonarConfigProject::scopeUri)
      .containsExactly(MODULE_A.toUri(), REPO.resolve("moduleB/api").toUri());
    assertThat(config.children()).extracting(SonarConfigProject::relativePath).containsExactly("moduleA", "moduleB/api");
  }

  @Test
  void get_keeps_cloud_entries() {
    loaded(REPO, new SonarConfigEntry("module-a", "moduleA", null, "my-org", "US"));

    var project = underTest.get(REPO_URI).orElseThrow().children().get(0);

    assertThat(project.serverUrl()).isNull();
    assertThat(project.organization()).isEqualTo("my-org");
    assertThat(project.region()).isEqualTo("US");
  }

  @Test
  void get_ignores_invalid_entries() {
    loaded(REPO,
      new SonarConfigEntry(null, "moduleA", SERVER_URL, null, null),
      new SonarConfigEntry("no-path", null, SERVER_URL, null, null),
      new SonarConfigEntry("no-target", "moduleA", null, null, null),
      entry("valid", "moduleA"));

    var config = underTest.get(REPO_URI).orElseThrow();

    assertThat(config.children()).extracting(SonarConfigProject::projectKey).containsExactly("valid");
    assertThat(logTester.logs()).filteredOn(log -> log.contains("Ignoring invalid entry")).hasSize(3);
  }

  @Test
  void get_is_empty_when_no_entry_relates_to_the_folder() {
    when(loader.load(MODULE_A)).thenReturn(Optional.of(fileIn(REPO, entry("other", "other"))));

    assertThat(underTest.get(MODULE_A.toUri())).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("has no entry matching folder"));
  }

  @Test
  void get_loads_a_folder_once_and_keeps_the_result() {
    loaded(REPO, entry("module-a", "moduleA"));

    assertThat(underTest.get(REPO_URI)).isEqualTo(underTest.get(REPO_URI));

    verify(loader, times(1)).load(REPO);
  }

  @Test
  void get_keeps_empty_results_too() {
    when(loader.load(REPO)).thenReturn(Optional.empty());

    underTest.get(REPO_URI);
    underTest.get(REPO_URI);

    verify(loader, times(1)).load(REPO);
  }

  @Test
  void subFolders_are_named_after_their_directory() {
    loaded(REPO, entry("module-b-api", "moduleB/api"));

    var api = REPO.resolve("moduleB/api").toUri();
    assertThat(underTest.subFolderUris(REPO_URI)).containsExactly(api);
    assertThat(underTest.subFolders(REPO_URI))
      .extracting(folder -> folder.getUri(), folder -> folder.getName())
      .containsExactly(tuple(api.toString(), "api"));
  }

  @Test
  void sub_folders_are_empty_without_config() {
    when(loader.load(REPO)).thenReturn(Optional.empty());

    assertThat(underTest.subFolderUris(REPO_URI)).isEmpty();
    assertThat(underTest.subFolders(REPO_URI)).isEmpty();
  }

  @Test
  void scopeUriForFile_is_the_deepest_sub_folder_containing_the_file() {
    loaded(REPO, entry("module-a", "moduleA"), entry("nested", "moduleA/nested"));

    assertThat(underTest.scopeUriForFile(REPO_URI, MODULE_A.resolve("nested/Foo.java").toUri())).isEqualTo(MODULE_A.resolve("nested").toUri());
    assertThat(underTest.scopeUriForFile(REPO_URI, MODULE_A.resolve("Foo.java").toUri())).isEqualTo(MODULE_A.toUri());
    assertThat(underTest.scopeUriForFile(REPO_URI, REPO.resolve("Foo.java").toUri())).isEqualTo(REPO_URI);
  }

  @Test
  void findForFile_returns_the_config_of_the_scope_containing_the_file() {
    loaded(REPO, entry("module-a", "moduleA"));
    when(loader.load(MODULE_A)).thenReturn(Optional.of(fileIn(REPO, entry("module-a", "moduleA"))));

    assertThat(underTest.findForFile(REPO_URI, MODULE_A.resolve("Foo.java").toUri()).orElseThrow().direct().projectKey()).isEqualTo("module-a");
    assertThat(underTest.findForFile(REPO_URI, REPO.resolve("Foo.java").toUri()).orElseThrow().direct()).isNull();
  }

  @Test
  void forget_makes_the_folder_load_again() {
    when(loader.load(REPO)).thenReturn(Optional.empty());
    underTest.get(REPO_URI);

    underTest.forget(REPO_URI);
    underTest.get(REPO_URI);

    verify(loader, times(2)).load(REPO);
  }

  @Test
  void forget_also_drops_the_sub_folders() {
    loaded(REPO, entry("module-a", "moduleA"));
    when(loader.load(MODULE_A)).thenReturn(Optional.empty());
    underTest.get(REPO_URI);
    underTest.get(MODULE_A.toUri());

    underTest.forget(REPO_URI);
    underTest.get(MODULE_A.toUri());

    verify(loader, times(2)).load(MODULE_A);
  }

  private void loaded(Path folder, SonarConfigEntry... entries) {
    when(loader.load(folder)).thenReturn(Optional.of(fileIn(folder, entries)));
  }

  private static SonarConfigFile fileIn(Path directory, SonarConfigEntry... entries) {
    return new SonarConfigFile(directory.resolve(".sonar-config.json"), List.of(entries));
  }

  private static SonarConfigEntry entry(String projectKey, String path) {
    return new SonarConfigEntry(projectKey, path, SERVER_URL, null, null);
  }
}
