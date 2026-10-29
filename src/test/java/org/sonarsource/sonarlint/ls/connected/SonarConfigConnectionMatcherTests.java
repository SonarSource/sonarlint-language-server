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
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.ls.folders.SonarConfigProject;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

import static org.assertj.core.api.Assertions.assertThat;

class SonarConfigConnectionMatcherTests {

  @Test
  void matches_a_server_by_url() {
    var connections = Map.of("sq", server("https://sonarqube.example.com"));

    assertThat(SonarConfigConnectionMatcher.findConnectionId(connections, serverProject("https://sonarqube.example.com"))).isEqualTo("sq");
  }

  @Test
  void ignores_a_trailing_slash_on_either_side() {
    assertThat(SonarConfigConnectionMatcher.findConnectionId(Map.of("sq", server("https://sonarqube.example.com/")), serverProject("https://sonarqube.example.com")))
      .isEqualTo("sq");
    assertThat(SonarConfigConnectionMatcher.findConnectionId(Map.of("sq", server("https://sonarqube.example.com")), serverProject("https://sonarqube.example.com/")))
      .isEqualTo("sq");
  }

  @Test
  void finds_nothing_for_another_server() {
    assertThat(SonarConfigConnectionMatcher.findConnectionId(Map.of("sq", server("https://sonarqube.example.com")), serverProject("https://other.example.com"))).isNull();
  }

  @Test
  void returns_the_first_matching_connection() {
    var connections = new LinkedHashMap<String, ServerConnectionSettings>();
    connections.put("first", server("https://sonarqube.example.com"));
    connections.put("second", server("https://sonarqube.example.com"));

    assertThat(SonarConfigConnectionMatcher.findConnectionId(connections, serverProject("https://sonarqube.example.com"))).isEqualTo("first");
  }

  @Test
  void matches_a_cloud_organization_in_the_default_eu_region() {
    var connections = Map.of("sc", cloud(ServerConnectionSettings.getSonarCloudUrl(), "my-org", null));

    assertThat(SonarConfigConnectionMatcher.findConnectionId(connections, cloudProject("my-org", null))).isEqualTo("sc");
    assertThat(SonarConfigConnectionMatcher.findConnectionId(connections, cloudProject("MY-ORG", "eu"))).isEqualTo("sc");
  }

  @Test
  void matches_a_cloud_organization_in_the_us_region() {
    var connections = Map.of("sc-us", cloud(ServerConnectionSettings.getSonarCloudUSUrl(), "my-org", SonarCloudRegion.US));

    assertThat(SonarConfigConnectionMatcher.findConnectionId(connections, cloudProject("my-org", "us"))).isEqualTo("sc-us");
    assertThat(SonarConfigConnectionMatcher.findConnectionId(connections, cloudProject("my-org", null))).isNull();
  }

  @Test
  void does_not_match_a_cloud_organization_of_another_name() {
    var connections = Map.of("sc", cloud(ServerConnectionSettings.getSonarCloudUrl(), "my-org", null));

    assertThat(SonarConfigConnectionMatcher.findConnectionId(connections, cloudProject("other-org", null))).isNull();
  }

  @Test
  void does_not_match_a_cloud_project_against_a_server_connection() {
    var connections = Map.of("sq", new ServerConnectionSettings("sq", "https://sonarqube.example.com", "token", "my-org", false, null));

    assertThat(SonarConfigConnectionMatcher.findConnectionId(connections, cloudProject("my-org", null))).isNull();
  }

  @Test
  void does_not_match_a_cloud_project_against_a_cloud_connection_without_organization() {
    var connections = Map.of("sc", cloud(ServerConnectionSettings.getSonarCloudUrl(), null, null));

    assertThat(SonarConfigConnectionMatcher.findConnectionId(connections, cloudProject("my-org", null))).isNull();
  }

  private static ServerConnectionSettings server(String url) {
    return new ServerConnectionSettings("sq", url, "token", null, false, null);
  }

  private static ServerConnectionSettings cloud(String url, @Nullable String organization, @Nullable SonarCloudRegion region) {
    return new ServerConnectionSettings("sc", url, "token", organization, false, region);
  }

  private static SonarConfigProject serverProject(String serverUrl) {
    return new SonarConfigProject(URI.create("file:///repo/"), "key", serverUrl, null, null, ".");
  }

  private static SonarConfigProject cloudProject(String organization, @Nullable String region) {
    return new SonarConfigProject(URI.create("file:///repo/"), "key", null, organization, region, ".");
  }
}
