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
import com.google.gson.annotations.Expose;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;

class StatusRequestHandler implements HttpRequestHandler {
  private final String ideName;
  private final String clientVersion;
  private final String workspaceName;
  private final RequestsHandlerServer requestsHandlerServer;

  public StatusRequestHandler(RequestsHandlerServer requestsHandlerServer, String ideName, String clientVersion, @Nullable String workspaceName) {
    this.requestsHandlerServer = requestsHandlerServer;
    this.ideName = ideName;
    this.clientVersion = clientVersion;
    this.workspaceName = workspaceName == null ? "(no open folder)" : workspaceName;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    boolean trustedServer = Optional.ofNullable(request.getHeader("Origin"))
      .map(Header::getValue)
      .map(requestsHandlerServer::isTrustedServer)
      .orElse(false);
    var maybeWorkspaceInfo = trustedServer ? (clientVersion + " - " + workspaceName) : "";
    response.setEntity(new StringEntity(new Gson().toJson(new StatusResponse(ideName, maybeWorkspaceInfo)), ContentType.APPLICATION_JSON));
  }

  private static class StatusResponse {
    @Expose
    private final String ideName;
    @Expose
    private final String description;

    public StatusResponse(String ideName, String description) {
      this.ideName = ideName;
      this.description = description;
    }
  }
}


