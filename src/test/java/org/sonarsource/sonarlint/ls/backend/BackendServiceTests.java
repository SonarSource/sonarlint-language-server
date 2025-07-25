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
package org.sonarsource.sonarlint.ls.backend;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.issue.IssueNotFoundException;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.BindingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.IssueRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.AiCodeFixRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.ChangeDependencyRiskStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.OpenDependencyRiskInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackendServiceTests {

  static SonarLintRpcServer backend = mock(SonarLintRpcServer.class);
  HotspotRpcService hotspotService = mock(HotspotRpcService.class);
  DependencyRiskRpcService dependencyRiskRpcService = mock(DependencyRiskRpcService.class);
  ConfigurationRpcService configurationService = mock(ConfigurationRpcService.class);
  BindingRpcService bindingRpcService = mock(BindingRpcService.class);
  ConnectionRpcService connectionRpcService = mock(ConnectionRpcService.class);
  IssueRpcService issueService = mock(IssueRpcService.class);
  static LanguageClientLogger lsLogOutput = mock(LanguageClientLogger.class);
  static SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  static BackendService underTest = new BackendService(backend, lsLogOutput, client);

  @BeforeAll
  static void setup() {
    when(backend.initialize(any())).thenReturn(CompletableFuture.completedFuture(null));
    underTest.initialize(null);
  }

  @BeforeEach
  void init() {
    when(backend.getHotspotService()).thenReturn(hotspotService);
    when(backend.getConfigurationService()).thenReturn(configurationService);
    when(backend.getBindingService()).thenReturn(bindingRpcService);
    when(backend.getConnectionService()).thenReturn(connectionRpcService);
    when(backend.getIssueService()).thenReturn(issueService);
  }

  @Test
  void openHotspotInBrowser() {
    underTest.openHotspotInBrowser(mock(OpenHotspotInBrowserParams.class));

    verify(backend).getHotspotService();
    verify(hotspotService).openHotspotInBrowser(any());
  }

  @Test
  void updateBinding() {
    underTest.updateBinding(mock(DidUpdateBindingParams.class));

    verify(backend).getConfigurationService();
    verify(configurationService).didUpdateBinding(any());
  }

  @Test
  void getConfigScopeDtoWithBinding() {
    var workspaceUri = "/workspace";
    var connectionId = "connectionId";
    var bindingWrapper = new ProjectBinding(connectionId, "projectKey");
    var result = underTest.getConfigScopeDto(new WorkspaceFolder(workspaceUri, workspaceUri), Optional.of(bindingWrapper));

    assertThat(result.getId()).isEqualTo(workspaceUri);
    assertThat(result.getParentId()).isEqualTo(BackendService.ROOT_CONFIGURATION_SCOPE);
    assertThat(result.getBinding().getConnectionId()).isEqualTo(connectionId);
  }

  @Test
  void getConfigScopeDtoWithoutBinding() {
    var workspaceUri = "/workspace";
    var result = underTest.getConfigScopeDto(new WorkspaceFolder(workspaceUri, workspaceUri), Optional.empty());

    assertThat(result.getId()).isEqualTo(workspaceUri);
    assertThat(result.getParentId()).isEqualTo(BackendService.ROOT_CONFIGURATION_SCOPE);
    assertThat(result.getBinding().getConnectionId()).isNull();
    assertThat(result.getBinding().getSonarProjectKey()).isNull();
    assertThat(result.getBinding().isBindingSuggestionDisabled()).isFalse();
  }

  @Test
  void getSharedConnectedModeFileContents() {
    var params = new GetSharedConnectedModeConfigFileParams("file:///workspace");

    underTest.getSharedConnectedModeConfigFileContents(params);

    verify(bindingRpcService).getSharedConnectedModeConfigFileContents(params);
  }

  @Test
  void shouldListUserOrganizations() {
    var argumentCaptor = ArgumentCaptor.forClass(ListUserOrganizationsParams.class);
    var token = "token";
    underTest.listUserOrganizations(token, SonarCloudRegion.EU.name());

    verify(connectionRpcService).listUserOrganizations(argumentCaptor.capture());

    assertThat(argumentCaptor.getValue().getCredentials().isLeft()).isTrue();
    assertThat(argumentCaptor.getValue().getCredentials().getLeft().getToken()).isEqualTo(token);
  }

  @Test
  void shouldAddIssueComment() {
    var issueId = UUID.randomUUID();
    when(issueService.addComment(any())).thenReturn(CompletableFuture.failedFuture(new IssueNotFoundException("Could not find issue", issueId)));

    underTest.addIssueComment(new AddIssueCommentParams("configScope", issueId.toString(), "comment"));

    var messageParamsCaptor = ArgumentCaptor.forClass(MessageParams.class);

    verify(client).showMessage(messageParamsCaptor.capture());
    assertThat(messageParamsCaptor.getValue().getMessage()).contains("Could not add a new issue comment. Look at the SonarQube for IDE output for details.");
    assertThat(messageParamsCaptor.getValue().getType()).isEqualTo(MessageType.Error);
  }

  @Test
  void shouldExtractSonarCloudConnections() {
    var connectionId = "connectionId";
    var connections = Map.of(connectionId,
      new ServerConnectionSettings(connectionId, "https://sonarqube.us", "1235", "orgKey", false, SonarCloudRegion.US));

    var result = BackendService.extractSonarCloudConnections(connections);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getRegion()).isEqualTo(SonarCloudRegion.US);
    assertThat(result.get(0).getConnectionId()).isEqualTo(connectionId);
  }

  @Test
  void shouldForwardSuggestFixRequestToBackend() {
    var configScopeId = "file:///User/folder";
    var issueId = UUID.randomUUID();
    var aiCodeFixRpcService = mock(AiCodeFixRpcService.class);

    when(backend.getAiCodeFixRpcService()).thenReturn(aiCodeFixRpcService);

    underTest.suggestFix(configScopeId, issueId);
    var argumentCaptor = ArgumentCaptor.forClass(SuggestFixParams.class);
    verify(aiCodeFixRpcService).suggestFix(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getConfigurationScopeId()).isEqualTo(configScopeId);
    assertThat(argumentCaptor.getValue().getIssueId()).isEqualTo(issueId);
  }

  @Test
  void shouldForwardOpenDependencyRiskInBrowserRequestToBackend() {
    var folderUri = "file:///User/folder";
    var dependencyKey = UUID.randomUUID();
    var argumentCaptor = ArgumentCaptor.forClass(OpenDependencyRiskInBrowserParams.class);
    when(backend.getDependencyRiskService()).thenReturn(dependencyRiskRpcService);

    underTest.openDependencyRiskInBrowser(folderUri, dependencyKey);

    verify(dependencyRiskRpcService).openDependencyRiskInBrowser(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getConfigScopeId()).isEqualTo(folderUri);
    assertThat(argumentCaptor.getValue().getDependencyRiskKey()).isEqualTo(dependencyKey);
  }

  @Test
  void shouldForwardChangeDependencyRiskStatusRequestToBackend() {
    var folderUri = "file:///User/folder";
    var dependencyKey = UUID.randomUUID();
    var transition = DependencyRiskTransition.ACCEPT;
    var comment = "Because it is acceptable";
    var argumentCaptor = ArgumentCaptor.forClass(ChangeDependencyRiskStatusParams.class);
    when(backend.getDependencyRiskService()).thenReturn(dependencyRiskRpcService);

    underTest.changeDependencyRiskStatus(new ChangeDependencyRiskStatusParams(folderUri, dependencyKey, transition, comment));

    verify(dependencyRiskRpcService).changeStatus(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getConfigurationScopeId()).isEqualTo(folderUri);
    assertThat(argumentCaptor.getValue().getDependencyRiskKey()).isEqualTo(dependencyKey);
    assertThat(argumentCaptor.getValue().getTransition()).isEqualTo(transition);
    assertThat(argumentCaptor.getValue().getComment()).isEqualTo(comment);
  }
}
