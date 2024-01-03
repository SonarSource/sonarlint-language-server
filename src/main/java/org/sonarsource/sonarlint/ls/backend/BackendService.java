/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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
import java.util.function.Function;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.GetSupportedFilePatternsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.GetSupportedFilePatternsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.binding.GetBindingSuggestionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.branch.DidChangeActiveSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.ChangeHotspotStatusParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ReopenAllIssuesForFileParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ReopenIssueResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.newcode.GetNewCodeDefinitionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.newcode.GetNewCodeDefinitionResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetStandaloneRuleDescriptionResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ListAllStandaloneRulesDefinitionsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.UpdateStandaloneRulesConfigurationParams;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesResponse;
import org.sonarsource.sonarlint.core.clientapi.client.binding.GetBindingSuggestionsResponse;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.util.EnumLabelsMapper;

import static org.sonarsource.sonarlint.ls.backend.BackendServiceFacade.ROOT_CONFIGURATION_SCOPE;

public class BackendService {

  private final SonarLintBackend backend;
  private final LanguageClientLogger logOutput;
  private final SonarLintExtendedLanguageClient client;
  private final CountDownLatch initializeLatch = new CountDownLatch(1);

  public BackendService(SonarLintBackend backend, LanguageClientLogger logOutput, SonarLintExtendedLanguageClient client) {
    this.backend = backend;
    this.logOutput = logOutput;
    this.client = client;
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

  private SonarLintBackend initializedBackend() {
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
    return new ConfigurationScopeDto(added.getUri(), ROOT_CONFIGURATION_SCOPE, true, added.getName(), bindingConfigurationDto);
  }

  public void removeWorkspaceFolder(String removedUri) {
    var params = new DidRemoveConfigurationScopeParams(removedUri);
    initializedBackend().getConfigurationService().didRemoveConfigurationScope(params);
  }

  public void updateBinding(DidUpdateBindingParams params) {
    initializedBackend().getConfigurationService().didUpdateBinding(params);
  }

  public void addWorkspaceFolders(List<WorkspaceFolder> added, Function<WorkspaceFolder, Optional<ProjectBindingWrapper>> bindingProvider) {
    List<ConfigurationScopeDto> addedScopeDtos = added.stream()
      .map(folder -> getConfigScopeDto(folder, bindingProvider.apply(folder)))
      .toList();
    var params = new DidAddConfigurationScopesParams(addedScopeDtos);
    addConfigurationScopes(params);
  }

  public void addWorkspaceFolder(WorkspaceFolder added, Optional<ProjectBindingWrapper> bindingWrapperOptional) {
    ConfigurationScopeDto dto = getConfigScopeDto(added, bindingWrapperOptional);
    var params = new DidAddConfigurationScopesParams(List.of(dto));
    addConfigurationScopes(params);
  }

  void addConfigurationScopes(DidAddConfigurationScopesParams params) {
    initializedBackend().getConfigurationService().didAddConfigurationScopes(params);
  }

  public CompletableFuture<GetEffectiveRuleDetailsResponse> getEffectiveRuleDetails(@Nullable String workspaceFolder, String ruleKey, String ruleContextKey) {
    var workspaceOrRootScope = Optional.ofNullable(workspaceFolder).orElse(ROOT_CONFIGURATION_SCOPE);
    var params = new GetEffectiveRuleDetailsParams(workspaceOrRootScope, ruleKey, ruleContextKey);
    return getRuleDetails(params);
  }

  public CompletableFuture<CheckLocalDetectionSupportedResponse> checkLocalDetectionSupported(String folderUri) {
    var params = new CheckLocalDetectionSupportedParams(folderUri);
    return initializedBackend().getHotspotService().checkLocalDetectionSupported(params)
      .exceptionally(e -> new CheckLocalDetectionSupportedResponse(false, e.getMessage()));
  }

  public void shutdown() {
    backend.shutdown();
  }

  public CompletableFuture<GetEffectiveRuleDetailsResponse> getRuleDetails(GetEffectiveRuleDetailsParams params) {
    return initializedBackend().getRulesService().getEffectiveRuleDetails(params);
  }

  public void updateStandaloneRulesConfiguration(Map<String, StandaloneRuleConfigDto> ruleConfigByKey) {
    var params = new UpdateStandaloneRulesConfigurationParams(ruleConfigByKey);
    initializedBackend().getRulesService().updateStandaloneRulesConfiguration(params);
  }

  public CompletableFuture<GetStandaloneRuleDescriptionResponse> getStandaloneRuleDetails(String ruleKey) {
    var params = new GetStandaloneRuleDescriptionParams(ruleKey);
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

  public CompletableFuture<SonarLintExtendedLanguageServer.CheckIssueStatusChangePermittedResponse>
  checkStatusChangePermitted(String connectionId, String issueKey) {
    return initializedBackend().getIssueService().checkStatusChangePermitted(
      new org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedParams(connectionId, issueKey))
      .thenApply(result -> new SonarLintExtendedLanguageServer.CheckIssueStatusChangePermittedResponse(result.isPermitted(),
        result.getNotPermittedReason(), result.getAllowedStatuses().stream().map(EnumLabelsMapper::resolutionStatusToLabel).toList()))
      .exceptionally(t -> {
        logOutput.error("Error getting issue status change permissions", t);
        client.logMessage(new MessageParams(MessageType.Error, "Could not get issue status change for issue \""
          + issueKey + "\". Look at the SonarLint output for details."));
        return null;
      });
  }

  public CompletableFuture<Void> changeIssueStatus(ChangeIssueStatusParams params) {
    return initializedBackend().getIssueService().changeStatus(params);
  }

  public CompletableFuture<Void> addIssueComment(AddIssueCommentParams params) {
    return initializedBackend().getIssueService().addComment(params)
      .exceptionally(t -> {
        logOutput.error("Error adding issue comment", t);
        client.showMessage(new MessageParams(MessageType.Error, "Could not add a new issue comment. Look at the SonarLint output for " +
          "details."));
        return null;
      });
  }

  public CompletableFuture<Void> changeHotspotStatus(ChangeHotspotStatusParams params) {
    return initializedBackend().getHotspotService().changeStatus(params);
  }

  public CompletableFuture<CheckStatusChangePermittedResponse> getAllowedHotspotStatuses(CheckStatusChangePermittedParams params) {
    return initializedBackend().getHotspotService().checkStatusChangePermitted(params);
  }

  public void notifyBackendOnBranchChanged(String folderUri, String newBranchName) {
    var params = new DidChangeActiveSonarProjectBranchParams(folderUri, newBranchName);
    initializedBackend().getSonarProjectBranchService().didChangeActiveSonarProjectBranch(params);
  }

  public CompletableFuture<HelpGenerateUserTokenResponse> helpGenerateUserToken(String serverUrl, boolean isSonarCloud) {
    var params = new HelpGenerateUserTokenParams(serverUrl, isSonarCloud);
    return initializedBackend().getConnectionService().helpGenerateUserToken(params);
  }

  public CompletableFuture<TrackWithServerIssuesResponse> matchIssues(TrackWithServerIssuesParams params) {
    return initializedBackend().getIssueTrackingService().trackWithServerIssues(params);
  }

  public CompletableFuture<org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse>
  checkChangeIssueStatusPermitted(org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedParams params) {
    return initializedBackend().getIssueService().checkStatusChangePermitted(params);
  }

  public CompletableFuture<ReopenIssueResponse> reopenAllIssuesForFile(ReopenAllIssuesForFileParams params) {
    return initializedBackend().getIssueService().reopenAllIssuesForFile(params);
  }

  public HttpClient getHttpClientNoAuth() {
    return initializedBackend().getHttpClientNoAuth();
  }

  public HttpClient getHttpClient(String connectionId) {
    var sonarLintBackend = initializedBackend();
    return sonarLintBackend.getHttpClient(connectionId);
  }

  public CompletableFuture<ValidateConnectionResponse> validateConnection(ValidateConnectionParams params) {
    return initializedBackend().getConnectionService().validateConnection(params);
  }

  public CompletableFuture<GetNewCodeDefinitionResponse> getNewCodeDefinition(String configScopeId) {
    return backend.getNewCodeService().getNewCodeDefinition(new GetNewCodeDefinitionParams(configScopeId));
  }

  public void toggleCleanAsYouCode() {
    backend.getNewCodeService().didToggleFocus();
  }
}
