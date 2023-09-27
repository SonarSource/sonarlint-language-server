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
import javax.annotation.Nullable;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.info.GetClientInfoResponse;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

public class RequestsHandlerServer {
  private final SonarLintExtendedLanguageClient client;
  private String clientVersion;
  private String workspaceName;

  public RequestsHandlerServer(SonarLintExtendedLanguageClient client) {
    this.client = client;
  }

  public void initialize(String clientVersion, @Nullable String workspaceName) {
    this.clientVersion = clientVersion;
    this.workspaceName = workspaceName == null ? "(no open folder)" : workspaceName;
  }

  public GetClientInfoResponse getHostInfo() {
    return new GetClientInfoResponse(this.clientVersion + " - " + this.workspaceName);
  }

  public void showIssueOrHotspotHandleUnknownServer(String url) {
    var params = new ShowMessageRequestParams();
    params.setMessage("To display findings, you need to configure a connection to SonarQube (" + url + ") in the settings");
    params.setType(MessageType.Error);
    var createConnectionAction = new MessageActionItem("Create Connection");
    params.setActions(List.of(createConnectionAction));
    client.showMessageRequest(params)
      .thenAccept(action -> {
        if (createConnectionAction.equals(action)) {
          client.assistCreatingConnection(new SonarLintExtendedLanguageClient.CreateConnectionParams(false, url));
        }
      });
  }

  public void showHotspotOrIssueHandleNoBinding(AssistBindingParams assistBindingParams) {
    var connectionId = assistBindingParams.getConnectionId();
    var projectKey = assistBindingParams.getProjectKey();
    var messageRequestParams = new ShowMessageRequestParams();
    messageRequestParams.setMessage("To display findings, you need to configure a project binding to '"
      + projectKey + "' on connection (" + connectionId + ")");
    messageRequestParams.setType(MessageType.Error);
    var configureBindingAction = new MessageActionItem("Configure Binding");
    messageRequestParams.setActions(List.of(configureBindingAction));
    client.showMessageRequest(messageRequestParams)
      .thenAccept(action -> {
        if (configureBindingAction.equals(action)) {
          client.assistBinding(assistBindingParams);
        }
      });
  }
}
