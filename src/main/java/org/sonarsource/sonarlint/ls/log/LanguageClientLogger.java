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
package org.sonarsource.sonarlint.ls.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import javax.annotation.CheckForNull;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

/**
 * Used by the language server
 */
public class LanguageClientLogger implements WorkspaceSettingsChangeListener {

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
  private boolean showAnalyzerLogs;
  private boolean showVerboseLogs;
  private final Clock clock;

  public LanguageClientLogger(LanguageClient client) {
    this(client, Clock.systemDefaultZone());
  }

  // Visible for testing
  LanguageClientLogger(LanguageClient client, Clock clock) {
    this.client = client;
    this.clock = clock;
  }

  public void initialize(boolean showVerboseLogs) {
    this.showVerboseLogs = showVerboseLogs;
  }

  private void log(String prefix, String formattedMessage, boolean isDebugOrTrace, boolean isFromAnalysis) {
    if ((!isFromAnalysis || showAnalyzerLogs) && (showVerboseLogs || !isDebugOrTrace)) {
      client.logMessage(new MessageParams(MessageType.Log, prefix(prefix, formattedMessage)));
    }
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    this.showVerboseLogs = newValue.showVerboseLogs();
    this.showAnalyzerLogs = newValue.showAnalyzerLogs();
  }

  private String prefix(String prefix, String formattedMessage) {
    return "[" + prefix + " - " + LocalTime.now(clock).format(LOG_DATE_FORMAT) + "] " + formattedMessage;
  }

  void error(String formattedMessage, boolean isFromAnalysis) {
    log("Error", formattedMessage, false, isFromAnalysis);
  }

  public void error(String formattedMessage) {
    error(formattedMessage, false);
  }

  public void error(String formattedMessage, Throwable t) {
    var sw = new StringWriter();
    var pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    var sStackTrace = sw.toString();
    error(formattedMessage + "\n" + sStackTrace, false);
  }

  void warn(String formattedMessage, boolean isFromAnalysis) {
    log("Warn ", formattedMessage, false, isFromAnalysis);
  }

  public void warn(String formattedMessage) {
    warn(formattedMessage, false);
  }

  void info(String formattedMessage, boolean isFromAnalysis) {
    log("Info ", formattedMessage, false, isFromAnalysis);
  }

  public void info(String formattedMessage) {
    info(formattedMessage, false);
  }

  void debug(String formattedMessage, boolean isFromAnalysis) {
    log("Debug", formattedMessage, true, isFromAnalysis);
  }

  public void debug(String formattedMessage) {
    debug(formattedMessage, false);
  }

  void trace(String formattedMessage, boolean isFromAnalysis) {
    log("Trace", formattedMessage, true, isFromAnalysis);
  }

  public void trace(String formattedMessage) {
    trace(formattedMessage, false);
  }

}
