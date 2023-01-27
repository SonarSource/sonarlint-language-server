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

import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

public class LanguageClientLogOutput implements ClientLogOutput {

  private final LanguageClientLogger lsLogger;
  private final boolean isFromAnalysis;

  public LanguageClientLogOutput(LanguageClientLogger lsLogger, boolean isFromAnalysis) {
    this.lsLogger = lsLogger;
    this.isFromAnalysis = isFromAnalysis;
  }

  @Override
  public void log(String formattedMessage, Level level) {
    switch (level) {
      case ERROR:
        lsLogger.error(formattedMessage, isFromAnalysis);
        break;
      case WARN:
        lsLogger.warn(formattedMessage, isFromAnalysis);
        break;
      case INFO:
        lsLogger.info(formattedMessage, isFromAnalysis);
        break;
      case DEBUG:
        lsLogger.debug(formattedMessage, isFromAnalysis);
        break;
      case TRACE:
        lsLogger.trace(formattedMessage, isFromAnalysis);
        break;
      default:
        throw new IllegalStateException("Unexpected level: " + level);
    }
  }

}
