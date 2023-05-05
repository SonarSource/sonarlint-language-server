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
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClientProvider;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

public class SonarLintVSCodeClient implements SonarLintClient {

  private final SonarLintExtendedLanguageClient client;
  private SettingsManager settingsManager;
  private final ApacheHttpClientProvider httpClientProvider;
  private SmartNotifications smartNotifications;

  public SonarLintVSCodeClient(SonarLintExtendedLanguageClient client, ApacheHttpClientProvider httpClientProvider) {
    this.client = client;
    this.httpClientProvider = httpClientProvider;
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

  @Nullable
  @Override
  public HttpClient getHttpClient(String connectionId) {
    var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
    if (connectionSettings == null) return null;
    var token = connectionSettings.getToken();
    return httpClientProvider.withToken(token);
  }

  @Nullable
  @Override
  public HttpClient getHttpClientNoAuth(String s) {
    return httpClientProvider.anonymous();
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
    throw new UnsupportedOperationException();
  }

  @Override
  public void showHotspot(org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams params) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse>
  assistCreatingConnection(org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams params) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse>
  assistBinding(org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams params) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Void> startProgress(StartProgressParams startProgressParams) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reportProgress(ReportProgressParams reportProgressParams) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams didSynchronizeConfigurationScopeParams) {
    throw new UnsupportedOperationException();
  }

  public void setSettingsManager(SettingsManager settingsManager) {
    this.settingsManager = settingsManager;
  }

  public void setSmartNotifications(SmartNotifications smartNotifications) {
    this.smartNotifications = smartNotifications;
  }
}
