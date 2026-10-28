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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class SonarConfigLoaderTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path repo;

  private SonarConfigLoader underTest;

  @BeforeEach
  void prepare() throws IOException {
    Files.createDirectory(repo.resolve(".git"));
    underTest = new SonarConfigLoader(logTester.getLogger());
  }

  @Test
  void load_is_empty_when_no_config_file_is_found() throws IOException {
    var folder = directory("moduleA");

    assertThat(underTest.load(folder)).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("No '.sonar-config.json' found"));
  }

  @Test
  void load_does_not_look_above_the_git_root() throws IOException {
    var nestedRepo = directory("nested");
    Files.createDirectory(nestedRepo.resolve(".git"));
    writeConfig(repo, "{\"projectKey\":\"outer\",\"path\":\"nested\",\"serverUrl\":\"https://sonarqube.example.com\"}");

    assertThat(underTest.load(nestedRepo)).isEmpty();
  }

  @Test
  void load_only_looks_in_the_folder_itself_outside_a_git_repository() throws IOException {
    var outsideRepo = Files.createTempDirectory(repo, "plain");
    Files.delete(repo.resolve(".git"));
    var folder = Files.createDirectory(outsideRepo.resolve("moduleA"));
    writeConfig(outsideRepo, "{\"projectKey\":\"a\",\"path\":\"moduleA\",\"serverUrl\":\"https://sonarqube.example.com\"}");

    assertThat(underTest.load(folder)).isEmpty();
  }

  @Test
  void load_is_empty_when_config_cannot_be_parsed() throws IOException {
    Files.writeString(repo.resolve(".sonar-config.json"), "not json");

    assertThat(underTest.load(repo)).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("could not be parsed"));
  }

  @Test
  void load_is_empty_when_config_has_no_projects_array() throws IOException {
    Files.writeString(repo.resolve(".sonar-config.json"), "{}");

    assertThat(underTest.load(repo)).isEmpty();
  }

  @Test
  void load_returns_the_entries_as_written_and_where_the_file_is() {
    writeConfig(repo, "{\"projectKey\":\"server\",\"path\":\"a\",\"serverUrl\":\"https://sonarqube.example.com\"},"
      + "{\"projectKey\":\"cloud\",\"path\":\"b\",\"organization\":\"my-org\",\"region\":\"US\"},"
      + "{\"path\":\"incomplete\"}");

    var file = underTest.load(repo).orElseThrow();

    assertThat(file.path()).isEqualTo(repo.resolve(".sonar-config.json"));
    assertThat(file.projects()).containsExactly(
      new SonarConfigEntry("server", "a", "https://sonarqube.example.com", null, null),
      new SonarConfigEntry("cloud", "b", null, "my-org", "US"),
      new SonarConfigEntry(null, "incomplete", null, null, null));
  }

  @Test
  void load_finds_the_config_in_a_parent_folder() throws IOException {
    var folder = directory("moduleA/src");
    writeConfig(repo, "{\"projectKey\":\"a\",\"path\":\"moduleA\",\"serverUrl\":\"https://sonarqube.example.com\"}");

    assertThat(underTest.load(folder).orElseThrow().path()).isEqualTo(repo.resolve(".sonar-config.json"));
  }

  @Test
  void load_prefers_the_nearest_config() throws IOException {
    var folder = directory("moduleA");
    writeConfig(repo, "{\"projectKey\":\"outer\",\"path\":\".\",\"serverUrl\":\"https://sonarqube.example.com\"}");
    writeConfig(folder, "{\"projectKey\":\"inner\",\"path\":\".\",\"serverUrl\":\"https://sonarqube.example.com\"}");

    assertThat(underTest.load(folder).orElseThrow().projects()).extracting(SonarConfigEntry::projectKey).containsExactly("inner");
  }

  @Test
  void load_reads_the_file_system_on_every_call() {
    writeConfig(repo, "{\"projectKey\":\"first\",\"path\":\".\",\"serverUrl\":\"https://sonarqube.example.com\"}");
    assertThat(underTest.load(repo).orElseThrow().projects()).extracting(SonarConfigEntry::projectKey).containsExactly("first");

    writeConfig(repo, "{\"projectKey\":\"second\",\"path\":\".\",\"serverUrl\":\"https://sonarqube.example.com\"}");

    assertThat(underTest.load(repo).orElseThrow().projects()).extracting(SonarConfigEntry::projectKey).containsExactly("second");
  }

  private Path directory(String relativePath) throws IOException {
    return Files.createDirectories(repo.resolve(relativePath));
  }

  private static void writeConfig(Path directory, String projectEntries) {
    try {
      Files.writeString(directory.resolve(".sonar-config.json"), "{\"projects\":[" + projectEntries + "]}");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
