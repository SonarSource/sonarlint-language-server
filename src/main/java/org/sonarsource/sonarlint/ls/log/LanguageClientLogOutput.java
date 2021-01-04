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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Collections;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

/**
 * Used by the language server
 */
public class LanguageClientLogOutput implements LogOutput, WorkspaceSettingsChangeListener {

  static final String SHOW_SONARLINT_OUTPUT_ACTION = "Show SonarLint Output";
  static final String NODE_COMMAND_EXCEPTION = "NodeCommandException";
  private static final DateTimeFormatter LOG_DATE_FORMAT = new DateTimeFormatterBuilder()
    .appendValue(HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(MINUTE_OF_HOUR, 2)
    .appendLiteral(':')
    .appendValue(SECOND_OF_MINUTE, 2)
    .appendLiteral('.')
    .appendValue(MILLI_OF_SECOND, 3)
    .toFormatter();

  private final LanguageClient client;
  private final Clock clock;
  private boolean showAnalyzerLogs;
  private boolean showVerboseLogs;
  private final InheritableThreadLocal<Boolean> isAnalysis = new InheritableThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return false;
    }
  };

  public LanguageClientLogOutput(LanguageClient client) {
    this(client, Clock.systemDefaultZone());
  }

  // Visible for testing
  LanguageClientLogOutput(LanguageClient client, Clock clock) {
    this.client = client;
    this.clock = clock;
  }

  @Override
  public void log(String formattedMessage, Level level) {
    if (formattedMessage.contains(NODE_COMMAND_EXCEPTION)) {
      ShowMessageRequestParams params = getShowMessageRequestParams();
      client.showMessageRequest(params).thenAccept(action -> ((SonarLintExtendedLanguageClient) client).showSonarLintOutput());
      client.logMessage(new MessageParams(MessageType.Log, addPrefixIfNeeded(level, formattedMessage)));
    }
    if ((!isAnalysis.get() || showAnalyzerLogs) && (showVerboseLogs || (level != Level.DEBUG && level != Level.TRACE))) {
      client.logMessage(new MessageParams(MessageType.Log, addPrefixIfNeeded(level, formattedMessage)));
    }
  }

  static ShowMessageRequestParams getShowMessageRequestParams() {
    MessageActionItem actionItem = new MessageActionItem(SHOW_SONARLINT_OUTPUT_ACTION);
    ShowMessageRequestParams params = new ShowMessageRequestParams(Collections.singletonList(actionItem));
    params.setType(MessageType.Error);
    params.setMessage("JS/TS analysis failed. Please check the SonarLint Output for more details.");
    return params;
  }

  private String addPrefixIfNeeded(Level level, String formattedMessage) {
    switch (level) {
      case ERROR:
        return prefix("Error", formattedMessage);
      case WARN:
        return prefix("Warn ", formattedMessage);
      case INFO:
        return prefix("Info ", formattedMessage);
      case DEBUG:
        return prefix("Debug", formattedMessage);
      case TRACE:
        return prefix("Trace", formattedMessage);
    }
    throw new IllegalStateException("Unexpected level: " + level);
  }

  private String prefix(String prefix, String formattedMessage) {
    return "[" + prefix + " - " + LocalTime.now(clock).format(LOG_DATE_FORMAT) + "] " + formattedMessage;
  }

  @Override
  public void onChange(WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    this.showVerboseLogs = newValue.showVerboseLogs();
    this.showAnalyzerLogs = newValue.showAnalyzerLogs();
  }

  public void setAnalysis(boolean isAnalysis) {
    this.isAnalysis.set(isAnalysis);
  }

}
