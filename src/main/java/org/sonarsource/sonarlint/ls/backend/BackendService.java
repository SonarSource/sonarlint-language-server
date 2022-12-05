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
package org.sonarsource.sonarlint.ls.backend;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
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

  public void didChangeConnections(Map<String, ServerConnectionSettings> connections) {
    var scConnections = extractSonarCloudConnections(connections);
    var sqConnections = extractSonarQubeConnections(connections);
    var params = new DidUpdateConnectionsParams(sqConnections, scConnections);
    backend.getConnectionService().didUpdateConnections(params);
  }

  public static List<SonarQubeConnectionConfigurationDto> extractSonarQubeConnections(Map<String, ServerConnectionSettings> connections) {
    return connections.entrySet().stream()
      .filter(it -> !it.getValue().isSonarCloudAlias())
      .map(it -> new SonarQubeConnectionConfigurationDto(it.getKey(), it.getValue().getServerUrl()))
      .collect(Collectors.toList());
  }

  public static List<SonarCloudConnectionConfigurationDto> extractSonarCloudConnections(Map<String, ServerConnectionSettings> connections) {
    return connections.entrySet().stream()
      .filter(it -> it.getValue().isSonarCloudAlias())
      .map(it -> new SonarCloudConnectionConfigurationDto(it.getKey(), it.getValue().getOrganizationKey()))
      .collect(Collectors.toList());
  }

  public ConfigurationScopeDto getConfigScopeDto(WorkspaceFolder added, Optional<ProjectBindingWrapper> bindingOptional) {
    BindingConfigurationDto bindingConfigurationDto;
    if (bindingOptional.isPresent()) {
      ProjectBindingWrapper bindingWrapper = bindingOptional.get();
      bindingConfigurationDto = new BindingConfigurationDto(bindingWrapper.getConnectionId(),
        bindingWrapper.getBinding().projectKey(), true);
    } else {
      bindingConfigurationDto = new BindingConfigurationDto(null, null, true);
    }
    return new ConfigurationScopeDto(added.getUri(), null, true, added.getName(), bindingConfigurationDto);
  }

  public void removeWorkspaceFolder(String removedUri) {
    var params = new DidRemoveConfigurationScopeParams(removedUri);
    backend.getConfigurationService().didRemoveConfigurationScope(params);
  }

  public void updateBinding(DidUpdateBindingParams params) {
    backend.getConfigurationService().didUpdateBinding(params);
  }

  public void addConfigurationScopes(DidAddConfigurationScopesParams params) {
    backend.getConfigurationService().didAddConfigurationScopes(params);
  }

  public void shutdown() {
    backend.shutdown();
  }
}
