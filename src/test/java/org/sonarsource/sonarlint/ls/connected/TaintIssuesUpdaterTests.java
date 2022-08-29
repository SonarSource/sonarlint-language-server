/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClient;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TaintIssuesUpdaterTests {

  private final static URI FILE_URI = URI.create("file:///some/file/uri/Name.java");
  private final static URI FOLDER_URI = URI.create("file:///folder/uri");
  private final static String CONNECTION_ID = "Connection ID";
  private final WorkspaceFoldersManager workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
  private final WorkspaceFolderWrapper workspaceFolderWrapper = mock(WorkspaceFolderWrapper.class);
  private final WorkspaceSettings workspaceSettings = mock(WorkspaceSettings.class);
  private final ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  private final ProjectBindingWrapper bindingWrapper = mock(ProjectBindingWrapper.class);
  private final ProjectBinding binding = mock(ProjectBinding.class);
  private final LanguageClientLogger lsLogOutput = mock(LanguageClientLogger.class);
  private final SettingsManager settingsManager = mock(SettingsManager.class);
  private final ServerConnectionSettings serverConnectionSettings = mock(ServerConnectionSettings.class);
  private final ServerConnectionSettings.EndpointParamsAndHttpClient endpointParamsAndHttpClient = mock(ServerConnectionSettings.EndpointParamsAndHttpClient.class);
  private final ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
  private final Map<String, ServerConnectionSettings> SERVER_CONNECTIONS = Map.of(CONNECTION_ID, serverConnectionSettings);
  private final TaintIssuesUpdater underTest = new TaintIssuesUpdater(bindingManager, new TaintVulnerabilitiesCache(), workspaceFoldersManager, lsLogOutput, settingsManager);

  @BeforeEach
  void init() {
    when(bindingManager.getBinding(FILE_URI)).thenReturn(Optional.of(bindingWrapper));
    when(workspaceFoldersManager.findFolderForFile(FILE_URI)).thenReturn(Optional.of(workspaceFolderWrapper));
    when(workspaceFolderWrapper.getUri()).thenReturn(FOLDER_URI);
    when(bindingWrapper.getEngine()).thenReturn(engine);
    when(bindingWrapper.getConnectionId()).thenReturn(CONNECTION_ID);
    when(bindingWrapper.getBinding()).thenReturn(binding);
    when(binding.projectKey()).thenReturn("projectKey");
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    when(workspaceSettings.getServerConnections()).thenReturn(SERVER_CONNECTIONS);
    when(serverConnectionSettings.getServerConfiguration()).thenReturn(endpointParamsAndHttpClient);
    when(endpointParamsAndHttpClient.getEndpointParams()).thenReturn(mock(EndpointParams.class));
    when(endpointParamsAndHttpClient.getHttpClient()).thenReturn(mock(ApacheHttpClient.class));
  }


  @Test
  void should_default_branch_to_master() {
    when(bindingManager.resolveBranchNameForFolder(FILE_URI)).thenReturn(null);

    underTest.updateTaintIssues(FILE_URI);

    verify(engine).syncServerTaintIssues(any(), any(), any(), eq("master"), isNull());
  }

  @Test
  void should_sync_and_download_taints() {
    underTest.updateTaintIssues(FILE_URI);

    verify(engine).syncServerTaintIssues(any(), any(), any(), eq("master"), isNull());
    verify(engine).downloadAllServerTaintIssuesForFile(any(), any(), any(), anyString(), eq("master"), isNull());
    verify(engine).getServerTaintIssues(any(), eq("master"), any());
    verifyNoMoreInteractions(engine);
  }

}
