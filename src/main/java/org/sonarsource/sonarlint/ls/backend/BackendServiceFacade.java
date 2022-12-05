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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

public class BackendServiceFacade {

  private final BackendService backend;
  private final BackendInitParams initParams;
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  public BackendServiceFacade(SonarLintBackend backend) {
    this.backend = new BackendService(backend);
    this.initParams = new BackendInitParams();
  }

  public BackendService getBackendService() {
    if (!initialized.get()) {
      throw new IllegalStateException("Backend service is not initialized");
    }
    return backend;
  }

  public BackendInitParams getInitParams() {
    return initParams;
  }

  public void didChangeConnections(Map<String, ServerConnectionSettings> connections) {
    initOnce(connections);
    backend.didChangeConnections(connections);
  }

  private void initOnce(Map<String, ServerConnectionSettings> connections) {
    if (initialized.getAndSet(true)) return;
    var sqConnections = BackendService.extractSonarQubeConnections(connections);
    var scConnections = BackendService.extractSonarCloudConnections(connections);
    initParams.setSonarQubeConnections(sqConnections);
    initParams.setSonarCloudConnections(scConnections);
    backend.initialize(toInitParams(initParams));
  }

  private static InitializeParams toInitParams(BackendInitParams initParams) {
    return new InitializeParams(
      initParams.getTelemetryProductKey(),
      initParams.getStorageRoot(),
      initParams.getEmbeddedPluginPaths(),
      initParams.getConnectedModeExtraPluginPathsByKey(),
      initParams.getConnectedModeEmbeddedPluginPathsByKey(),
      initParams.getEnabledLanguagesInStandaloneMode(),
      initParams.getExtraEnabledLanguagesInConnectedMode(),
      initParams.isEnableSecurityHotspots(),
      initParams.getSonarQubeConnections(),
      initParams.getSonarCloudConnections(),
      initParams.getSonarlintUserHome()
    );
  }

  public void shutdown() {
    backend.shutdown();
  }

  public void addFolders(List<WorkspaceFolder> added, Function<WorkspaceFolder, Optional<ProjectBindingWrapper>> bindingProvider) {
    List<ConfigurationScopeDto> addedScopeDtos = added.stream()
      .map(folder -> getBackendService().getConfigScopeDto(folder, bindingProvider.apply(folder)))
      .collect(Collectors.toList());
    var params = new DidAddConfigurationScopesParams(addedScopeDtos);
    backend.addConfigurationScopes(params);
  }

  public void addFolder(WorkspaceFolder added, Optional<ProjectBindingWrapper> bindingWrapperOptional) {
    ConfigurationScopeDto dto = getBackendService().getConfigScopeDto(added, bindingWrapperOptional);
    var params = new DidAddConfigurationScopesParams(List.of(dto));
    backend.addConfigurationScopes(params);
  }

  public void removeWorkspaceFolder(String removedUri) {
    backend.removeWorkspaceFolder(removedUri);
  }
}
