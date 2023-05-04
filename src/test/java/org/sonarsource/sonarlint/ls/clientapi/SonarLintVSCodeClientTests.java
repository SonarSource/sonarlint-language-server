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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClient;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClientProvider;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarLintVSCodeClientTests {

  SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  ApacheHttpClientProvider httpClientProvider = mock(ApacheHttpClientProvider.class);

  SettingsManager settingsManager = mock(SettingsManager.class);
  SmartNotifications smartNotifications = mock(SmartNotifications.class);
  SonarLintVSCodeClient underTest;

  @BeforeEach
  public void setup() {
    underTest = new SonarLintVSCodeClient(client, httpClientProvider);
    underTest.setSmartNotifications(smartNotifications);
    underTest.setSettingsManager(settingsManager);
  }

  @Test
  void openUrlInBrowserTest() {
    var params = new OpenUrlInBrowserParams("url");

    underTest.openUrlInBrowser(params);

    verify(client).browseTo(params.getUrl());
  }

  @Test
  void shouldReturnNullHttpClientForNonExistingConnection() {
    var settingsManager = mock(SettingsManager.class);
    underTest.setSettingsManager(settingsManager);
    var workspaceSettings = mock(WorkspaceSettings.class);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    when(workspaceSettings.getServerConnections()).thenReturn(Collections.emptyMap());

    assertThat(underTest.getHttpClient("nonExistingConnection")).isNull();
  }

  @Test
  void shouldReturnHttpClientForExistingConnection() {
    var settingsManager = mock(SettingsManager.class);
    underTest.setSettingsManager(settingsManager);
    var workspaceSettings = mock(WorkspaceSettings.class);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    var serverConnectionSettings = mock(ServerConnectionSettings.class);
    when(serverConnectionSettings.getToken()).thenReturn("token");
    when(workspaceSettings.getServerConnections()).thenReturn(Map.of("existingConnection", serverConnectionSettings));
    var httpClient = mock(ApacheHttpClient.class);
    when(httpClientProvider.withToken("token")).thenReturn(httpClient);

    assertThat(underTest.getHttpClient("existingConnection")).isEqualTo(httpClient);
  }


  @Test
  void shouldCallClientToFindFile() {
    var params = mock(FindFileByNamesInScopeParams.class);
    underTest.findFileByNamesInScope(params);
    var expectedClientParams =
      new SonarLintExtendedLanguageClient.FindFileByNamesInFolder(params.getConfigScopeId(), params.getFilenames());
    verify(client).findFileByNamesInFolder(expectedClientParams);
  }

  @Test
  void shouldThrowForSuggestBinding() {
    var suggestions = new HashMap<String, List<BindingSuggestionDto>>();
    suggestions.put("key", Collections.emptyList());
    var params = new SuggestBindingParams(suggestions);
    underTest.suggestBinding(params);

    verify(client).suggestBinding(params);
  }

  @Test
  void shouldThrowForHttpClientNoAuth() {
    assertThrows(UnsupportedOperationException.class, () -> underTest.getHttpClientNoAuth(""));
  }

  @Test
  void shouldThrowForShowMessage() {
    assertThrows(UnsupportedOperationException.class, () -> underTest.showMessage(mock(ShowMessageParams.class)));
  }

  @Test
  void shouldThrowForGetHostInfo() {
    assertThrows(UnsupportedOperationException.class, () -> underTest.getHostInfo());
  }

  @Test
  void shouldThrowForShowHotspot() {
    assertThrows(UnsupportedOperationException.class, () -> underTest.showHotspot(mock(ShowHotspotParams.class)));
  }

  @Test
  void shouldThrowForAssistCreatingConnection() {
    assertThrows(UnsupportedOperationException.class, () -> underTest.assistCreatingConnection(mock(AssistCreatingConnectionParams.class)));
  }

  @Test
  void shouldThrowForAssistBinding() {
    assertThrows(UnsupportedOperationException.class, () -> underTest.assistBinding(mock(AssistBindingParams.class)));
  }

  @Test
  void shouldHandleShowSmartNotificationWhenConnectionExists() {
    var workspaceSettings = mock(WorkspaceSettings.class);
    var client = mock(ApacheHttpClientProvider.class);
    var showSmartNotificationParams = mock(ShowSmartNotificationParams.class);
    when(showSmartNotificationParams.getConnectionId()).thenReturn("testId");
    var serverConnections = Map.of("testId",
      new ServerConnectionSettings("testId",
        "http://localhost:9000",
        "abcdefg",
        null,
        false,
        client));
    when(workspaceSettings.getServerConnections()).thenReturn(serverConnections);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    underTest.showSmartNotification(showSmartNotificationParams);

    verify(smartNotifications).showSmartNotification(any(ShowSmartNotificationParams.class), eq(false));
  }

  @Test
  void shouldHandleShowSmartNotificationWhenConnectionExistsForSonarCloud() {
    var workspaceSettings = mock(WorkspaceSettings.class);
    var client = mock(ApacheHttpClientProvider.class);
    var showSmartNotificationParams = mock(ShowSmartNotificationParams.class);
    when(showSmartNotificationParams.getConnectionId()).thenReturn("testId");
    var serverConnections = Map.of("testId",
      new ServerConnectionSettings("testId",
        "https://sonarcloud.io",
        "abcdefg",
        "test-org",
        false,
        client));
    when(workspaceSettings.getServerConnections()).thenReturn(serverConnections);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    underTest.showSmartNotification(showSmartNotificationParams);

    verify(smartNotifications).showSmartNotification(any(ShowSmartNotificationParams.class), eq(true));
  }

  @Test
  void shouldDoNothingOnShowSmartNotificationWhenConnectionIsNotFound() {
    var workspaceSettings = mock(WorkspaceSettings.class);
    var showSmartNotificationParams = mock(ShowSmartNotificationParams.class);
    when(showSmartNotificationParams.getConnectionId()).thenReturn("testId");
    var serverConnections = new HashMap<String, ServerConnectionSettings>();
    when(workspaceSettings.getServerConnections()).thenReturn(serverConnections);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    underTest.showSmartNotification(showSmartNotificationParams);

    verify(smartNotifications, never()).showSmartNotification(any(ShowSmartNotificationParams.class), eq(false));
  }

  @Test
  void shouldThrowForStartProgress() {
    assertThrows(UnsupportedOperationException.class, () -> underTest.startProgress(mock(StartProgressParams.class)));
  }

  @Test
  void shouldThrowForReportProgress() {
    assertThrows(UnsupportedOperationException.class, () -> underTest.reportProgress(mock(ReportProgressParams.class)));
  }

  @Test
  void shouldThrowForDidSynchronizeConfigurationScopes() {
    assertThrows(UnsupportedOperationException.class, () ->
      underTest.didSynchronizeConfigurationScopes(mock(DidSynchronizeConfigurationScopeParams.class)));
  }

  @Test
  void shouldAskTheClientToFindFiles() {
    var folderUri = "file:///some/folder";
    var filesToFind = List.of("file1", "file2");
    var params = new FindFileByNamesInScopeParams(folderUri, filesToFind);
    underTest.findFileByNamesInScope(params);
    var argumentCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.FindFileByNamesInFolder.class);
    verify(client).findFileByNamesInFolder(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).extracting(
      SonarLintExtendedLanguageClient.FindFileByNamesInFolder::getFolderUri,
      SonarLintExtendedLanguageClient.FindFileByNamesInFolder::getFilenames
    ).containsExactly(
      folderUri,
      filesToFind.toArray()
    );
  }
}
