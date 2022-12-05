/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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
package org.sonarsource.sonarlint.ls;

import java.util.Map;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

public class BackendService {

  private final SonarLintBackend backend;

  public BackendService(SonarLintBackend backend) {
    this.backend = backend;
  }

  public void initialize(InitializeParams backendInitParams) {
    backend.initialize(backendInitParams);
  }

  public void openHotspotInBrowser(OpenHotspotInBrowserParams params) {
    backend.getHotspotService().openHotspotInBrowser(params);
  }

  public void didChangeConfiguration(Map<String, ServerConnectionSettings> connections) {
    var scConnections = connections.entrySet().stream()
      .filter(it -> it.getValue().isSonarCloudAlias())
      .map(it -> new SonarCloudConnectionConfigurationDto(it.getKey(), it.getValue().getOrganizationKey()))
      .collect(Collectors.toList());
    var sqConnections = connections.entrySet().stream()
      .filter(it -> !it.getValue().isSonarCloudAlias())
      .map(it -> new SonarQubeConnectionConfigurationDto(it.getKey(), it.getValue().getServerUrl()))
      .collect(Collectors.toList());
    var params = new DidUpdateConnectionsParams(sqConnections, scConnections);
    backend.getConnectionService().didUpdateConnections(params);
  }
}
