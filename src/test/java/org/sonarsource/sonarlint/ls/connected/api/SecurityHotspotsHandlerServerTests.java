/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.eclipse.lsp4j.MessageActionItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.serverapi.hotspot.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClientProvider;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestsHandlerServerTests {

  private static final String TRUSTED_SERVER_URL = "http://myServer";
  private RequestsHandlerServer server;
  private final ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  private final SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  private final LanguageClientLogger output = mock(LanguageClientLogger.class);
  private final SonarLintTelemetry telemetry = mock(SonarLintTelemetry.class);
  private final HotspotApi hotspotApi = mock(HotspotApi.class);
  private final SettingsManager settingsManager = mock(SettingsManager.class);

  @BeforeEach
  void setUp() {
    mockTrustedServer();
    server = new RequestsHandlerServer(output, bindingManager, client, telemetry, (e, c) -> hotspotApi, settingsManager);
  }

  @AfterEach
  void cleanUp() {
    server.shutdown();
  }

  @Test
  void shouldStartServerAndReplyToStatusRequest() throws Exception {
    var ideName = "SonarSource Editor";
    var clientVersion = "1.42";
    var workspaceName = "polop";
    server.initialize(ideName, clientVersion, workspaceName);

    var port = server.getPort();
    assertThat(port).isBetween(RequestsHandlerServer.STARTING_PORT, RequestsHandlerServer.ENDING_PORT);

    Map<String, String> response;
    try (CloseableHttpClient client = HttpClients.custom().build()) {
      ClassicHttpRequest request = ClassicRequestBuilder.get()
        .setUri(String.format("http://localhost:%d/sonarlint/api/status", port))
        .setHeader("Origin", TRUSTED_SERVER_URL)
        .build();
      try (var responseHttp = client.execute(request)) {
        response = new Gson().fromJson(new InputStreamReader(responseHttp.getEntity().getContent()), Map.class);
      }
    }

    assertThat(response).containsOnly(
      entry("ideName", ideName),
      entry("description", clientVersion + " - " + workspaceName));
  }

  @Test
  void shouldNotDiscloseWorkspaceDetailsToUntrustedServers() throws Exception {
    var ideName = "SonarSource Editor";
    var clientVersion = "1.42";
    var workspaceName = "polop";
    server.initialize(ideName, clientVersion, workspaceName);

    var port = server.getPort();
    assertThat(port).isBetween(RequestsHandlerServer.STARTING_PORT, RequestsHandlerServer.ENDING_PORT);

    Map<String, String> response;
    try (CloseableHttpClient client = HttpClients.custom().build()) {
      ClassicHttpRequest request = ClassicRequestBuilder.get()
        .setUri(String.format("http://localhost:%d/sonarlint/api/status", port))
        .setHeader("Origin", "http://untrusted")
        .build();
      try (var responseHttp = client.execute(request)) {
        response = new Gson().fromJson(new InputStreamReader(responseHttp.getEntity().getContent()), Map.class);
      }
    }

    assertThat(response).containsOnly(
      entry("ideName", ideName),
      entry("description", ""));
  }

  @Test
  void shouldStartServerAndReplyToStatusRequestWhenNoFolderIsOpen() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";
    server.initialize(ideName, clientVersion, null);

    int port = server.getPort();
    assertThat(port).isBetween(RequestsHandlerServer.STARTING_PORT, RequestsHandlerServer.ENDING_PORT);

    Map<String, String> response;
    try (CloseableHttpClient client = HttpClients.custom().build()) {
      ClassicHttpRequest request = ClassicRequestBuilder.get()
        .setUri(String.format("http://localhost:%d/sonarlint/api/status", port))
        .setHeader("Origin", TRUSTED_SERVER_URL)
        .build();
      try (var responseHttp = client.execute(request)) {
        response = new Gson().fromJson(new InputStreamReader(responseHttp.getEntity().getContent()), Map.class);
      }
    }

    assertThat(response).containsOnly(
      entry("ideName", ideName),
      entry("description", clientVersion + " - (no open folder)"));
  }

  private void mockTrustedServer() {
    ApacheHttpClientProvider httpClientProvider = mock(ApacheHttpClientProvider.class);
    ServerConnectionSettings localhostTrustedConnection = new ServerConnectionSettings("myServer", TRUSTED_SERVER_URL, null, null, false, httpClientProvider);
    when(settingsManager.getCurrentSettings()).thenReturn(new WorkspaceSettings(true,
      Map.of("localhost", localhostTrustedConnection), null, null, null, false, false,
      null));
  }

  @Test
  void shouldStartServersOnSeparatePorts() throws Exception {
    String ideName = "SonarSource Editor";
    String clientVersion = "1.42";
    String workspaceName1 = "polop";
    String workspaceName2 = "palap";
    server.initialize(ideName, clientVersion, workspaceName1);

    RequestsHandlerServer otherServer = new RequestsHandlerServer(output, bindingManager, client, telemetry, mock(SettingsManager.class));
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

    List<RequestsHandlerServer> startedServers = new ArrayList<>();

    int serverId = 0;
    int lastPortTried = RequestsHandlerServer.STARTING_PORT;
    try {
      while (lastPortTried < RequestsHandlerServer.ENDING_PORT) {
        RequestsHandlerServer triedServer = new RequestsHandlerServer(output, bindingManager, client, telemetry, mock(SettingsManager.class));
        triedServer.initialize(ideName, clientVersion, "sample-" + serverId);
        assertThat(triedServer.isStarted()).isTrue();
        startedServers.add(triedServer);
        lastPortTried = triedServer.getPort();
      }

      RequestsHandlerServer failedServer = new RequestsHandlerServer(output, bindingManager, client, telemetry, mock(SettingsManager.class));
      failedServer.initialize(ideName, clientVersion, "sample-" + serverId);
      assertThat(failedServer.isStarted()).isFalse();
    } finally {
      for (RequestsHandlerServer serverToShutdown : startedServers) {
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
    assertThat(port).isBetween(RequestsHandlerServer.STARTING_PORT, RequestsHandlerServer.ENDING_PORT);

    String server = "http://some.sonar.server";
    String hotspot = "someHotspotKey";
    String project = "someProjectKey";
    HttpURLConnection showHotspotConnection = (HttpURLConnection) new URL(
      String.format("http://localhost:%d/sonarlint/api/hotspots/show?server=%s&hotspot=%s&project=%s", port, server, hotspot, project)).openConnection();
    try {
      showHotspotConnection.connect();
      assertThat(showHotspotConnection.getContent()).isNotNull();
    } finally {
      showHotspotConnection.disconnect();
    }

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
    assertThat(port).isBetween(RequestsHandlerServer.STARTING_PORT, RequestsHandlerServer.ENDING_PORT);

    String server = "http://some.sonar.server";
    String hotspot = "someHotspotKey";
    String project = "someProjectKey";
    HttpURLConnection showHotspotConnection = (HttpURLConnection) new URL(
      String.format("http://localhost:%d/sonarlint/api/hotspots/show?server=%s&hotspot=%s&project=%s", port, server, hotspot, project)).openConnection();
    try {
      showHotspotConnection.connect();
      assertThat(showHotspotConnection.getContent()).isNotNull();
    } finally {
      showHotspotConnection.disconnect();
    }

    verify(bindingManager).getServerConnectionSettingsForUrl(server);

    verify(client).showMessageRequest(any());
    verify(client).openConnectionSettings(false);
    verify(telemetry).showHotspotRequestReceived();
  }

  @ParameterizedTest
  @CsvSource({
    "hotspot=hotspot&project=project",
    "server=server&project=project",
    "server=server&hotspot=hotspot"
  })
  void shouldFailOnMissingParam(String queryString) throws Exception {
    var ideName = "SonarSource Editor";
    var clientVersion = "1.42";
    var workspaceName = "polop";
    server.initialize(ideName, clientVersion, workspaceName);

    int port = server.getPort();
    assertThat(port).isBetween(RequestsHandlerServer.STARTING_PORT, RequestsHandlerServer.ENDING_PORT);

    try (CloseableHttpClient client = HttpClients.custom().build()) {
      ClassicHttpRequest request = ClassicRequestBuilder.get()
        .setUri(String.format("http://localhost:%d/sonarlint/api/hotspots/show?%s", port, queryString))
        .build();
      try (var responseHttp = client.execute(request)) {
        assertThat(responseHttp.getCode()).isEqualTo(400);
      }
    }
  }
}
