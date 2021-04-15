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

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class BindingUpdateNotificationTest {

  private final LanguageClient languageClient = mock(LanguageClient.class);
  private final BindingUpdateNotification bindingUpdateNotification = new BindingUpdateNotification(languageClient);

  @Test
  void should_request_update_when_user_accepts_notification() {
    // returning any item will trigger the update request
    when(languageClient.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(new MessageActionItem()));

    CompletableFuture<Boolean> shouldUpdateBinding = bindingUpdateNotification.notifyBindingUpdateAvailable("projectKey");

    assertThat(shouldUpdateBinding).isCompletedWithValue(true);
  }

  @Test
  void should_not_request_update_when_user_dismisses_notification() {
    when(languageClient.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(null));

    CompletableFuture<Boolean> shouldUpdateBinding = bindingUpdateNotification.notifyBindingUpdateAvailable("projectKey");

    assertThat(shouldUpdateBinding).isCompletedWithValue(false);
  }

  @Test
  void should_wait_for_client_response_before_updating_binding() {
    CompletableFuture<MessageActionItem> completableFuture = new CompletableFuture<>();
    when(languageClient.showMessageRequest(any())).thenReturn(completableFuture);

    CompletableFuture<Boolean> shouldUpdateBinding = bindingUpdateNotification.notifyBindingUpdateAvailable("projectKey");

    assertThat(shouldUpdateBinding).isNotCompleted();
  }

  @Test
  void should_not_show_notification_for_a_given_project_key_when_one_is_already_active() {
    CompletableFuture<MessageActionItem> completableFuture = new CompletableFuture<>();
    when(languageClient.showMessageRequest(any())).thenReturn(completableFuture);

    bindingUpdateNotification.notifyBindingUpdateAvailable("projectKey");
    CompletableFuture<Boolean> shouldUpdateBinding = bindingUpdateNotification.notifyBindingUpdateAvailable("projectKey");

    assertThat(shouldUpdateBinding).isCompletedWithValue(false);
  }
}
