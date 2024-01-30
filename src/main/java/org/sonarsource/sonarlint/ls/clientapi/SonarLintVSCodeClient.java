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
package org.sonarsource.sonarlint.ls.clientapi;

import java.net.URI;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import nl.altindag.ssl.util.CertificateUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.clientapi.client.binding.NoBindingSuggestionFoundParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.clientapi.client.event.DidReceiveServerEventParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.CheckServerTrustedResponse;
import org.sonarsource.sonarlint.core.clientapi.client.info.GetClientInfoResponse;
import org.sonarsource.sonarlint.core.clientapi.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.common.TokenDto;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.CreateConnectionParams;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.api.HostInfoProvider;
import org.sonarsource.sonarlint.ls.connected.events.ServerSentEventsHandlerService;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.util.Utils;

public class SonarLintVSCodeClient implements SonarLintClient {

  private final SonarLintExtendedLanguageClient client;
  private SettingsManager settingsManager;
  private SmartNotifications smartNotifications;
  private final HostInfoProvider hostInfoProvider;
  private final LanguageClientLogOutput logOutput;
  private ProjectBindingManager bindingManager;
  private ServerSentEventsHandlerService serverSentEventsHandlerService;
  private BackendServiceFacade backendServiceFacade;

  public SonarLintVSCodeClient(SonarLintExtendedLanguageClient client, HostInfoProvider hostInfoProvider,
    LanguageClientLogOutput logOutput) {
    this.client = client;
    this.hostInfoProvider = hostInfoProvider;
    this.logOutput = logOutput;
  }

  @Override
  public void suggestBinding(SuggestBindingParams params) {
    if (!params.getSuggestions().isEmpty()) {
      client.suggestBinding(params);
    }
  }

  @Override
  public CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInScope(FindFileByNamesInScopeParams params) {
    return CompletableFutures.computeAsync( cancelToken -> client.findFileByNamesInFolder(
      new SonarLintExtendedLanguageClient.FindFileByNamesInFolder(params.getConfigScopeId(), params.getFilenames()))
      .join());
  }

  @Override
  public void openUrlInBrowser(OpenUrlInBrowserParams params) {
    client.browseTo(params.getUrl());
  }

  @Override
  public void showMessage(org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams params) {
    throw new UnsupportedOperationException();
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
  public CompletableFuture<GetClientInfoResponse> getClientInfo() {
    return CompletableFuture.completedFuture(hostInfoProvider.getHostInfo());
  }

  @Override
  public void showHotspot(org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams params) {
    client.showHotspot(params.getHotspotDetails());
  }

  @Override
  public void showIssue(ShowIssueParams showIssueParams) {
    var maybeFileUri = bindingManager.serverPathToFileUri(showIssueParams.getServerRelativeFilePath());
    Optional<ProjectBindingWrapper> maybeBinding = Optional.empty();
    if (maybeFileUri.isPresent()) {
      maybeBinding = bindingManager.getBinding(maybeFileUri.get());
    }
    maybeBinding.ifPresent(projectBindingWrapper -> client.showIssue(new ShowAllLocationsCommand.Param(showIssueParams, bindingManager, projectBindingWrapper.getConnectionId())));
  }

  @Override
  public CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(AssistCreatingConnectionParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var tokenValue = params.getTokenValue();
      var workspaceFoldersFuture = client.workspaceFolders();
      var assistCreatingConnectionFuture = client.assistCreatingConnection(
        new CreateConnectionParams(false, params.getServerUrl(), tokenValue));
      return workspaceFoldersFuture.thenCombine(assistCreatingConnectionFuture, (workspaceFolders, assistCreatingConnectionResponse) -> {
        var currentConnections = getCurrentConnections(params, assistCreatingConnectionResponse);
        var newConnectionId = assistCreatingConnectionResponse.getNewConnectionId();
        if (newConnectionId != null) {
          client.showMessage(new MessageParams(MessageType.Info, "Connection to SonarQube was successfully created."));
          backendServiceFacade.getBackendService().didChangeConnections(currentConnections);
        }
        return new AssistCreatingConnectionResponse(newConnectionId,
          workspaceFolders.stream().map(WorkspaceFolder::getUri).collect(Collectors.toSet()));
      }).join();
    });
  }

  @NotNull
  private HashMap<String, ServerConnectionSettings> getCurrentConnections(AssistCreatingConnectionParams params,
    @Nullable SonarLintExtendedLanguageClient.AssistCreatingConnectionResponse assistCreatingConnectionResponse) {
    if (assistCreatingConnectionResponse == null) {
      throw new CancellationException("Automatic connection setup was cancelled");
    }
    var newConnection = new ServerConnectionSettings(assistCreatingConnectionResponse.getNewConnectionId(), params.getServerUrl(), params.getTokenValue(), null, false);
    var currentConnections = new HashMap<>(settingsManager.getCurrentSettings().getServerConnections());
    currentConnections.put(assistCreatingConnectionResponse.getNewConnectionId(), newConnection);
    return currentConnections;
  }

  @Override
  public CompletableFuture<AssistBindingResponse> assistBinding(org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> client.assistBinding(params)
      .thenCompose(response -> bindingManager.getUpdatedBindingForWorkspaceFolder(URI.create(response.getConfigurationScopeId())))
      .thenApply(configurationScopeId -> {
        var pathParts = configurationScopeId.split("/");
        var projectName = pathParts[pathParts.length - 1];
        client.showMessage(new MessageParams(MessageType.Info, "Project '" + projectName + "' was successfully bound to '" + params.getProjectKey() + "'."));
        return new AssistBindingResponse(configurationScopeId);
      }).join());
  }


  @Override
  public void noBindingSuggestionFound(NoBindingSuggestionFoundParams params) {
    var messageRequestParams = new ShowMessageRequestParams();
    messageRequestParams.setMessage("SonarLint couldn't match SonarQube project '" + params.getProjectKey() + "' to any of the currently " +
      "open workspace folders. Please open your project in VSCode and try again.");
    messageRequestParams.setType(MessageType.Error);
    var learnMoreAction = new MessageActionItem("Learn more");
    messageRequestParams.setActions(List.of(learnMoreAction));
    client.showMessageRequest(messageRequestParams)
      .thenAccept(action -> {
        if (learnMoreAction.equals(action)) {
          client.browseTo("https://docs.sonarsource.com/sonarlint/vs-code/troubleshooting/#troubleshooting-connected-mode-setup");
        }
      });
  }

  @Override
  public CompletableFuture<Void> startProgress(StartProgressParams startProgressParams) {
    // no-op
    return CompletableFuture.completedFuture(null);
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

  @Override
  public CompletableFuture<GetCredentialsResponse> getCredentials(GetCredentialsParams params) {
    var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(params.getConnectionId());
    if (connectionSettings == null) return null;
    var token = connectionSettings.getToken();
    return CompletableFuture.completedFuture(new GetCredentialsResponse(new TokenDto(token)));
  }

  @Override
  public CompletableFuture<CheckServerTrustedResponse> checkServerTrusted(CheckServerTrustedParams params) {
    var certs = CertificateUtils.parsePemCertificate(params.getChain().get(0).getPem());
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

    return client.askSslCertificateConfirmation(confirmationParams).thenApply(CheckServerTrustedResponse::new);
  }

  @Override
  public void didReceiveServerEvent(DidReceiveServerEventParams params) {
    serverSentEventsHandlerService.handleEvents(params.getServerEvent());
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

}
