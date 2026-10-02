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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationCheckStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationRecordingStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationState;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.SonarQubeCliState;

import static org.assertj.core.api.Assertions.assertThat;

class AiIntegrationStateProtocolTests {
  private final MessageJsonHandler json = new MessageJsonHandler(
    ServiceEndpoints.getSupportedMethods(SonarLintExtendedLanguageServer.class));

  @ParameterizedTest
  @EnumSource(value = CliAuthenticationStatus.class, names = {"UNAUTHENTICATED", "INVALID", "UNKNOWN"})
  void shouldSerializeCliIntegrationEvidenceWithNumericEnumsIndependentlyOfAuthentication(CliAuthenticationStatus authentication) {
    var configurations = List.of(
      new CliIntegrationConfiguration("/agent", CliIntegrationCheckStatus.CONFIGURED, CliIntegrationCheckStatus.NOT_CONFIGURED),
      new CliIntegrationConfiguration("/agent", CliIntegrationCheckStatus.INVALID, CliIntegrationCheckStatus.UNKNOWN),
      new CliIntegrationConfiguration(null, null, null));
    var response = new GetAiIntegrationStateResponse(new SonarQubeCliState(CliInstallationStatus.INSTALLED,
      authentication, null, null, null, null), List.of(), List.of(), null, List.of(
        new CliIntegrationState(AiAgent.CODEX, CliIntegrationRecordingStatus.RECORDED, configurations),
        new CliIntegrationState(AiAgent.CURSOR, CliIntegrationRecordingStatus.NOT_RECORDED, List.of()),
        new CliIntegrationState(AiAgent.CLAUDE_CODE, CliIntegrationRecordingStatus.UNKNOWN, List.of())));
    var message = new ResponseMessage();
    message.setId("1");
    message.setResult(response);

    var result = JsonParser.parseString(json.serialize(message)).getAsJsonObject().getAsJsonObject("result");
    var states = result.getAsJsonArray("cliIntegrations");

    assertNumericEnum(result.getAsJsonObject("cli"), "authenticationStatus", authentication.ordinal());
    assertThat(result.getAsJsonArray("agents")).isEmpty();
    assertThat(states.size()).isEqualTo(3);
    assertNumericEnum(states.get(0).getAsJsonObject(), "agent", 5);
    assertNumericEnum(states.get(0).getAsJsonObject(), "recordingStatus", 0);
    assertNumericEnum(states.get(1).getAsJsonObject(), "recordingStatus", 1);
    assertNumericEnum(states.get(2).getAsJsonObject(), "recordingStatus", 2);
    var rows = states.get(0).getAsJsonObject().getAsJsonArray("configurations");
    assertThat(rows.size()).isEqualTo(3);
    assertThat(rows.get(0).getAsJsonObject().get("path").getAsString()).isEqualTo("/agent");
    assertThat(rows.get(1).getAsJsonObject().get("path").getAsString()).isEqualTo("/agent");
    assertNumericEnum(rows.get(0).getAsJsonObject(), "mcp", 0);
    assertNumericEnum(rows.get(0).getAsJsonObject(), "hooks", 1);
    assertNumericEnum(rows.get(1).getAsJsonObject(), "mcp", 2);
    assertNumericEnum(rows.get(1).getAsJsonObject(), "hooks", 3);
    assertThat(rows.get(2).getAsJsonObject().get("path")).isNull();
    assertThat(rows.get(2).getAsJsonObject().get("mcp")).isNull();
    assertThat(rows.get(2).getAsJsonObject().get("hooks")).isNull();
  }

  private static void assertNumericEnum(JsonObject object, String property, int expected) {
    var value = object.getAsJsonPrimitive(property);
    assertThat(value.isNumber()).isTrue();
    assertThat(value.getAsInt()).isEqualTo(expected);
  }
}
