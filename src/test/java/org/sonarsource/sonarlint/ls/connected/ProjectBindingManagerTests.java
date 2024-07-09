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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetProjectNamesByKeyResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.ls.ForcedAnalysisCoordinator;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
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
import static org.mockito.ArgumentMatchers.eq;
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
  private static final org.sonarsource.sonarlint.core.serverconnection.ProjectBinding FAKE_BINDING = new org.sonarsource.sonarlint.core.serverconnection.ProjectBinding(PROJECT_KEY, "sqPrefix", "idePrefix");
  private static final org.sonarsource.sonarlint.core.serverconnection.ProjectBinding FAKE_BINDING2 = new org.sonarsource.sonarlint.core.serverconnection.ProjectBinding(PROJECT_KEY2, "sqPrefix2", "idePrefix2");
  private static final String CONNECTION_ID = "myServer";
  private static final String SERVER_ID2 = "myServer2";
  private static final BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
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
  ConcurrentMap<URI, Optional<ProjectBinding>> folderBindingCache;
  private ProjectBindingManager underTest;
  private final SettingsManager settingsManager = mock(SettingsManager.class);
  private final WorkspaceFoldersManager foldersManager = mock(WorkspaceFoldersManager.class);
  private final Map<String, ServerConnectionSettings> servers = new HashMap<>();
  private final SonarLintAnalysisEngine fakeEngine = mock(SonarLintAnalysisEngine.class);
  private final ForcedAnalysisCoordinator analysisManager = mock(ForcedAnalysisCoordinator.class);
  SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  private final OpenNotebooksCache openNotebooksCache = mock(OpenNotebooksCache.class);
  private final OpenFilesCache openFilesCache = mock(OpenFilesCache.class);

  @BeforeEach
  public void prepare() throws IOException {
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


    when(backendServiceFacade.getBackendService()).thenReturn(mock(BackendService.class));
    when(client.getTokenForServer(any())).thenReturn(CompletableFuture.supplyAsync(() -> "token"));

    folderBindingCache = new ConcurrentHashMap<>();

    when(openNotebooksCache.getFile(any(URI.class))).thenReturn(Optional.empty());

    underTest = new ProjectBindingManager(foldersManager, settingsManager, client, folderBindingCache, logTester.getLogger(),
      backendServiceFacade, openNotebooksCache);
    underTest.setAnalysisManager(analysisManager);
  }

  private static WorkspaceSettings newWorkspaceSettingsWithServers(Map<String, ServerConnectionSettings> servers) {
    return new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false, false, "", false, "");
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

    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("The specified connection id 'myServer' doesn't exist."))
      .anyMatch(log -> log.contains("Invalid binding for '" + workspaceFolderPath.toString() + "'"));
    assertThat(underTest.usesConnectedMode()).isFalse();
    assertThat(underTest.usesSonarCloud()).isFalse();
  }

  @Test
  void get_binding_should_update_if_project_storage_missing() {
    mockFileInABoundWorkspaceFolder();

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());

    assertThat(binding).isNotEmpty();
  }

  @Test
  void test_use_sonarcloud() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(new WorkspaceFolderSettings("sonarcloud", PROJECT_KEY, Collections.emptyMap(), null, null));

    mockFileInABoundWorkspaceFolder();
    servers.put("sonarcloud", new ServerConnectionSettings("sonarcloud", "https://sonarcloud.io", "token", null, true));

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
    assertThat(logTester.logs()).anyMatch(log -> log.contains("Workspace 'WorkspaceFolder[name=<null>,uri=" + workspaceFolderPath.toUri() + "]' unbound"));
  }

  @Test
  void test_unbind_file() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND_SETTINGS);
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);

    var binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(UNBOUND_SETTINGS);
    underTest.onChange(null, BOUND_SETTINGS, UNBOUND_SETTINGS);

    verify(analysisManager).analyzeAllOpenFilesInFolder(null);

    binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());

    assertThat(binding).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("All files outside workspace are now unbound"));
  }

  @Test
  void test_rebind_folder_after_project_key_change() {
    var folder = mockFileInABoundWorkspaceFolder();
    when(foldersManager.getAll()).thenReturn(List.of(folder));

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    when(folder.getSettings()).thenReturn(BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);

    underTest.onChange(folder, BOUND_SETTINGS, BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder);

    binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());

    assertThat(binding).isNotEmpty();
    verify(fakeEngine, never()).stop();
    assertThat(logTester.logs())
      .anyMatch(log -> log.contains("Resolved binding myProject2 for folder " + workspaceFolderPath.toString()));
  }

  @Test
  void test_rebind_file_after_project_key_change() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND_SETTINGS);
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);

    var binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);

    underTest.onChange(null, BOUND_SETTINGS, BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);

    verify(analysisManager).analyzeAllOpenFilesInFolder(null);

    binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());

    assertThat(binding).isNotEmpty();
    verify(fakeEngine, never()).stop();
    assertThat(logTester.logs())
      .anyMatch(log -> log.contains("Resolved binding myProject2 for folder " + anotherFolderPath.toString()));
  }

  @Test
  void update_all_project_bindings_on_get_binding() {
    var folder1 = mockFileInABoundWorkspaceFolder();
    when(foldersManager.getAll()).thenReturn(List.of(folder1));

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());

    assertThat(binding).isNotEmpty();
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
    when(backendServiceFacade.getBackendService().getAllProjects(any()))
      .thenReturn(CompletableFuture.completedFuture(new GetAllProjectsResponse(List.of(
        new SonarProjectDto(key1, name1),
        new SonarProjectDto(key2, name2)
      ))));
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    assertThat(underTest.getRemoteProjects(CONNECTION_ID)).containsExactlyInAnyOrderEntriesOf(Map.of(
      key1, name1,
      key2, name2
    ));
  }

  @Test
  void should_get_project_names_for_projects() throws ExecutionException, InterruptedException {
    var key1 = "key1";
    var key2 = "key2";
    var name1 = "name1";
    var name2 = "name2";
    when(backendServiceFacade.getBackendService().getProjectNamesByKeys(any(), eq(List.of(key1, key2))))
      .thenReturn(CompletableFuture.completedFuture(new GetProjectNamesByKeyResponse(Map.of(
        key1, name1,
        key2, name2
      ))));
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    assertThat(underTest.getRemoteProjectsByKeys(CONNECTION_ID, List.of(key1, key2)).get()).containsExactlyInAnyOrderEntriesOf(Map.of(
      key1, name1,
      key2, name2
    ));
  }

  @Test
  void should_throw_on_download_exception_when_getting_project_names_for_projects() {
    when(backendServiceFacade.getBackendService().getProjectNamesByKeys(any(), any()))
      .thenThrow(DownloadException.class);
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    var projectList = List.of("key1", "key2");
    assertThatThrownBy(() -> underTest.getRemoteProjectsByKeys(CONNECTION_ID, projectList))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Failed to fetch list of projects from '" + CONNECTION_ID + "'");
  }

  @Test
  void should_get_all_projects_for_default_connection() {
    var key1 = "key1";
    var key2 = "key2";
    var name1 = "name1";
    var name2 = "name2";
    when(backendServiceFacade.getBackendService().getAllProjects(any()))
      .thenReturn(CompletableFuture.completedFuture(new GetAllProjectsResponse(List.of(
        new SonarProjectDto(key1, name1),
        new SonarProjectDto(key2, name2)
      ))));
    servers.put(SettingsManager.connectionIdOrDefault(null), GLOBAL_SETTINGS);
    assertThat(underTest.getRemoteProjects("<default>")).containsExactlyInAnyOrderEntriesOf(Map.of(
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
    when(backendServiceFacade.getBackendService().getAllProjects(any())).thenThrow(DownloadException.class);
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    assertThatThrownBy(() -> underTest.getRemoteProjects(CONNECTION_ID))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Failed to fetch list of projects from '" + CONNECTION_ID + "'");
  }

  @Test
  void shouldComputeEmptyFullFilePathFromRelative() {
    var fullFilePath = underTest.fullFilePathFromRelative(Path.of("idePath"), "connectionId", "projectKey");

    assertThat(fullFilePath).isEmpty();
  }

  @Test
  void shouldComputeFullFilePathFromRelative() {
    var result = Paths.get(URI.create(URI.create("file:///idePath").toString()))
      .resolve(Path.of("filePath"))
      .toUri();
    folderBindingCache.put(URI.create("file:///idePath"), Optional.of(new ProjectBinding("connectionId", "projectKey")));

    var fullFilePath = underTest.fullFilePathFromRelative(Path.of("filePath"), "connectionId", "projectKey");

    assertThat(fullFilePath).contains(result);
  }

  @Test
  void should_get_binding_if_existis_empty() {
    var uri = URI.create("file:///folderUri");

    var maybeBinding = underTest.getBindingIfExists(uri);

    assertThat(maybeBinding).isEmpty();
  }

  @Test
  void should_get_binding_if_existis() {
    var fileUri = URI.create("file:///fileUri");
    var folderUri = URI.create("file:///folderUri");
    when(foldersManager.findFolderForFile(fileUri))
      .thenReturn(Optional.of(new WorkspaceFolderWrapper(folderUri, null, null)));
    folderBindingCache.put(folderUri, Optional.of(new ProjectBinding("connectionId",
      "projectKey")));
    var maybeBinding = underTest.getBindingIfExists(fileUri);

    assertThat(maybeBinding.get().connectionId()).isEqualTo("connectionId");
    assertThat(maybeBinding.get().projectKey()).isEqualTo("projectKey");
  }

  private WorkspaceFolderWrapper mockFileInABoundWorkspaceFolder() {
    var folder = mockFileInAFolder();
    folder.setSettings(BOUND_SETTINGS);
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    return folder;
  }

  private void mockFileOutsideFolder() {
    when(foldersManager.findFolderForFile(fileNotInAWorkspaceFolderPath.toUri())).thenReturn(Optional.empty());
  }

  private WorkspaceFolderWrapper mockFileInAFolder() {
    var folderWrapper = spy(new WorkspaceFolderWrapper(workspaceFolderPath.toUri(), new WorkspaceFolder(workspaceFolderPath.toUri().toString()), logTester.getLogger()));
    when(foldersManager.findFolderForFile(fileInAWorkspaceFolderPath.toUri())).thenReturn(Optional.of(folderWrapper));
    return folderWrapper;
  }

  private WorkspaceFolderWrapper mockFileInAFolder2() {
    var folderWrapper2 = spy(new WorkspaceFolderWrapper(workspaceFolderPath2.toUri(), new WorkspaceFolder(workspaceFolderPath2.toUri().toString()), logTester.getLogger()));
    when(foldersManager.findFolderForFile(fileInAWorkspaceFolderPath2.toUri())).thenReturn(Optional.of(folderWrapper2));
    return folderWrapper2;
  }
}
