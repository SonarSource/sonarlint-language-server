/*
 * SonarLint Language Server
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.ls;

import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse.Status;

import static org.assertj.core.api.Assertions.assertThat;

class AiAuthenticationProtocolTests {
  private final MessageJsonHandler json = new MessageJsonHandler(
    ServiceEndpoints.getSupportedMethods(SonarLintExtendedLanguageServer.class));

  @Test
  void readsAuthenticationRequestWithOnlyConnectionId() {
    var request = (RequestMessage) json.parseMessage("""
      {"jsonrpc":"2.0","id":1,"method":"sonarlint/authenticateCliWithConnection","params":{"connectionId":"connection"}}
      """);

    assertThat(request.getMethod()).isEqualTo("sonarlint/authenticateCliWithConnection");
    var params = (AuthenticateCliWithConnectionParams) request.getParams();
    assertThat(params.getConnectionId()).isEqualTo("connection");
    assertThat(json.getGson().toJson(params)).isEqualTo("{\"connectionId\":\"connection\"}");
  }

  @ParameterizedTest
  @CsvSource({"AUTHENTICATED,0", "INTERACTIVE_LOGIN_REQUIRED,1", "UPGRADE_REQUIRED,2", "FAILED,3"})
  void serializesAuthenticationStatusAndDiagnostic(Status status, int serializedStatus) {
    var response = new AuthenticateCliWithConnectionResponse(status, "diagnostic");

    var result = json.getGson().toJsonTree(response).getAsJsonObject();

    assertThat(result.keySet()).containsExactlyInAnyOrder("status", "message");
    assertThat(result.get("status").getAsInt()).isEqualTo(serializedStatus);
    assertThat(result.get("message").getAsString()).isEqualTo("diagnostic");
  }
}
