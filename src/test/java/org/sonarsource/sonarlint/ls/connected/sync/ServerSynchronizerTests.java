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
package org.sonarsource.sonarlint.ls.connected.sync;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBranches;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.progress.ProgressManager;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ServerSynchronizerTests {

  private static final String FILE_PHP = "fileInAWorkspaceFolderPath.php";
  private static final String PROJECT_KEY = "myProject";
  private static final String PROJECT_KEY2 = "myProject2";
  private static final ProjectBinding FAKE_BINDING = new ProjectBinding(PROJECT_KEY, "sqPrefix", "idePrefix");
  private static final ProjectBinding FAKE_BINDING2 = new ProjectBinding(PROJECT_KEY2, "sqPrefix2", "idePrefix2");
  private static final String CONNECTION_ID = "myServer";
  private static final String CONNECTION_ID2 = "myServer2";
  private static final BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  private static final ServerConnectionSettings GLOBAL_SETTINGS = new ServerConnectionSettings(CONNECTION_ID, "http://foo", "token", null, true);
  private static final ServerConnectionSettings GLOBAL_SETTINGS_DIFFERENT_SERVER_ID = new ServerConnectionSettings(CONNECTION_ID2, "http://foo2", "token2", null, true
  );
  private static final WorkspaceFolderSettings UNBOUND_SETTINGS = new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null, null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS = new WorkspaceFolderSettings(CONNECTION_ID, PROJECT_KEY, Collections.emptyMap(), null, null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS2 = new WorkspaceFolderSettings(CONNECTION_ID2, PROJECT_KEY2, Collections.emptyMap(), null, null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS_DIFFERENT_PROJECT_KEY = new WorkspaceFolderSettings(CONNECTION_ID, PROJECT_KEY2, Collections.emptyMap(), null, null);

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path basedir;
  private Path workspaceFolderPath;
  private Path workspaceFolderPath2;
  private Path fileInAWorkspaceFolderPath;
  private Path fileInAWorkspaceFolderPath2;
  private Path fileNotInAWorkspaceFolderPath;
  ConcurrentMap<URI, Optional<ProjectBindingWrapper>> folderBindingCache;
  private ServerSynchronizer underTest;
  private final SettingsManager settingsManager = mock(SettingsManager.class);
  private final WorkspaceFoldersManager foldersManager = mock(WorkspaceFoldersManager.class);
  private final Map<String, ServerConnectionSettings> servers = new HashMap<>();
  private final EnginesFactory enginesFactory = mock(EnginesFactory.class);
  private final ConnectedSonarLintEngine fakeEngine = mock(ConnectedSonarLintEngine.class);
  private final ConnectedSonarLintEngine fakeEngine2 = mock(ConnectedSonarLintEngine.class);
  private final AnalysisScheduler analysisManager = mock(AnalysisScheduler.class);
  SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  private ProjectBindingManager bindingManager;
  private Timer syncTimer;
  private Runnable syncTask;
  private TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private DiagnosticPublisher diagnosticPublisher;

  @BeforeEach
  public void prepare() throws IOException, ExecutionException, InterruptedException {
    workspaceFolderPath = basedir.resolve("myWorkspaceFolder");
    Files.createDirectories(workspaceFolderPath);
    workspaceFolderPath2 = basedir.resolve("myWorkspaceFolder2");
    Files.createDirectories(workspaceFolderPath2);
    Path anotherFolderPath = basedir.resolve("anotherFolder");
    Files.createDirectories(anotherFolderPath);
    fileInAWorkspaceFolderPath = workspaceFolderPath.resolve(FILE_PHP);
    Files.createFile(fileInAWorkspaceFolderPath);
    fileInAWorkspaceFolderPath2 = workspaceFolderPath2.resolve(FILE_PHP);
    Files.createFile(fileInAWorkspaceFolderPath2);
    fileNotInAWorkspaceFolderPath = anotherFolderPath.resolve(FILE_PHP);
    Files.createFile(fileNotInAWorkspaceFolderPath);

    ProjectBranches serverBranches = new ProjectBranches(Set.of("main", "dev"), "main");
    when(fakeEngine.getServerBranches(anyString())).thenReturn(serverBranches);
    when(fakeEngine2.getServerBranches(anyString())).thenReturn(serverBranches);

    when(settingsManager.getCurrentSettings())
      .thenReturn(newWorkspaceSettingsWithServers(servers));
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(UNBOUND_SETTINGS);

    when(enginesFactory.createConnectedEngine(anyString(), any(ServerConnectionSettings.class))).thenReturn(fakeEngine);

    when(client.getTokenForServer(any())).thenReturn(CompletableFuture.supplyAsync(() -> "token"));

    folderBindingCache = new ConcurrentHashMap<>();
    taintVulnerabilitiesCache = mock(TaintVulnerabilitiesCache.class);
    diagnosticPublisher = mock(DiagnosticPublisher.class);
    bindingManager = new ProjectBindingManager(enginesFactory, foldersManager, settingsManager, client, mock(LanguageClientLogOutput.class),
      taintVulnerabilitiesCache, diagnosticPublisher, backendServiceFacade, mock(OpenNotebooksCache.class));
    syncTimer = mock(Timer.class);
    var syncTaskCaptor = ArgumentCaptor.forClass(TimerTask.class);
    underTest = new ServerSynchronizer(client, new ProgressManager(client), bindingManager, analysisManager, syncTimer, backendServiceFacade);
    verify(syncTimer).scheduleAtFixedRate(syncTaskCaptor.capture(), anyLong(), anyLong());
    syncTask = syncTaskCaptor.getValue();
    bindingManager.setAnalysisManager(analysisManager);
    bindingManager.setBranchResolver(uri -> Optional.of("master"));
  }

  private static WorkspaceSettings newWorkspaceSettingsWithServers(Map<String, ServerConnectionSettings> servers) {
    return new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false, false, "", false);
  }

  @Test
  void update_all_project_bindings_update_already_started_servers() {
    var folder1 = mockFileInABoundWorkspaceFolder();
    var folder2 = mockFileInABoundWorkspaceFolder2();

    when(foldersManager.getAll()).thenReturn(List.of(folder1, folder2));
    when(enginesFactory.createConnectedEngine(anyString(), any(ServerConnectionSettings.class)))
      .thenReturn(fakeEngine)
      .thenReturn(fakeEngine2);

    bindingManager.getOrCreateConnectedEngine(CONNECTION_ID);
    bindingManager.getOrCreateConnectedEngine(CONNECTION_ID2);

    underTest.updateAllBindings(mock(CancelChecker.class), null);

    verify(fakeEngine, times(2)).updateProject(any(), any(), eq(PROJECT_KEY), any());
    verify(fakeEngine, times(2)).sync(any(), any(), eq(Set.of(PROJECT_KEY)), any());
    verify(fakeEngine2, times(2)).updateProject(any(), any(), eq(PROJECT_KEY2), any());
    verify(fakeEngine2, times(2)).sync(any(), any(), eq(Set.of(PROJECT_KEY2)), any());

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder1);
    verify(analysisManager).analyzeAllOpenFilesInFolder(folder2);
    verifyNoMoreInteractions(analysisManager);
  }

  @Test
  void update_all_project_bindings_update_not_started_servers() {
    var folder1 = mockFileInABoundWorkspaceFolder();
    var folder2 = mockFileInABoundWorkspaceFolder2();

    when(foldersManager.getAll()).thenReturn(List.of(folder1, folder2));

    when(enginesFactory.createConnectedEngine(anyString(), any(ServerConnectionSettings.class)))
      .thenReturn(fakeEngine)
      .thenReturn(fakeEngine2);

    bindingManager.getOrCreateConnectedEngine(CONNECTION_ID);
    bindingManager.getOrCreateConnectedEngine(CONNECTION_ID2);

    underTest.updateAllBindings(mock(CancelChecker.class), null);

    verify(fakeEngine, times(2)).updateProject(any(), any(), eq(PROJECT_KEY), any());
    verify(fakeEngine, times(2)).sync(any(), any(), eq(Set.of(PROJECT_KEY)), any());
    verify(fakeEngine2, times(2)).updateProject(any(), any(), eq(PROJECT_KEY2), any());
    verify(fakeEngine2, times(2)).sync(any(), any(), eq(Set.of(PROJECT_KEY2)), any());

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder1);
    verify(analysisManager).analyzeAllOpenFilesInFolder(folder2);
    verifyNoMoreInteractions(analysisManager);
  }

  @Test
  void update_all_project_bindings_update_once_each_project_same_server() {
    var folder1 = mockFileInABoundWorkspaceFolder();
    var folder2 = mockFileInABoundWorkspaceFolder2();
    // Folder 2 is bound to the same server, different project
    folder2.setSettings(BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);

    when(foldersManager.getAll()).thenReturn(List.of(folder1, folder2));
    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY2), anyCollection())).thenReturn(new ProjectBinding(PROJECT_KEY2, "", ""));
    when(enginesFactory.createConnectedEngine(anyString(), any(ServerConnectionSettings.class)))
      .thenReturn(fakeEngine);

    bindingManager.getOrCreateConnectedEngine(CONNECTION_ID);

    underTest.updateAllBindings(mock(CancelChecker.class), null);

    verify(fakeEngine, times(2)).updateProject(any(), any(), eq(PROJECT_KEY), any());
    verify(fakeEngine, times(2)).updateProject(any(), any(), eq(PROJECT_KEY2), any());
    verify(fakeEngine).sync(any(), any(), eq(Set.of(PROJECT_KEY, PROJECT_KEY2)), any());

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder1);
    verify(analysisManager).analyzeAllOpenFilesInFolder(folder2);
    verifyNoMoreInteractions(analysisManager);
  }

  @Test
  void update_all_project_bindings_update_only_once_each_project_same_server() {
    var folder1 = mockFileInABoundWorkspaceFolder();
    var folder2 = mockFileInABoundWorkspaceFolder2();
    // Folder 2 is bound to the same server, same project
    folder2.setSettings(BOUND_SETTINGS);

    when(foldersManager.getAll()).thenReturn(List.of(folder1, folder2));

    when(enginesFactory.createConnectedEngine(anyString(), any(ServerConnectionSettings.class)))
      .thenReturn(fakeEngine);

    bindingManager.getOrCreateConnectedEngine(CONNECTION_ID);

    underTest.updateAllBindings(mock(CancelChecker.class), null);

    verify(fakeEngine, times(3)).updateProject(any(), any(), eq(PROJECT_KEY), any());
    verify(fakeEngine, times(3)).sync(any(), any(), eq(Set.of(PROJECT_KEY)), any());

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder1);
    verify(analysisManager).analyzeAllOpenFilesInFolder(folder2);
    verifyNoMoreInteractions(analysisManager);
  }

  @Test
  void update_all_project_bindings_ignore_wrong_binding() {
    var folder = mockFileInAFolder();
    folder.setSettings(BOUND_SETTINGS);

    when(foldersManager.getAll()).thenReturn(List.of(folder));

    underTest.updateAllBindings(mock(CancelChecker.class), null);

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder);
    verifyNoMoreInteractions(analysisManager);
    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).contains("The specified connection id '" + CONNECTION_ID + "' doesn't exist.");
  }

  @Test
  void update_all_project_bindings_ignore_wrong_binding_default_folder() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND_SETTINGS);

    underTest.updateAllBindings(mock(CancelChecker.class), null);

    verify(analysisManager).analyzeAllOpenFilesInFolder(null);
    verifyNoMoreInteractions(analysisManager);
  }

  @Test
  void update_all_bindings_success() {
    var folderSettings = mock(WorkspaceFolderSettings.class);
    var settings = mock(WorkspaceSettings.class);
    var connectionId = "serverId";
    var projectKey = "projectKey";
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(folderSettings);
    when(folderSettings.hasBinding()).thenReturn(true);
    when(folderSettings.getConnectionId()).thenReturn(connectionId);
    when(folderSettings.getProjectKey()).thenReturn(projectKey);
    when(settingsManager.getCurrentSettings()).thenReturn(settings);
    var serverConnectionSettings = new ServerConnectionSettings("serverId", "serverUrl", "token", "organizationKey", true);
    when(settings.getServerConnections()).thenReturn(Map.of(connectionId, serverConnectionSettings));
    when(enginesFactory.createConnectedEngine(connectionId, serverConnectionSettings)).thenReturn(fakeEngine);
    when(settingsManager.getCurrentSettings()).thenReturn(settings);
    when(enginesFactory.createConnectedEngine(connectionId, serverConnectionSettings)).thenReturn(fakeEngine);
    when(fakeEngine.calculatePathPrefixes(eq(projectKey), anyCollection())).thenReturn(new ProjectBinding(projectKey, "", ""));
    bindingManager.getOrCreateConnectedEngine(connectionId);

    underTest.updateAllBindings(mock(CancelChecker.class), null);

    verify(client).showMessage(new MessageParams(MessageType.Info, "All SonarLint bindings successfully updated"));
  }

  @Test
  void sync_bound_folders() {
    var folder1 = mockFileInABoundWorkspaceFolder();
    var folder2 = mockFileInABoundWorkspaceFolder2();

    when(foldersManager.getAll()).thenReturn(List.of(folder1, folder2));
    when(enginesFactory.createConnectedEngine(anyString(), any(ServerConnectionSettings.class)))
      .thenReturn(fakeEngine)
      .thenReturn(fakeEngine2);
    bindingManager.getOrCreateConnectedEngine(CONNECTION_ID);
    bindingManager.getOrCreateConnectedEngine(CONNECTION_ID2);

    syncTask.run();

    verify(fakeEngine, times(2)).sync(any(), any(), eq(Set.of(PROJECT_KEY)), any());
    verify(fakeEngine, times(2)).syncServerIssues(any(), any(), eq(PROJECT_KEY), eq("master"), any());
    verify(fakeEngine, times(2)).syncServerTaintIssues(any(), any(), eq(PROJECT_KEY), eq("master"), any());
    verify(fakeEngine2, times(2)).sync(any(), any(), eq(Set.of(PROJECT_KEY2)), any());
    verify(fakeEngine2, times(2)).syncServerIssues(any(), any(), eq(PROJECT_KEY2), eq("master"), any());
    verify(fakeEngine2, times(2)).syncServerTaintIssues(any(), any(), eq(PROJECT_KEY2), eq("master"), any());
  }

  @Test
  void shutdown_should_stop_automatic_sync() {
    underTest.shutdown();

    verify(syncTimer).cancel();
  }

  private WorkspaceFolderWrapper mockFileInABoundWorkspaceFolder() {
    var folder = mockFileInAFolder();
    folder.setSettings(BOUND_SETTINGS);
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);
    return folder;
  }

  private WorkspaceFolderWrapper mockFileInABoundWorkspaceFolder2() {
    var folder2 = mockFileInAFolder2();
    folder2.setSettings(BOUND_SETTINGS2);
    servers.put(CONNECTION_ID2, GLOBAL_SETTINGS_DIFFERENT_SERVER_ID);
    when(fakeEngine2.calculatePathPrefixes(eq(PROJECT_KEY2), any())).thenReturn(FAKE_BINDING2);
    return folder2;
  }

  private void mockFileOutsideFolder() {
    when(foldersManager.findFolderForFile(fileNotInAWorkspaceFolderPath.toUri())).thenReturn(Optional.empty());
  }

  private WorkspaceFolderWrapper mockFileInAFolder() {
    var folderWrapper = spy(new WorkspaceFolderWrapper(workspaceFolderPath.toUri(), new WorkspaceFolder(workspaceFolderPath.toUri().toString())));
    when(foldersManager.findFolderForFile(fileInAWorkspaceFolderPath.toUri())).thenReturn(Optional.of(folderWrapper));
    return folderWrapper;
  }

  private WorkspaceFolderWrapper mockFileInAFolder2() {
    var folderWrapper2 = spy(new WorkspaceFolderWrapper(workspaceFolderPath2.toUri(), new WorkspaceFolder(workspaceFolderPath2.toUri().toString())));
    when(foldersManager.findFolderForFile(fileInAWorkspaceFolderPath2.toUri())).thenReturn(Optional.of(folderWrapper2));
    return folderWrapper2;
  }
}
