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
package org.sonarsource.sonarlint.ls.clientapi;

import java.net.URI;
import java.net.URL;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import nl.altindag.ssl.util.CertificateUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.lsp4j.LogTraceParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.DidChangeMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FoundFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.info.GetClientLiveInfoResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidUpdatePluginsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.taint.vulnerability.DidChangeTaintVulnerabilitiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryLiveAttributesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.NodeJsRuntime;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.api.RequestsHandlerServer;
import org.sonarsource.sonarlint.ls.connected.events.ServerSentEventsHandlerService;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderBranchManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.util.Utils;

public class SonarLintVSCodeClient implements SonarLintRpcClientDelegate {

  private final SonarLintExtendedLanguageClient client;
  private SettingsManager settingsManager;
  private SmartNotifications smartNotifications;
  private final RequestsHandlerServer server;
  private final LanguageClientLogOutput logOutput;
  private ProjectBindingManager bindingManager;
  private ServerSentEventsHandlerService serverSentEventsHandlerService;
  private BackendServiceFacade backendServiceFacade;
  private WorkspaceFolderBranchManager branchManager;
  private NodeJsRuntime nodeJsRuntime;

  public SonarLintVSCodeClient(SonarLintExtendedLanguageClient client, RequestsHandlerServer server,
    LanguageClientLogOutput logOutput) {
    this.client = client;
    this.server = server;
    this.logOutput = logOutput;
  }

  @Override
  public void suggestBinding(Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope) {
    if (!suggestionsByConfigScope.isEmpty()) {
      client.suggestBinding(new SuggestBindingParams(suggestionsByConfigScope));
    }
  }

  @Override
  public List<FoundFileDto> findFileByNamesInScope(String configScopeId, List<String> filenames, CancelChecker cancelChecker) {
    try {
      return client.findFileByNamesInFolder(new SonarLintExtendedLanguageClient.FindFileByNamesInFolder(configScopeId, filenames)).get().getFoundFiles();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
  @Override
  public void openUrlInBrowser(URL url) {
    client.browseTo(url.toString());
  }


  @Override
  public void showMessage(MessageType type, String text) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void log(LogParams params) {
    // TODO log to sonarlint output
    client.logTrace(new LogTraceParams());
  }

  @Override
  public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams coreParams) {
    var clientParams = new SonarLintExtendedLanguageClient.ShowSoonUnsupportedVersionMessageParams(
      coreParams.getDoNotShowAgainId(), coreParams.getText()
    );
    client.showSoonUnsupportedVersionMessage(clientParams);
  }

  @Override
  public void showSmartNotification(ShowSmartNotificationParams showSmartNotificationParams) {
    var connectionOpt = settingsManager.getCurrentSettings().getServerConnections().get(showSmartNotificationParams.getConnectionId());
    if (connectionOpt == null) {
      return;
    }
    smartNotifications.showSmartNotification(showSmartNotificationParams, connectionOpt.isSonarCloudAlias());
  }

  @Override
  public String getClientLiveDescription() {
    return server.getHostInfo().getDescription();
  }

  @Override
  public void showHotspot(String configurationScopeId, HotspotDetailsDto hotspotDetails) {
    client.showHotspot(hotspotDetails);
  }

  @Override
  public void showIssue(String configurationScopeId, IssueDetailsDto issueDetails) {
    var maybeFileUri = bindingManager.serverPathToFileUri(configurationScopeId);
    Optional<ProjectBindingWrapper> maybeBinding = Optional.empty();
    if (maybeFileUri.isPresent()) {
      maybeBinding = bindingManager.getBinding(maybeFileUri.get());
    }
    maybeBinding.ifPresent(projectBindingWrapper ->
      client.showIssue(new ShowAllLocationsCommand.Param(new ShowIssueParams(configurationScopeId, issueDetails),
        bindingManager, projectBindingWrapper.getConnectionId())));
  }

  @Override
  public AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, CancelChecker cancelChecker) throws CancellationException {
    server.showIssueOrHotspotHandleUnknownServer(params.getServerUrl());
    throw new UnsupportedOperationException();
  }

  @Override
  public AssistBindingResponse assistBinding(AssistBindingParams params, CancelChecker cancelChecker) throws CancellationException {
    server.showHotspotOrIssueHandleNoBinding(params);
    throw new UnsupportedOperationException();
  }

  @Override
  public void startProgress(StartProgressParams startProgressParams) {
    // no-op
  }

  @Override
  public void reportProgress(ReportProgressParams reportProgressParams) {
    // no-op
  }

  @Override
  public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams didSynchronizeConfigurationScopeParams) {
    bindingManager.updateAllTaintIssues();
    didSynchronizeConfigurationScopeParams.getConfigurationScopeIds()
      .forEach(this::getNewCodeDefinitionAndSubmitToClient);
  }

  @Nullable
  @Override
  public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) {
    var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
    if (connectionSettings == null) return null;
    var token = connectionSettings.getToken();
    return Either.forLeft(new TokenDto(token));
  }

  @Override
  public TelemetryLiveAttributesResponse getTelemetryLiveAttributes() {
    return new TelemetryLiveAttributesResponse(
      bindingManager.usesConnectedMode(), bindingManager.usesSonarCloud(),
      nodeJsRuntime.nodeVersion(),
      bindingManager.smartNotificationsDisabled(), getNonDefaultEnabledRules(),
      getDefaultDisabledRules(), backendServiceFacade.getTelemetryInitParams().getAdditionalAttributes()
    );
  }


  public List<String> getNonDefaultEnabledRules() {
    var enabled = settingsManager.getCurrentSettings().getIncludedRules()
      .stream().map(RuleKey::toString).collect(Collectors.toList());
    if (!enabled.isEmpty()) {
      enabled.removeAll(getDefaultEnabledRules());
    }
    return enabled;
  }

  private List<String> getDefaultEnabledRules() {
    return backendServiceFacade.listAllStandaloneRulesDefinitions().thenApply(response ->
        response.getRulesByKey()
          .values()
          .stream()
          .filter(RuleDefinitionDto::isActiveByDefault)
          .map(RuleDefinitionDto::getKey)
          .collect(Collectors.toList()))
      .join();
  }

  public List<String> getDefaultDisabledRules() {
    var disabled = settingsManager.getCurrentSettings().getExcludedRules()
      .stream().map(RuleKey::toString).collect(Collectors.toList());
    if (!disabled.isEmpty()) {
      var defaultEnabledRules = getDefaultEnabledRules();
      disabled.removeIf(r -> !defaultEnabledRules.contains(r));
    }
    return disabled;
  }

  @Override
  public List<ProxyDto> selectProxies(URI uri) {
    return List.of();
  }

  @Override
  public GetProxyPasswordAuthenticationResponse getProxyPasswordAuthentication(String host, int port, String protocol, String prompt, String scheme, URL targetHost) {
    return new GetProxyPasswordAuthenticationResponse(null, null);
  }

  @Override
  public boolean checkServerTrusted(List<X509CertificateDto> chain, String authType) {
    var certs = CertificateUtils.parsePemCertificate(chain.get(0).getPem());
    var sha1fingerprint = "";
    var sha256fingerprint = "";
    X509Certificate untrustedCert = null;
    try {
      untrustedCert = (X509Certificate) certs.get(0);
      sha1fingerprint = Utils.formatSha1Fingerprint(DigestUtils.sha1Hex(untrustedCert.getEncoded()));
      sha256fingerprint = Utils.formatSha256Fingerprint(DigestUtils.sha256Hex(untrustedCert.getEncoded()));
    } catch (CertificateEncodingException | IndexOutOfBoundsException e) {
      logOutput.error("Certificate encoding is malformed, SHA fingerprints will not be displayed", e);
    }
    var actualSonarLintUserHome = Optional.ofNullable(EnginesFactory.sonarLintUserHomeOverride).orElse(SonarLintUserHome.get());
    var confirmationParams = new SonarLintExtendedLanguageClient.SslCertificateConfirmationParams(
      untrustedCert == null ? "" : untrustedCert.getSubjectX500Principal().getName(),
      untrustedCert == null ? "" : untrustedCert.getIssuerX500Principal().getName(),
      untrustedCert == null ? "" : untrustedCert.getNotAfter().toString(),
      untrustedCert == null ? "" : untrustedCert.getNotBefore().toString(),
      sha1fingerprint,
      sha256fingerprint,
      actualSonarLintUserHome.toString()
    );

    try {
      return client.askSslCertificateConfirmation(confirmationParams).get();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  //  @Override
//  public void didReceiveServerTaintVulnerabilityRaisedEvent(DidReceiveServerTaintVulnerabilityRaisedEvent event) {
//    serverSentEventsHandlerService.handleTaintVulnerabilityRaisedEvent(event);
//  }
//
//  @Override
//  public void didReceiveServerTaintVulnerabilityChangedOrClosedEvent(DidReceiveServerTaintVulnerabilityChangedOrClosedEvent event) {
//    serverSentEventsHandlerService.updateTaintCache();
//  }

  @Override
  public void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent event) {
    serverSentEventsHandlerService.handleHotspotEvent(event);
  }


  @Nullable
  @Override
  public String matchSonarProjectBranch(String configurationScopeId, String mainBranchName, Set<String> allBranchesNames, CancelChecker cancelChecker) {
    return branchManager.matchSonarProjectBranch(configurationScopeId, mainBranchName, allBranchesNames, cancelChecker);
  }

  @Override
  public void didChangeMatchedSonarProjectBranch(String configScopeId, String newMatchedBranchName) {

  }

  @Override
  public void didUpdatePlugins(String connectionId) {

  }
  @Override
  public List<String> listAllFilePaths(String configurationScopeId) throws ConfigScopeNotFoundException {
    return List.of();
  }

  @Override
  public void didChangeTaintVulnerabilities(String configurationScopeId, Set<UUID> closedTaintVulnerabilityIds,
    List<TaintVulnerabilityDto> addedTaintVulnerabilities, List<TaintVulnerabilityDto> updatedTaintVulnerabilities) {

  }

  public void setSettingsManager(SettingsManager settingsManager) {
    this.settingsManager = settingsManager;
  }

  public void setBindingManager(ProjectBindingManager bindingManager) {
    this.bindingManager = bindingManager;
  }

  public void setSmartNotifications(SmartNotifications smartNotifications) {
    this.smartNotifications = smartNotifications;
  }

  public void setServerSentEventsHandlerService(ServerSentEventsHandlerService serverSentEventsHandlerService) {
    this.serverSentEventsHandlerService = serverSentEventsHandlerService;
  }

  public void setBackendServiceFacade(BackendServiceFacade backendServiceFacade) {
    this.backendServiceFacade = backendServiceFacade;
  }

  private void getNewCodeDefinitionAndSubmitToClient(String folderUri) {
    backendServiceFacade.getBackendService().getNewCodeDefinition(folderUri)
      .handle((response, e) -> {
        if (e != null) {
          return new SonarLintExtendedLanguageClient.SubmitNewCodeDefinitionParams(folderUri, response.getDescription(), false);
        }
        return new SonarLintExtendedLanguageClient.SubmitNewCodeDefinitionParams(folderUri, response.getDescription(), response.isSupported());
      })
      .thenAccept(client::submitNewCodeDefinition);
  }

  public void setBranchManager(WorkspaceFolderBranchManager branchManager) {
    this.branchManager = branchManager;
  }

  public WorkspaceFolderBranchManager getBranchManager() {
    return branchManager;
  }

  public void setNodeJsRuntime(NodeJsRuntime nodeJsRuntime) {
    this.nodeJsRuntime = nodeJsRuntime;
  }

  public NodeJsRuntime getNodeJsRuntime() {
    return nodeJsRuntime;
  }
}
