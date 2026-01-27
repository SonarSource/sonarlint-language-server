/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package testutils;

import com.google.protobuf.Message;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assertions.fail;

public class MockWebServerExtension implements BeforeAllCallback, AfterAllCallback, AfterEachCallback {

  private MockWebServer server;
  private MockWebServerDispatcher dispatcher;
  private final Integer port;

  private MockWebServerExtension(Integer port) {
    this.port = port;
  }

  public static MockWebServerExtension onPort(int port) {
    return new MockWebServerExtension(port);
  }

  public static MockWebServerExtension onRandomPort() {
    return new MockWebServerExtension(null);
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    this.dispatcher = new MockWebServerDispatcher();
    this.server = new MockWebServer();
    this.server.setDispatcher(this.dispatcher);
    if (this.port != null) {
      server.start(this.port);
    } else {
      server.start();
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    dispatcher.clear();
  }

  @Override
  public void afterAll(ExtensionContext context) throws IOException {
    if (server != null) {
      server.shutdown();
    }
  }

  public void addStringResponse(String path, String body) {
    dispatcher.mockResponse(path, new MockResponse().setBody(body));
  }

  public void addResponse(String path, MockResponse response) {
    dispatcher.mockResponse(path, response);
  }

  public String url(String path) {
    return server.url(path).toString();
  }

  public void addProtobufResponse(String path, Message m) {
    try {
      var b = new Buffer();
      m.writeTo(b.outputStream());
      dispatcher.mockResponse(path, new MockResponse().setBody(b));
    } catch (IOException e) {
      fail(e);
    }
  }

  public void addProtobufResponseDelimited(String path, Message... m) {
    var b = new Buffer();
    writeMessages(b.outputStream(), Arrays.asList(m).iterator());
    dispatcher.mockResponse(path, new MockResponse().setBody(b));
  }

  public static <T extends Message> void writeMessages(OutputStream output, Iterator<T> messages) {
    while (messages.hasNext()) {
      writeMessage(output, messages.next());
    }
  }

  public static <T extends Message> void writeMessage(OutputStream output, T message) {
    try {
      message.writeDelimitedTo(output);
    } catch (IOException e) {
      throw new IllegalStateException("failed to write message: " + message, e);
    }
  }

  private static class MockWebServerDispatcher extends Dispatcher {

    protected final Map<String, MockResponse> responsesByPath = new ConcurrentHashMap<>();

    @NotNull
    @Override
    public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
      var requestPath = recordedRequest.getPath();

      // Try exact match first
      if (responsesByPath.containsKey(requestPath)) {
        return responsesByPath.get(requestPath);
      }

      // Try matching ignoring timestamp parameters (changedSince values vary at runtime)
      if (requestPath != null) {
        var normalizedRequestPath = removeTimestampParameters(requestPath);
        for (var entry : responsesByPath.entrySet()) {
          var mockPath = entry.getKey();
          if (mockPath != null && normalizedRequestPath.equals(removeTimestampParameters(mockPath))) {
            return entry.getValue();
          }
        }
      }

      return new MockResponse().setResponseCode(404);
    }

    public void clear() {
      responsesByPath.clear();
    }

    private void mockResponse(String path, MockResponse response) {
      responsesByPath.put(path, response);
    }

    /**
     * Removes timestamp-based query parameters (like changedSince) from a URL path.
     * This allows mock responses to match requests regardless of the actual timestamp value,
     * which can vary between test runs.
     */
    private static String removeTimestampParameters(String path) {
      return path.replaceFirst("[&?]changedSince=\\d+", "");
    }
  }
}
