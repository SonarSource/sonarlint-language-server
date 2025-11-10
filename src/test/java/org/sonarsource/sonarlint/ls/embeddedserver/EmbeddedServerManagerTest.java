/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.ls.embeddedserver;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.client.embeddedserver.EmbeddedServerStartedParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class EmbeddedServerManagerTest {

  private final SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);

  @Test
  void shouldSendNotificationToClientWhenInitializedThenStarted() {
    var port = 64127;
    var underTest = new EmbeddedServerManager(client);

    underTest.initialized();
    verifyNoInteractions(client);

    underTest.embeddedServerStarted(port);
    var embeddedServerParamCaptor = ArgumentCaptor.forClass(EmbeddedServerStartedParams.class);
    verify(client).embeddedServerStarted(embeddedServerParamCaptor.capture());

    assertThat(embeddedServerParamCaptor.getValue().getPort()).isEqualTo(port);
  }

  @Test
  void shouldSendNotificationToClientWhenStartedThenInitialized() {
    var port = 64121;
    var underTest = new EmbeddedServerManager(client);

    underTest.embeddedServerStarted(port);
    verifyNoInteractions(client);

    underTest.initialized();
    var embeddedServerParamCaptor = ArgumentCaptor.forClass(EmbeddedServerStartedParams.class);
    verify(client).embeddedServerStarted(embeddedServerParamCaptor.capture());

    assertThat(embeddedServerParamCaptor.getValue().getPort()).isEqualTo(port);
  }
}
