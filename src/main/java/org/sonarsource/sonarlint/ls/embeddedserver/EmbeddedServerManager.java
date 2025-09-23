/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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
package org.sonarsource.sonarlint.ls.embeddedserver;

import org.sonarsource.sonarlint.core.rpc.protocol.client.embeddedserver.EmbeddedServerStartedParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

public class EmbeddedServerManager {

  private final SonarLintExtendedLanguageClient client;

  private boolean initialized;
  private Integer port;

  public EmbeddedServerManager(SonarLintExtendedLanguageClient client) {
    this.client = client;
  }

  public void embeddedServerStarted(int port) {
    this.port = port;
    if (initialized) {
      sendEmbeddedServerStartedToClient();
    }
  }

  public void initialized() {
    this.initialized = true;
    if (port != null) {
      sendEmbeddedServerStartedToClient();
    }
  }

  private void sendEmbeddedServerStartedToClient() {
    client.embeddedServerStarted(new EmbeddedServerStartedParams(port));
  }
}
