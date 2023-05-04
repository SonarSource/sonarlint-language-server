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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SmartNotificationsTests {

  private final SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);

  private final SonarLintTelemetry telemetry = mock(SonarLintTelemetry.class);

  private SmartNotifications underTest;

  @BeforeEach
  public void setup() {
    underTest = new SmartNotifications(client, telemetry);
  }

  @AfterEach
  public void finish() {
    verifyNoMoreInteractions(client, telemetry);
  }

  @Test
  void shouldShowSonarQubeNotificationToUserAndClickOnNotificationLink() {
    var category = "category";
    var message = "message";
    var link = "http://some.link";
    var showNotificationParams = new ShowSmartNotificationParams(message, link, Set.of(), category, "testConnection");

    var browseAction = new MessageActionItem("Show on SonarQube");
    var settingsAction = new MessageActionItem("Open Settings");
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(browseAction));

    underTest.showSmartNotification(showNotificationParams, false);

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
    var category = "category";
    var message = "message";
    var link = "http://some.link";
    var showNotificationParams = new ShowSmartNotificationParams(message, link, Set.of(), category, "testConnection");

    var browseAction = new MessageActionItem("Show on SonarQube");
    var settingsAction = new MessageActionItem("Open Settings");
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(settingsAction));

    underTest.showSmartNotification(showNotificationParams, false);

    var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(client).showMessageRequest(messageCaptor.capture());
    var shownMessage = messageCaptor.getValue();
    assertThat(shownMessage).extracting(ShowMessageRequestParams::getMessage, ShowMessageRequestParams::getActions)
      .containsExactly("SonarQube Notification: message", List.of(browseAction, settingsAction));

    verify(client).openConnectionSettings(false);
  }

  @Test
  void shouldShowSonarCloudNotificationToUserAndNotClickOnNotification() {
    var category = "category";
    var message = "message";
    var link = "http://some.link";
    var showNotificationParams = new ShowSmartNotificationParams(message, link, Set.of(), category, "testConnection");

    var browseAction = new MessageActionItem("Show on SonarCloud");
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(null));
    var settingsAction = new MessageActionItem("Open Settings");

    underTest.showSmartNotification(showNotificationParams, true);

    var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(client).showMessageRequest(messageCaptor.capture());
    var shownMessage = messageCaptor.getValue();
    assertThat(shownMessage).extracting(ShowMessageRequestParams::getMessage, ShowMessageRequestParams::getActions)
      .containsExactly("SonarCloud Notification: message", List.of(browseAction, settingsAction));
    verify(telemetry, never()).devNotificationsClicked(category);
  }
}
