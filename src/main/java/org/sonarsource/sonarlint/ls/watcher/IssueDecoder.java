package org.sonarsource.sonarlint.ls.watcher;

import com.google.gson.Gson;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;
import jakarta.websocket.EndpointConfig;

public class IssueDecoder implements Decoder.Text<IssueParams> {

  private static Gson gson = new Gson();

  @Override
  public IssueParams decode(String s) throws DecodeException {
    return gson.fromJson(s, IssueParams.class);
  }

  @Override
  public boolean willDecode(String s) {
    return (s != null);
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
