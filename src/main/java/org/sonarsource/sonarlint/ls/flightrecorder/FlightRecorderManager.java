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
package org.sonarsource.sonarlint.ls.flightrecorder;

import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

public class FlightRecorderManager {

  private final SonarLintExtendedLanguageClient client;

  private boolean initialized;
  private String sessionId;

  public FlightRecorderManager(SonarLintExtendedLanguageClient client) {
    this.client = client;
  }

  public void onFlightRecorderStarted(String sessionId) {
    this.sessionId = sessionId;
    if (initialized) {
      sendSessionIdToClient();
    }
  }

  public void initialized() {
    this.initialized = true;
    if (sessionId != null) {
      sendSessionIdToClient();
    }
  }

  private void sendSessionIdToClient() {
    client.flightRecorderStarted(new SonarLintExtendedLanguageClient.FlightRecorderStartedParams(sessionId));
  }
}
