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
package org.sonarsource.sonarlint.ls.connected.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestsHandlerServerTests {
  SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);

  String clientVersion = "1.77.4";
  String workspaceName = "sonarlint-language-server";
  RequestsHandlerServer underTest;

  @BeforeEach
  void setup() {
    underTest = new RequestsHandlerServer(client);
  }

  @Test
  void shouldGetHostInfoWithWorkspaceName() {
    underTest.initialize(clientVersion, workspaceName);

    assertThat(underTest.getHostInfo().getDescription()).isEqualTo("1.77.4 - sonarlint-language-server");
  }

  @Test
  void shouldGetHostInfoWithoutWorkspaceName() {
    underTest.initialize(clientVersion, null);

    assertThat(underTest.getHostInfo().getDescription()).isEqualTo("1.77.4 - (no open folder)");
  }

  @Test
  void shouldDisplayNotificationAndStartCreatingConnectionWhenShowHotspotWithUnknownServer() {
    underTest.initialize(clientVersion, workspaceName);
    var serverUrl = "http://localhost:9000";
    var expectedMessage = "To display findings, you need to configure a connection to SonarQube (" + serverUrl + ") in the settings";
    var expectedType = MessageType.Error;
    var expectedActions = List.of(new MessageActionItem("Create Connection"));

    var expectedParams = new ShowMessageRequestParams();
    expectedParams.setMessage(expectedMessage);
    expectedParams.setActions(expectedActions);
    expectedParams.setType(expectedType);

    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(expectedActions.get(0)));

    underTest.showIssueOrHotspotHandleUnknownServer(serverUrl);

    verify(client).showMessageRequest(expectedParams);
    verify(client).assistCreatingConnection(any(SonarLintExtendedLanguageClient.CreateConnectionParams.class));
  }

  @Test
  void shouldDisplayNotificationWhenShowHotspotWithUnknownServer() {
    underTest.initialize(clientVersion, workspaceName);
    var serverUrl = "http://localhost:9000";
    var expectedMessage = "To display findings, you need to configure a connection to SonarQube (" + serverUrl + ") in the settings";
    var expectedType = MessageType.Error;
    var expectedActions = List.of(new MessageActionItem("Create Connection"));

    var expectedParams = new ShowMessageRequestParams();
    expectedParams.setMessage(expectedMessage);
    expectedParams.setActions(expectedActions);
    expectedParams.setType(expectedType);

    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(null));

    underTest.showIssueOrHotspotHandleUnknownServer(serverUrl);

    verify(client).showMessageRequest(expectedParams);
    verify(client, never()).assistCreatingConnection(any(SonarLintExtendedLanguageClient.CreateConnectionParams.class));
  }

  @Test
  void shouldCreateConnectionParams() {
    var serverUrl = "localhost:9000";
    var createConnectionParams = new SonarLintExtendedLanguageClient.CreateConnectionParams(false, serverUrl);

    assertThat(createConnectionParams.getServerUrl()).isEqualTo(serverUrl);
    assertThat(createConnectionParams.isSonarCloud()).isFalse();
  }

  @Test
  void shouldDisplayNotificationOnShowHotspotNoBindingCallsClientMethod() {
    underTest.initialize(clientVersion, workspaceName);
    var connectionId = "test";
    var projectKey = "sonarlint-language-server";
    var expectedMessage = "To display findings, you need to configure a project binding to '"
      + projectKey + "' on connection (" + connectionId + ")";
    var expectedType = MessageType.Error;
    var expectedActions = List.of(new MessageActionItem("Configure Binding"));
    var assistBindingParams = new AssistBindingParams(connectionId, projectKey);

    var expectedParams = new ShowMessageRequestParams();
    expectedParams.setMessage(expectedMessage);
    expectedParams.setActions(expectedActions);
    expectedParams.setType(expectedType);

    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(expectedActions.get(0)));

    underTest.showHotspotOrIssueHandleNoBinding(assistBindingParams);

    verify(client).showMessageRequest(expectedParams);
    verify(client).assistBinding(assistBindingParams);
  }

  @Test
  void shouldDisplayNotificationOnShowHotspotNoBinding() {
    underTest.initialize(clientVersion, workspaceName);
    var connectionId = "test";
    var projectKey = "sonarlint-language-server";
    var expectedMessage = "To display findings, you need to configure a project binding to '"
      + projectKey + "' on connection (" + connectionId + ")";
    var expectedType = MessageType.Error;
    var expectedActions = List.of(new MessageActionItem("Configure Binding"));
    var assistBindingParams = new AssistBindingParams(connectionId, projectKey);

    var expectedParams = new ShowMessageRequestParams();
    expectedParams.setMessage(expectedMessage);
    expectedParams.setActions(expectedActions);
    expectedParams.setType(expectedType);

    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(null));

    underTest.showHotspotOrIssueHandleNoBinding(assistBindingParams);

    verify(client).showMessageRequest(expectedParams);
    verify(client, never()).assistCreatingConnection(any(SonarLintExtendedLanguageClient.CreateConnectionParams.class));
  }
}
