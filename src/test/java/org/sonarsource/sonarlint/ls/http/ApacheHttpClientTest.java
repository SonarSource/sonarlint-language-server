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
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;

class ApacheHttpClientTest {

  private static HttpServer server;
  private static String serverBase;
  private static RecordingHandler handler;

  ApacheHttpClient underTest = ApacheHttpClient.create();

  @BeforeAll
  static void startServer() throws Exception {
    handler = new RecordingHandler();
    server = ServerBootstrap.bootstrap()
      .setLocalAddress(InetAddress.getLoopbackAddress())
      .register("/*", handler)
      .create();
    server.start();
    serverBase = String.format("http://localhost:%d", server.getLocalPort());
  }

  @AfterAll
  static void cleanupServer() {
    server.stop();
  }

  @BeforeEach
  void resetHandler() {
    handler.reset();
  }

  @Test
  void get_request_test() {
    HttpClient.Response response = underTest.get(serverBase);
    String responseString = response.bodyAsString();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(responseString).isNotEmpty();
    handler.assertRequest(Method.GET.name(), "/");
  }

  @Test
  void post_request_test() {
    HttpClient.Response response = underTest.post(serverBase, "image/jpeg", "");
    String responseString = response.bodyAsString();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(responseString).isNotEmpty();
    handler.assertRequest(Method.POST.name(), "/");
  }

  @Test
  void delete_request_test() {
    HttpClient.Response response = underTest.delete(serverBase, "image/jpeg", "");
    String responseString = response.bodyAsString();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(responseString).isNotEmpty();
    handler.assertRequest(Method.DELETE.name(), "/");
  }

  @Test
  void basic_auth_test() {
    ApacheHttpClient basicAuthClient = underTest.withToken("token");
    HttpClient.Response response = basicAuthClient.get(serverBase);
    String responseString = response.bodyAsString();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(responseString).isEqualTo("OK");
    handler.assertRequest(Method.GET.name(), "/",
      "Authorization", "Basic " + Base64.getEncoder().encodeToString("token:".getBytes(StandardCharsets.UTF_8)));
  }

  private static class RecordingHandler implements HttpRequestHandler {

    public static final String DEFAULT_RESPONSE_BODY = "OK";
    private final List<ClassicHttpRequest> requests;

    private RecordingHandler() {
      requests = new ArrayList<>();
    }

    private void reset() {
      requests.clear();
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
      requests.add(request);
      response.setCode(HttpURLConnection.HTTP_OK);
      response.setHeader("Content-Type", "text/plain");
      response.setEntity(new StringEntity(DEFAULT_RESPONSE_BODY));
    }

    private void assertRequest(String method, String path, String... headers) {
      if (headers.length % 2 != 0) {
        fail("headers should be in the form header1, expectedValue1, ... headerN, expectedValueN");
      }
      assertThat(requests).extracting(ClassicHttpRequest::getMethod, ClassicHttpRequest::getPath)
        .containsExactly(tuple(method, path));
      if (headers.length > 0) {
        Tuple[] nameValues = new Tuple[headers.length/2];
        for (int hIndex = 0; hIndex < headers.length; hIndex += 2) {
          nameValues[hIndex / 2] = tuple(headers[hIndex], headers[hIndex + 1]);
        }
        assertThat(requests.get(0).getHeaders())
          .extracting(Header::getName, Header::getValue)
          .contains(nameValues);
      }
    }
  }
}
