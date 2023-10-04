/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.ClientInfoDto;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ReopenAllIssuesForFileParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ReopenIssueResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetStandaloneRuleDescriptionResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ListAllStandaloneRulesDefinitionsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.UpdateStandaloneRulesConfigurationParams;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesResponse;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.telemetry.TelemetryInitParams;

public class BackendServiceFacade {

  public static final String ROOT_CONFIGURATION_SCOPE = "<root>";

  private final BackendService backend;
  private final BackendInitParams initParams;
  private final ConfigurationScopeDto rootConfigurationScope;
  private SettingsManager settingsManager;
  private SonarLintTelemetry telemetry;
  private TelemetryInitParams telemetryInitParams;
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  public BackendServiceFacade(SonarLintBackend backend) {
    this.backend = new BackendService(backend);
    this.initParams = new BackendInitParams();
    this.rootConfigurationScope = new ConfigurationScopeDto(ROOT_CONFIGURATION_SCOPE, null, false, ROOT_CONFIGURATION_SCOPE,
      new BindingConfigurationDto(null, null, false)
    );
  }

  public BackendService getBackendService() {
    if (!initialized.get()) {
      throw new IllegalStateException("Backend service is not initialized");
    }
    return backend;
  }

  public void setSettingsManager(SettingsManager settingsManager) {
    this.settingsManager = settingsManager;
  }

  public BackendInitParams getInitParams() {
    return initParams;
  }

  public void didChangeConnections(Map<String, ServerConnectionSettings> connections) {
    backend.didChangeConnections(connections);
  }

  public void didChangeCredentials(String connectionId) {
    backend.didChangeCredentials(connectionId);
  }

  private void initOnce(Map<String, ServerConnectionSettings> connections) {
    if (initialized.getAndSet(true)) return;
    var sqConnections = BackendService.extractSonarQubeConnections(connections);
    var scConnections = BackendService.extractSonarCloudConnections(connections);
    initParams.setSonarQubeConnections(sqConnections);
    initParams.setSonarCloudConnections(scConnections);
    initParams.setStandaloneRuleConfigByKey(settingsManager.getStandaloneRuleConfigByKey());
    initParams.setFocusOnNewCode(settingsManager.getCurrentSettings().isFocusOnNewCode());
    backend.initialize(toInitParams(initParams));
    backend.addConfigurationScopes(new DidAddConfigurationScopesParams(List.of(rootConfigurationScope)));
  }

  private static InitializeParams toInitParams(BackendInitParams initParams) {
    return new InitializeParams(
      new ClientInfoDto("Visual Studio Code", initParams.getTelemetryProductKey(), initParams.getUserAgent()),
      new FeatureFlagsDto(true, true, true, true, initParams.isEnableSecurityHotspots(), true),
      initParams.getStorageRoot(),
      null,
      initParams.getEmbeddedPluginPaths(),
      initParams.getConnectedModeEmbeddedPluginPathsByKey(),
      initParams.getEnabledLanguagesInStandaloneMode(),
      initParams.getExtraEnabledLanguagesInConnectedMode(),
      initParams.getSonarQubeConnections(),
      initParams.getSonarCloudConnections(),
      initParams.getSonarlintUserHome(),
      initParams.getStandaloneRuleConfigByKey(),
      initParams.isFocusOnNewCode()
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

  public CompletableFuture<GetEffectiveRuleDetailsResponse> getEffectiveRuleDetails(@Nullable String workspaceFolder, String ruleKey, String ruleContextKey) {
    var workspaceOrRootScope = Optional.ofNullable(workspaceFolder).orElse(ROOT_CONFIGURATION_SCOPE);
    var params = new GetEffectiveRuleDetailsParams(workspaceOrRootScope, ruleKey, ruleContextKey);
    return backend.getRuleDetails(params);
  }

  public void updateStandaloneRulesConfiguration(Map<String, StandaloneRuleConfigDto> ruleConfigByKey) {
    var params = new UpdateStandaloneRulesConfigurationParams(ruleConfigByKey);
    backend.updateStandaloneRulesConfiguration(params);
  }

  public CompletableFuture<GetStandaloneRuleDescriptionResponse> getStandaloneRuleDetails(String ruleKey) {
    var params = new GetStandaloneRuleDescriptionParams(ruleKey);
    return backend.getStandaloneRuleDetails(params);
  }

  public CompletableFuture<ListAllStandaloneRulesDefinitionsResponse> listAllStandaloneRulesDefinitions() {
    return backend.listAllStandaloneRulesDefinitions();
  }

  public CompletableFuture<CheckLocalDetectionSupportedResponse> checkLocalDetectionSupported(String workspaceFolder) {
    var params = new CheckLocalDetectionSupportedParams(workspaceFolder);
    return backend.checkLocalDetectionSupported(params);
  }

  public CompletableFuture<HelpGenerateUserTokenResponse> helpGenerateUserToken(String serverUrl, boolean isSonarCloud) {
    var params = new HelpGenerateUserTokenParams(serverUrl, isSonarCloud);
    return backend.helpGenerateUserToken(params);
  }

  public void initialize(Map<String, ServerConnectionSettings> serverConnections) {
    initOnce(serverConnections);
    telemetry.initialize(telemetryInitParams);
  }

  public void notifyBackendOnBranchChanged(String folderUri, String newBranchName) {
    backend.notifyBackendOnBranchChanged(folderUri, newBranchName);
  }

  public CompletableFuture<TrackWithServerIssuesResponse> matchIssues(TrackWithServerIssuesParams params) {
    return backend.matchIssues(params);
  }

  public CompletableFuture<CheckStatusChangePermittedResponse> checkChangeIssueStatusPermitted(CheckStatusChangePermittedParams params) {
    return backend.checkChangeIssueStatusPermitted(params);
  }

  public CompletableFuture<ReopenIssueResponse> reopenAllIssuesForFile(ReopenAllIssuesForFileParams params) {
    return backend.reopenAllIssuesForFile(params);
  }

  public HttpClient getHttpClientNoAuth() {
    return backend.getHttpClientNoAuth();
  }

  public HttpClient getHttpClient(String connectionId) {
    return backend.getHttpClient(connectionId);
  }

  public CompletableFuture<ValidateConnectionResponse> validateConnection(ValidateConnectionParams params) {
    return backend.validateConnection(params);
  }

  public void setTelemetry(SonarLintTelemetry telemetry) {
    this.telemetry = telemetry;
  }

  public void setTelemetryInitParams(TelemetryInitParams telemetryInitParams) {
    this.telemetryInitParams = telemetryInitParams;
  }
}
