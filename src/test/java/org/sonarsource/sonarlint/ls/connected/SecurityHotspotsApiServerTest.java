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
package org.sonarsource.sonarlint.ls.connected;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

class SecurityHotspotsApiServerTest {

  private SecurityHotspotsApiServer server;

  @BeforeEach
  void setUp() {
    LanguageClientLogOutput output = mock(LanguageClientLogOutput.class);
    server = new SecurityHotspotsApiServer(output);
  }

  @AfterEach
  void cleanUp() {
    server.shutdown();
  }

  @Test
  void shouldStartServerAndReplyToStatusRequest() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";
    String workspaceName = "polop";
    server.init(ideName, clientVersion, workspaceName);

    int port = server.getPort();
    assertThat(port).isBetween(SecurityHotspotsApiServer.STARTING_PORT, SecurityHotspotsApiServer.ENDING_PORT);

    URLConnection statusConnection = new URL(String.format("http://localhost:%d/sonarlint/api/status", port)).openConnection();
    statusConnection.connect();
    Map<String, String> response = new Gson().fromJson(new InputStreamReader(statusConnection.getInputStream()), Map.class);

    assertThat(response).containsOnly(
      entry("ideName", ideName),
      entry("description", clientVersion + " - " + workspaceName)
    );
  }
}
