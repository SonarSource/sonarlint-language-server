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

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.common.TokenDto;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.api.RequestsHandlerServer;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

public class SonarLintVSCodeClient implements SonarLintClient {

  private final SonarLintExtendedLanguageClient client;
  private SettingsManager settingsManager;
  private SmartNotifications smartNotifications;
  private final RequestsHandlerServer server;
  private ProjectBindingManager bindingManager;

  public SonarLintVSCodeClient(SonarLintExtendedLanguageClient client, RequestsHandlerServer server) {
    this.client = client;
    this.server = server;
  }

  @Override
  public void suggestBinding(SuggestBindingParams params) {
    if (!params.getSuggestions().isEmpty()) {
      client.suggestBinding(params);
    }
  }

  @Override
  public CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInScope(FindFileByNamesInScopeParams params) {
    return client.findFileByNamesInFolder(new SonarLintExtendedLanguageClient.FindFileByNamesInFolder(params.getConfigScopeId(), params.getFilenames()));
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
  public void showSmartNotification(ShowSmartNotificationParams showSmartNotificationParams) {
    var connectionOpt = settingsManager.getCurrentSettings().getServerConnections().get(showSmartNotificationParams.getConnectionId());
    if (connectionOpt == null) {
      return;
    }
    smartNotifications.showSmartNotification(showSmartNotificationParams, connectionOpt.isSonarCloudAlias());
  }

  @Override
  public CompletableFuture<org.sonarsource.sonarlint.core.clientapi.client.host.GetHostInfoResponse> getHostInfo() {
    return CompletableFuture.completedFuture(server.getHostInfo());
  }

  @Override
  public void showHotspot(org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams params) {
    client.showHotspot(params.getHotspotDetails());
  }

  @Override
  public CompletableFuture<org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse>
  assistCreatingConnection(org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams params) {
    server.showHotspotHandleUnknownServer(params.getServerUrl());
    return CompletableFuture.failedFuture(new UnsupportedOperationException());
  }

  @Override
  public CompletableFuture<org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse>
  assistBinding(org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams params) {
    server.showHotspotHandleNoBinding(params);
    return CompletableFuture.failedFuture(new UnsupportedOperationException());
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
  }

  @Override
  public CompletableFuture<GetCredentialsResponse> getCredentials(GetCredentialsParams params) {
    var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(params.getConnectionId());
    if (connectionSettings == null) return null;
    var token = connectionSettings.getToken();
    return CompletableFuture.completedFuture(new GetCredentialsResponse(new TokenDto(token)));
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
}
