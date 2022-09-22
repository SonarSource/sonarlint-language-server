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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.hotspot.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

class ShowHotspotRequestHandler implements HttpRequestHandler {
  private final LanguageClientLogger output;
  private final ProjectBindingManager bindingManager;
  private final SonarLintExtendedLanguageClient client;
  private final SonarLintTelemetry telemetry;
  private final BiFunction<EndpointParams, HttpClient, HotspotApi> hotspotApiFactory;

  public ShowHotspotRequestHandler(LanguageClientLogger output, ProjectBindingManager bindingManager, SonarLintExtendedLanguageClient client,
    SonarLintTelemetry telemetry, BiFunction<EndpointParams, HttpClient, HotspotApi> hotspotApiFactory) {
    this.output = output;
    this.bindingManager = bindingManager;
    this.client = client;
    this.telemetry = telemetry;
    this.hotspotApiFactory = hotspotApiFactory;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    var params = new HashMap<String, String>();
    try {
      new URIBuilder(request.getUri(), StandardCharsets.UTF_8)
        .getQueryParams()
        .forEach(p -> params.put(p.getName(), p.getValue()));
    } catch (URISyntaxException e) {
      // Ignored
    }
    if (!params.containsKey("server") || !params.containsKey("project") || !params.containsKey("hotspot")) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
    } else {
      var serverUrl = params.get("server");
      var project = params.get("project");
      var hotspot = params.get("hotspot");

      output.info(String.format("Opening hotspot %s for project %s of server %s", hotspot, project, serverUrl));
      telemetry.showHotspotRequestReceived();
      var serverSettings = bindingManager.getServerConnectionSettingsForUrl(serverUrl);
      serverSettings.ifPresentOrElse(
        settings -> showHotspot(hotspot, project, settings),
        () -> showUnknownServer(serverUrl));
      response.setCode(HttpStatus.SC_OK);
      response.setEntity(new StringEntity("OK"));
    }
  }

  void showHotspot(String hotspotKey, String projectKey, ServerConnectionSettings.EndpointParamsAndHttpClient endpointParamsAndHttpClient) {
    var hotspotApi = hotspotApiFactory.apply(endpointParamsAndHttpClient.getEndpointParams(), endpointParamsAndHttpClient.getHttpClient());
    hotspotApi.fetch(new GetSecurityHotspotRequestParams(hotspotKey, projectKey)).ifPresent(client::showHotspot);
  }

  void showUnknownServer(String url) {
    var params = new ShowMessageRequestParams();
    params.setMessage("To display Security Hotspots, you need to configure a connection to SonarQube (" + url + ") in the settings");
    params.setType(MessageType.Error);
    var showSettingsAction = new MessageActionItem("Open Settings");
    params.setActions(List.of(showSettingsAction));
    client.showMessageRequest(params)
      .thenAccept(action -> {
        if (showSettingsAction.equals(action)) {
          client.openConnectionSettings(false);
        }
      });
  }
}
