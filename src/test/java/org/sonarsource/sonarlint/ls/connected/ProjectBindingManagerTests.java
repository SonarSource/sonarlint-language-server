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
package org.sonarsource.sonarlint.ls.connected;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBranches;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.notebooks.VersionedOpenNotebook;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectBindingManagerTests {

  private static final String FILE_PHP = "fileInAWorkspaceFolderPath.php";
  private static final String PROJECT_KEY = "myProject";
  private static final String PROJECT_KEY2 = "myProject2";
  private static final ProjectBinding FAKE_BINDING = new ProjectBinding(PROJECT_KEY, "sqPrefix", "idePrefix");
  private static final ProjectBinding FAKE_BINDING2 = new ProjectBinding(PROJECT_KEY2, "sqPrefix2", "idePrefix2");
  private static final String CONNECTION_ID = "myServer";
  private static final String SERVER_ID2 = "myServer2";
  private static BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  private static final ServerConnectionSettings GLOBAL_SETTINGS = new ServerConnectionSettings(CONNECTION_ID, "http://foo", "token", null, true);
  private static final ServerConnectionSettings GLOBAL_SETTINGS_DIFFERENT_SERVER_ID = new ServerConnectionSettings(SERVER_ID2, "http://foo2", "token2", null, true);
  private static final WorkspaceFolderSettings UNBOUND_SETTINGS = new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null, null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS = new WorkspaceFolderSettings(CONNECTION_ID, PROJECT_KEY, Collections.emptyMap(), null, null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS2 = new WorkspaceFolderSettings(SERVER_ID2, PROJECT_KEY2, Collections.emptyMap(), null, null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS_DIFFERENT_PROJECT_KEY = new WorkspaceFolderSettings(CONNECTION_ID, PROJECT_KEY2, Collections.emptyMap(), null, null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS_DIFFERENT_SERVER_ID = new WorkspaceFolderSettings(SERVER_ID2, PROJECT_KEY, Collections.emptyMap(), null, null);
  private static final String BRANCH_NAME = "main";
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path basedir;
  private Path workspaceFolderPath;
  private Path workspaceFolderPath2;
  private Path anotherFolderPath;
  private Path fileInAWorkspaceFolderPath;
  private Path fileInAWorkspaceFolderPath2;
  private Path fileNotInAWorkspaceFolderPath;
  ConcurrentMap<URI, Optional<ProjectBindingWrapper>> folderBindingCache;
  ConcurrentMap<String, Optional<ConnectedSonarLintEngine>> connectedEngineCacheByConnectionId;
  private ProjectBindingManager underTest;
  private final SettingsManager settingsManager = mock(SettingsManager.class);
  private final WorkspaceFoldersManager foldersManager = mock(WorkspaceFoldersManager.class);
  private final Map<String, ServerConnectionSettings> servers = new HashMap<>();
  private final EnginesFactory enginesFactory = mock(EnginesFactory.class);
  private final ConnectedSonarLintEngine fakeEngine = mock(ConnectedSonarLintEngine.class);
  private final ConnectedSonarLintEngine fakeEngine2 = mock(ConnectedSonarLintEngine.class);
  private final AnalysisScheduler analysisManager = mock(AnalysisScheduler.class);
  SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  private final DiagnosticPublisher diagnosticPublisher = mock(DiagnosticPublisher.class);
  private final OpenNotebooksCache openNotebooksCache = mock(OpenNotebooksCache.class);

  @BeforeEach
  public void prepare() throws IOException, ExecutionException, InterruptedException {
    workspaceFolderPath = basedir.resolve("myWorkspaceFolder");
    Files.createDirectories(workspaceFolderPath);
    workspaceFolderPath2 = basedir.resolve("myWorkspaceFolder2");
    Files.createDirectories(workspaceFolderPath2);
    anotherFolderPath = basedir.resolve("anotherFolder");
    Files.createDirectories(anotherFolderPath);
    fileInAWorkspaceFolderPath = workspaceFolderPath.resolve(FILE_PHP);
    Files.createFile(fileInAWorkspaceFolderPath);
    fileInAWorkspaceFolderPath2 = workspaceFolderPath2.resolve(FILE_PHP);
    Files.createFile(fileInAWorkspaceFolderPath2);
    fileNotInAWorkspaceFolderPath = anotherFolderPath.resolve(FILE_PHP);
    Files.createFile(fileNotInAWorkspaceFolderPath);

    when(settingsManager.getCurrentSettings())
      .thenReturn(newWorkspaceSettingsWithServers(servers));
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(UNBOUND_SETTINGS);

    when(fakeEngine.getServerBranches(any(String.class))).thenReturn(new ProjectBranches(Set.of(BRANCH_NAME), BRANCH_NAME));
    when(fakeEngine2.getServerBranches(any(String.class))).thenReturn(new ProjectBranches(Set.of(BRANCH_NAME), BRANCH_NAME));
    when(enginesFactory.createConnectedEngine(anyString(), any(ServerConnectionSettings.class))).thenReturn(fakeEngine);
    when(backendServiceFacade.getBackendService()).thenReturn(mock(BackendService.class));
    when(client.getTokenForServer(any())).thenReturn(CompletableFuture.supplyAsync(() -> "token"));

    folderBindingCache = new ConcurrentHashMap<>();
    connectedEngineCacheByConnectionId = new ConcurrentHashMap<>();
    TaintVulnerabilitiesCache taintVulnerabilitiesCache = new TaintVulnerabilitiesCache();

    when(openNotebooksCache.getFile(any(URI.class))).thenReturn(Optional.empty());

    underTest = new ProjectBindingManager(enginesFactory, foldersManager, settingsManager, client, folderBindingCache, null,
      connectedEngineCacheByConnectionId, taintVulnerabilitiesCache, diagnosticPublisher, backendServiceFacade, openNotebooksCache);
    underTest.setAnalysisManager(analysisManager);
    underTest.setBranchResolver(uri -> Optional.of("main"));
  }

  private static WorkspaceSettings newWorkspaceSettingsWithServers(Map<String, ServerConnectionSettings> servers) {
    return new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false, false, "", false);
  }

  @Test
  void get_binding_returns_empty_for_single_file_with_no_binding_and_cache_result() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(UNBOUND_SETTINGS);

    assertThat(underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri())).isEmpty();

    // Second call is cached
    assertThat(underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri())).isEmpty();
    verify(settingsManager, times(1)).getCurrentDefaultFolderSettings();

    assertThat(underTest.usesConnectedMode()).isFalse();
  }

  @Test
  void get_binding_returns_empty_for_non_file_uri() {
    assertThat(underTest.getBinding(URI.create("not-a-file-scheme://definitely.not/a/file"))).isEmpty();
  }

  @Test
  void get_binding_returns_empty_for_notebook() {
    when(openNotebooksCache.getFile(fileInAWorkspaceFolderPath.toUri())).thenReturn(Optional.of(mock(VersionedOpenNotebook.class)));
    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();
  }

  @Test
  void get_binding_returns_empty_for_file_in_a_folder_with_no_binding() {
    var folder = mockFileInAFolder();
    folder.setSettings(UNBOUND_SETTINGS);

    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();

    // Second call is cached
    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();
    verify(folder, times(1)).getSettings();
  }

  @Test
  void get_binding_default_to_standalone_if_unknown_server_id() {
    var folder = mockFileInAFolder();
    folder.setSettings(BOUND_SETTINGS);

    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR))
      .containsOnly("The specified connection id 'myServer' doesn't exist.", "Invalid binding for '" + workspaceFolderPath.toString() + "'");
    assertThat(underTest.usesConnectedMode()).isFalse();
    assertThat(underTest.usesSonarCloud()).isFalse();
  }

  @Test
  void get_binding_default_to_standalone_if_server_fail_to_start() {
    mockFileInABoundWorkspaceFolder();

    when(enginesFactory.createConnectedEngine(anyString(), any(ServerConnectionSettings.class))).thenThrow(new IllegalStateException("Unable to start"));

    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).contains("Error starting connected SonarLint engine for '" + CONNECTION_ID + "'");
  }

  @Test
  void get_binding_for_single_file_uses_parent_dir_as_basedir() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND_SETTINGS);
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    var binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    assertThat(binding.get().getEngine()).isEqualTo(fakeEngine);
    assertThat(binding.get().getConnectionId()).isEqualTo(CONNECTION_ID);
    assertThat(binding.get().getServerIssueTracker()).isNotNull();
    assertThat(binding.get().getBinding()).isEqualTo(FAKE_BINDING);

    verify(fakeEngine).calculatePathPrefixes(eq(PROJECT_KEY), argThat(set -> set.contains(FILE_PHP)));
  }

  @Test
  void get_binding_should_update_if_project_storage_missing() {
    mockFileInABoundWorkspaceFolder();

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine).updateProject(any(), any(), eq(PROJECT_KEY), any());
  }

  @Test
  void test_use_sonarcloud() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(new WorkspaceFolderSettings("sonarcloud", PROJECT_KEY, Collections.emptyMap(), null, null));

    mockFileInABoundWorkspaceFolder();
    servers.put("sonarcloud", new ServerConnectionSettings("sonarcloud", "https://sonarcloud.io", "token", null, true));

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    assertThat(underTest.usesConnectedMode()).isTrue();
    assertThat(underTest.usesSonarCloud()).isFalse();

    binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    assertThat(underTest.usesConnectedMode()).isTrue();
    assertThat(underTest.usesSonarCloud()).isTrue();
  }

  @Test
  void ignore_first_change_event() {
    underTest.onChange(null, null, UNBOUND_SETTINGS);
    verifyNoInteractions(settingsManager, fakeEngine);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void ignore_change_if_same_binding() {
    underTest.onChange(null, UNBOUND_SETTINGS, UNBOUND_SETTINGS);
    underTest.onChange(null, BOUND_SETTINGS, BOUND_SETTINGS);
    verifyNoInteractions(settingsManager, fakeEngine);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void ignore_change_if_folder_binding_not_cached() {
    var folder = mockFileInABoundWorkspaceFolder();
    when(folder.getSettings()).thenReturn(UNBOUND_SETTINGS);

    underTest.onChange(folder, BOUND_SETTINGS, UNBOUND_SETTINGS);
    verifyNoInteractions(settingsManager, fakeEngine);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void ignore_change_if_file_binding_not_cached() {
    mockFileOutsideFolder();

    underTest.onChange(null, BOUND_SETTINGS, UNBOUND_SETTINGS);
    verifyNoInteractions(settingsManager, fakeEngine);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void test_unbind_folder() {
    var folder = mockFileInABoundWorkspaceFolder();

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    when(folder.getSettings()).thenReturn(UNBOUND_SETTINGS);
    underTest.onChange(folder, BOUND_SETTINGS, UNBOUND_SETTINGS);

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder);

    binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());

    assertThat(binding).isEmpty();
    verify(fakeEngine).stop(false);
    assertThat(logTester.logs()).contains("Workspace 'WorkspaceFolder[name=<null>,uri=" + workspaceFolderPath.toUri() + "]' unbound");
  }

  @Test
  void test_unbind_file() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND_SETTINGS);
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    var binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(UNBOUND_SETTINGS);
    underTest.onChange(null, BOUND_SETTINGS, UNBOUND_SETTINGS);

    verify(analysisManager).analyzeAllOpenFilesInFolder(null);

    binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());

    assertThat(binding).isEmpty();
    verify(fakeEngine).stop(false);
    assertThat(logTester.logs()).contains("All files outside workspace are now unbound");
  }

  @Test
  void test_rebind_folder_after_project_key_change() {
    var folder = mockFileInABoundWorkspaceFolder();
    when(foldersManager.getAll()).thenReturn(List.of(folder));

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    when(folder.getSettings()).thenReturn(BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);
    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY2), any())).thenReturn(FAKE_BINDING2);

    underTest.onChange(folder, BOUND_SETTINGS, BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder);

    binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());

    assertThat(binding).isNotEmpty();
    verify(fakeEngine, never()).stop(anyBoolean());
    verify(fakeEngine).calculatePathPrefixes(eq(PROJECT_KEY2), any());
    assertThat(logTester.logs())
      .contains("Resolved binding ProjectBinding[idePathPrefix=idePrefix2,projectKey=myProject2,serverPathPrefix=sqPrefix2] for folder " + workspaceFolderPath.toString());
  }

  @Test
  void unbind_when_global_server_deleted() {
    var settingsWithServer1 = newWorkspaceSettingsWithServers(Map.of(CONNECTION_ID, GLOBAL_SETTINGS));
    var settingsWithServer2 = newWorkspaceSettingsWithServers(Map.of(SERVER_ID2, GLOBAL_SETTINGS_DIFFERENT_SERVER_ID));

    var spiedUnderTest = spy(underTest);
    // Skip actual connection test
    doNothing().when(spiedUnderTest).validateConnection(SERVER_ID2);

    when(settingsManager.getCurrentSettings()).thenReturn(settingsWithServer1);

    var folder = mockFileInABoundWorkspaceFolder();
    when(foldersManager.getAll()).thenReturn(List.of(folder));

    var binding = spiedUnderTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    when(settingsManager.getCurrentSettings()).thenReturn(settingsWithServer2);
    spiedUnderTest.onChange(settingsWithServer1, settingsWithServer2);
    spiedUnderTest.onChange(settingsWithServer2, settingsWithServer2);
    // Should validate connection only once (when changed from server1 to server2)
    verify(spiedUnderTest, times(1)).validateConnection(SERVER_ID2);

    binding = spiedUnderTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isEmpty();

    verify(fakeEngine).stop(anyBoolean());
    assertThat(logTester.logs())
      .contains("The specified connection id 'myServer' doesn't exist.", "Invalid binding for '" + workspaceFolderPath.toString() + "'");
  }

  @Test
  void test_rebind_file_after_project_key_change() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND_SETTINGS);
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    var binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);
    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY2), any())).thenReturn(FAKE_BINDING2);

    underTest.onChange(null, BOUND_SETTINGS, BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);

    verify(analysisManager).analyzeAllOpenFilesInFolder(null);

    binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());

    assertThat(binding).isNotEmpty();
    verify(fakeEngine, never()).stop(anyBoolean());
    verify(fakeEngine).calculatePathPrefixes(eq(PROJECT_KEY2), any());
    assertThat(logTester.logs())
      .contains("Resolved binding ProjectBinding[idePathPrefix=idePrefix2,projectKey=myProject2,serverPathPrefix=sqPrefix2] for folder " + anotherFolderPath.toString());
  }

  @Test
  void test_rebind_folder_after_server_id_change() {
    servers.put(SERVER_ID2, GLOBAL_SETTINGS_DIFFERENT_SERVER_ID);
    var folder = mockFileInABoundWorkspaceFolder();

    when(enginesFactory.createConnectedEngine(anyString(), any(ServerConnectionSettings.class)))
      .thenReturn(fakeEngine)
      .thenReturn(fakeEngine2);

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    when(folder.getSettings()).thenReturn(BOUND_SETTINGS_DIFFERENT_SERVER_ID);
    when(fakeEngine2.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING2);

    underTest.onChange(folder, BOUND_SETTINGS, BOUND_SETTINGS_DIFFERENT_SERVER_ID);

    binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());

    assertThat(binding).isNotEmpty();
    verify(fakeEngine).calculatePathPrefixes(eq(PROJECT_KEY), any());
    verify(fakeEngine).stop(false);
    verify(fakeEngine2).calculatePathPrefixes(eq(PROJECT_KEY), any());
    assertThat(logTester.logs())
      .contains("Resolved binding ProjectBinding[idePathPrefix=idePrefix2,projectKey=myProject2,serverPathPrefix=sqPrefix2] for folder " + workspaceFolderPath.toString());
  }

  @Test
  void shutdown_should_stop_all_servers() {
    mockFileInABoundWorkspaceFolder();
    mockFileInABoundWorkspaceFolder2();
    when(enginesFactory.createConnectedEngine("myServer", GLOBAL_SETTINGS))
      .thenReturn(fakeEngine);
    when(enginesFactory.createConnectedEngine("myServer2", GLOBAL_SETTINGS_DIFFERENT_SERVER_ID))
      .thenReturn(fakeEngine2);
    var projectBinding = mock(ProjectBinding.class);
    when(projectBinding.projectKey()).thenReturn(PROJECT_KEY);
    var projectBinding2 = mock(ProjectBinding.class);
    when(projectBinding2.projectKey()).thenReturn(PROJECT_KEY2);
    when(fakeEngine.calculatePathPrefixes(any(), any())).thenReturn(projectBinding);
    when(fakeEngine2.calculatePathPrefixes(any(), any())).thenReturn(projectBinding2);

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    assertThat(logTester.logs())
      .contains("Starting connected SonarLint engine for 'myServer'...");

    var binding2 = underTest.getBinding(fileInAWorkspaceFolderPath2.toUri());
    assertThat(binding2).isNotEmpty();

    assertThat(logTester.logs())
      .contains("Starting connected SonarLint engine for 'myServer2'...");

    verify(enginesFactory).createConnectedEngine(eq("myServer"), any(ServerConnectionSettings.class));
    verify(enginesFactory).createConnectedEngine(eq("myServer2"), any(ServerConnectionSettings.class));

    underTest.shutdown();

    verify(fakeEngine).stop(false);
    verify(fakeEngine2).stop(false);
  }

  @Test
  void failure_during_stop_should_not_prevent_others_to_stop() {
    mockFileInABoundWorkspaceFolder();
    mockFileInABoundWorkspaceFolder2();

    when(enginesFactory.createConnectedEngine(anyString(), any(ServerConnectionSettings.class)))
      .thenReturn(fakeEngine)
      .thenReturn(fakeEngine2);

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    var binding2 = underTest.getBinding(fileInAWorkspaceFolderPath2.toUri());
    assertThat(binding2).isNotEmpty();

    doThrow(new RuntimeException("stop error")).when(fakeEngine).stop(anyBoolean());
    doThrow(new RuntimeException("stop error")).when(fakeEngine2).stop(anyBoolean());

    underTest.shutdown();

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR))
      .contains("Unable to stop engine 'myServer'", "Unable to stop engine 'myServer2'");

    verify(fakeEngine).stop(false);
    verify(fakeEngine2).stop(false);
  }

  @Test
  void update_all_project_bindings_on_get_binding() {
    var folder1 = mockFileInABoundWorkspaceFolder();
    when(foldersManager.getAll()).thenReturn(List.of(folder1));
    when(enginesFactory.createConnectedEngine(anyString(), any(ServerConnectionSettings.class)))
      .thenReturn(fakeEngine);

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine).updateProject(any(), any(), eq(PROJECT_KEY), any());
    verify(fakeEngine).sync(any(), any(), eq(Set.of(PROJECT_KEY)), any());
  }

  @Test
  void should_return_empty_optional_on_invalid_path() {
    var uri = underTest.serverPathToFileUri("invalidPath");

    assertThat(uri).isEmpty();
  }

  @Test
  void should_return_optional_on_valid_path() {
    var serverPath = "src/test/resources/sample-folder/Test.java";
    var projectBindingWrapperMock = mock(ProjectBindingWrapper.class);
    var projectBinding = mock(ProjectBinding.class);
    when(projectBindingWrapperMock.getBinding()).thenReturn(projectBinding);
    when((projectBinding.serverPathToIdePath(serverPath))).thenReturn(Optional.of(serverPath));
    folderBindingCache.put(new File(".").toURI(), Optional.of(projectBindingWrapperMock));

    var uri = underTest.serverPathToFileUri(serverPath);

    assertThat(uri).isNotEmpty();
    assertThat(uri.get().toString())
      .startsWith("file:///")
      .contains("src/test/resources/sample-folder/Test.java");
  }

  @Test
  void should_get_all_projects_for_a_connection() {
    var key1 = "key1";
    var key2 = "key2";
    var name1 = "name1";
    var name2 = "name2";
    var project1 = mock(ServerProject.class);
    when(project1.getKey()).thenReturn(key1);
    when(project1.getName()).thenReturn(name1);
    var project2 = mock(ServerProject.class);
    when(project2.getKey()).thenReturn(key2);
    when(project2.getName()).thenReturn(name2);
    when(fakeEngine.downloadAllProjects(any(), any(), any())).thenReturn(Map.of(
      key1, project1,
      key2, project2
    ));
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    assertThat(underTest.getRemoteProjects(CONNECTION_ID)).containsExactlyInAnyOrderEntriesOf(Map.of(
      key1, name1,
      key2, name2
    ));
  }

  @Test
  void should_get_all_projects_for_default_connection() {
    var key1 = "key1";
    var key2 = "key2";
    var name1 = "name1";
    var name2 = "name2";
    var project1 = mock(ServerProject.class);
    when(project1.getKey()).thenReturn(key1);
    when(project1.getName()).thenReturn(name1);
    var project2 = mock(ServerProject.class);
    when(project2.getKey()).thenReturn(key2);
    when(project2.getName()).thenReturn(name2);
    when(fakeEngine.downloadAllProjects(any(), any(), any())).thenReturn(Map.of(
      key1, project1,
      key2, project2
    ));
    servers.put(SettingsManager.connectionIdOrDefault(null), GLOBAL_SETTINGS);
    assertThat(underTest.getRemoteProjects(null)).containsExactlyInAnyOrderEntriesOf(Map.of(
      key1, name1,
      key2, name2
    ));
  }

  @Test
  void should_get_no_project_for_unknown_connection() {
    assertThatThrownBy(() -> underTest.getRemoteProjects("unknown"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("No server configuration found with ID 'unknown'");
  }

  @Test
  void should_wrap_download_exception_when_downloading_projects() {
    when(fakeEngine.downloadAllProjects(any(), any(), any())).thenThrow(DownloadException.class);
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    assertThatThrownBy(() -> underTest.getRemoteProjects(CONNECTION_ID))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Failed to fetch list of projects from '" + CONNECTION_ID + "'");
  }

  @Test
  void should_update_taint_issue_cache_from_storage() {
    var serverPath = fileInAWorkspaceFolderPath.toUri().toString();
    var fileUri = fileInAWorkspaceFolderPath.toUri();
    var folderUri = workspaceFolderPath.toUri();
    var projectBindingWrapperMock = mock(ProjectBindingWrapper.class);
    var projectBinding = mock(ProjectBinding.class);
    var connectedEngine = mock(ConnectedSonarLintEngine.class);
    when(projectBindingWrapperMock.getBinding()).thenReturn(projectBinding);
    when(projectBindingWrapperMock.getConnectionId()).thenReturn("connectionId");
    when(projectBindingWrapperMock.getEngine()).thenReturn(connectedEngine);

    when(connectedEngine.getServerBranches(any())).thenReturn(new ProjectBranches(Set.of("main", "feature"), "main"));
    when(connectedEngine.getAllServerTaintIssues(any(), any())).thenReturn(List.of(
      new ServerTaintIssue("taint1", false, "ruleKey1",
        "message", fileUri.getRawPath(), Instant.now(), IssueSeverity.CRITICAL, RuleType.VULNERABILITY,
        new TextRangeWithHash(1, 1, 1, 1, ""),
        null, null, null)));

    when((projectBinding.serverPathToIdePath(fileUri.getRawPath()))).thenReturn(Optional.of(FILE_PHP));
    folderBindingCache.put(folderUri, Optional.of(projectBindingWrapperMock));
    connectedEngineCacheByConnectionId.put("connectionId", Optional.of(connectedEngine));
    var workspaceFolderWrapper = new WorkspaceFolderWrapper(folderUri, new WorkspaceFolder(folderUri.toString(), "sample-folder"));
    when(foldersManager.findFolderForFile(fileUri)).thenReturn(Optional.of(workspaceFolderWrapper));

    underTest.getBindingAndRepublishTaints(fileUri);

    verify(diagnosticPublisher).publishDiagnostics(URI.create(serverPath), false);
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
    servers.put(SERVER_ID2, GLOBAL_SETTINGS_DIFFERENT_SERVER_ID);
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
