/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidChangeActiveSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.SonarProjectBranchRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.connected.ServerIssueTrackerWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackendServiceTests {

  static SonarLintRpcServer backend = mock(SonarLintRpcServer.class);
  HotspotRpcService hotspotService = mock(HotspotRpcService.class);
  ConfigurationRpcService configurationService = mock(ConfigurationRpcService.class);
  static LanguageClientLogger lsLogOutput = mock(LanguageClientLogger.class);
  static SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  static BackendService underTest = new BackendService(backend, lsLogOutput, client);

  @BeforeAll
  public static void setup() {
    when(backend.initialize(any())).thenReturn(CompletableFuture.completedFuture(null));
    underTest.initialize(null);
  }

  @BeforeEach
  void init() {
    when(backend.getHotspotService()).thenReturn(hotspotService);
    when(backend.getConfigurationService()).thenReturn(configurationService);
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
    var binding = mock(org.sonarsource.sonarlint.core.serverconnection.ProjectBinding.class);
    var bindingWrapper = new ProjectBinding(connectionId,"projectKey", mock(SonarLintAnalysisEngine.class), mock(ServerIssueTrackerWrapper.class));
    var result = underTest.getConfigScopeDto(new WorkspaceFolder(workspaceUri), Optional.of(bindingWrapper));

    assertThat(result.getId()).isEqualTo(workspaceUri);
    assertThat(result.getParentId()).isEqualTo(BackendServiceFacade.ROOT_CONFIGURATION_SCOPE);
    assertThat(result.getBinding().getConnectionId()).isEqualTo(connectionId);
  }

  @Test
  void getConfigScopeDtoWithoutBinding() {
    var workspaceUri = "/workspace";
    var result = underTest.getConfigScopeDto(new WorkspaceFolder(workspaceUri), Optional.empty());

    assertThat(result.getId()).isEqualTo(workspaceUri);
    assertThat(result.getParentId()).isEqualTo(BackendServiceFacade.ROOT_CONFIGURATION_SCOPE);
    assertThat(result.getBinding().getConnectionId()).isNull();
    assertThat(result.getBinding().getSonarProjectKey()).isNull();
    assertThat(result.getBinding().isBindingSuggestionDisabled()).isFalse();
  }

  @Test
  void notifyBackendOnBranchChanged() {
    var branchService = mock(SonarProjectBranchRpcService.class);
    when(backend.getSonarProjectBranchService()).thenReturn(branchService);
    var paramsArgumentCaptor = ArgumentCaptor.forClass(DidVcsRepositoryChangeParams.class);
    var expectedParams = new DidChangeActiveSonarProjectBranchParams("f", "b");

    underTest.notifyBackendOnVscChange("f");

    verify(branchService).didVcsRepositoryChange(paramsArgumentCaptor.capture());
    var actualParams = paramsArgumentCaptor.getValue();
    assertThat(expectedParams.getConfigScopeId()).isEqualTo(actualParams.getConfigurationScopeId());
  }

}
