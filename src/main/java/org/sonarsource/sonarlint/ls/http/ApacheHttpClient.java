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
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.async.methods.AbstractCharResponseConsumer;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.util.Timeout;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.http.HttpConnectionListener;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class ApacheHttpClient implements HttpClient {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final CloseableHttpAsyncClient client;
  @CheckForNull
  private final String token;

  ApacheHttpClient(@Nullable String token, CloseableHttpAsyncClient client) {
    this.token = token;
    this.client = client;
  }

  @Override
  public Response get(String url) {
    return executeSync(SimpleRequestBuilder.get(url));
  }

  @Override
  public CompletableFuture<Response> getAsync(String url) {
    return executeAsync(SimpleRequestBuilder.get(url));
  }

  private static final Timeout STREAM_CONNECTION_REQUEST_TIMEOUT = Timeout.ofSeconds(10);
  private static final Timeout STREAM_CONNECTION_TIMEOUT = Timeout.ofMinutes(1);

  @Override
  public AsyncRequest getEventStream(String url, HttpConnectionListener connectionListener, Consumer<String> messageConsumer) {
    var request = SimpleRequestBuilder.get(url)
      .setRequestConfig(RequestConfig.custom()
        .setConnectionRequestTimeout(STREAM_CONNECTION_REQUEST_TIMEOUT)
        .setConnectTimeout(STREAM_CONNECTION_TIMEOUT)
        // infinite timeout, rely on heart beat check
        .setResponseTimeout(Timeout.ZERO_MILLISECONDS)
        .build())
      .build();
    if (token != null) {
      request.setHeader("Authorization", basic(token));
    }
    request.setHeader("Accept", "text/event-stream");
    var status = new EventStreamStatus();
    var httpFuture = client.execute(
      new BasicRequestProducer(request, null),
      new AbstractCharResponseConsumer<Void>() {
        @Override
        public void releaseResources() {
          // nothing to do
        }

        @Override
        protected int capacityIncrement() {
          return Integer.MAX_VALUE;
        }

        @Override
        protected void data(CharBuffer charBuffer, boolean b) {
          if (status.connected) {
            messageConsumer.accept(charBuffer.toString());
          }
        }

        @Override
        protected void start(HttpResponse httpResponse, ContentType contentType) {
          var responseCode = httpResponse.getCode();
          if (responseCode < 200 || responseCode >= 300) {
            connectionListener.onError(responseCode);
          } else {
            status.markConnected();
            connectionListener.onConnected();
          }
        }

        @Override
        public void failed(Exception cause) {
          // log: might be internal error (e.g. in event handling) or disconnection from server
          // notification of listener will happen in the FutureCallback
        }

        @Override
        protected Void buildResult() {
          return null;
        }
      }, new FutureCallback<>() {
        @Override
        public void completed(Void unused) {
          if (status.connected) {
            connectionListener.onClosed();
          }
        }

        @Override
        public void failed(Exception e) {
          if (status.connected) {
            // called when disconnected from server
            connectionListener.onClosed();
          } else {
            connectionListener.onError(null);
          }
        }

        @Override
        public void cancelled() {
          // nothing to do, the completable future is already canceled
        }
      }
    );
    return new ApacheAsyncRequest(httpFuture);
  }

  private static class EventStreamStatus {
    private boolean connected;

    private void markConnected() {
      connected = true;
    }
  }

  static class ApacheAsyncRequest implements HttpClient.AsyncRequest {
    final Future<?> httpFuture;

    private ApacheAsyncRequest(Future<?> httpFuture) {
      this.httpFuture = httpFuture;
    }

    @Override
    public void cancel() {
      httpFuture.cancel(true);
    }
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
      httpRequest.setHeader(HttpHeaders.AUTHORIZATION, basic(token));
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

  private static String basic(String token) {
    var credentials = token + ":";
    var encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.ISO_8859_1));
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

}
