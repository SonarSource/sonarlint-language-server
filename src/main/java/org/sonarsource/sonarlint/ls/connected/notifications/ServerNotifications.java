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
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.LastNotificationTime;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotification;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotificationListener;
import org.sonarsource.sonarlint.core.notifications.ServerNotificationsRegistry;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

public class ServerNotifications implements WorkspaceSettingsChangeListener, WorkspaceFolderSettingsChangeListener {

  private static final MessageActionItem SETTINGS_ACTION = new MessageActionItem("Open Settings");

  private final SonarLintExtendedLanguageClient client;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final SonarLintTelemetry telemetry;
  private final LanguageClientLogOutput logOutput;

  private final Map<String, ServerConnectionSettings> connections;
  private final Map<String, Map<String, NotificationConfiguration>> configurationsByProjectKeyByConnectionId;
  private final ServerNotificationsRegistry serverNotificationsRegistry;

  public ServerNotifications(SonarLintExtendedLanguageClient client, WorkspaceFoldersManager workspaceFoldersManager,
      SonarLintTelemetry telemetry, LanguageClientLogOutput output) {
    this.client = client;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.telemetry = telemetry;
    this.logOutput = output;

    connections = new HashMap<>();
    configurationsByProjectKeyByConnectionId = new HashMap<>();
    serverNotificationsRegistry = new ServerNotificationsRegistry();

  }

  public void shutdown() {
    serverNotificationsRegistry.stop();
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    connections.clear();
    connections.putAll(newValue.getServerConnections());
    configurationsByProjectKeyByConnectionId.keySet().forEach(connectionId -> {
      Map<String, NotificationConfiguration> copyOfConfigurations = new HashMap<>(configurationsByProjectKeyByConnectionId.get(connectionId));
      copyOfConfigurations.forEach((projectKey, config) -> {
        unregisterConfigurationIfExists(connectionId, projectKey);
        registerConfigurationIfNeeded(connectionId, projectKey);
      });
    });
    workspaceFoldersManager.getAll().stream()
      .map(WorkspaceFolderWrapper::getSettings)
      .filter(WorkspaceFolderSettings::hasBinding)
      .forEach(s -> registerConfigurationIfNeeded(s.getConnectionId(), s.getProjectKey()));
  }

  @Override
  public void onChange(@CheckForNull WorkspaceFolderWrapper folder, @CheckForNull WorkspaceFolderSettings oldValue, WorkspaceFolderSettings newValue) {
    if (oldValue != null && (
        !newValue.hasBinding() ||
        !connections.containsKey(newValue.getConnectionId()) ||
        connections.get(newValue.getConnectionId()).isDevNotificationsDisabled()
      )
    ) {
      // Project is now unbound, or bound to a server that has dev notifications disabled => unregister matching config if exists
      unregisterConfigurationIfExists(oldValue.getConnectionId(), oldValue.getProjectKey());
    }

    if (newValue.hasBinding()) {
      registerConfigurationIfNeeded(newValue.getConnectionId(), newValue.getProjectKey());
    }
  }

  private void unregisterConfigurationIfExists(@Nullable String oldConnectionId, @Nullable String oldProjectKey) {
    Map<String, NotificationConfiguration> configsForOldConnectionId = configurationsByProjectKeyByConnectionId.get(oldConnectionId);
    if (configsForOldConnectionId != null && configsForOldConnectionId.containsKey(oldProjectKey)) {
      logDebugMessage(String.format("De-registering notifications for project '%s' on connection '%s'", oldProjectKey, oldConnectionId));
      NotificationConfiguration config = configsForOldConnectionId.remove(oldProjectKey);
      serverNotificationsRegistry.remove(config.listener());
    }
  }

  private void registerConfigurationIfNeeded(String connectionId, String projectKey) {
    if (!alreadyHasConfiguration(connectionId, projectKey)) {
      if(!connections.containsKey(connectionId) || connections.get(connectionId).isDevNotificationsDisabled()) {
        // Connection is unknown, or has notifications disabled - do nothing
        return;
      }
      logDebugMessage(String.format("Enabling notifications for project '%s' on connection '%s'", projectKey, connectionId));
      NotificationConfiguration newConfiguration = newNotificationConfiguration(connections.get(connectionId), projectKey);
      serverNotificationsRegistry.register(newConfiguration);
      configurationsByProjectKeyByConnectionId.computeIfAbsent(connectionId, k -> new HashMap<>()).put(projectKey, newConfiguration);
    }
  }

  private boolean alreadyHasConfiguration(@Nullable String connectionId, @Nullable String projectKey) {
    return configurationsByProjectKeyByConnectionId.containsKey(connectionId) && configurationsByProjectKeyByConnectionId.get(connectionId).containsKey(projectKey);
  }


  private NotificationConfiguration newNotificationConfiguration(ServerConnectionSettings serverConnectionSettings, String projectKey) {
    return new NotificationConfiguration(
      new EventListener(serverConnectionSettings.isSonarCloudAlias()),
      new ConnectionNotificationTime(),
      projectKey,
      serverConnectionSettings.getServerConfiguration()::getEndpointParams,
      serverConnectionSettings.getServerConfiguration()::getHttpClient);
  }

  private void logDebugMessage(String message) {
    logOutput.log(message, LogOutput.Level.DEBUG);
  }

  /**
   * Simply displays the events and discards it
   */
  class EventListener implements ServerNotificationListener {

    private final boolean isSonarCloud;

    EventListener(boolean isSonarCloud) {
      this.isSonarCloud = isSonarCloud;
    }

    @Override
    public void handle(ServerNotification serverNotification) {
      final String category = serverNotification.category();
      telemetry.devNotificationsReceived(category);
      final String label = isSonarCloud ? "SonarCloud" : "SonarQube";
      ShowMessageRequestParams params = new ShowMessageRequestParams();
      params.setType(MessageType.Info);
      params.setMessage(String.format("%s Notification: %s", label, serverNotification.message()));
      MessageActionItem browseAction = new MessageActionItem("Show on " + label);
      params.setActions(Arrays.asList(browseAction, SETTINGS_ACTION));
      client.showMessageRequest(params).thenAccept(action -> {
        if(browseAction.equals(action)) {
          telemetry.devNotificationsClicked(serverNotification.category());
          client.browseTo(serverNotification.link());
        } else if (SETTINGS_ACTION.equals(action)) {
          client.openConnectionSettings(isSonarCloud);
        }
      });
    }
  }

  /**
   * TODO Persist this value. For now, this is initialized to "now" at initialization of NotificationConfiguration
   */
  private static class ConnectionNotificationTime implements LastNotificationTime {

    private ZonedDateTime last;

    @Override
    public ZonedDateTime get() {
      if (last == null) {
        set(ZonedDateTime.now());
      }
      return last;
    }

    @Override
    public void set(ZonedDateTime dateTime) {
      last = dateTime;
    }
  }
}
