/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.container.model.DefaultServerNotification;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClient;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ServerNotificationsTest {

  private final SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);

  private final WorkspaceFoldersManager workspaceFoldersManager = mock(WorkspaceFoldersManager.class);

  private final SonarLintTelemetry telemetry = mock(SonarLintTelemetry.class);

  private final LanguageClientLogger output = mock(LanguageClientLogger.class);

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
    verifyNoInteractions(output);
  }

  @Test
  void registerOnlyOnceWithFullSettings() {
    var connectionId = "connectionId";
    var projectKey = "projectKey";

    var newWorkspaceSettings = mock(WorkspaceSettings.class);
    var settings = new ServerConnectionSettings(connectionId, "http://my.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, settings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings);

    var folder1 = mock(WorkspaceFolderWrapper.class);
    var newFolderSettings1 = createSettings(connectionId, projectKey);
    underTest.onChange(folder1, null, newFolderSettings1);

    // Same settings, only one registration sent
    var folder2 = mock(WorkspaceFolderWrapper.class);
    var newFolderSettings2 = createSettings(connectionId, projectKey);
    underTest.onChange(folder2, null, newFolderSettings2);

    verify(output, times(1)).debug("Enabling notifications for project 'projectKey' on connection 'connectionId'");
    verify(workspaceFoldersManager).getAll();
  }

  @Test
  void register2FoldersOn2Projects() {
    var connectionId = "connectionId";
    var projectKey1 = "projectKey1";
    var projectKey2 = "projectKey2";

    var newWorkspaceSettings = mock(WorkspaceSettings.class);
    var settings = new ServerConnectionSettings(connectionId, "http://my.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, settings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings);

    var folder1 = mock(WorkspaceFolderWrapper.class);
    var newFolderSettings1 = createSettings(connectionId, projectKey1);
    underTest.onChange(folder1, null, newFolderSettings1);

    // Same settings, only one registration sent
    var folder2 = mock(WorkspaceFolderWrapper.class);
    var newFolderSettings2 = createSettings(connectionId, projectKey2);
    underTest.onChange(folder2, null, newFolderSettings2);

    verify(output, times(1)).debug("Enabling notifications for project 'projectKey1' on connection 'connectionId'");
    verify(output, times(1)).debug("Enabling notifications for project 'projectKey2' on connection 'connectionId'");
    verify(workspaceFoldersManager).getAll();
  }

  @Test
  void registerThenUnregisterOnUnknownConnection() {
    var connectionId = "connectionId";
    var projectKey = "projectKey";

    var newWorkspaceSettings = mock(WorkspaceSettings.class);
    var settings = new ServerConnectionSettings(connectionId, "http://my.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, settings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings);

    var folder = mock(WorkspaceFolderWrapper.class);
    var newFolderSettings1 = createSettings(connectionId, projectKey);
    underTest.onChange(folder, null, newFolderSettings1);

    verify(output, times(1)).debug("Enabling notifications for project 'projectKey' on connection 'connectionId'");

    var newFolderSettings2 = createSettings("otherConnectionId", projectKey);
    underTest.onChange(folder, newFolderSettings1, newFolderSettings2);

    verify(output, times(1)).debug("De-registering notifications for project 'projectKey' on connection 'connectionId'");
    verify(workspaceFoldersManager).getAll();
  }

  @Test
  void doNothingOnUnknownConnectionThenRegisterOnWorkspaceSettingsChange() {
    var connectionId = "connectionId";
    var projectKey = "projectKey";

    // Initially, do not put any connection settings
    var newWorkspaceSettings = mock(WorkspaceSettings.class);
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings);

    // Try to bind to unknown connection
    var folder = mock(WorkspaceFolderWrapper.class);
    var newFolderSettings = createSettings(connectionId, projectKey);
    underTest.onChange(folder, null, newFolderSettings);

    // Then fix connection settings with known connection
    var newWorkspaceSettings2 = mock(WorkspaceSettings.class);
    var settings = new ServerConnectionSettings(connectionId, "http://my.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings2.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, settings));
    when(folder.getSettings()).thenReturn(newFolderSettings);
    when(workspaceFoldersManager.getAll()).thenReturn(Collections.singleton(folder));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings2);

    verify(output, times(1)).debug("Enabling notifications for project 'projectKey' on connection 'connectionId'");
    verify(workspaceFoldersManager, times(2)).getAll();
  }

  @Test
  void doNothingOnDisabledConnection() {
    var connectionId = "connectionId";
    var projectKey = "projectKey";

    var newWorkspaceSettings = mock(WorkspaceSettings.class);
    var serverConnectionSettings = new ServerConnectionSettings(connectionId, "", "", null, true, httpClient);
    when(newWorkspaceSettings.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, serverConnectionSettings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings);

    var newFolderSettings = createSettings(connectionId, projectKey);
    underTest.onChange(mock(WorkspaceFolderWrapper.class), mock(WorkspaceFolderSettings.class), newFolderSettings);

    verifyNoInteractions(output);
    verify(workspaceFoldersManager).getAll();
  }

  @Test
  void doNothingOnEmptyWorkspaceSettings() {
    underTest.onChange(null, mock(WorkspaceSettings.class));
    verifyNoInteractions(output);
    verify(workspaceFoldersManager).getAll();
  }

  @Test
  void updateRegistrationsOnWorkspaceSettingsChange() {
    var connectionId = "connectionId";
    var projectKey = "projectKey";

    var newWorkspaceSettings1 = mock(WorkspaceSettings.class);
    var serverConnectionSettings = new ServerConnectionSettings(connectionId, "http://my.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings1.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, serverConnectionSettings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings1);

    var folder = mock(WorkspaceFolderWrapper.class);
    var newFolderSettings = createSettings(connectionId, projectKey);
    underTest.onChange(folder, null, newFolderSettings);

    var newWorkspaceSettings2 = mock(WorkspaceSettings.class);
    var settings = new ServerConnectionSettings(connectionId, "http://other.sq", "token", null, false, httpClient);
    when(newWorkspaceSettings2.getServerConnections()).thenReturn(Collections.singletonMap(connectionId, settings));
    underTest.onChange(mock(WorkspaceSettings.class), newWorkspaceSettings2);

    verify(output, times(1)).debug("De-registering notifications for project 'projectKey' on connection 'connectionId'");
    verify(output, times(2)).debug("Enabling notifications for project 'projectKey' on connection 'connectionId'");
    verify(workspaceFoldersManager, times(2)).getAll();
  }

  @Test
  void shouldShowSonarQubeNotificationToUserAndClickOnNotificationLink() {
    var listener = underTest.new EventListener(false);
    var category = "category";
    var message = "message";
    var link = "http://some.link";
    var projectKey = "projectKey";
    var notification = new DefaultServerNotification(category, message, link, projectKey, ZonedDateTime.now());

    var browseAction = new MessageActionItem("Show on SonarQube");
    var settingsAction = new MessageActionItem("Open Settings");
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(browseAction));

    listener.handle(notification);

    verify(telemetry).devNotificationsReceived(category);
    var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(client).showMessageRequest(messageCaptor.capture());
    var shownMessage = messageCaptor.getValue();
    assertThat(shownMessage).extracting(ShowMessageRequestParams::getMessage, ShowMessageRequestParams::getActions)
      .containsExactly("SonarQube Notification: message", List.of(browseAction, settingsAction));
    verify(telemetry).devNotificationsClicked(category);
    verify(client).browseTo(link);
  }

  @Test
  void shouldShowSonarQubeNotificationToUserAndOpenSettings() {
    var listener = underTest.new EventListener(false);
    var category = "category";
    var message = "message";
    var link = "http://some.link";
    var projectKey = "projectKey";
    var notification = new DefaultServerNotification(category, message, link, projectKey, ZonedDateTime.now());

    var browseAction = new MessageActionItem("Show on SonarQube");
    var settingsAction = new MessageActionItem("Open Settings");
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(settingsAction));

    listener.handle(notification);

    verify(telemetry).devNotificationsReceived(category);
    var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(client).showMessageRequest(messageCaptor.capture());
    var shownMessage = messageCaptor.getValue();
    assertThat(shownMessage).extracting(ShowMessageRequestParams::getMessage, ShowMessageRequestParams::getActions)
      .containsExactly("SonarQube Notification: message", List.of(browseAction, settingsAction));

    verify(client).openConnectionSettings(false);
  }

  @Test
  void shouldShowSonarCloudNotificationToUserAndNotClickOnNotification() {
    var listener = underTest.new EventListener(true);
    var category = "category";
    var message = "message";
    var link = "http://some.link";
    var projectKey = "projectKey";
    var notification = new DefaultServerNotification(category, message, link, projectKey, ZonedDateTime.now());

    var browseAction = new MessageActionItem("Show on SonarCloud");
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(null));
    var settingsAction = new MessageActionItem("Open Settings");

    listener.handle(notification);

    verify(telemetry).devNotificationsReceived(category);
    var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(client).showMessageRequest(messageCaptor.capture());
    var shownMessage = messageCaptor.getValue();
    assertThat(shownMessage).extracting(ShowMessageRequestParams::getMessage, ShowMessageRequestParams::getActions)
      .containsExactly("SonarCloud Notification: message", List.of(browseAction, settingsAction));
    verify(telemetry, never()).devNotificationsClicked(category);
  }

  private WorkspaceFolderSettings createSettings(String connectionId, String projectKey) {
    return new WorkspaceFolderSettings(connectionId, projectKey, Collections.emptyMap(), null, null);
  }

}
