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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SonarConfigResolverTests {

  private static final String SERVER_URL = "https://sonarqube.example.com";

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path repo;

  private SonarConfigResolver underTest;

  @BeforeEach
  void prepare() throws IOException {
    Files.createDirectory(repo.resolve(".git"));
    underTest = new SonarConfigResolver(logTester.getLogger());
  }

  @Test
  void get_is_empty_for_non_file_uri() {
    assertThat(underTest.get(URI.create("ftp://example.com/folder"))).isEmpty();
  }

  @Test
  void get_is_empty_when_no_config_file_is_found() throws IOException {
    var folder = directory("moduleA");

    assertThat(underTest.get(folder.toUri())).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("No '.sonar-config.json' found"));
  }

  @Test
  void get_does_not_look_above_the_git_root() throws IOException {
    var outerConfigDir = repo;
    var nestedRepo = directory("nested");
    Files.createDirectory(nestedRepo.resolve(".git"));
    writeConfig(outerConfigDir, entry("outer", "nested"));

    assertThat(underTest.get(nestedRepo.toUri())).isEmpty();
  }

  @Test
  void get_is_empty_when_config_cannot_be_parsed() throws IOException {
    Files.writeString(repo.resolve(".sonar-config.json"), "not json");

    assertThat(underTest.get(repo.toUri())).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("could not be parsed"));
  }

  @Test
  void get_is_empty_when_config_has_no_projects_array() throws IOException {
    Files.writeString(repo.resolve(".sonar-config.json"), "{}");

    assertThat(underTest.get(repo.toUri())).isEmpty();
  }

  @Test
  void get_binds_the_folder_directly_when_an_entry_points_at_it() {
    writeConfig(repo, entry("root-project", "."));

    var config = underTest.get(repo.toUri()).orElseThrow();

    assertThat(config.children()).isEmpty();
    assertThat(config.direct().scopeUri()).isEqualTo(repo.toUri());
    assertThat(config.direct().projectKey()).isEqualTo("root-project");
    assertThat(config.direct().relativePath()).isEqualTo(".");
  }

  @Test
  void get_finds_the_config_in_a_parent_folder() throws IOException {
    var folder = directory("moduleA");
    writeConfig(repo, entry("module-a", "moduleA"));

    var config = underTest.get(folder.toUri()).orElseThrow();

    assertThat(config.direct().projectKey()).isEqualTo("module-a");
    assertThat(config.children()).isEmpty();
  }

  @Test
  void get_prefers_the_deepest_entry_containing_the_folder() throws IOException {
    var folder = directory("moduleA/src");
    writeConfig(repo, entry("root-project", ".") + "," + entry("module-a", "moduleA"));

    assertThat(underTest.get(folder.toUri()).orElseThrow().direct().projectKey()).isEqualTo("module-a");
  }

  @Test
  void get_declares_nested_entries_as_children() throws IOException {
    var moduleA = directory("moduleA");
    var api = directory("moduleB/api");
    writeConfig(repo, entry("module-a", "moduleA") + "," + entry("module-b-api", "moduleB/api"));

    var config = underTest.get(repo.toUri()).orElseThrow();

    assertThat(config.direct()).isNull();
    assertThat(config.children()).extracting(SonarConfigProject::scopeUri).containsExactly(moduleA.toUri(), api.toUri());
    assertThat(config.children()).extracting(SonarConfigProject::relativePath).containsExactly("moduleA", "moduleB/api");
    assertThat(config.allProjects()).hasSize(2);
  }

  @Test
  void get_keeps_cloud_entries() throws IOException {
    directory("moduleA");
    writeConfig(repo, "{\"projectKey\":\"module-a\",\"path\":\"moduleA\",\"organization\":\"my-org\",\"region\":\"US\"}");

    var project = underTest.get(repo.toUri()).orElseThrow().children().get(0);

    assertThat(project.serverUrl()).isNull();
    assertThat(project.organization()).isEqualTo("my-org");
    assertThat(project.region()).isEqualTo("US");
  }

  @Test
  void get_ignores_invalid_entries() throws IOException {
    directory("moduleA");
    writeConfig(repo, "{\"path\":\"moduleA\",\"serverUrl\":\"" + SERVER_URL + "\"},"
      + "{\"projectKey\":\"no-path\",\"serverUrl\":\"" + SERVER_URL + "\"},"
      + "{\"projectKey\":\"no-target\",\"path\":\"moduleA\"},"
      + entry("valid", "moduleA"));

    var config = underTest.get(repo.toUri()).orElseThrow();

    assertThat(config.children()).extracting(SonarConfigProject::projectKey).containsExactly("valid");
    assertThat(logTester.logs()).filteredOn(log -> log.contains("Ignoring invalid entry")).hasSize(3);
  }

  @Test
  void get_is_empty_when_no_entry_relates_to_the_folder() throws IOException {
    var folder = directory("moduleA");
    directory("other");
    writeConfig(repo, entry("other", "other"));

    assertThat(underTest.get(folder.toUri())).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("has no entry matching folder"));
  }

  @Test
  void subFolders_are_named_after_their_directory() throws IOException {
    var api = directory("moduleB/api");
    writeConfig(repo, entry("module-b-api", "moduleB/api"));

    assertThat(underTest.subFolderUris(repo.toUri())).containsExactly(api.toUri());
    assertThat(underTest.subFolders(repo.toUri()))
      .extracting(folder -> folder.getUri(), folder -> folder.getName())
      .containsExactly(tuple(api.toUri().toString(), "api"));
  }

  @Test
  void sub_folders_are_empty_without_config() {
    assertThat(underTest.subFolderUris(repo.toUri())).isEmpty();
    assertThat(underTest.subFolders(repo.toUri())).isEmpty();
  }

  @Test
  void scopeUriForFile_is_the_deepest_sub_folder_containing_the_file() throws IOException {
    var moduleA = directory("moduleA");
    var nested = directory("moduleA/nested");
    writeConfig(repo, entry("module-a", "moduleA") + "," + entry("nested", "moduleA/nested"));

    assertThat(underTest.scopeUriForFile(repo.toUri(), nested.resolve("Foo.java").toUri())).isEqualTo(nested.toUri());
    assertThat(underTest.scopeUriForFile(repo.toUri(), moduleA.resolve("Foo.java").toUri())).isEqualTo(moduleA.toUri());
    assertThat(underTest.scopeUriForFile(repo.toUri(), repo.resolve("Foo.java").toUri())).isEqualTo(repo.toUri());
  }

  @Test
  void findForFile_returns_the_config_of_the_scope_containing_the_file() throws IOException {
    var moduleA = directory("moduleA");
    writeConfig(repo, entry("module-a", "moduleA"));

    var config = underTest.findForFile(repo.toUri(), moduleA.resolve("Foo.java").toUri()).orElseThrow();

    assertThat(config.direct().projectKey()).isEqualTo("module-a");
    assertThat(underTest.findForFile(repo.toUri(), repo.resolve("Foo.java").toUri())).isPresent();
  }

  @Test
  void get_caches_until_the_folder_is_forgotten() throws IOException {
    directory("moduleA");
    writeConfig(repo, entry("first", "moduleA"));
    assertThat(underTest.get(repo.toUri()).orElseThrow().children()).extracting(SonarConfigProject::projectKey).containsExactly("first");

    writeConfig(repo, entry("second", "moduleA"));
    assertThat(underTest.get(repo.toUri()).orElseThrow().children()).extracting(SonarConfigProject::projectKey).containsExactly("first");

    underTest.forget(repo.toUri());
    assertThat(underTest.get(repo.toUri()).orElseThrow().children()).extracting(SonarConfigProject::projectKey).containsExactly("second");
  }

  @Test
  void forget_also_drops_the_cached_sub_folders() throws IOException {
    var moduleA = directory("moduleA");
    writeConfig(repo, entry("first", "moduleA"));
    underTest.get(repo.toUri());
    assertThat(underTest.get(moduleA.toUri()).orElseThrow().direct().projectKey()).isEqualTo("first");

    writeConfig(repo, entry("second", "moduleA"));
    underTest.forget(repo.toUri());

    assertThat(underTest.get(moduleA.toUri()).orElseThrow().direct().projectKey()).isEqualTo("second");
  }

  private Path directory(String relativePath) throws IOException {
    return Files.createDirectories(repo.resolve(relativePath));
  }

  private static String entry(String projectKey, String path) {
    return "{\"projectKey\":\"" + projectKey + "\",\"path\":\"" + path + "\",\"serverUrl\":\"" + SERVER_URL + "\"}";
  }

  private static void writeConfig(Path directory, String projectEntries) {
    try {
      Files.writeString(directory.resolve(".sonar-config.json"), "{\"projects\":[" + projectEntries + "]}");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
