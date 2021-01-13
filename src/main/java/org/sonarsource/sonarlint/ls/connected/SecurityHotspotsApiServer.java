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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;

public class SecurityHotspotsApiServer {

  static final int STARTING_PORT = 64120;
  static final int ENDING_PORT = 64130;

  private static final int INVALID_PORT = -1;

  private final LanguageClientLogOutput output;

  private HttpServer server;
  private int port;

  public SecurityHotspotsApiServer(LanguageClientLogOutput output) {
    this.output = output;
  }

  public void init(String ideName, String clientVersion, String workspaceName) {
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
          .setListenerPort(triedPort)
          .setSocketConfig(socketConfig)
          .addFilterFirst("CORS", new CorsFilter())
          .register("/sonarlint/api/status", new StatusRequestHandler(ideName, clientVersion, workspaceName))
          .register("/sonarlint/api/hotspots/show", new ShowHotspotRequestHandler(output))
          .create();
        startedServer.start();
        port = triedPort;
      } catch (Exception t) {
        triedPort++;
      }
    }
    if (startedServer != null) {
      output.log("Started hotspots server on port " + port, LogOutput.Level.INFO);
      server = startedServer;
    } else {
      output.log("Unable to start hotspots server", LogOutput.Level.ERROR);
      server = null;
    }
  }

  @VisibleForTesting
  int getPort() {
    return port;
  }

  public void shutdown() {
    if(server != null) {
      server.close(CloseMode.IMMEDIATE);
      port = INVALID_PORT;
    }
  }

  private static class StatusRequestHandler implements HttpRequestHandler {
    private final String ideName;
    private final String clientVersion;
    private final String workspaceName;

    public StatusRequestHandler(String ideName, String clientVersion, String workspaceName) {
      this.ideName = ideName;
      this.clientVersion = clientVersion;
      this.workspaceName = workspaceName;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
      response.setEntity(new StringEntity(new Gson().toJson(new StatusResponse(ideName, clientVersion + " - " + workspaceName)), ContentType.APPLICATION_JSON));
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

  private static class ShowHotspotRequestHandler implements HttpRequestHandler {
    private final LanguageClientLogOutput output;

    public ShowHotspotRequestHandler(LanguageClientLogOutput output) {
      this.output = output;
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
        String serverKey = params.get("server");
        String project = params.get("project");
        String hotspot = params.get("hotspot");
        output.log(String.format("Opening hotspot %s for project %s of server %s", hotspot, project, serverKey), LogOutput.Level.INFO);
        response.setCode(HttpURLConnection.HTTP_OK);
        response.setEntity(new StringEntity("OK"));
      }
    }
  }
}
