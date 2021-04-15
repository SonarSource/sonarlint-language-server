/*
 * SonarLint Language Server
 * Copyright (C) 2009-2021 SonarSource SA
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
package org.sonarsource.sonarlint.ls.connected.notifications;


import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotification;
import org.sonarsource.sonarlint.core.container.model.DefaultServerNotification;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClient;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class ServerNotificationsTest {

  private final SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);

  private final WorkspaceFoldersManager workspaceFoldersManager = mock(WorkspaceFoldersManager.class);

  private final SonarLintTelemetry telemetry = mock(SonarLintTelemetry.class);

  private final LanguageClientLogOutput output = mock(LanguageClientLogOutput.class);

  private final ApacheHttpClient httpClient = mock(ApacheHttpClient.class);

  private ServerNotifications underTest;

  @BeforeEach
  public void setup() {
    underTest = new ServerNotifications(client, workspaceFoldersManager, telemetry, output);
  }

  @AfterEach
  public void finish() {
    verifyNoMoreInteractions(client, workspaceFoldersManager, telemetry, output);
    underTest.shutdown();
  }

  @Test
  void doNothingOnEmptyFolderSettings() {
    underTest.onChange(null, null, mock(WorkspaceFolderSettings.class));
    verifyZeroInteractions(output);
  }

  @Test
  void registerOnlyOnceWithFullSettings() {
    String connectionId = "connectionId";
    String projectKey = "projectKey";

    WorkspaceSettings newWorkspaceSettings = mock(WorkspaceSettings.class);
    ServerConnectionSettings settings = new ServerConnectionSettings(connectionId, "http://my.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, settings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings);

    WorkspaceFolderWrapper folder1 = mock(WorkspaceFolderWrapper.class);
    WorkspaceFolderSettings newFolderSettings1 = new WorkspaceFolderSettings(connectionId, projectKey, Collections.emptyMap(), null);
    underTest.onChange(folder1, null, newFolderSettings1);

    // Same settings, only one registration sent
    WorkspaceFolderWrapper folder2 = mock(WorkspaceFolderWrapper.class);
    WorkspaceFolderSettings newFolderSettings2 = new WorkspaceFolderSettings(connectionId, projectKey, Collections.emptyMap(), null);
    underTest.onChange(folder2, null, newFolderSettings2);

    verify(output, times(1)).log("Enabling notifications for project 'projectKey' on connection 'connectionId'", LogOutput.Level.DEBUG);
    verify(workspaceFoldersManager).getAll();
  }

  @Test
  void register2FoldersOn2Projects() {
    String connectionId = "connectionId";
    String projectKey1 = "projectKey1";
    String projectKey2 = "projectKey2";

    WorkspaceSettings newWorkspaceSettings = mock(WorkspaceSettings.class);
    ServerConnectionSettings settings = new ServerConnectionSettings(connectionId, "http://my.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, settings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings);

    WorkspaceFolderWrapper folder1 = mock(WorkspaceFolderWrapper.class);
    WorkspaceFolderSettings newFolderSettings1 = new WorkspaceFolderSettings(connectionId, projectKey1, Collections.emptyMap(), null);
    underTest.onChange(folder1, null, newFolderSettings1);

    // Same settings, only one registration sent
    WorkspaceFolderWrapper folder2 = mock(WorkspaceFolderWrapper.class);
    WorkspaceFolderSettings newFolderSettings2 = new WorkspaceFolderSettings(connectionId, projectKey2, Collections.emptyMap(), null);
    underTest.onChange(folder2, null, newFolderSettings2);

    verify(output, times(1)).log("Enabling notifications for project 'projectKey1' on connection 'connectionId'", LogOutput.Level.DEBUG);
    verify(output, times(1)).log("Enabling notifications for project 'projectKey2' on connection 'connectionId'", LogOutput.Level.DEBUG);
    verify(workspaceFoldersManager).getAll();
  }

  @Test
  void registerThenUnregisterOnUnknownConnection() {
    String connectionId = "connectionId";
    String projectKey = "projectKey";

    WorkspaceSettings newWorkspaceSettings = mock(WorkspaceSettings.class);
    ServerConnectionSettings settings = new ServerConnectionSettings(connectionId, "http://my.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, settings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings);

    WorkspaceFolderWrapper folder = mock(WorkspaceFolderWrapper.class);
    WorkspaceFolderSettings newFolderSettings1 = new WorkspaceFolderSettings(connectionId, projectKey, Collections.emptyMap(), null);
    underTest.onChange(folder, null, newFolderSettings1);

    verify(output, times(1)).log("Enabling notifications for project 'projectKey' on connection 'connectionId'", LogOutput.Level.DEBUG);

    WorkspaceFolderSettings newFolderSettings2 = new WorkspaceFolderSettings("otherConnectionId", projectKey, Collections.emptyMap(), null);
    underTest.onChange(folder, newFolderSettings1, newFolderSettings2);

    verify(output, times(1)).log("De-registering notifications for project 'projectKey' on connection 'connectionId'", LogOutput.Level.DEBUG);
    verify(workspaceFoldersManager).getAll();
  }

  @Test
  void doNothingOnUnknownConnectionThenRegisterOnWorkspaceSettingsChange() {
    String connectionId = "connectionId";
    String projectKey = "projectKey";

    // Initially, do not put any connection settings
    WorkspaceSettings newWorkspaceSettings = mock(WorkspaceSettings.class);
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings);

    // Try to bind to unknown connection
    WorkspaceFolderWrapper folder = mock(WorkspaceFolderWrapper.class);
    WorkspaceFolderSettings newFolderSettings = new WorkspaceFolderSettings(connectionId, projectKey, Collections.emptyMap(), null);
    underTest.onChange(folder, null, newFolderSettings);

    // Then fix connection settings with known connection
    WorkspaceSettings newWorkspaceSettings2 = mock(WorkspaceSettings.class);
    ServerConnectionSettings settings = new ServerConnectionSettings(connectionId, "http://my.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings2.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, settings));
    when(folder.getSettings()).thenReturn(newFolderSettings);
    when(workspaceFoldersManager.getAll()).thenReturn(Collections.singleton(folder));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings2);

    verify(output, times(1)).log("Enabling notifications for project 'projectKey' on connection 'connectionId'", LogOutput.Level.DEBUG);
    verify(workspaceFoldersManager, times(2)).getAll();
  }

  @Test
  void doNothingOnDisabledConnection() {
    String connectionId = "connectionId";
    String projectKey = "projectKey";

    WorkspaceSettings newWorkspaceSettings = mock(WorkspaceSettings.class);
    ServerConnectionSettings serverConnectionSettings = new ServerConnectionSettings(connectionId, "", "", null, true, httpClient);
    when(newWorkspaceSettings.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, serverConnectionSettings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings);

    WorkspaceFolderSettings newFolderSettings = new WorkspaceFolderSettings(connectionId, projectKey, Collections.emptyMap(), null);
    underTest.onChange(mock(WorkspaceFolderWrapper.class), mock(WorkspaceFolderSettings.class), newFolderSettings);

    verifyZeroInteractions(output);
    verify(workspaceFoldersManager).getAll();
  }

  @Test
  void doNothingOnEmptyWorkspaceSettings() {
    underTest.onChange(null, mock(WorkspaceSettings.class));
    verifyZeroInteractions(output);
    verify(workspaceFoldersManager).getAll();
  }

  @Test
  void updateRegistrationsOnWorkspaceSettingsChange() {
    String connectionId = "connectionId";
    String projectKey = "projectKey";

    WorkspaceSettings newWorkspaceSettings1 = mock(WorkspaceSettings.class);
    ServerConnectionSettings serverConnectionSettings = new ServerConnectionSettings(connectionId, "http://my.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings1.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, serverConnectionSettings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings1);

    WorkspaceFolderWrapper folder = mock(WorkspaceFolderWrapper.class);
    WorkspaceFolderSettings newFolderSettings = new WorkspaceFolderSettings(connectionId, projectKey, Collections.emptyMap(), null);
    underTest.onChange(folder, null, newFolderSettings);

    WorkspaceSettings newWorkspaceSettings2 = mock(WorkspaceSettings.class);
    ServerConnectionSettings settings = new ServerConnectionSettings(connectionId, "http://other.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings2.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, settings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings2);

    verify(output, times(1)).log("De-registering notifications for project 'projectKey' on connection 'connectionId'", LogOutput.Level.DEBUG);
    verify(output, times(2)).log("Enabling notifications for project 'projectKey' on connection 'connectionId'", LogOutput.Level.DEBUG);
    verify(workspaceFoldersManager, times(2)).getAll();
  }

  @Test
  void shouldShowSonarQubeNotificationToUserAndClickOnNotificationLink() {
    ServerNotifications.EventListener listener = underTest.new EventListener(false);
    String category = "category";
    String message = "message";
    String link = "http://some.link";
    String projectKey = "projectKey";
    ServerNotification notification = new DefaultServerNotification(category, message, link, projectKey, ZonedDateTime.now());

    MessageActionItem browseAction = new MessageActionItem("Show on SonarQube");
    MessageActionItem settingsAction = new MessageActionItem("Open Settings");
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(browseAction));

    listener.handle(notification);

    verify(telemetry).devNotificationsReceived(category);
    ArgumentCaptor<ShowMessageRequestParams> messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(client).showMessageRequest(messageCaptor.capture());
    ShowMessageRequestParams shownMessage = messageCaptor.getValue();
    assertThat(shownMessage).extracting(ShowMessageRequestParams::getMessage, ShowMessageRequestParams::getActions)
      .containsExactly("SonarQube Notification: message", Arrays.asList(browseAction, settingsAction));
    verify(telemetry).devNotificationsClicked(category);
    verify(client).browseTo(link);
  }

  @Test
  void shouldShowSonarQubeNotificationToUserAndOpenSettings() {
    ServerNotifications.EventListener listener = underTest.new EventListener(false);
    String category = "category";
    String message = "message";
    String link = "http://some.link";
    String projectKey = "projectKey";
    ServerNotification notification = new DefaultServerNotification(category, message, link, projectKey, ZonedDateTime.now());

    MessageActionItem browseAction = new MessageActionItem("Show on SonarQube");
    MessageActionItem settingsAction = new MessageActionItem("Open Settings");
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(settingsAction));

    listener.handle(notification);

    verify(telemetry).devNotificationsReceived(category);
    ArgumentCaptor<ShowMessageRequestParams> messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(client).showMessageRequest(messageCaptor.capture());
    ShowMessageRequestParams shownMessage = messageCaptor.getValue();
    assertThat(shownMessage).extracting(ShowMessageRequestParams::getMessage, ShowMessageRequestParams::getActions)
      .containsExactly("SonarQube Notification: message", Arrays.asList(browseAction, settingsAction));

    verify(client).openConnectionSettings(false);
  }

  @Test
  void shouldShowSonarCloudNotificationToUserAndNotClickOnNotification() {
    ServerNotifications.EventListener listener = underTest.new EventListener(true);
    String category = "category";
    String message = "message";
    String link = "http://some.link";
    String projectKey = "projectKey";
    ServerNotification notification = new DefaultServerNotification(category, message, link, projectKey, ZonedDateTime.now());

    MessageActionItem browseAction = new MessageActionItem("Show on SonarCloud");
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(null));
    MessageActionItem settingsAction = new MessageActionItem("Open Settings");

    listener.handle(notification);

    verify(telemetry).devNotificationsReceived(category);
    ArgumentCaptor<ShowMessageRequestParams> messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(client).showMessageRequest(messageCaptor.capture());
    ShowMessageRequestParams shownMessage = messageCaptor.getValue();
    assertThat(shownMessage).extracting(ShowMessageRequestParams::getMessage, ShowMessageRequestParams::getActions)
      .containsExactly("SonarCloud Notification: message", Arrays.asList(browseAction, settingsAction));
    verify(telemetry, never()).devNotificationsClicked(category);
  }

}
