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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.rpc.impl.SonarLintRpcServerImpl;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetBindingSuggestionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.ChangeHotspotStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.GetNewCodeDefinitionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.GetNewCodeDefinitionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ListAllStandaloneRulesDefinitionsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.UpdateStandaloneRulesConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.GetStatusResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.GetBindingSuggestionsResponse;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

public class BackendService {

  private final SonarLintRpcServer backend;
  private final CountDownLatch initializeLatch = new CountDownLatch(1);

  public BackendService(SonarLintRpcServer backend) {
    this.backend = backend;
  }

  public void initialize(InitializeParams backendInitParams) {
    try {
      backend.initialize(backendInitParams).thenRun(initializeLatch::countDown).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Could not initialize SonarLint Backend", e);
    }
  }

  private SonarLintRpcServer initializedBackend() {
    try {
      initializeLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("SonarLint backend initialization interrupted", e);
    }
    return backend;
  }

  public void openHotspotInBrowser(OpenHotspotInBrowserParams params) {
    initializedBackend().getHotspotService().openHotspotInBrowser(params);
  }

  public void didChangeConnections(Map<String, ServerConnectionSettings> connections) {
    var scConnections = extractSonarCloudConnections(connections);
    var sqConnections = extractSonarQubeConnections(connections);
    var params = new DidUpdateConnectionsParams(sqConnections, scConnections);
    initializedBackend().getConnectionService().didUpdateConnections(params);
  }

  public void didChangeCredentials(String connectionId) {
    var params = new DidChangeCredentialsParams(connectionId);
    initializedBackend().getConnectionService().didChangeCredentials(params);
  }

  public static List<SonarQubeConnectionConfigurationDto> extractSonarQubeConnections(Map<String, ServerConnectionSettings> connections) {
    return connections.entrySet().stream()
      .filter(it -> !it.getValue().isSonarCloudAlias())
      .map(it -> new SonarQubeConnectionConfigurationDto(it.getKey(), it.getValue().getServerUrl(), it.getValue().isSmartNotificationsDisabled()))
      .toList();
  }

  public static List<SonarCloudConnectionConfigurationDto> extractSonarCloudConnections(Map<String, ServerConnectionSettings> connections) {
    return connections.entrySet().stream()
      .filter(it -> it.getValue().isSonarCloudAlias())
      .map(it -> new SonarCloudConnectionConfigurationDto(it.getKey(), it.getValue().getOrganizationKey(), it.getValue().isSmartNotificationsDisabled()))
      .toList();
  }

  public ConfigurationScopeDto getConfigScopeDto(WorkspaceFolder added, Optional<ProjectBindingWrapper> bindingOptional) {
    BindingConfigurationDto bindingConfigurationDto;
    if (bindingOptional.isPresent()) {
      ProjectBindingWrapper bindingWrapper = bindingOptional.get();
      bindingConfigurationDto = new BindingConfigurationDto(bindingWrapper.getConnectionId(),
        bindingWrapper.getBinding().projectKey(), true);
    } else {
      bindingConfigurationDto = new BindingConfigurationDto(null, null, false);
    }
    return new ConfigurationScopeDto(added.getUri(), BackendServiceFacade.ROOT_CONFIGURATION_SCOPE, true, added.getName(), bindingConfigurationDto);
  }

  public void removeWorkspaceFolder(String removedUri) {
    var params = new DidRemoveConfigurationScopeParams(removedUri);
    initializedBackend().getConfigurationService().didRemoveConfigurationScope(params);
  }

  public void updateBinding(DidUpdateBindingParams params) {
    initializedBackend().getConfigurationService().didUpdateBinding(params);
  }

  public void addConfigurationScopes(DidAddConfigurationScopesParams params) {
    initializedBackend().getConfigurationService().didAddConfigurationScopes(params);
  }

  public CompletableFuture<CheckLocalDetectionSupportedResponse> checkLocalDetectionSupported(CheckLocalDetectionSupportedParams params) {
    return initializedBackend().getHotspotService().checkLocalDetectionSupported(params);
  }

  public CompletableFuture<Void> shutdown() {
    return backend.shutdown();
  }

  public CompletableFuture<GetEffectiveRuleDetailsResponse> getRuleDetails(GetEffectiveRuleDetailsParams params) {
    return initializedBackend().getRulesService().getEffectiveRuleDetails(params);
  }

  public void updateStandaloneRulesConfiguration(UpdateStandaloneRulesConfigurationParams params) {
    initializedBackend().getRulesService().updateStandaloneRulesConfiguration(params);
  }

  public CompletableFuture<GetStandaloneRuleDescriptionResponse> getStandaloneRuleDetails(GetStandaloneRuleDescriptionParams params) {
    return initializedBackend().getRulesService().getStandaloneRuleDetails(params);
  }

  public CompletableFuture<ListAllStandaloneRulesDefinitionsResponse> listAllStandaloneRulesDefinitions() {
    return initializedBackend().getRulesService().listAllStandaloneRulesDefinitions();
  }

  public CompletableFuture<GetSupportedFilePatternsResponse> getFilePatternsForAnalysis(GetSupportedFilePatternsParams params) {
    return initializedBackend().getAnalysisService().getSupportedFilePatterns(params);
  }

  public CompletableFuture<GetBindingSuggestionsResponse> getBindingSuggestion(GetBindingSuggestionParams params) {
    return initializedBackend().getBindingService().getBindingSuggestions(params);
  }

  public CompletableFuture<Void> changeIssueStatus(ChangeIssueStatusParams params) {
    return initializedBackend().getIssueService().changeStatus(params);
  }

  public CompletableFuture<Void> addIssueComment(AddIssueCommentParams params) {
    return initializedBackend().getIssueService().addComment(params);
  }

  public CompletableFuture<Void> changeHotspotStatus(ChangeHotspotStatusParams params) {
    return initializedBackend().getHotspotService().changeStatus(params);
  }

  public CompletableFuture<CheckStatusChangePermittedResponse> getAllowedHotspotStatuses(CheckStatusChangePermittedParams params) {
    return initializedBackend().getHotspotService().checkStatusChangePermitted(params);
  }

  public void notifyBackendOnVscChange(String folderUri) {
    // TODO
    // client is not notifying the backend about branch change, it wait for request to recompute branch from the backend
    // also client should notify backend about generic VSC event and let backend decide if branch should be recomputed
    initializedBackend().getSonarProjectBranchService()
      .didVcsRepositoryChange(new DidVcsRepositoryChangeParams(folderUri));
  }

  public CompletableFuture<HelpGenerateUserTokenResponse> helpGenerateUserToken(HelpGenerateUserTokenParams params) {
    return initializedBackend().getConnectionService().helpGenerateUserToken(params);
  }

  public CompletableFuture<TrackWithServerIssuesResponse> matchIssues(TrackWithServerIssuesParams params) {
    return initializedBackend().getIssueTrackingService().trackWithServerIssues(params);
  }

  public CompletableFuture<org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse>
  checkChangeIssueStatusPermitted(org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedParams params) {
    return initializedBackend().getIssueService().checkStatusChangePermitted(params);
  }

  public CompletableFuture<ReopenAllIssuesForFileResponse> reopenAllIssuesForFile(ReopenAllIssuesForFileParams params) {
    return initializedBackend().getIssueService().reopenAllIssuesForFile(params);
  }

  public HttpClient getHttpClient(String connectionId) {
    return ((SonarLintRpcServerImpl) initializedBackend()).getHttpClient(connectionId);
  }

  public CompletableFuture<ValidateConnectionResponse> validateConnection(ValidateConnectionParams params) {
    return initializedBackend().getConnectionService().validateConnection(params);
  }

  public CompletableFuture<GetNewCodeDefinitionResponse> getNewCodeDefinition(String configScopeId) {
    return initializedBackend().getNewCodeService().getNewCodeDefinition(new GetNewCodeDefinitionParams(configScopeId));
  }

  public void toggleCleanAsYouCode() {
    initializedBackend().getNewCodeService().didToggleFocus();
  }

  public CompletableFuture<GetStatusResponse> getTelemetryStatus() {
    return initializedBackend().getTelemetryService().getStatus();
  }

  public void enableTelemetry() {
    initializedBackend().getTelemetryService().enableTelemetry();
  }

  public void disableTelemetry() {
    initializedBackend().getTelemetryService().disableTelemetry();
  }

  public TelemetryRpcService getTelemetryService() {
    return initializedBackend().getTelemetryService();
  }
}
