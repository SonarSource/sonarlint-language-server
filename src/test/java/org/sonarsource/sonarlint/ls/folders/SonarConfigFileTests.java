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
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SonarConfigFileTests {

  private static final String SERVER_URL = "https://sonarqube.example.com";
  private static final Path REPO = Path.of("repo").toAbsolutePath().normalize();
  private static final URI REPO_URI = REPO.toUri();
  private static final Path MODULE_A = REPO.resolve("moduleA");

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void resolveFor_binds_the_folder_directly_when_an_entry_points_at_it() {
    var config = fileIn(REPO, entry("root-project", ".")).resolveFor(REPO_URI).orElseThrow();

    assertThat(config.children()).isEmpty();
    assertThat(config.direct().scopeUri()).isEqualTo(REPO_URI);
    assertThat(config.direct().projectKey()).isEqualTo("root-project");
    assertThat(config.direct().relativePath()).isEqualTo(".");
  }

  @Test
  void resolveFor_binds_a_folder_below_the_config_directly() {
    var config = fileIn(REPO, entry("module-a", "moduleA")).resolveFor(MODULE_A.toUri()).orElseThrow();

    assertThat(config.direct().projectKey()).isEqualTo("module-a");
    assertThat(config.children()).isEmpty();
  }

  @Test
  void resolveFor_prefers_the_deepest_entry_containing_the_folder() {
    var folder = MODULE_A.resolve("src");

    var config = fileIn(REPO, entry("root-project", "."), entry("module-a", "moduleA")).resolveFor(folder.toUri()).orElseThrow();

    assertThat(config.direct().projectKey()).isEqualTo("module-a");
  }

  @Test
  void resolveFor_declares_nested_entries_as_children() {
    var config = fileIn(REPO, entry("module-a", "moduleA"), entry("module-b-api", "moduleB/api")).resolveFor(REPO_URI).orElseThrow();

    assertThat(config.direct()).isNull();
    assertThat(config.children()).extracting(SonarConfigProject::scopeUri)
      .containsExactly(MODULE_A.toUri(), REPO.resolve("moduleB/api").toUri());
    assertThat(config.children()).extracting(SonarConfigProject::relativePath).containsExactly("moduleA", "moduleB/api");
  }

  @Test
  void resolveFor_keeps_the_first_entry_when_several_point_at_the_same_directory() {
    var config = fileIn(REPO, entry("module-a", "moduleA"), entry("module-a-again", "./moduleA"), entry("module-b", "moduleB"))
      .resolveFor(REPO_URI).orElseThrow();

    assertThat(config.children()).extracting(SonarConfigProject::projectKey).containsExactly("module-a", "module-b");
  }

  @Test
  void resolveFor_keeps_cloud_entries() {
    var project = fileIn(REPO, new SonarConfigEntry("module-a", "moduleA", null, "my-org", "US")).resolveFor(REPO_URI).orElseThrow().children().get(0);

    assertThat(project.serverUrl()).isNull();
    assertThat(project.organization()).isEqualTo("my-org");
    assertThat(project.region()).isEqualTo(SonarCloudRegion.US);
  }

  @Test
  void resolveFor_defaults_a_cloud_entry_to_the_eu_region_and_reads_the_region_whatever_its_case() {
    var config = fileIn(REPO, new SonarConfigEntry("module-a", "moduleA", null, "my-org", null), new SonarConfigEntry("module-b", "moduleB", null, "my-org", "us"))
      .resolveFor(REPO_URI).orElseThrow();

    assertThat(config.children()).extracting(SonarConfigProject::region).containsExactly(SonarCloudRegion.EU, SonarCloudRegion.US);
  }

  @Test
  void resolveFor_has_no_region_for_a_server_entry() {
    var config = fileIn(REPO, new SonarConfigEntry("module-a", "moduleA", SERVER_URL, null, "US")).resolveFor(REPO_URI).orElseThrow();

    assertThat(config.children()).extracting(SonarConfigProject::region).containsOnlyNulls();
  }

  @Test
  void resolveFor_ignores_a_cloud_entry_of_an_unknown_region() {
    var config = fileIn(REPO, new SonarConfigEntry("module-a", "moduleA", null, "my-org", "mars"), entry("module-b", "moduleB")).resolveFor(REPO_URI).orElseThrow();

    assertThat(config.children()).extracting(SonarConfigProject::projectKey).containsExactly("module-b");
    assertThat(logTester.logs()).anyMatch(log -> log.contains("Ignoring invalid entry") && log.contains("module-a"));
  }

  @Test
  void resolveFor_resolves_the_path_to_compile_commands_against_the_root_of_the_project() {
    var config = fileIn(REPO, entryWithCompileCommands("module-a", "moduleA", "build/compile_commands.json")).resolveFor(REPO_URI).orElseThrow();

    assertThat(config.children().get(0).pathToCompileCommands()).isEqualTo(MODULE_A.resolve("build/compile_commands.json").toString());
  }

  @Test
  void resolveFor_resolves_it_against_the_root_of_the_project_rather_than_the_folder_below_it() {
    var folder = MODULE_A.resolve("src");

    var config = fileIn(REPO, entryWithCompileCommands("module-a", "moduleA", "compile_commands.json")).resolveFor(folder.toUri()).orElseThrow();

    assertThat(config.direct().pathToCompileCommands()).isEqualTo(MODULE_A.resolve("compile_commands.json").toString());
  }

  @Test
  void resolveFor_resolves_it_for_the_project_of_the_folder_itself() {
    var config = fileIn(REPO, entryWithCompileCommands("root-project", ".", "build/compile_commands.json")).resolveFor(REPO_URI).orElseThrow();

    assertThat(config.direct().pathToCompileCommands()).isEqualTo(REPO.resolve("build/compile_commands.json").toString());
  }

  @Test
  void resolveFor_keeps_an_absolute_path_to_compile_commands() {
    var elsewhere = REPO.resolve("elsewhere/compile_commands.json").toAbsolutePath();

    var config = fileIn(REPO, entryWithCompileCommands("module-a", "moduleA", elsewhere.toString())).resolveFor(REPO_URI).orElseThrow();

    assertThat(config.children().get(0).pathToCompileCommands()).isEqualTo(elsewhere.toString());
  }

  @Test
  void resolveFor_has_no_path_to_compile_commands_without_settings() {
    var config = fileIn(REPO, entry("module-a", "moduleA"), new SonarConfigEntry("module-b", "moduleB", SERVER_URL, null, null, Map.of("sonar.some.future.property", "value")))
      .resolveFor(REPO_URI).orElseThrow();

    assertThat(config.children()).extracting(SonarConfigProject::pathToCompileCommands).containsOnlyNulls();
  }

  @Test
  void resolveFor_ignores_an_invalid_path_to_compile_commands_and_keeps_the_project() {
    var config = fileIn(REPO, entryWithCompileCommands("module-a", "moduleA", "build/\0compile_commands.json")).resolveFor(REPO_URI).orElseThrow();

    assertThat(config.children()).extracting(SonarConfigProject::projectKey, SonarConfigProject::pathToCompileCommands).containsExactly(tuple("module-a", null));
    assertThat(logTester.logs()).anyMatch(log -> log.contains("Ignoring invalid sonar.cfamily.compile-commands") && log.contains("module-a"));
  }

  @Test
  void resolveFor_ignores_invalid_entries() {
    var config = fileIn(REPO,
      new SonarConfigEntry(null, "moduleA", SERVER_URL, null, null),
      new SonarConfigEntry("no-path", null, SERVER_URL, null, null),
      new SonarConfigEntry("no-target", "moduleA", null, null, null),
      null,
      entry("valid", "moduleA")).resolveFor(REPO_URI).orElseThrow();

    assertThat(config.children()).extracting(SonarConfigProject::projectKey).containsExactly("valid");
    assertThat(logTester.logs()).filteredOn(log -> log.contains("Ignoring invalid entry")).hasSize(4);
  }

  @Test
  void resolveFor_is_empty_when_no_entry_relates_to_the_folder() {
    assertThat(fileIn(REPO, entry("other", "other")).resolveFor(MODULE_A.toUri())).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("has no entry matching folder"));
  }

  private SonarConfigFile fileIn(Path directory, SonarConfigEntry... entries) {
    return new SonarConfigFile(directory.resolve(".sonar-config.json"), Arrays.asList(entries), logTester.getLogger());
  }

  private static SonarConfigEntry entry(String projectKey, String path) {
    return new SonarConfigEntry(projectKey, path, SERVER_URL, null, null);
  }

  private static SonarConfigEntry entryWithCompileCommands(String projectKey, String path, String pathToCompileCommands) {
    return new SonarConfigEntry(projectKey, path, SERVER_URL, null, null, Map.of("sonar.cfamily.compile-commands", pathToCompileCommands));
  }
}
