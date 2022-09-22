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

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.HashMap;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

class SubmitTokenRequestHandler implements HttpRequestHandler {
  private final LanguageClientLogger output;
  private final SonarLintExtendedLanguageClient client;

  public SubmitTokenRequestHandler(LanguageClientLogger output, SonarLintExtendedLanguageClient client) {
    this.output = output;
    this.client = client;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    var params = new HashMap<String, String>();
    var requestEntityString = EntityUtils.toString(request.getEntity(), "UTF-8");
    var requestEntityJson = JsonParser.parseString(requestEntityString).getAsJsonObject();

    requestEntityJson.keySet().forEach(k -> params.put(k, requestEntityJson.get(k).getAsString()));

    if (!params.containsKey("token")) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
    } else {
      var token = params.get("token");

      output.info("Received SonarQube token");
      client.submitToken(token);
      response.setCode(HttpStatus.SC_OK);
      response.setEntity(new StringEntity("OK"));
    }
  }
}
