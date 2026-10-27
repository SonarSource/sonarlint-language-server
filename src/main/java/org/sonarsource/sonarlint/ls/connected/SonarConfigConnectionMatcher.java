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

import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.ls.folders.SonarConfigProject;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

/**
 * Finds which configured connection a project declared in {@code .sonar-config.json} belongs to: by server URL for
 * SonarQube Server, or by organization and region for SonarQube Cloud.
 */
class SonarConfigConnectionMatcher {

  private static final String DEFAULT_CLOUD_REGION = "EU";

  @CheckForNull
  String findConnectionId(Map<String, ServerConnectionSettings> connections, SonarConfigProject project) {
    return connections.entrySet().stream()
      .filter(e -> matches(e.getValue(), project))
      .map(Map.Entry::getKey)
      .findFirst()
      .orElse(null);
  }

  private static boolean matches(ServerConnectionSettings connection, SonarConfigProject project) {
    if (project.serverUrl() != null) {
      return withoutTrailingSlash(connection.getServerUrl()).equals(withoutTrailingSlash(project.serverUrl()));
    }
    if (!connection.isSonarCloudAlias() || connection.getOrganizationKey() == null) {
      return false;
    }
    var connectionRegion = connection.getRegion() != null ? connection.getRegion().name() : DEFAULT_CLOUD_REGION;
    var projectRegion = project.region() != null ? project.region() : DEFAULT_CLOUD_REGION;
    return connection.getOrganizationKey().equalsIgnoreCase(project.organization()) && connectionRegion.equalsIgnoreCase(projectRegion);
  }

  private static String withoutTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
