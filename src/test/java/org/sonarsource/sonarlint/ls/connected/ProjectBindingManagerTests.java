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
package org.sonarsource.sonarlint.ls.connected;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetProjectNamesByKeyResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.ls.ForcedAnalysisCoordinator;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.notebooks.VersionedOpenNotebook;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScopeManager;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import testutils.ImmediateExecutorService;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
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
  private static final String CONNECTION_ID = "myServer";
  private static final BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  private static final ServerConnectionSettings GLOBAL_SETTINGS = new ServerConnectionSettings(CONNECTION_ID, "http://foo", "token", null, true, SonarCloudRegion.EU);
  private static final WorkspaceFolderSettings UNBOUND_SETTINGS = new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null, null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS = new WorkspaceFolderSettings(CONNECTION_ID, PROJECT_KEY, Collections.emptyMap(), null, null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS_DIFFERENT_PROJECT_KEY = new WorkspaceFolderSettings(CONNECTION_ID, PROJECT_KEY2, Collections.emptyMap(), null, null);
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
  private ProjectBindingManager underTest;
  private ConfigurationScopeManager scopeManager;
  private BackendService backendService;
  private final SettingsManager settingsManager = mock(SettingsManager.class);
  private final WorkspaceFoldersManager foldersManager = mock(WorkspaceFoldersManager.class);
  private final Map<String, ServerConnectionSettings> servers = new HashMap<>();
  private final ForcedAnalysisCoordinator analysisManager = mock(ForcedAnalysisCoordinator.class);
  SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  private final OpenNotebooksCache openNotebooksCache = mock(OpenNotebooksCache.class);

  @BeforeEach
  void prepare() throws IOException {
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

    backendService = mock(BackendService.class);
    when(backendServiceFacade.getBackendService()).thenReturn(backendService);
    when(client.getTokenForServer(any())).thenReturn(CompletableFuture.supplyAsync(() -> "token"));
    when(settingsManager.connectionOf(any())).thenAnswer(invocation -> servers.get(SettingsManager.connectionIdOrDefault(invocation.getArgument(0))));


    when(openNotebooksCache.getFile(any(URI.class))).thenReturn(Optional.empty());

    var folderSettingsSource = new FolderSettingsBindingSource(settingsManager, logTester.getLogger());
    var sources = new BindingSources(List.of(folderSettingsSource));
    scopeManager = new ConfigurationScopeManager(new ImmediateExecutorService(), foldersManager, sources, backendServiceFacade, logTester.getLogger());
    underTest = new ProjectBindingManager(foldersManager, settingsManager, client, logTester.getLogger(),
      backendServiceFacade, openNotebooksCache, scopeManager);
    underTest.setAnalysisManager(analysisManager);
    scopeManager.addListener(underTest);
  }

  private static WorkspaceSettings newWorkspaceSettingsWithServers(Map<String, ServerConnectionSettings> servers) {
    return new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false, "", false, true, "", false);
  }

  @Test
  void get_binding_returns_empty_for_a_file_outside_every_folder() {
    mockFileOutsideFolder();

    assertThat(underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri())).isEmpty();

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
    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();

    verify(folder, times(1)).getSettings();
  }

  @Test
  void get_binding_default_to_standalone_if_unknown_server_id() {
    var folder = mockFileInAFolder();
    folder.setSettings(BOUND_SETTINGS);

    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();

    assertThat(logTester.logs(MessageType.Log))
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
    mockFileInABoundWorkspaceFolder();

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    assertThat(underTest.usesConnectedMode()).isTrue();
    assertThat(underTest.usesSonarCloud()).isFalse();

    var sonarCloudFolder = new WorkspaceFolderWrapper(workspaceFolderPath2.toUri(),
      new WorkspaceFolder(workspaceFolderPath2.toUri().toString(), workspaceFolderPath2.getFileName().toString()), logTester.getLogger());
    sonarCloudFolder.setSettings(new WorkspaceFolderSettings("sonarcloud", PROJECT_KEY, Collections.emptyMap(), null, null));
    servers.put("sonarcloud", new ServerConnectionSettings("sonarcloud", "https://sonarcloud.io", "token", null, true, SonarCloudRegion.EU));
    scopeManager.added(sonarCloudFolder);
    when(foldersManager.findFolderForFile(fileInAWorkspaceFolderPath2.toUri())).thenReturn(Optional.of(sonarCloudFolder));

    binding = underTest.getBinding(fileInAWorkspaceFolderPath2.toUri());
    assertThat(binding).isNotEmpty();

    assertThat(underTest.usesConnectedMode()).isTrue();
    assertThat(underTest.usesSonarCloud()).isTrue();
  }

  @Test
  void ignore_first_change_event() {
    underTest.onChange(null, null, UNBOUND_SETTINGS);
    verifyNoInteractions(settingsManager);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void ignore_change_if_same_binding() {
    underTest.onChange(null, UNBOUND_SETTINGS, UNBOUND_SETTINGS);
    underTest.onChange(null, BOUND_SETTINGS, BOUND_SETTINGS);
    verifyNoInteractions(settingsManager);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void test_unbind_folder() {
    var folder = mockFileInABoundWorkspaceFolder();

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();
    clearInvocations(client);

    folder.setSettings(UNBOUND_SETTINGS);
    underTest.onChange(folder, BOUND_SETTINGS, UNBOUND_SETTINGS);

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder);
    verify(backendService).unbind(workspaceFolderPath.toUri().toString());
    verify(client).bindingsChanged();

    binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());

    assertThat(binding).isEmpty();
  }

  @Test
  void test_rebind_folder_after_project_key_change() {
    var folder = mockFileInABoundWorkspaceFolder();
    when(foldersManager.getAll()).thenReturn(List.of(folder));

    var binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    folder.setSettings(BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);

    underTest.onChange(folder, BOUND_SETTINGS, BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder);
    verify(backendService).bind(workspaceFolderPath.toUri().toString(), new ProjectBinding(CONNECTION_ID, PROJECT_KEY2));

    binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());

    assertThat(binding).isNotEmpty();
    assertThat(logTester.logs())
      .anyMatch(log -> log.contains("Resolved binding myProject2 for folder " + workspaceFolderPath.toString()));
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
    when(project1.key()).thenReturn(key1);
    when(project1.name()).thenReturn(name1);
    var project2 = mock(ServerProject.class);
    when(project2.key()).thenReturn(key2);
    when(project2.name()).thenReturn(name2);
    when(backendServiceFacade.getBackendService().getAllProjects(any()))
      .thenReturn(CompletableFuture.completedFuture(new GetAllProjectsResponse(List.of(
        new SonarProjectDto(key1, name1),
        new SonarProjectDto(key2, name2)))));
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    assertThat(underTest.getRemoteProjects(CONNECTION_ID)).containsExactlyInAnyOrderEntriesOf(Map.of(
      key1, name1,
      key2, name2));
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
        key2, name2))));
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);
    assertThat(underTest.getRemoteProjectsByKeys(CONNECTION_ID, List.of(key1, key2)).get()).containsExactlyInAnyOrderEntriesOf(Map.of(
      key1, name1,
      key2, name2));
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
        new SonarProjectDto(key2, name2)))));
    servers.put(SettingsManager.connectionIdOrDefault(null), GLOBAL_SETTINGS);
    assertThat(underTest.getRemoteProjects("<default>")).containsExactlyInAnyOrderEntriesOf(Map.of(
      key1, name1,
      key2, name2));
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
  void validateConnection_should_handle_null_token_exception() {
    var connectionId = "connectionWithNullToken";
    var serverConnectionSettings = new ServerConnectionSettings(connectionId, "http://localhost:8080", null, null, false, null);
    servers.put(connectionId, serverConnectionSettings);

    underTest.validateConnection(connectionId);

    verify(client).reportConnectionCheckResult(argThat(result -> result.getConnectionId().equals(connectionId) &&
      !result.isSuccess() &&
      Objects.equals(result.getReason(), "Invalid credentials: Token cannot be null or empty for connection validation")));
  }

  @Test
  void validateConnection_should_handle_empty_token_exception() {
    var connectionId = "connectionWithEmptyToken";
    var serverConnectionSettings = new ServerConnectionSettings(connectionId, "http://localhost:8080", "", null, false, null);
    servers.put(connectionId, serverConnectionSettings);

    underTest.validateConnection(connectionId);

    verify(client).reportConnectionCheckResult(argThat(result -> result.getConnectionId().equals(connectionId) &&
      !result.isSuccess() &&
      Objects.equals(result.getReason(), "Invalid credentials: Token cannot be null or empty for connection validation")));
  }

  @Test
  void validateConnection_should_handle_unknown_connection() {
    var connectionId = "unknownConnection";

    underTest.validateConnection(connectionId);

    // Should not call reportConnectionCheckResult for unknown connections
    verifyNoInteractions(client);
  }






  @Test
  void a_change_of_the_scopes_tells_the_client() {
    underTest.scopesChanged();

    verify(client).bindingsChanged();
  }

  @Test
  void getBindings_reports_what_was_declared_and_not_what_the_settings_say_now() {
    var folder = mockFileInABoundWorkspaceFolder();
    scopeManager.added(folder);

    folder.setSettings(BOUND_SETTINGS_DIFFERENT_PROJECT_KEY);

    assertThat(underTest.getBindings()).singleElement().satisfies(scope -> {
      assertThat(scope.uri()).isEqualTo(workspaceFolderPath.toUri());
      assertThat(scope.binding()).isEqualTo(new ProjectBinding(CONNECTION_ID, PROJECT_KEY));
      assertThat(scope.origin()).isEqualTo(Origin.SETTINGS);
    });
  }

  @Test
  void getBindings_skips_a_settings_binding_whose_connection_does_not_exist() {
    var folder = mockFileInAFolder();
    folder.setSettings(BOUND_SETTINGS);
    scopeManager.added(folder);

    assertThat(underTest.getBindings()).isEmpty();
  }





  @Test
  void adding_a_connection_resolves_the_settings_bindings_that_were_waiting_for_it() {
    var folder = mockFileInAFolder();
    folder.setSettings(BOUND_SETTINGS);
    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);

    underTest.onChange(newWorkspaceSettingsWithServers(Map.of()), newWorkspaceSettingsWithServers(servers));

    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).contains(new ProjectBinding(CONNECTION_ID, PROJECT_KEY));
  }


  @Test
  void a_folder_settings_change_rebinds_the_folder_and_tells_the_client() {
    var folder = mockFileInAFolder();
    folder.setSettings(UNBOUND_SETTINGS);
    scopeManager.added(folder);
    clearInvocations(client);
    folder.setSettings(BOUND_SETTINGS);
    servers.put(CONNECTION_ID, GLOBAL_SETTINGS);

    underTest.onChange(folder, UNBOUND_SETTINGS, BOUND_SETTINGS);

    verify(backendService).bind(workspaceFolderPath.toUri().toString(), new ProjectBinding(CONNECTION_ID, PROJECT_KEY));
    verify(client).bindingsChanged();
    verify(analysisManager).analyzeAllOpenFilesInFolder(folder);
  }

  @Test
  void a_folder_settings_change_that_leaves_the_binding_alone_does_nothing() {
    var folder = mockFileInAFolder();

    underTest.onChange(folder, BOUND_SETTINGS, BOUND_SETTINGS);

    verify(backendService, never()).bind(any(), any());
    verify(backendService, never()).unbind(any());
    verify(analysisManager, never()).analyzeAllOpenFilesInFolder(any());
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
    var folderWrapper = spy(new WorkspaceFolderWrapper(workspaceFolderPath.toUri(),
      new WorkspaceFolder(workspaceFolderPath.toUri().toString(), workspaceFolderPath.getFileName().toString()), logTester.getLogger()));
    var registered = new AtomicBoolean();
    when(foldersManager.findFolderForFile(fileInAWorkspaceFolderPath.toUri())).thenAnswer(invocation -> {
      if (registered.compareAndSet(false, true)) {
        scopeManager.added(folderWrapper);
      }
      return Optional.of(folderWrapper);
    });
    return folderWrapper;
  }

}
