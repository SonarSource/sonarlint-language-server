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
package org.sonarsource.sonarlint.ls.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class ApacheHttpClient implements org.sonarsource.sonarlint.core.serverapi.HttpClient {

  private static final Logger LOG = Loggers.get(ApacheHttpClient.class);

  public static final Timeout CONNECTION_TIMEOUT = Timeout.ofSeconds(30);
  private static final Timeout RESPONSE_TIMEOUT = Timeout.ofMinutes(10);
  private static final String USER_AGENT = "SonarLint VSCode";

  private final CloseableHttpClient client;
  @CheckForNull
  private final String login;
  @CheckForNull
  private final String password;

  private ApacheHttpClient(CloseableHttpClient client, @Nullable String login, @Nullable String password) {
    this.client = client;
    this.login = login;
    this.password = password;
  }

  public ApacheHttpClient withToken(String token) {
    return new ApacheHttpClient(client, token, null);
  }

  @Override
  public Response get(String s) {
    return execute(new HttpGet(s));
  }

  @Override
  public Response post(String url, String contentType, String body) {
    HttpPost httpPost = new HttpPost(url);
    httpPost.setEntity(new StringEntity(body, ContentType.parse(contentType)));
    return execute(httpPost);
  }

  @Override
  public Response delete(String url, String contentType, String body) {
    HttpDelete httpDelete = new HttpDelete(url);
    httpDelete.setEntity(new StringEntity(body, ContentType.parse(contentType)));
    return execute(httpDelete);
  }

  private Response execute(HttpUriRequestBase httpRequest) {
    try {
      if (login != null) {
        httpRequest.setHeader("Authorization", basic(login, password == null ? "" : password));
      }
      CloseableHttpResponse httpResponse = client.execute(httpRequest);
      return new ApacheHttpResponse(httpRequest.getRequestUri(), httpResponse);
    } catch (IOException e) {
      throw new IllegalStateException("Error processing HTTP request", e);
    }
  }

  private static String basic(String username, String password) {
    String usernameAndPassword = username + ":" + password;
    String encoded = Base64.getEncoder().encodeToString(usernameAndPassword.getBytes(StandardCharsets.ISO_8859_1));
    return "Basic " + encoded;
  }

  public void close() {
    try {
      client.close();
    } catch (IOException e) {
      LOG.error("Unable to close http client: ", e.getMessage());
    }
  }

  public static ApacheHttpClient create() {
    CloseableHttpClient httpClient = HttpClients.custom()
      .useSystemProperties()
      .setUserAgent(USER_AGENT)
      .setDefaultRequestConfig(
        RequestConfig.copy(RequestConfig.DEFAULT)
          .setConnectionRequestTimeout(CONNECTION_TIMEOUT)
          .setResponseTimeout(RESPONSE_TIMEOUT)
          .build()
      )
      .build();
    return new ApacheHttpClient(httpClient, null, null);
  }

}
