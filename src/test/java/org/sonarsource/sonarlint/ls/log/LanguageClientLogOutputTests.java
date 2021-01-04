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
package org.sonarsource.sonarlint.ls.log;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput.Level;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput.NODE_COMMAND_EXCEPTION;
import static org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput.SHOW_SONARLINT_OUTPUT_ACTION;

class LanguageClientLogOutputTests {

  private LanguageClientLogOutput underTest;
  private SonarLintExtendedLanguageClient languageClient = mock(SonarLintExtendedLanguageClient.class);

  @BeforeEach
  public void prepare() {
    underTest = new LanguageClientLogOutput(languageClient, Clock.fixed(Instant.ofEpochMilli(12345678), ZoneOffset.UTC));
  }

  @Test
  public void no_debug_logs() {
    underTest.log("error", Level.ERROR);
    underTest.log("warn", Level.WARN);
    underTest.log("info", Level.INFO);
    underTest.log("debug", Level.DEBUG);
    underTest.log("trace", Level.TRACE);

    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Error - 03:25:45.678] error"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Warn  - 03:25:45.678] warn"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Info  - 03:25:45.678] info"));
    verifyNoMoreInteractions(languageClient);
  }

  @Test
  public void enable_debug_logs() {
    underTest.onChange(null, new WorkspaceSettings(false, null, null, null, null, false, true, null));

    underTest.log("error", Level.ERROR);
    underTest.log("warn", Level.WARN);
    underTest.log("info", Level.INFO);
    underTest.log("debug", Level.DEBUG);
    underTest.log("trace", Level.TRACE);

    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Error - 03:25:45.678] error"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Warn  - 03:25:45.678] warn"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Info  - 03:25:45.678] info"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Debug - 03:25:45.678] debug"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Trace - 03:25:45.678] trace"));
    verifyNoMoreInteractions(languageClient);
  }

  @Test
  public void no_analyzer_logs_by_default() {
    underTest.setAnalysis(true);
    underTest.log("error", Level.ERROR);
    underTest.log("warn", Level.WARN);
    underTest.log("info", Level.INFO);
    underTest.log("debug", Level.DEBUG);
    underTest.log("trace", Level.TRACE);

    verifyZeroInteractions(languageClient);
  }

  @Test
  public void enable_analyzer_logs() {
    underTest.onChange(null, new WorkspaceSettings(false, null, null, null, null, true, false, null));

    underTest.setAnalysis(true);
    underTest.log("error", Level.ERROR);
    underTest.log("warn", Level.WARN);
    underTest.log("info", Level.INFO);
    underTest.log("debug", Level.DEBUG);
    underTest.log("trace", Level.TRACE);

    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Error - 03:25:45.678] error"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Warn  - 03:25:45.678] warn"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Info  - 03:25:45.678] info"));
    verifyNoMoreInteractions(languageClient);
  }

  @Test
  public void enable_analyzer_debug_logs() {
    underTest.onChange(null, new WorkspaceSettings(false, null, null, null, null,true, true, null));

    underTest.setAnalysis(true);
    underTest.log("error", Level.ERROR);
    underTest.log("warn", Level.WARN);
    underTest.log("info", Level.INFO);
    underTest.log("debug", Level.DEBUG);
    underTest.log("trace", Level.TRACE);

    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Error - 03:25:45.678] error"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Warn  - 03:25:45.678] warn"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Info  - 03:25:45.678] info"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Debug - 03:25:45.678] debug"));
    verify(languageClient).logMessage(new MessageParams(MessageType.Log, "[Trace - 03:25:45.678] trace"));
    verifyNoMoreInteractions(languageClient);
  }

  @Test
  public void notification_to_client_for_node_command_exception() {
    MessageActionItem actionItem = new MessageActionItem(SHOW_SONARLINT_OUTPUT_ACTION);
    CompletableFuture<MessageActionItem> completableFuture = CompletableFuture.completedFuture(actionItem);
    when(languageClient.showMessageRequest(LanguageClientLogOutput.getShowMessageRequestParams())).thenReturn(completableFuture);

    underTest.log(NODE_COMMAND_EXCEPTION, Level.DEBUG);

    verify(languageClient).showMessageRequest(LanguageClientLogOutput.getShowMessageRequestParams());
    verify(languageClient).showSonarLintOutput();
    verify(languageClient).logMessage(any(MessageParams.class));
    verifyNoMoreInteractions(languageClient);
  }

}
