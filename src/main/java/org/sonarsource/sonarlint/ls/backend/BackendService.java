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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFileListParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFullProjectParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangeAnalysisPropertiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangeClientNodeJsPathParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangePathToCompileCommandsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetBindingSuggestionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetProjectNamesByKeyParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetProjectNamesByKeyResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidCloseFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidOpenFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.ChangeHotspotStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.GetEffectiveIssueDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.GetEffectiveIssueDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.GetNewCodeDefinitionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.GetNewCodeDefinitionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ListAllStandaloneRulesDefinitionsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.UpdateStandaloneRulesConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.GetStatusResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.GetBindingSuggestionsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.util.EnumLabelsMapper;

import static org.sonarsource.sonarlint.ls.backend.BackendServiceFacade.ROOT_CONFIGURATION_SCOPE;

public class BackendService {

  private final SonarLintRpcServer backend;
  private final LanguageClientLogger logOutput;
  private final SonarLintExtendedLanguageClient client;
  private final CountDownLatch initializeLatch = new CountDownLatch(1);

  public BackendService(SonarLintRpcServer backend, LanguageClientLogger logOutput, SonarLintExtendedLanguageClient client) {
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

  public ConfigurationScopeDto getConfigScopeDto(WorkspaceFolder added, Optional<ProjectBinding> bindingOptional) {
    BindingConfigurationDto bindingConfigurationDto;
    if (bindingOptional.isPresent()) {
      ProjectBinding bindingWrapper = bindingOptional.get();
      bindingConfigurationDto = new BindingConfigurationDto(bindingWrapper.connectionId(),
        bindingWrapper.projectKey(), true);
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

  public void addWorkspaceFolders(List<WorkspaceFolder> added, Function<WorkspaceFolder, Optional<ProjectBinding>> bindingProvider) {
    List<ConfigurationScopeDto> addedScopeDtos = added.stream()
      .map(folder -> getConfigScopeDto(folder, bindingProvider.apply(folder)))
      .toList();
    var params = new DidAddConfigurationScopesParams(addedScopeDtos);
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

  public CompletableFuture<Void> shutdown() {
    return backend.shutdown();
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

  public CompletableFuture<GetSharedConnectedModeConfigFileResponse> getSharedConnectedModeConfigFileContents(GetSharedConnectedModeConfigFileParams params) {
    return initializedBackend().getBindingService().getSharedConnectedModeConfigFileContents(params);
  }

  public void didChangeClientNodeJsPath(DidChangeClientNodeJsPathParams params) {
    initializedBackend().getAnalysisService().didChangeClientNodeJsPath(params);
  }

  public CompletableFuture<SonarLintExtendedLanguageServer.CheckIssueStatusChangePermittedResponse>
  checkStatusChangePermitted(String connectionId, String issueKey) {
    return initializedBackend().getIssueService().checkStatusChangePermitted(
        new org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedParams(connectionId, issueKey))
      .thenApply(result -> new SonarLintExtendedLanguageServer.CheckIssueStatusChangePermittedResponse(result.isPermitted(),
        result.getNotPermittedReason(), result.getAllowedStatuses().stream().map(EnumLabelsMapper::resolutionStatusToLabel).toList()))
      .exceptionally(t -> {
        logOutput.errorWithStackTrace("Error getting issue status change permissions", t);
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
        logOutput.errorWithStackTrace("Error adding issue comment", t);
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

  public void notifyBackendOnVcsChange(String folderUri) {
    initializedBackend().getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams(folderUri));
  }

  public CompletableFuture<GetMatchedSonarProjectBranchResponse> getMatchedSonarProjectBranch(String configurationScopeId) {
    return initializedBackend().getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams(configurationScopeId));
  }

  public CompletableFuture<HelpGenerateUserTokenResponse> helpGenerateUserToken(String serverUrl) {
    var params = new HelpGenerateUserTokenParams(serverUrl);
    return initializedBackend().getConnectionService().helpGenerateUserToken(params);
  }

  public CompletableFuture<org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse>
  checkChangeIssueStatusPermitted(org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedParams params) {
    return initializedBackend().getIssueService().checkStatusChangePermitted(params);
  }

  public CompletableFuture<ReopenAllIssuesForFileResponse> reopenAllIssuesForFile(ReopenAllIssuesForFileParams params) {
    return initializedBackend().getIssueService().reopenAllIssuesForFile(params);
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

  public CompletableFuture<GetAllProjectsResponse> getAllProjects(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection) {
    return initializedBackend().getConnectionService().getAllProjects(new GetAllProjectsParams(transientConnection));
  }

  public void updateFileSystem(List<URI> deletedFileUris, List<ClientFileDto> addedOrChangedFiles) {
    initializedBackend().getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(deletedFileUris, addedOrChangedFiles));
  }

  public CompletableFuture<ListAllResponse> getAllTaints(String folderUri) {
    var params = new ListAllParams(folderUri, true);
    return backend.getTaintVulnerabilityTrackingService().listAll(params);
  }

  public CompletableFuture<GetProjectNamesByKeyResponse> getProjectNamesByKeys(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection,
    List<String> projectKeys) {
    var params = new GetProjectNamesByKeyParams(transientConnection, projectKeys);
    return initializedBackend().getConnectionService().getProjectNamesByKey(params);
  }

  public SonarLintRpcServer getBackend() {
    return backend;
  }

  public CompletableFuture<ListUserOrganizationsResponse> listUserOrganizations(String token) {
    var params = new ListUserOrganizationsParams(Either.forLeft(new TokenDto(token)));
    return initializedBackend().getConnectionService().listUserOrganizations(params);
  }

  public void didOpenFile(String configScopeId, URI fileUri) {
    var params = new DidOpenFileParams(configScopeId, fileUri);
    initializedBackend().getFileService().didOpenFile(params);
  }

  public void didCloseFile(String configScopeId, URI fileUri) {
    var params = new DidCloseFileParams(configScopeId, fileUri);
    initializedBackend().getFileService().didCloseFile(params);
  }

  public void didSetUserAnalysisProperties(String configScopeId, Map<String, String> properties) {
    var params = new DidChangeAnalysisPropertiesParams(configScopeId, properties);
    initializedBackend().getAnalysisService().didSetUserAnalysisProperties(params);
  }

  public void analyzeFullProject(String configScopeId, boolean hotspotsOnly) {
    var params = new AnalyzeFullProjectParams(configScopeId, hotspotsOnly);
    initializedBackend().getAnalysisService().analyzeFullProject(params);
  }

  public void analyzeFilesList(String configScopeId, List<URI> filesToAnalyze) {
    var params = new AnalyzeFileListParams(configScopeId, filesToAnalyze);
    initializedBackend().getAnalysisService().analyzeFileList(params);
  }

  public void didChangePathToCompileCommands(String configScopeId, @Nullable String pathToCompileCommands) {
    var params = new DidChangePathToCompileCommandsParams(configScopeId, pathToCompileCommands == null ? "" : pathToCompileCommands);
    initializedBackend().getAnalysisService().didChangePathToCompileCommands(params);
  }

  public CompletableFuture<GetEffectiveIssueDetailsResponse> getEffectiveIssueDetails(@Nullable String workspaceFolder, UUID issueKey) {
    var workspaceOrRootScope = Optional.ofNullable(workspaceFolder).orElse(ROOT_CONFIGURATION_SCOPE);
    var params = new GetEffectiveIssueDetailsParams(workspaceOrRootScope, issueKey);
    return initializedBackend().getIssueService().getEffectiveIssueDetails(params);
  }
}
