/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
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
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class ApacheHttpClientProvider {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final Timeout CONNECTION_TIMEOUT = Timeout.ofSeconds(30);
  private static final Timeout RESPONSE_TIMEOUT = Timeout.ofMinutes(10);
  private static final Timeout SOCKET_OPTIONS_TIMEOUT = Timeout.ofMinutes(1);

  private CloseableHttpAsyncClient client;

  public ApacheHttpClient withToken(String token) {
    return new ApacheHttpClient(token, client);
  }

  public ApacheHttpClient anonymous() {
    return new ApacheHttpClient(null, client);
  }

  public void initialize(String productName, String productVersion) {
    this.client = HttpAsyncClients.custom()
      .useSystemProperties()
      .setUserAgent(productName + " " + productVersion)
      .setIOReactorConfig(
        IOReactorConfig.custom()
          .setSoTimeout(SOCKET_OPTIONS_TIMEOUT)
          .build())
      .setDefaultRequestConfig(
        RequestConfig.copy(RequestConfig.DEFAULT)
          .setConnectTimeout(CONNECTION_TIMEOUT)
          .setConnectionRequestTimeout(CONNECTION_TIMEOUT)
          .setResponseTimeout(RESPONSE_TIMEOUT)
          .build())
      .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
      .build();
    this.client.start();
  }

  public void close() {
    try {
      if (client != null) {
        client.close();
      }
    } catch (IOException e) {
      LOG.error("Unable to close http client: ", e.getMessage());
    }
  }

}
