/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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
package org.sonarsource.sonarlint.ls;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkippedPluginsNotifierTests {
  private SonarLintExtendedLanguageClient languageClient;
  private SkippedPluginsNotifier underTest;

  @BeforeEach
  void initClient() {
    languageClient = mock(SonarLintExtendedLanguageClient.class);
    underTest = new SkippedPluginsNotifier(languageClient, mock(LanguageClientLogger.class));
  }


  @Nested
  class CanShowMissingRequirementsNotification {

    private void preparePopupSelection(@Nullable MessageActionItem selectedItem) {
      when(languageClient.showMessageRequest(any(ShowMessageRequestParams.class))).thenReturn(CompletableFuture.completedFuture(selectedItem));
    }

    @BeforeEach
    void configureClient() {
      when(languageClient.canShowMissingRequirementsNotification()).thenReturn(CompletableFuture.completedFuture(true));
    }

    @Test
    void no_job_if_notifs_disabled() {
      when(languageClient.canShowMissingRequirementsNotification()).thenReturn(CompletableFuture.completedFuture(false));

      underTest.notifyOnceForSkippedPlugins(Language.YAML, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_NODE_JS, "18", "14");

      verify(languageClient, never()).showMessageRequest(any());
    }

    @Test
    void send_notification_once_for_jre() {
      preparePopupSelection(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS);

      underTest.notifyOnceForSkippedPlugins(Language.JAVA, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_JRE, "17", "11");
      underTest.notifyOnceForSkippedPlugins(Language.JAVA, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_JRE, "17", "11");

      var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
      verify(languageClient, times(1)).showMessageRequest(messageCaptor.capture());
      verify(languageClient, times(1)).openJavaHomeSettings();

      var message = messageCaptor.getValue();
      assertThat(message.getMessage()).contains("SonarQube for VS Code failed to analyze Java code")
        .contains("Java runtime version 17 or later is required. Current version is 11.");
      assertThat(message.getActions()).containsExactly(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS);
    }

    @Test
    void send_notification_for_jre_and_close_notification() {
      preparePopupSelection(null);

      underTest.notifyOnceForSkippedPlugins(Language.JAVA, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_JRE, "17", "11");

      var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
      verify(languageClient, times(1)).showMessageRequest(messageCaptor.capture());

      var message = messageCaptor.getValue();
      assertThat(message.getMessage()).contains("SonarQube for VS Code failed to analyze Java code")
        .contains("Java runtime version 17 or later is required. Current version is 11.");
      assertThat(message.getActions()).containsExactly(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS);
    }

    @Test
    void send_notification_for_node_when_found() {
      preparePopupSelection(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS);

      underTest.notifyOnceForSkippedPlugins(Language.JS, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_NODE_JS, "minNodeJsVersion", "currentNodeJsVersion");

      var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
      verify(languageClient, times(1)).showMessageRequest(messageCaptor.capture());
      verify(languageClient, times(1)).openPathToNodeSettings();

      var message = messageCaptor.getValue();
      assertThat(message.getMessage()).contains("SonarQube for VS Code failed to analyze JavaScript code")
        .contains("Node.js runtime version minNodeJsVersion or later is required. Current version is currentNodeJsVersion.");
      assertThat(message.getActions()).containsExactly(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS, SkippedPluginsNotifier.ACTION_DONT_SHOW_AGAIN);
    }

    @Test
    void send_notification_for_node_when_not_found() {
      preparePopupSelection(null);

      underTest.notifyOnceForSkippedPlugins(Language.JS, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_NODE_JS, "minNodeJsVersion", null);

      var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
      verify(languageClient, times(1)).showMessageRequest(messageCaptor.capture());

      var message = messageCaptor.getValue();
      assertThat(message.getMessage()).contains("SonarQube for VS Code failed to analyze JavaScript code")
        .contains("Node.js runtime version minNodeJsVersion or later is required.")
        .doesNotContain("Current version is");
      assertThat(message.getActions()).containsExactly(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS, SkippedPluginsNotifier.ACTION_DONT_SHOW_AGAIN);
    }

    @Test
    void should_turn_off_notifications_when_user_opts_out() {
      preparePopupSelection(SkippedPluginsNotifier.ACTION_DONT_SHOW_AGAIN);

      underTest.notifyOnceForSkippedPlugins(Language.JS, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_NODE_JS, "minNodeJsVersion", "currentNodeJsVersion");

      var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
      verify(languageClient, times(1)).showMessageRequest(messageCaptor.capture());
      verify(languageClient, times(1)).doNotShowMissingRequirementsMessageAgain();
      verify(languageClient, never()).openPathToNodeSettings();

      var message = messageCaptor.getValue();
      assertThat(message.getMessage()).contains("SonarQube for VS Code failed to analyze JavaScript code")
        .contains("Node.js runtime version minNodeJsVersion or later is required. Current version is currentNodeJsVersion.");
      assertThat(message.getActions()).containsExactly(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS, SkippedPluginsNotifier.ACTION_DONT_SHOW_AGAIN);
    }
  }


  @Test
  void send_message_for_node_when_found() {
    when(languageClient.canShowMissingRequirementsNotification())
      .thenReturn(CompletableFuture.completedFuture(null));

    underTest.notifyOnceForSkippedPlugins(Language.JS, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_NODE_JS, "minNodeJsVersion", "currentNodeJsVersion");

    var messageCaptor = ArgumentCaptor.forClass(MessageParams.class);
    verify(languageClient, times(1)).showMessage(messageCaptor.capture());

    var message = messageCaptor.getValue();
    assertThat(message.getMessage())
      .contains("Node.js runtime version minNodeJsVersion or later is required. Current version is currentNodeJsVersion.");
  }
}
