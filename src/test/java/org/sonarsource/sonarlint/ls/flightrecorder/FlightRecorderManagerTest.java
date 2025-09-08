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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FlightRecorderManagerTest {

  private final SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);

  @Test
  void shouldSendNotificationToClientWhenInitializedThenStarted() {
    var sessionId = "test-session-id";
    var underTest = new FlightRecorderManager(client);

    underTest.initialized();
    verifyNoInteractions(client);

    underTest.onFlightRecorderStarted(sessionId);
    var paramCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.FlightRecorderStartedParams.class);
    verify(client).flightRecorderStarted(paramCaptor.capture());
    assertThat(paramCaptor.getValue().sessionId()).isEqualTo(sessionId);
  }

  @Test
  void shouldSendNotificationToClientWhenStartedThenInitialized() {
    var sessionId = "test-session-id";
    var underTest = new FlightRecorderManager(client);

    underTest.onFlightRecorderStarted(sessionId);
    verifyNoInteractions(client);

    underTest.initialized();
    var paramCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.FlightRecorderStartedParams.class);
    verify(client).flightRecorderStarted(paramCaptor.capture());
    assertThat(paramCaptor.getValue().sessionId()).isEqualTo(sessionId);
  }
}
