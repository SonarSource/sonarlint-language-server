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
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.host.GetHostInfoResponse;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.api.RequestsHandlerServer;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
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

  SettingsManager settingsManager = mock(SettingsManager.class);
  SmartNotifications smartNotifications = mock(SmartNotifications.class);
  SonarLintVSCodeClient underTest;

  RequestsHandlerServer server = mock(RequestsHandlerServer.class);
  ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);

  @BeforeEach
  public void setup() {
    underTest = new SonarLintVSCodeClient(client, server);
    underTest.setSmartNotifications(smartNotifications);
    underTest.setSettingsManager(settingsManager);
    underTest.setBindingManager(bindingManager);
  }

  @Test
  void openUrlInBrowserTest() {
    var params = new OpenUrlInBrowserParams("url");

    underTest.openUrlInBrowser(params);

    verify(client).browseTo(params.getUrl());
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
  void shouldSuggestBinding() {
    var suggestions = new HashMap<String, List<BindingSuggestionDto>>();
    suggestions.put("key", Collections.emptyList());
    var params = new SuggestBindingParams(suggestions);
    underTest.suggestBinding(params);

    verify(client).suggestBinding(params);
  }

  @Test
  void shouldThrowForShowMessage() {
    assertThrows(UnsupportedOperationException.class, () -> underTest.showMessage(mock(ShowMessageParams.class)));
  }

  @Test
  void shouldHandleShowSmartNotificationWhenConnectionExists() {
    var workspaceSettings = mock(WorkspaceSettings.class);
    var backendServiceFacade = mock(BackendServiceFacade.class);
    var showSmartNotificationParams = mock(ShowSmartNotificationParams.class);
    when(showSmartNotificationParams.getConnectionId()).thenReturn("testId");
    var serverConnections = Map.of("testId",
      new ServerConnectionSettings("testId",
        "http://localhost:9000",
        "abcdefg",
        null,
        false
      ));
    when(workspaceSettings.getServerConnections()).thenReturn(serverConnections);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    underTest.showSmartNotification(showSmartNotificationParams);

    verify(smartNotifications).showSmartNotification(any(ShowSmartNotificationParams.class), eq(false));
  }

  @Test
  void shouldHandleShowSmartNotificationWhenConnectionExistsForSonarCloud() {
    var workspaceSettings = mock(WorkspaceSettings.class);
    var backendServiceFacade = mock(BackendServiceFacade.class);
    var showSmartNotificationParams = mock(ShowSmartNotificationParams.class);
    when(showSmartNotificationParams.getConnectionId()).thenReturn("testId");
    var serverConnections = Map.of("testId",
      new ServerConnectionSettings("testId",
        "https://sonarcloud.io",
        "abcdefg",
        "test-org",
        false
      ));
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
  void shouldReturnEmptyFutureForStartProgress() throws ExecutionException, InterruptedException {
    assertThat(underTest.startProgress(mock(StartProgressParams.class)).get()).isNull();
  }

  @Test
  void shouldUpdateAllTaintIssuesForDidSynchronizeConfigurationScopes() {
      underTest.didSynchronizeConfigurationScopes(mock(DidSynchronizeConfigurationScopeParams.class));

      verify(bindingManager).updateAllTaintIssues();
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

  @Test
  void shouldCallServerOnGetHostInfo() {
    underTest.getHostInfo();
    verify(server).getHostInfo();
  }

  @Test
  void shouldGetHostInfo() throws ExecutionException, InterruptedException {
    var desc = "This is Test";
    when(server.getHostInfo()).thenReturn(new GetHostInfoResponse("This is Test"));
    var result = underTest.getHostInfo().get();
    assertThat(result.getDescription()).isEqualTo(desc);
  }

  @Test
  void shouldCallClientShowHotspot() {
    var showHotspotParams = new ShowHotspotParams("myFolder", new HotspotDetailsDto("key1",
      "message1",
      "myfolder/myFile",
      null,
      null,
      "TO_REVIEW",
      "fixed",
      null,
      null
    ));
    underTest.showHotspot(showHotspotParams);
    verify(client).showHotspot(showHotspotParams.getHotspotDetails());
  }

  @Test
  void assistCreateConnectionShouldCallServerMethod() {
    var assistCreatingConnectionParams = new AssistCreatingConnectionParams("http://localhost:9000");
    var future = underTest.assistCreatingConnection(assistCreatingConnectionParams);
    verify(server).showHotspotHandleUnknownServer(assistCreatingConnectionParams.getServerUrl());
    assertThat(future).isNotCompleted();
  }

  @Test
  void assistBindingShouldCallServerMethod() {
    var assistBindingParams = new AssistBindingParams("connectionId", "projectKey");
    var future = underTest.assistBinding(assistBindingParams);

    verify(server).showHotspotHandleNoBinding(assistBindingParams);
    assertThat(future).isNotCompleted();
  }
}
