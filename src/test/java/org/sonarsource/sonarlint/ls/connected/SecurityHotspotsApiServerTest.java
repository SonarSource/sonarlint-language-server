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
import java.util.ArrayList;
import java.util.List;
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

  @Test
  void shouldStartServerAndReplyToStatusRequestWhenNoFolderIsOpen() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";
    server.init(ideName, clientVersion, null);

    int port = server.getPort();
    assertThat(port).isBetween(SecurityHotspotsApiServer.STARTING_PORT, SecurityHotspotsApiServer.ENDING_PORT);

    URLConnection statusConnection = new URL(String.format("http://localhost:%d/sonarlint/api/status", port)).openConnection();
    statusConnection.connect();
    Map<String, String> response = new Gson().fromJson(new InputStreamReader(statusConnection.getInputStream()), Map.class);

    assertThat(response).containsOnly(
      entry("ideName", ideName),
      entry("description", clientVersion + " - (no open folder)")
    );
  }

  @Test
  void shouldStartServersOnSeparatePorts() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";
    String workspaceName1 = "polop";
    String workspaceName2 = "palap";
    server.init(ideName, clientVersion, workspaceName1);

    SecurityHotspotsApiServer otherServer = new SecurityHotspotsApiServer(mock(LanguageClientLogOutput.class));
    try {
      otherServer.init(ideName, clientVersion, workspaceName2);
      assertThat(otherServer.getPort()).isNotEqualTo(server.getPort());
    } finally {
      otherServer.shutdown();
    }
  }

  @Test
  void shouldNotBeAbleToStartServerWhenMaxPortIsReached() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";

    List<SecurityHotspotsApiServer> startedServers = new ArrayList<>();

    LanguageClientLogOutput testOutput = mock(LanguageClientLogOutput.class);

    int serverId = 0;
    int lastPortTried = SecurityHotspotsApiServer.STARTING_PORT;
    try {
      while (lastPortTried < SecurityHotspotsApiServer.ENDING_PORT) {
        SecurityHotspotsApiServer triedServer = new SecurityHotspotsApiServer(testOutput);
        triedServer.init(ideName, clientVersion, "sample-" + serverId);
        assertThat(triedServer.isStarted()).isTrue();
        startedServers.add(triedServer);
        lastPortTried = triedServer.getPort();
      }

      SecurityHotspotsApiServer failedServer = new SecurityHotspotsApiServer(testOutput);
      failedServer.init(ideName, clientVersion, "sample-" + serverId);
      assertThat(failedServer.isStarted()).isFalse();
    } finally {
      for(SecurityHotspotsApiServer serverToShutdown: startedServers) {
        serverToShutdown.shutdown();
      }
    }
  }
}
