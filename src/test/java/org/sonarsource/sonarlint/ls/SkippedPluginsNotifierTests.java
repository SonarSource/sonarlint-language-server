/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.plugin.commons.SkipReason;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.plugin.commons.SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE;
import static org.sonarsource.sonarlint.core.plugin.commons.SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS;

class SkippedPluginsNotifierTests {

  private SonarLintExtendedLanguageClient languageClient;
  private SkippedPluginsNotifier underTest;

  @BeforeEach
  void initClient() {
    languageClient = mock(SonarLintExtendedLanguageClient.class);
    underTest = new SkippedPluginsNotifier(languageClient);
    when(languageClient.canShowMissingRequirementsNotification()).thenReturn(CompletableFuture.completedFuture(true));
  }

  private void preparePopupSelection(@Nullable MessageActionItem selectedItem) {
    when(languageClient.showMessageRequest(any(ShowMessageRequestParams.class))).thenReturn(CompletableFuture.completedFuture(selectedItem));
  }

  @Test
  void no_job_if_no_plugins() {
    var results = mock(AnalysisResults.class);
    when(results.languagePerFile()).thenReturn(Collections.emptyMap());
    underTest.notifyOnceForSkippedPlugins(results, Collections.emptyList());

    verify(languageClient, never()).showMessageRequest(any());
  }

  @Test
  void no_job_if_notifs_disabled() {
    when(languageClient.canShowMissingRequirementsNotification()).thenReturn(CompletableFuture.completedFuture(false));

    var results = mock(AnalysisResults.class);
    when(results.languagePerFile()).thenReturn(Collections.singletonMap(null, Language.JAVA));

    underTest.notifyOnceForSkippedPlugins(results, Collections.emptyList());

    verify(languageClient, never()).showMessageRequest(any());
  }

  @Test
  void send_notification_once_for_jre() {
    var results = mock(AnalysisResults.class);
    when(results.languagePerFile()).thenReturn(Collections.singletonMap(null, Language.JAVA));
    preparePopupSelection(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS);

    var analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE = new PluginDetails("java", "Java Code Quality and Security", "6.7.8.90123",
      new SkipReason.UnsatisfiedRuntimeRequirement(JRE, "currentJavaVersion", "minJavaVersion"));
    underTest.notifyOnceForSkippedPlugins(results, Collections.singleton(analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE));
    underTest.notifyOnceForSkippedPlugins(results, Collections.singleton(analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE));

    var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(languageClient, times(1)).showMessageRequest(messageCaptor.capture());
    verify(languageClient, times(1)).openJavaHomeSettings();

    var message = messageCaptor.getValue();
    assertThat(message.getMessage()).contains("SonarLint failed to analyze Java code")
      .contains("Java runtime version minJavaVersion or later is required. Current version is currentJavaVersion.");
    assertThat(message.getActions()).containsExactly(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS);
  }

  @Test
  void send_notification_for_jre_and_close_notification() {
    var results = mock(AnalysisResults.class);
    when(results.languagePerFile()).thenReturn(Collections.singletonMap(null, Language.JAVA));
    preparePopupSelection(null);

    var analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE = new PluginDetails("java", "Java Code Quality and Security", "6.7.8.90123",
      new SkipReason.UnsatisfiedRuntimeRequirement(JRE, "currentJavaVersion", "minJavaVersion"));
    underTest.notifyOnceForSkippedPlugins(results, Collections.singleton(analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE));

    var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(languageClient, times(1)).showMessageRequest(messageCaptor.capture());

    var message = messageCaptor.getValue();
    assertThat(message.getMessage()).contains("SonarLint failed to analyze Java code")
      .contains("Java runtime version minJavaVersion or later is required. Current version is currentJavaVersion.");
    assertThat(message.getActions()).containsExactly(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS);
  }

  @Test
  void send_notification_for_node_when_found() {
    var results = mock(AnalysisResults.class);
    when(results.languagePerFile()).thenReturn(Collections.singletonMap(null, Language.JS));
    preparePopupSelection(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS);

    var analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs = new PluginDetails("javascript", "JS and TS Code Quality and Security", "6.5.4.32109",
      new SkipReason.UnsatisfiedRuntimeRequirement(NODEJS, "currentNodeJsVersion", "minNodeJsVersion"));
    underTest.notifyOnceForSkippedPlugins(results, Collections.singleton(analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs));

    var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(languageClient, times(1)).showMessageRequest(messageCaptor.capture());
    verify(languageClient, times(1)).openPathToNodeSettings();

    var message = messageCaptor.getValue();
    assertThat(message.getMessage()).contains("SonarLint failed to analyze JavaScript code")
      .contains("Node.js runtime version minNodeJsVersion or later is required. Current version is currentNodeJsVersion.");
    assertThat(message.getActions()).containsExactly(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS, SkippedPluginsNotifier.ACTION_DONT_SHOW_AGAIN);
  }

  @Test
  void send_notification_for_node_when_not_found() {
    var results = mock(AnalysisResults.class);
    when(results.languagePerFile()).thenReturn(Collections.singletonMap(null, Language.JS));
    preparePopupSelection(null);

    var analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs = new PluginDetails("javascript", "JS and TS Code Quality and Security", "6.5.4.32109",
      new SkipReason.UnsatisfiedRuntimeRequirement(NODEJS, null, "minNodeJsVersion"));
    underTest.notifyOnceForSkippedPlugins(results, Collections.singleton(analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs));

    var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(languageClient, times(1)).showMessageRequest(messageCaptor.capture());

    var message = messageCaptor.getValue();
    assertThat(message.getMessage()).contains("SonarLint failed to analyze JavaScript code")
      .contains("Node.js runtime version minNodeJsVersion or later is required.")
      .doesNotContain("Current version is");
    assertThat(message.getActions()).containsExactly(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS, SkippedPluginsNotifier.ACTION_DONT_SHOW_AGAIN);
  }

  @Test
  void should_turn_off_notifications_when_user_opts_out() {
    var results = mock(AnalysisResults.class);
    when(results.languagePerFile()).thenReturn(Collections.singletonMap(null, Language.JS));
    preparePopupSelection(SkippedPluginsNotifier.ACTION_DONT_SHOW_AGAIN);

    var analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs = new PluginDetails("javascript", "JS and TS Code Quality and Security", "6.5.4.32109",
      new SkipReason.UnsatisfiedRuntimeRequirement(NODEJS, "currentNodeJsVersion", "minNodeJsVersion"));
    underTest.notifyOnceForSkippedPlugins(results, Collections.singleton(analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs));

    var messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(languageClient, times(1)).showMessageRequest(messageCaptor.capture());
    verify(languageClient, times(1)).doNotShowMissingRequirementsMessageAgain();
    verify(languageClient, never()).openPathToNodeSettings();

    var message = messageCaptor.getValue();
    assertThat(message.getMessage()).contains("SonarLint failed to analyze JavaScript code")
      .contains("Node.js runtime version minNodeJsVersion or later is required. Current version is currentNodeJsVersion.");
    assertThat(message.getActions()).containsExactly(SkippedPluginsNotifier.ACTION_OPEN_SETTINGS, SkippedPluginsNotifier.ACTION_DONT_SHOW_AGAIN);
  }
}
