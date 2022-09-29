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

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.io.CloseMode;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

public class RequestsHandlerServer {
  static final int STARTING_PORT = 64120;
  static final int ENDING_PORT = 64130;

  private static final int INVALID_PORT = -1;

  private final LanguageClientLogger output;
  private final ProjectBindingManager bindingManager;
  private final SonarLintExtendedLanguageClient client;
  private final BiFunction<EndpointParams, HttpClient, HotspotApi> hotspotApiFactory;
  private final SonarLintTelemetry telemetry;
  private final SettingsManager settingsManager;

  private HttpServer server;
  private int port;

  public RequestsHandlerServer(LanguageClientLogger output, ProjectBindingManager bindingManager, SonarLintExtendedLanguageClient client,
    SonarLintTelemetry telemetry, SettingsManager settingsManager) {
    this(output, bindingManager, client, telemetry, (e, c) -> new ServerApi(e, c).hotspot(), settingsManager);
  }

  public RequestsHandlerServer(LanguageClientLogger output, ProjectBindingManager bindingManager, SonarLintExtendedLanguageClient client,
    SonarLintTelemetry telemetry,
    BiFunction<EndpointParams, HttpClient, HotspotApi> hotspotApiFactory, SettingsManager settingsManager) {
    this.output = output;
    this.bindingManager = bindingManager;
    this.client = client;
    this.telemetry = telemetry;
    this.hotspotApiFactory = hotspotApiFactory;
    this.settingsManager = settingsManager;
  }

  public void initialize(String ideName, String clientVersion, @Nullable String workspaceName) {
    final var socketConfig = SocketConfig.custom()
      .setSoTimeout(15, TimeUnit.SECONDS)
      .setTcpNoDelay(true)
      .build();
    port = INVALID_PORT;
    var triedPort = STARTING_PORT;
    HttpServer startedServer = null;
    while (port < 0 && triedPort <= ENDING_PORT) {
      try {
        startedServer = ServerBootstrap.bootstrap()
          .setLocalAddress(InetAddress.getLoopbackAddress())
          .setListenerPort(triedPort)
          .setSocketConfig(socketConfig)
          .addFilterFirst("CORS", new CorsFilter())
          .register("/sonarlint/api/status", new StatusRequestHandler(this, ideName, clientVersion, workspaceName))
          .register("/sonarlint/api/hotspots/show", new ShowHotspotRequestHandler(output, bindingManager, client, telemetry, hotspotApiFactory))
          .register("/sonarlint/api/submit-token", new SubmitTokenRequestHandler(output, client))
          .create();
        startedServer.start();
        port = triedPort;
      } catch (Exception t) {
        output.debug("Error while starting port: " + t.getMessage());
        triedPort++;
      }
    }
    if (port > 0) {
      output.info("Started request handler on port " + port);
      server = startedServer;
    } else {
      output.error("Unable to start request handler");
      server = null;
    }
  }

  public int getPort() {
    return port;
  }

  boolean isStarted() {
    return server != null;
  }

  public void shutdown() {
    if (isStarted()) {
      server.close(CloseMode.GRACEFUL);
      port = INVALID_PORT;
    }
  }

  public boolean isTrustedServer(String serverOrigin) {
    // A server is trusted if the Origin HTTP header matches one of the already configured servers
    // The Origin header has the following format: <scheme>://<host>(:<port>)
    // Since servers can have an optional "context path" after this, we consider a valid match when the server's configured URL begins with
    // the passed Origin
    // See https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Origin
    return settingsManager.getCurrentSettings().getServerConnections().values().stream().anyMatch(s -> s.getServerUrl().startsWith(serverOrigin));
  }
}
