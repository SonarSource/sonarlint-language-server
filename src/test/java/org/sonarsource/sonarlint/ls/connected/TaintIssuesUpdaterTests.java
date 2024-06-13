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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import testutils.ImmediateExecutorService;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaintIssuesUpdaterTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();
  private final static URI FILE_URI = URI.create("file:///some/file/uri/Name.java");
  private final static URI FOLDER_URI = URI.create("file:///folder/uri");
  private final static String PROJECT_KEY = "projectKey";
  private final static String CONNECTION_ID = "Connection ID";
  private final static String BRANCH_NAME = "my/branch";
  private final WorkspaceFoldersManager workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
  private final WorkspaceFolderWrapper workspaceFolderWrapper = mock(WorkspaceFolderWrapper.class);
  private final WorkspaceSettings workspaceSettings = mock(WorkspaceSettings.class);
  private final ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  private final ProjectBinding bindingWrapper = mock(ProjectBinding.class);
  private final org.sonarsource.sonarlint.core.serverconnection.ProjectBinding binding = mock(org.sonarsource.sonarlint.core.serverconnection.ProjectBinding.class);
  private final DiagnosticPublisher diagnosticPublisher = mock(DiagnosticPublisher.class);
  private final SettingsManager settingsManager = mock(SettingsManager.class);
  private final ServerConnectionSettings serverConnectionSettings = mock(ServerConnectionSettings.class);
  private final SonarLintAnalysisEngine engine = mock(SonarLintAnalysisEngine.class);
  private final BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  private final BackendService backendService = mock(BackendService.class);
  private final Map<String, ServerConnectionSettings> SERVER_CONNECTIONS = Map.of(CONNECTION_ID, serverConnectionSettings);
  private final ImmediateExecutorService executorService = new ImmediateExecutorService();
  private final TaintIssuesUpdater underTest = new TaintIssuesUpdater(bindingManager, new TaintVulnerabilitiesCache(), workspaceFoldersManager,
    diagnosticPublisher, executorService, logTester.getLogger());

  @BeforeEach
  void init() {
    when(bindingManager.getBinding(FILE_URI)).thenReturn(Optional.of(bindingWrapper));
    when(bindingManager.resolveBranchNameForFolder(FOLDER_URI)).thenReturn(Optional.of(BRANCH_NAME));
    when(workspaceFoldersManager.findFolderForFile(FILE_URI)).thenReturn(Optional.of(workspaceFolderWrapper));
    when(workspaceFolderWrapper.getUri()).thenReturn(FOLDER_URI);
    when(bindingWrapper.connectionId()).thenReturn(CONNECTION_ID);
    when(bindingWrapper.projectKey()).thenReturn(PROJECT_KEY);
    when(binding.projectKey()).thenReturn(PROJECT_KEY);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    when(workspaceSettings.getServerConnections()).thenReturn(SERVER_CONNECTIONS);
    when(serverConnectionSettings.getEndpointParams()).thenReturn(mock(EndpointParams.class));
    when(backendServiceFacade.getBackendService()).thenReturn(backendService);
  }

  @Test
  void should_stop_executor_on_shutdown() {
    underTest.shutdown();

    assertThat(executorService.isShutdown()).isTrue();
  }

}
