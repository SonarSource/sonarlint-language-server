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
package org.sonarsource.sonarlint.ls.log;

import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

/**
 * Used by the language server
 */
public class LanguageClientLogOutput implements LogOutput, WorkspaceSettingsChangeListener {

  private final LanguageClient client;
  private final Clock clock;
  private boolean showAnalyzerLogs;
  private boolean showVerboseLogs;
  private boolean isAnalysis;

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
    if ((!isAnalysis || showAnalyzerLogs) && (showVerboseLogs || (level != Level.DEBUG && level != Level.TRACE))) {
      client.logMessage(new MessageParams(MessageType.Log, addPrefixIfNeeded(level, formattedMessage)));
    }
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
    return "[" + prefix + " - " + LocalTime.now(clock).format(DateTimeFormatter.ISO_LOCAL_TIME) + "] " + formattedMessage;
  }

  @Override
  public void onChange(WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    this.showVerboseLogs = newValue.showVerboseLogs();
    this.showAnalyzerLogs = newValue.showAnalyzerLogs();
  }

  public void setAnalysis(boolean isAnalysis) {
    this.isAnalysis = isAnalysis;

  }

}
