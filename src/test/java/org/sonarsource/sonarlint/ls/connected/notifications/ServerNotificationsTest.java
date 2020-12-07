/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotification;
import org.sonarsource.sonarlint.core.container.model.DefaultServerNotification;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerNotificationsTest {

  private final SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);

  private final ProjectBindingManager projectBindingManager = mock(ProjectBindingManager.class);

  private final SonarLintTelemetry telemetry = mock(SonarLintTelemetry.class);

  private final LanguageClientLogOutput output = mock(LanguageClientLogOutput.class);

  private ServerNotifications underTest;

  @BeforeEach
  public void setup() {
    underTest = new ServerNotifications(client, projectBindingManager, telemetry, output);
  }


  @Test
  void shouldShowSonarQubeNotificationToUserAndClickOnNotification() {
    ServerNotifications.EventListener listener = underTest.new EventListener(false);
    String category = "category";
    String message = "message";
    String link = "http://some.link";
    String projectKey = "projectKey";
    ServerNotification notification = new DefaultServerNotification(category, message, link, projectKey, ZonedDateTime.now());

    MessageActionItem browseAction = new MessageActionItem("Open in SonarQube");
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(browseAction));
    listener.handle(notification);

    verify(telemetry).devNotificationsReceived(category);
    ArgumentCaptor<ShowMessageRequestParams> messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(client).showMessageRequest(messageCaptor.capture());
    ShowMessageRequestParams shownMessage = messageCaptor.getValue();
    assertThat(shownMessage).extracting(ShowMessageRequestParams::getMessage, ShowMessageRequestParams::getActions)
      .containsExactly("SonarQube Notification: message", Collections.singletonList(browseAction));
    verify(telemetry).devNotificationsClicked(category);
  }

  @Test
  void shouldShowSonarCloudNotificationToUserAndNotClickOnNotification() {
    ServerNotifications.EventListener listener = underTest.new EventListener(true);
    String category = "category";
    String message = "message";
    String link = "http://some.link";
    String projectKey = "projectKey";
    ServerNotification notification = new DefaultServerNotification(category, message, link, projectKey, ZonedDateTime.now());

    MessageActionItem browseAction = new MessageActionItem("Open in SonarCloud");
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(null));
    listener.handle(notification);

    verify(telemetry).devNotificationsReceived(category);
    ArgumentCaptor<ShowMessageRequestParams> messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(client).showMessageRequest(messageCaptor.capture());
    ShowMessageRequestParams shownMessage = messageCaptor.getValue();
    assertThat(shownMessage).extracting(ShowMessageRequestParams::getMessage, ShowMessageRequestParams::getActions)
      .containsExactly("SonarCloud Notification: message", Collections.singletonList(browseAction));
    verify(telemetry, never()).devNotificationsClicked(category);
  }

}
