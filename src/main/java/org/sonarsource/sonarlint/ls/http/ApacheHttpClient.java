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
package org.sonarsource.sonarlint.ls.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.http.HttpConnectionListener;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class ApacheHttpClient implements HttpClient {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static final Timeout CONNECTION_TIMEOUT = Timeout.ofSeconds(30);
  private static final Timeout RESPONSE_TIMEOUT = Timeout.ofMinutes(10);
  private static final Timeout SOCKET_OPTIONS_TIMEOUT = Timeout.ofMinutes(1);

  private CloseableHttpAsyncClient client;
  @CheckForNull
  private final String token;
  private String userAgent;
  private final Function<String, CloseableHttpAsyncClient> httpClientFactory;

  ApacheHttpClient(@Nullable String token) {
    this.token = token;
    this.httpClientFactory = ua -> HttpAsyncClients.custom()
      .useSystemProperties()
      .setUserAgent(ua)
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
  }

  public ApacheHttpClient withToken(String token) {
    return new ApacheHttpClient(token);
  }

  @Override
  public Response get(String url) {
    return executeSync(SimpleRequestBuilder.get(url));
  }

  @Override
  public CompletableFuture<Response> getAsync(String url) {
    return executeAsync(SimpleRequestBuilder.get(url));
  }

  @Override
  public Response post(String url, String contentType, String body) {
    var httpPost = SimpleRequestBuilder.post(url);
    httpPost.setBody(body, ContentType.parse(contentType));
    return executeSync(httpPost);
  }

  @Override
  public Response delete(String url, String contentType, String body) {
    var httpDelete = SimpleRequestBuilder.delete(url);
    httpDelete.setBody(body, ContentType.parse(contentType));
    return executeSync(httpDelete);
  }

  private Response executeSync(SimpleRequestBuilder httpRequest) {
    try {
      return executeAsync(httpRequest).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted!", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e.getMessage(), e.getCause());
    }
  }

  private CompletableFuture<Response> executeAsync(SimpleRequestBuilder httpRequest) {
    if (token != null) {
      httpRequest.setHeader(HttpHeaders.AUTHORIZATION, basic(token, ""));
    }
    if (client == null) {
      client = httpClientFactory.apply(userAgent);
    }
    var futureWrapper = new CompletableFutureWrapper(httpRequest);
    futureWrapper.wrapped = client.execute(httpRequest.build(), futureWrapper);
    return futureWrapper;
  }

  private static final class CompletableFutureWrapper extends CompletableFuture<Response> implements FutureCallback<SimpleHttpResponse> {

    private Future<SimpleHttpResponse> wrapped;
    private final SimpleRequestBuilder httpRequest;

    CompletableFutureWrapper(SimpleRequestBuilder httpRequest) {
      this.httpRequest = httpRequest;
    }

    @Override
    public void completed(SimpleHttpResponse result) {
      this.complete(new ApacheHttpResponse(httpRequest.getUri().toString(), result));
    }

    @Override
    public void failed(Exception ex) {
      this.completeExceptionally(ex);
    }

    @Override
    public void cancelled() {
      this.completeExceptionally(new CancellationException());
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (wrapped != null) {
        return wrapped.cancel(mayInterruptIfRunning);
      }
      return super.cancel(mayInterruptIfRunning);
    }
  }

  private static String basic(String username, String password) {
    var usernameAndPassword = username + ":" + password;
    var encoded = Base64.getEncoder().encodeToString(usernameAndPassword.getBytes(StandardCharsets.ISO_8859_1));
    return "Basic " + encoded;
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

  public static ApacheHttpClient create() {
    return new ApacheHttpClient(null);
  }

  public void initialize(String userAgent) {
    this.userAgent = userAgent;
  }

  @Override
  public AsyncRequest getEventStream(String url, HttpConnectionListener connectionListener, Consumer<String> messageConsumer) {
    throw new UnsupportedOperationException("Event streaming not supported in SonarLint LS");
  }

}
