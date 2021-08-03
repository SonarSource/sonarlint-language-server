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

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.HttpFilterHandler;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

public class SecurityHotspotsHandlerServer {

  static final int STARTING_PORT = 64120;
  static final int ENDING_PORT = 64130;

  private static final int INVALID_PORT = -1;

  private final LanguageClientLogOutput output;
  private final ProjectBindingManager bindingManager;
  private final SonarLintExtendedLanguageClient client;
  private final BiFunction<EndpointParams, HttpClient, HotspotApi> hotspotApiFactory;
  private final SonarLintTelemetry telemetry;

  private HttpServer server;
  private int port;

  public SecurityHotspotsHandlerServer(LanguageClientLogOutput output, ProjectBindingManager bindingManager, SonarLintExtendedLanguageClient client,
      SonarLintTelemetry telemetry) {
    this(output, bindingManager, client, telemetry, (e, c) -> new ServerApi(e, c).hotspot());
  }

  SecurityHotspotsHandlerServer(LanguageClientLogOutput output, ProjectBindingManager bindingManager, SonarLintExtendedLanguageClient client,
    SonarLintTelemetry telemetry,
    BiFunction<EndpointParams, HttpClient, HotspotApi> hotspotApiFactory) {
    this.output = output;
    this.bindingManager = bindingManager;
    this.client = client;
    this.telemetry = telemetry;
    this.hotspotApiFactory = hotspotApiFactory;
  }

  public void initialize(String ideName, String clientVersion, @Nullable String workspaceName) {
    final SocketConfig socketConfig = SocketConfig.custom()
      .setSoTimeout(15, TimeUnit.SECONDS)
      .setTcpNoDelay(true)
      .build();
    port = INVALID_PORT;
    int triedPort = STARTING_PORT;
    HttpServer startedServer = null;
    while(port < 0 && triedPort <= ENDING_PORT) {
      try {
        startedServer = ServerBootstrap.bootstrap()
          .setLocalAddress(InetAddress.getLoopbackAddress())
          .setListenerPort(triedPort)
          .setSocketConfig(socketConfig)
          .addFilterFirst("CORS", new CorsFilter())
          .register("/sonarlint/api/status", new StatusRequestHandler(ideName, clientVersion, workspaceName))
          .register("/sonarlint/api/hotspots/show", new ShowHotspotRequestHandler(output, bindingManager, client, telemetry))
          .create();
        startedServer.start();
        port = triedPort;
      } catch (Exception t) {
        output.log("Error while starting port: " + t.getMessage(), LogOutput.Level.DEBUG);
        triedPort++;
      }
    }
    if (port > 0) {
      output.log("Started security hotspot handler on port " + port, LogOutput.Level.INFO);
      server = startedServer;
    } else {
      output.log("Unable to start security hotspot handler", LogOutput.Level.ERROR);
      server = null;
    }
  }

  @VisibleForTesting
  int getPort() {
    return port;
  }

  boolean isStarted() {
    return server != null;
  }

  public void shutdown() {
    if(isStarted()) {
      server.close(CloseMode.IMMEDIATE);
      port = INVALID_PORT;
    }
  }

  private static class StatusRequestHandler implements HttpRequestHandler {
    private final String ideName;
    private final String clientVersion;
    private final String workspaceName;

    public StatusRequestHandler(String ideName, String clientVersion, @Nullable String workspaceName) {
      this.ideName = ideName;
      this.clientVersion = clientVersion;
      this.workspaceName = workspaceName;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
      String description = clientVersion + " - " + (workspaceName == null ? "(no open folder)" : workspaceName);
      response.setEntity(new StringEntity(new Gson().toJson(new StatusResponse(ideName, description)), ContentType.APPLICATION_JSON));
    }
  }

  private static class StatusResponse {
    private final String ideName;
    private final String description;

    public StatusResponse(String ideName, String description) {
      this.ideName = ideName;
      this.description = description;
    }
  }

  private static class CorsFilter implements HttpFilterHandler {

    @Override
    public void handle(ClassicHttpRequest request, HttpFilterChain.ResponseTrigger responseTrigger, HttpContext context, HttpFilterChain chain)
      throws HttpException, IOException {
      Header origin = request.getHeader("Origin");
      chain.proceed(request, new HttpFilterChain.ResponseTrigger() {
        @Override
        public void sendInformation(ClassicHttpResponse classicHttpResponse) throws HttpException, IOException {
          responseTrigger.sendInformation(classicHttpResponse);
        }

        @Override
        public void submitResponse(ClassicHttpResponse classicHttpResponse) throws HttpException, IOException {
          if (origin != null) {
            classicHttpResponse.addHeader("Access-Control-Allow-Origin", origin.getValue());
          }
          responseTrigger.submitResponse(classicHttpResponse);
        }
      }, context);
    }
  }

  private class ShowHotspotRequestHandler implements HttpRequestHandler {
    private final LanguageClientLogOutput output;
    private final ProjectBindingManager bindingManager;
    private final SonarLintExtendedLanguageClient client;
    private final SonarLintTelemetry telemetry;

    public ShowHotspotRequestHandler(LanguageClientLogOutput output, ProjectBindingManager bindingManager, SonarLintExtendedLanguageClient client,
        SonarLintTelemetry telemetry) {
      this.output = output;
      this.bindingManager = bindingManager;
      this.client = client;
      this.telemetry = telemetry;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
      Map<String, String> params = new HashMap<>();
      try {
        params = URLEncodedUtils.parse(request.getUri(), StandardCharsets.UTF_8)
          .stream()
          .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
      } catch (URISyntaxException e) {
        // Ignored
      }
      if (!params.containsKey("server") || !params.containsKey("project") || !params.containsKey("hotspot")) {
        response.setCode(HttpURLConnection.HTTP_BAD_REQUEST);
      } else {
        String serverUrl = params.get("server");
        String project = params.get("project");
        String hotspot = params.get("hotspot");

        output.log(String.format("Opening hotspot %s for project %s of server %s", hotspot, project, serverUrl), LogOutput.Level.INFO);
        telemetry.showHotspotRequestReceived();
        Optional<ServerConnectionSettings.EndpointParamsAndHttpClient> serverSettings = bindingManager.getServerConnectionSettingsForUrl(serverUrl);
        // TODO Replace with ifPresentOrElse when we move to Java 9+
        if(serverSettings.isPresent()) {
          showHotspot(hotspot, project, serverSettings.get());
        } else {
          showUnknownServer(serverUrl);
        }
        response.setCode(HttpURLConnection.HTTP_OK);
        response.setEntity(new StringEntity("OK"));
      }
    }

    void showHotspot(String hotspotKey, String projectKey, ServerConnectionSettings.EndpointParamsAndHttpClient endpointParamsAndHttpClient) {
      HotspotApi hotspotApi = hotspotApiFactory.apply(endpointParamsAndHttpClient.getEndpointParams(), endpointParamsAndHttpClient.getHttpClient());
      hotspotApi.fetch(new GetSecurityHotspotRequestParams(hotspotKey, projectKey)).ifPresent(client::showHotspot);
    }

    void showUnknownServer(String url) {
      ShowMessageRequestParams params = new ShowMessageRequestParams();
      params.setMessage("No SonarQube connection settings found for URL " + url);
      params.setType(MessageType.Error);
      MessageActionItem showSettingsAction = new MessageActionItem("Open Settings");
      params.setActions(Collections.singletonList(showSettingsAction));
      client.showMessageRequest(params)
        .thenAccept(action -> {
          if (showSettingsAction.equals(action)) {
            client.openConnectionSettings(false);
          }
        });
    }
  }
}
