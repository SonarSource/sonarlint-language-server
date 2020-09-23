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
package org.sonarsource.sonarlint.ls;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.container.model.DefaultLoadedAnalyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.client.api.common.SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE;
import static org.sonarsource.sonarlint.core.client.api.common.SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS;

class SkippedPluginsNotifierTest {

  private static final MessageActionItem actionItem = new MessageActionItem("Open Java Settings");

  private SonarLintExtendedLanguageClient languageClient;

  @BeforeEach
  void initClient() {
    languageClient = mock(SonarLintExtendedLanguageClient.class);
    when(languageClient.showMessageRequest(any(ShowMessageRequestParams.class))).thenReturn(CompletableFuture.completedFuture(actionItem));
  }

  @Test
  void no_job_if_no_plugins() {
    AnalysisResults results = mock(AnalysisResults.class);
    when(results.languagePerFile()).thenReturn(Collections.emptyMap());
    SkippedPluginsNotifier.notifyOnceForSkippedPlugins(results, Collections.emptyList(), languageClient);

    verifyZeroInteractions(languageClient);
  }

  @Test
  void send_notification_for_jre() {

    AnalysisResults results = mock(AnalysisResults.class);
    when(results.languagePerFile()).thenReturn(Collections.singletonMap(null, Language.JAVA));

    DefaultLoadedAnalyzer analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE =
      new DefaultLoadedAnalyzer("java", "Java Code Quality and Security", "6.7.8.90123",
        new SkipReason.UnsatisfiedRuntimeRequirement(JRE, "currentJavaVersion", "minJavaVersion"));
    SkippedPluginsNotifier.notifyOnceForSkippedPlugins(results, Collections.singleton(analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE), languageClient);

    ArgumentCaptor<ShowMessageRequestParams> messageCaptor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(languageClient).showMessageRequest(messageCaptor.capture());
    verify(languageClient).openJavaHomeSettings();
    verifyNoMoreInteractions(languageClient);

    ShowMessageRequestParams message = messageCaptor.getValue();
    assertThat(message.getMessage()).contains("SonarLint failed to analyze Java code")
      .contains("Java runtime version minJavaVersion or later is required. Current version is currentJavaVersion.");
    assertThat(message.getActions()).containsExactly(actionItem);
  }

  @Test
  void send_notification_for_node_when_found() {
    AnalysisResults results = mock(AnalysisResults.class);
    when(results.languagePerFile()).thenReturn(Collections.singletonMap(null, Language.JS));

    DefaultLoadedAnalyzer analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs =
      new DefaultLoadedAnalyzer("javascript", "JS and TS Code Quality and Security", "6.5.4.32109",
        new SkipReason.UnsatisfiedRuntimeRequirement(NODEJS, "currentNodeJsVersion", "minNodeJsVersion"));
    SkippedPluginsNotifier.notifyOnceForSkippedPlugins(results, Collections.singleton(analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs), languageClient);

    ArgumentCaptor<MessageParams> messageCaptor = ArgumentCaptor.forClass(MessageParams.class);
    verify(languageClient).showMessage(messageCaptor.capture());
    verifyNoMoreInteractions(languageClient);

    MessageParams message = messageCaptor.getValue();
    assertThat(message.getMessage()).contains("SonarLint failed to analyze JavaScript code")
      .contains("Node.js runtime version minNodeJsVersion or later is required. Current version is currentNodeJsVersion.");
  }


  @Test
  void send_notification_for_node_when_not_found() {
    AnalysisResults results = mock(AnalysisResults.class);
    when(results.languagePerFile()).thenReturn(Collections.singletonMap(null, Language.JS));

    DefaultLoadedAnalyzer analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs =
      new DefaultLoadedAnalyzer("javascript", "JS and TS Code Quality and Security", "6.5.4.32109",
        new SkipReason.UnsatisfiedRuntimeRequirement(NODEJS, null, "minNodeJsVersion"));
    SkippedPluginsNotifier.notifyOnceForSkippedPlugins(results, Collections.singleton(analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs), languageClient);

    ArgumentCaptor<MessageParams> messageCaptor = ArgumentCaptor.forClass(MessageParams.class);
    verify(languageClient).showMessage(messageCaptor.capture());
    verifyNoMoreInteractions(languageClient);

    MessageParams message = messageCaptor.getValue();
    assertThat(message.getMessage()).contains("SonarLint failed to analyze JavaScript code")
      .contains("Node.js runtime version minNodeJsVersion or later is required.")
      .doesNotContain("Current version is");
  }
}
