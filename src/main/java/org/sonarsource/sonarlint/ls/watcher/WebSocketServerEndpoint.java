package org.sonarsource.sonarlint.ls.watcher;

import jakarta.websocket.EncodeException;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;

@ServerEndpoint(value="/issues",
  decoders = IssueDecoder.class,
  encoders = IssueEncoder.class)
public class WebSocketServerEndpoint {

  private Session session;

  @OnOpen
  public void onOpen(Session session) throws IOException {
    this.session = session;
    broadcast("Opened!");
  }

  @OnMessage
  public void onMessage(Session session, String issue) throws IOException {
    broadcast(issue);
  }

  @OnClose
  public void onClose(Session session) throws IOException {
    broadcast("Disconnected!");
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    broadcast("Error!");
  }

  private void broadcast(String issue) {
    try {
      session.getBasicRemote().sendObject(issue);
    } catch (IOException | EncodeException e) {
      e.printStackTrace();
    }
  }

}
