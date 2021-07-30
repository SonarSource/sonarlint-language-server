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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.serverapi.hotspot.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityHotspotsHandlerServerTest {

  private SecurityHotspotsHandlerServer server;
  private final ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  private final SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  private final LanguageClientLogOutput output = mock(LanguageClientLogOutput.class);
  private final SonarLintTelemetry telemetry = mock(SonarLintTelemetry.class);
  private final HotspotApi hotspotApi = mock(HotspotApi.class);

  @BeforeEach
  void setUp() {
    server = new SecurityHotspotsHandlerServer(output, bindingManager, client, telemetry, (e, c) -> hotspotApi);
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
    server.initialize(ideName, clientVersion, workspaceName);

    int port = server.getPort();
    assertThat(port).isBetween(SecurityHotspotsHandlerServer.STARTING_PORT, SecurityHotspotsHandlerServer.ENDING_PORT);

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
    server.initialize(ideName, clientVersion, null);

    int port = server.getPort();
    assertThat(port).isBetween(SecurityHotspotsHandlerServer.STARTING_PORT, SecurityHotspotsHandlerServer.ENDING_PORT);

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
    server.initialize(ideName, clientVersion, workspaceName1);

    SecurityHotspotsHandlerServer otherServer = new SecurityHotspotsHandlerServer(output, bindingManager, client, telemetry);
    try {
      otherServer.initialize(ideName, clientVersion, workspaceName2);
      assertThat(otherServer.getPort()).isNotEqualTo(server.getPort());
    } finally {
      otherServer.shutdown();
    }
  }

  @Test
  void shouldNotBeAbleToStartServerWhenMaxPortIsReached() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";

    List<SecurityHotspotsHandlerServer> startedServers = new ArrayList<>();

    int serverId = 0;
    int lastPortTried = SecurityHotspotsHandlerServer.STARTING_PORT;
    try {
      while (lastPortTried < SecurityHotspotsHandlerServer.ENDING_PORT) {
        SecurityHotspotsHandlerServer triedServer = new SecurityHotspotsHandlerServer(output, bindingManager, client, telemetry);
        triedServer.initialize(ideName, clientVersion, "sample-" + serverId);
        assertThat(triedServer.isStarted()).isTrue();
        startedServers.add(triedServer);
        lastPortTried = triedServer.getPort();
      }

      SecurityHotspotsHandlerServer failedServer = new SecurityHotspotsHandlerServer(output, bindingManager, client, telemetry);
      failedServer.initialize(ideName, clientVersion, "sample-" + serverId);
      assertThat(failedServer.isStarted()).isFalse();
    } finally {
      for(SecurityHotspotsHandlerServer serverToShutdown: startedServers) {
        serverToShutdown.shutdown();
      }
    }
  }

  @Test
  void shouldNotifyClientWhenConnectionIsFoundForHotspot() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";
    String workspaceName = "polop";
    server.initialize(ideName, clientVersion, workspaceName);
    ServerHotspot remoteHotspot = mock(ServerHotspot.class);
    when(bindingManager.getServerConnectionSettingsForUrl(anyString())).thenReturn(Optional.of(new ServerConnectionSettings.EndpointParamsAndHttpClient(null, null)));
    when(hotspotApi.fetch(any(GetSecurityHotspotRequestParams.class))).thenReturn(Optional.of(remoteHotspot));


    int port = server.getPort();
    assertThat(port).isBetween(SecurityHotspotsHandlerServer.STARTING_PORT, SecurityHotspotsHandlerServer.ENDING_PORT);

    String server = "http://some.sonar.server";
    String hotspot = "someHotspotKey";
    String project = "someProjectKey";
    URLConnection showHotspotConnection = new URL(
            String.format("http://localhost:%d/sonarlint/api/hotspots/show?server=%s&hotspot=%s&project=%s", port, server, hotspot, project)
    ).openConnection();
    showHotspotConnection.connect();
    assertThat(showHotspotConnection.getContent()).isNotNull();

    verify(bindingManager).getServerConnectionSettingsForUrl(server);

    ArgumentCaptor<GetSecurityHotspotRequestParams> getHotspotParamsCaptor = ArgumentCaptor.forClass(GetSecurityHotspotRequestParams.class);
    verify(hotspotApi).fetch(getHotspotParamsCaptor.capture());
    GetSecurityHotspotRequestParams passedParams = getHotspotParamsCaptor.getValue();
    assertThat(passedParams.hotspotKey).isEqualTo(hotspot);
    assertThat(passedParams.projectKey).isEqualTo(project);

    verify(client).showHotspot(remoteHotspot);
    verify(telemetry).showHotspotRequestReceived();
  }

  @Test
  void shouldShowErrorMessageWhenNoConnectionIsFoundForHotspot() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";
    String workspaceName = "polop";
    server.initialize(ideName, clientVersion, workspaceName);
    when(bindingManager.getServerConnectionSettingsForUrl(anyString())).thenReturn(Optional.empty());
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(new MessageActionItem("Open Settings")));

    int port = server.getPort();
    assertThat(port).isBetween(SecurityHotspotsHandlerServer.STARTING_PORT, SecurityHotspotsHandlerServer.ENDING_PORT);

    String server = "http://some.sonar.server";
    String hotspot = "someHotspotKey";
    String project = "someProjectKey";
    URLConnection showHotspotConnection = new URL(
      String.format("http://localhost:%d/sonarlint/api/hotspots/show?server=%s&hotspot=%s&project=%s", port, server, hotspot, project)
    ).openConnection();
    showHotspotConnection.connect();
    assertThat(showHotspotConnection.getContent()).isNotNull();

    verify(bindingManager).getServerConnectionSettingsForUrl(server);

    verify(client).showMessageRequest(any());
    verify(client).openConnectionSettings(false);
    verify(telemetry).showHotspotRequestReceived();
  }

  @Test
  void shouldFailOnMissingServer() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";
    String workspaceName = "polop";
    server.initialize(ideName, clientVersion, workspaceName);

    int port = server.getPort();
    assertThat(port).isBetween(SecurityHotspotsHandlerServer.STARTING_PORT, SecurityHotspotsHandlerServer.ENDING_PORT);

    URLConnection statusConnection = new URL(
            String.format("http://localhost:%d/sonarlint/api/hotspots/show?hotspot=hotspot&project=project", port)
    ).openConnection();
    statusConnection.connect();
    assertThatThrownBy(statusConnection::getContent).isInstanceOf(IOException.class).hasMessageContaining("400 for URL");
  }

  @Test
  void shouldFailOnMissingHotspot() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";
    String workspaceName = "polop";
    server.initialize(ideName, clientVersion, workspaceName);

    int port = server.getPort();
    assertThat(port).isBetween(SecurityHotspotsHandlerServer.STARTING_PORT, SecurityHotspotsHandlerServer.ENDING_PORT);

    URLConnection statusConnection = new URL(
            String.format("http://localhost:%d/sonarlint/api/hotspots/show?server=server&project=project", port)
    ).openConnection();
    statusConnection.connect();
    assertThatThrownBy(statusConnection::getContent).isInstanceOf(IOException.class).hasMessageContaining("400 for URL");
  }

  @Test
  void shouldFailOnMissingProject() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";
    String workspaceName = "polop";
    server.initialize(ideName, clientVersion, workspaceName);

    int port = server.getPort();
    assertThat(port).isBetween(SecurityHotspotsHandlerServer.STARTING_PORT, SecurityHotspotsHandlerServer.ENDING_PORT);

    URLConnection statusConnection = new URL(
            String.format("http://localhost:%d/sonarlint/api/hotspots/show?server=server&hotspot=hotspot", port)
    ).openConnection();
    statusConnection.connect();
    assertThatThrownBy(statusConnection::getContent).isInstanceOf(IOException.class).hasMessageContaining("400 for URL");
  }
}
