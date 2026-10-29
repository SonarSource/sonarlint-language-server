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
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedSonarConfigTests {

  private static final URI SCOPE = URI.create("file:///repo/");

  @Test
  void allProjects_lists_the_direct_project_before_the_children() {
    var direct = new SonarConfigProject(SCOPE, "direct", "https://sq.example.com", null, null, ".");
    var child = new SonarConfigProject(URI.create("file:///repo/a/"), "child", "https://sq.example.com", null, null, "a");

    assertThat(new ResolvedSonarConfig(direct, List.of(child)).allProjects()).containsExactly(direct, child);
  }

  @Test
  void allProjects_has_only_the_children_without_a_direct_project() {
    var child = new SonarConfigProject(URI.create("file:///repo/a/"), "child", "https://sq.example.com", null, null, "a");

    assertThat(new ResolvedSonarConfig(null, List.of(child)).allProjects()).containsExactly(child);
  }

  @Test
  void describeTarget_names_the_server_url() {
    assertThat(new SonarConfigProject(SCOPE, "key", "https://sq.example.com", null, null, ".").describeTarget())
      .isEqualTo("serverUrl=https://sq.example.com");
  }

  @Test
  void describeTarget_names_the_organization_and_defaults_the_region_to_eu() {
    assertThat(new SonarConfigProject(SCOPE, "key", null, "my-org", null, ".").describeTarget()).isEqualTo("organization=my-org region=EU");
    assertThat(new SonarConfigProject(SCOPE, "key", null, "my-org", "US", ".").describeTarget()).isEqualTo("organization=my-org region=US");
  }
}
