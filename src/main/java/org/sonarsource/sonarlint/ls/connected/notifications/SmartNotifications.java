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
package org.sonarsource.sonarlint.ls.connected.notifications;

import java.util.List;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

public class SmartNotifications {

  private static final MessageActionItem SETTINGS_ACTION = new MessageActionItem("Open Settings");

  private final SonarLintExtendedLanguageClient client;
  private final SonarLintTelemetry telemetry;

  public SmartNotifications(SonarLintExtendedLanguageClient client, SonarLintTelemetry telemetry) {
    this.client = client;
    this.telemetry = telemetry;
  }

  public void showSmartNotification(ShowSmartNotificationParams showSmartNotificationParams, boolean isSonarCloud) {
    final var label = isSonarCloud ? "SonarCloud" : "SonarQube";
    var params = new ShowMessageRequestParams();
    params.setType(MessageType.Info);
    params.setMessage(String.format("%s Notification: %s", label, showSmartNotificationParams.getText()));
    var browseAction = new MessageActionItem("Show on " + label);
    params.setActions(List.of(browseAction, SETTINGS_ACTION));
    client.showMessageRequest(params).thenAccept(action -> {
      if (browseAction.equals(action)) {
        telemetry.devNotificationsClicked(showSmartNotificationParams.getCategory());
        client.browseTo(showSmartNotificationParams.getLink());
      } else if (SETTINGS_ACTION.equals(action)) {
        client.openConnectionSettings(isSonarCloud);
      }
    });
  }
}
