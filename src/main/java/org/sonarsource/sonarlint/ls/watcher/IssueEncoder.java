package org.sonarsource.sonarlint.ls.watcher;

import com.google.gson.Gson;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;

public class IssueEncoder implements Encoder.Text<IssueParams> {

  private static Gson gson = new Gson();

  @Override
  public String encode(IssueParams message) throws EncodeException {
    return gson.toJson(message);
  }

  @Override
  public void init(EndpointConfig endpointConfig) {
    // Custom initialization logic
  }

  @Override
  public void destroy() {
    // Close resources
  }

}
