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
package org.sonarsource.sonarlint.ls.watcher;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import java.net.URI;
import java.nio.ByteBuffer;

@ClientEndpoint
public class WebsocketClientEndpoint {

  Session userSession = null;
  private MessageHandler messageHandler;

  public WebsocketClientEndpoint(URI endpointURI) {
    try {
      WebSocketContainer container = ContainerProvider.getWebSocketContainer();
      container.connectToServer(this, endpointURI);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Callback hook for Connection open events.
   *
   * @param userSession the userSession which is opened.
   */
  @OnOpen
  public void onOpen(Session userSession) {
    System.out.println("opening websocket");
    this.userSession = userSession;
  }

  /**
   * Callback hook for Connection close events.
   *
   * @param userSession the userSession which is getting closed.
   * @param reason the reason for connection close
   */
  @OnClose
  public void onClose(Session userSession, CloseReason reason) {
    System.out.println("closing websocket");
    this.userSession = null;
  }

  /**
   * Callback hook for Message Events. This method will be invoked when a client send a message.
   *
   * @param message The text message
   */
  @OnMessage
  public void onMessage(String message) {
    if (this.messageHandler != null) {
      this.messageHandler.handleMessage(message);
    }
  }

  @OnMessage
  public void onMessage(ByteBuffer bytes) {
    System.out.println("Handle byte buffer");
  }

  /**
   * register message handler
   *
   * @param msgHandler
   */
  public void addMessageHandler(MessageHandler msgHandler) {
    this.messageHandler = msgHandler;
  }

  /**
   * Send a message.
   *
   * @param message
   */
  public void sendMessage(String message) {
    System.out.println("supermessage");
    this.userSession.getAsyncRemote().sendText(message);
  }

  /**
   * Message handler.
   *
   * @author Jiji_Sasidharan
   */
  public static interface MessageHandler {

    public void handleMessage(String message);
  }

}
