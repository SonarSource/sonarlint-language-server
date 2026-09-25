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
package org.sonarsource.sonarlint.ls.telemetry;

import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiAgentIntegrationStateObservedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationCliStateObservedParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;

import static org.assertj.core.api.Assertions.assertThat;

class AiIntegrationTelemetryProtocolTests {
  private final MessageJsonHandler json = new MessageJsonHandler(
    ServiceEndpoints.getSupportedMethods(SonarLintExtendedLanguageServer.class));

  @Test
  void readsActionNotificationWithEnumNames() {
    var message = parse("""
      {"jsonrpc":"2.0","method":"sonarlint/aiIntegrationAction","params":{
        "action":"INSTALL_CLI","status":"FAILED","agent":"CODEX","host":"VSCODE"
      }}
      """);

    assertThat(message.getMethod()).isEqualTo("sonarlint/aiIntegrationAction");
    var params = (AiIntegrationActionParams) message.getParams();
    assertThat(params.getAction()).isEqualTo(AiIntegrationAction.INSTALL_CLI);
    assertThat(params.getStatus()).isEqualTo(AiIntegrationActionStatus.FAILED);
    assertThat(params.getAgent()).isEqualTo(AiAgent.CODEX);
    assertThat(params.getHost()).isEqualTo(AiIntegrationHost.VSCODE);
  }

  @Test
  void readsCliObservation() {
    var message = parse("""
      {"jsonrpc":"2.0","method":"sonarlint/aiIntegrationCliStateObserved","params":{
        "installationStatus":"INSTALLED","authenticationStatus":"AUTHENTICATED","host":"VSCODE"
      }}
      """);

    var params = (AiIntegrationCliStateObservedParams) message.getParams();
    assertThat(params.getInstallationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    assertThat(params.getAuthenticationStatus()).isEqualTo(CliAuthenticationStatus.AUTHENTICATED);
    assertThat(params.getHost()).isEqualTo(AiIntegrationHost.VSCODE);
  }

  @Test
  void readsAgentObservationWithBothDetectionSources() {
    var message = parse("""
      {"jsonrpc":"2.0","method":"sonarlint/aiAgentIntegrationStateObserved","params":{
        "agent":"GITHUB_COPILOT","detectionSources":["IDE","CLI"],
        "standaloneMcpState":"UNKNOWN","host":"VSCODE"
      }}
      """);

    var params = (AiAgentIntegrationStateObservedParams) message.getParams();
    assertThat(params.getAgent()).isEqualTo(AiAgent.GITHUB_COPILOT);
    assertThat(params.getDetectionSources()).containsExactly(AiAgentDetectionSource.IDE, AiAgentDetectionSource.CLI);
    assertThat(params.getStandaloneMcpState()).isEqualTo(McpConfigurationState.UNKNOWN);
    assertThat(params.getHost()).isEqualTo(AiIntegrationHost.VSCODE);
  }

  private NotificationMessage parse(String text) {
    return (NotificationMessage) json.parseMessage(text);
  }
}
