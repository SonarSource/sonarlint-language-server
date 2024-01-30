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
package org.sonarsource.sonarlint.ls.connected.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class HostInfoProviderTests {

  String clientVersion = "1.77.4";
  String workspaceName = "sonarlint-language-server";
  HostInfoProvider underTest;

  @BeforeEach
  void setup() {
    underTest = new HostInfoProvider();
  }

  @Test
  void shouldGetHostInfoWithWorkspaceName() {
    underTest.initialize(clientVersion, workspaceName);

    assertThat(underTest.getHostInfo().getDescription()).isEqualTo("1.77.4 - sonarlint-language-server");
  }

  @Test
  void shouldGetHostInfoWithoutWorkspaceName() {
    underTest.initialize(clientVersion, null);

    assertThat(underTest.getHostInfo().getDescription()).isEqualTo("1.77.4 - (no open folder)");
  }

  @Test
  void shouldCreateConnectionParams() {
    var serverUrl = "localhost:9000";
    var token = "squ_123";
    var createConnectionParams = new SonarLintExtendedLanguageClient.CreateConnectionParams(false, serverUrl, token);

    assertThat(createConnectionParams.getServerUrl()).isEqualTo(serverUrl);
    assertThat(createConnectionParams.isSonarCloud()).isFalse();
    assertThat(createConnectionParams.getToken()).isEqualTo(token);
  }
}
