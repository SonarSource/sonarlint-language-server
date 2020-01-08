/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.UpdateResult;
import org.sonarsource.sonarlint.ls.AnalysisManager;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class ProjectBindingManagerTests {

  private static final String FILE_PHP = "fileInAWorkspaceFolderPath.php";
  private static final String PROJECT_KEY = "myProject";
  private static final String PROJECT_KEY2 = "myProject2";
  private static final ProjectBinding FAKE_BINDING = new ProjectBinding(PROJECT_KEY, "sqPrefix", "idePrefix");
  private static final ProjectBinding FAKE_BINDING2 = new ProjectBinding(PROJECT_KEY2, "sqPrefix2", "idePrefix2");
  private static final String SERVER_ID = "myServer";
  private static final String SERVER_ID2 = "myServer2";
  private static final ServerConnectionSettings GLOBAL_SETTINGS = new ServerConnectionSettings(SERVER_ID, "http://foo", "token", null);
  private static final ServerConnectionSettings GLOBAL_SETTINGS_DIFFERENT_SERVER_ID = new ServerConnectionSettings(SERVER_ID2, "http://foo2", "token2", null);
  private static final WorkspaceFolderSettings UNBOUND_SETTINGS = new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS = new WorkspaceFolderSettings(SERVER_ID, PROJECT_KEY, Collections.emptyMap(), null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS2 = new WorkspaceFolderSettings(SERVER_ID2, PROJECT_KEY2, Collections.emptyMap(), null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS_DIFFERENT_PROJECT_KEY = new WorkspaceFolderSettings(SERVER_ID, PROJECT_KEY2, Collections.emptyMap(), null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS_DIFFERENT_SERVER_ID = new WorkspaceFolderSettings(SERVER_ID2, PROJECT_KEY, Collections.emptyMap(), null);

  @RegisterExtension
  LogTesterJUnit5 logTester = new LogTesterJUnit5();

  @TempDir
  Path basedir;
  private Path workspaceFolderPath;
  private Path workspaceFolderPath2;
  private Path anotherFolderPath;
  private Path fileInAWorkspaceFolderPath;
  private Path fileInAWorkspaceFolderPath2;
  private Path fileNotInAWorkspaceFolderPath;
  private ProjectBindingManager underTest;
  private SettingsManager settingsManager = mock(SettingsManager.class);
  private WorkspaceFoldersManager foldersManager = mock(WorkspaceFoldersManager.class);
  private Map<String, ServerConnectionSettings> servers = new HashMap<String, ServerConnectionSettings>();
  private EnginesFactory enginesFactory = mock(EnginesFactory.class);
  private ConnectedSonarLintEngine fakeEngine = mock(ConnectedSonarLintEngine.class);
  private GlobalStorageStatus globalStorageStatus = mock(GlobalStorageStatus.class);
  private ProjectStorageStatus projectStorageStatus = mock(ProjectStorageStatus.class);
  private UpdateResult updateResult = mock(UpdateResult.class);
  private ConnectedSonarLintEngine fakeEngine2 = mock(ConnectedSonarLintEngine.class);
  private GlobalStorageStatus globalStorageStatus2 = mock(GlobalStorageStatus.class);
  private ProjectStorageStatus projectStorageStatus2 = mock(ProjectStorageStatus.class);
  private UpdateResult updateResult2 = mock(UpdateResult.class);
  private AnalysisManager analysisManager = mock(AnalysisManager.class);

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
      .thenReturn(new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(), false, false));
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(UNBOUND_SETTINGS);

    when(enginesFactory.createConnectedEngine(anyString())).thenReturn(fakeEngine);
    when(globalStorageStatus.isStale()).thenReturn(false);
    when(fakeEngine.getState()).thenReturn(State.UPDATED);
    when(fakeEngine.getGlobalStorageStatus()).thenReturn(globalStorageStatus);
    when(projectStorageStatus.isStale()).thenReturn(false);
    when(fakeEngine.getProjectStorageStatus(PROJECT_KEY)).thenReturn(projectStorageStatus);
    when(fakeEngine.update(any(), any())).thenReturn(updateResult);

    when(globalStorageStatus2.isStale()).thenReturn(false);
    when(fakeEngine2.getState()).thenReturn(State.UPDATED);
    when(fakeEngine2.getGlobalStorageStatus()).thenReturn(globalStorageStatus2);
    when(projectStorageStatus2.isStale()).thenReturn(false);
    when(fakeEngine2.getProjectStorageStatus(PROJECT_KEY)).thenReturn(projectStorageStatus2);
    when(fakeEngine2.update(any(), any())).thenReturn(updateResult2);

    underTest = new ProjectBindingManager(enginesFactory, foldersManager, settingsManager, mock(LanguageClient.class));
    underTest.setAnalysisManager(analysisManager);
  }

  @Test
  public void get_binding_returns_empty_for_single_file_with_no_binding_and_cache_result() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(UNBOUND_SETTINGS);

    assertThat(underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri())).isEmpty();

    // Second call is cached
    assertThat(underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri())).isEmpty();
    verify(settingsManager, times(1)).getCurrentDefaultFolderSettings();

    assertThat(underTest.usesConnectedMode()).isFalse();
  }

  @Test
  public void get_binding_returns_empty_for_file_in_a_folder_with_no_binding() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    folder.setSettings(UNBOUND_SETTINGS);

    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();

    // Second call is cached
    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();
    verify(folder, times(1)).getSettings();
  }

  @Test
  public void get_binding_default_to_standalone_if_unknown_server_id() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    folder.setSettings(BOUND_SETTINGS);

    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();

    assertThat(logTester.logs(LoggerLevel.ERROR))
      .containsOnly("The specified serverId 'myServer' doesn't exist.", "Invalid binding for '" + workspaceFolderPath.toString() + "'");
    assertThat(underTest.usesConnectedMode()).isFalse();
    assertThat(underTest.usesSonarCloud()).isFalse();
  }

  @Test
  public void get_binding_default_to_standalone_if_server_fail_to_start() {
    mockFileInABoundWorkspaceFolder();

    when(enginesFactory.createConnectedEngine(anyString())).thenThrow(new IllegalStateException("Unable to start"));

    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();

    assertThat(logTester.logs(LoggerLevel.ERROR)).containsOnly("Error starting connected SonarLint engine for '" + SERVER_ID + "'");
  }

  @Test
  public void get_binding_should_not_update_if_storage_up_to_date() {
    mockFileInABoundWorkspaceFolder();

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, never()).update(any(), any());
    verify(fakeEngine, never()).updateProject(any(), any(), any());

    assertThat(underTest.usesConnectedMode()).isTrue();
  }

  @Test
  public void get_binding_for_single_file_uses_parent_dir_as_basedir() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND_SETTINGS);
    servers.put(SERVER_ID, GLOBAL_SETTINGS);

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    assertThat(binding.get().getEngine()).isEqualTo(fakeEngine);
    assertThat(binding.get().getServerId()).isEqualTo(SERVER_ID);
    assertThat(binding.get().getServerIssueTracker()).isNotNull();
    assertThat(binding.get().getBinding()).isEqualTo(FAKE_BINDING);

    verify(fakeEngine).calculatePathPrefixes(eq(PROJECT_KEY), argThat(set -> set.contains(FILE_PHP)));
  }

  @Test
  public void get_binding_should_update_if_global_storage_missing() {
    mockFileInABoundWorkspaceFolder();

    when(fakeEngine.getGlobalStorageStatus()).thenReturn(null);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, times(1)).update(any(), any());
    verify(fakeEngine, never()).updateProject(any(), any(), any());
  }

  @Test
  public void get_binding_should_update_if_global_storage_is_stale() {
    mockFileInABoundWorkspaceFolder();

    when(globalStorageStatus.isStale()).thenReturn(true);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, times(1)).update(any(), any());
    verify(fakeEngine, never()).updateProject(any(), any(), any());
  }

  @Test
  public void get_binding_should_update_if_global_storage_need_updated() {
    mockFileInABoundWorkspaceFolder();

    when(fakeEngine.getState()).thenReturn(State.NEED_UPDATE);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, times(1)).update(any(), any());
    verify(fakeEngine, never()).updateProject(any(), any(), any());
  }

  @Test
  public void get_binding_should_update_if_global_storage_never_updated() {
    mockFileInABoundWorkspaceFolder();

    when(fakeEngine.getState()).thenReturn(State.NEVER_UPDATED);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, times(1)).update(any(), any());
    verify(fakeEngine, never()).updateProject(any(), any(), any());
  }

  @Test
  public void get_binding_should_not_update_if_already_updating() {
    mockFileInABoundWorkspaceFolder();

    when(fakeEngine.getState()).thenReturn(State.UPDATING);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, never()).update(any(), any());
    verify(fakeEngine, never()).updateProject(any(), any(), any());
  }

  @Test
  public void get_binding_should_update_if_project_storage_missing() {
    mockFileInABoundWorkspaceFolder();

    when(fakeEngine.getProjectStorageStatus(PROJECT_KEY)).thenReturn(null);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, never()).update(any(), any());
    verify(fakeEngine, times(1)).updateProject(any(), eq(PROJECT_KEY), any());
  }

  @Test
  public void get_binding_should_update_if_project_storage_stale() {
    mockFileInABoundWorkspaceFolder();

    when(projectStorageStatus.isStale()).thenReturn(true);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, never()).update(any(), any());
    verify(fakeEngine, times(1)).updateProject(any(), eq(PROJECT_KEY), any());
  }

  @Test
  public void test_use_sonarcloud() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(new WorkspaceFolderSettings("sonarcloud", PROJECT_KEY, Collections.emptyMap(), null));

    mockFileInABoundWorkspaceFolder();
    servers.put("sonarcloud", new ServerConnectionSettings("sonarcloud", "https://sonarcloud.io", "token", null));

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    assertThat(underTest.usesConnectedMode()).isTrue();
    assertThat(underTest.usesSonarCloud()).isFalse();

    binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    assertThat(underTest.usesConnectedMode()).isTrue();
    assertThat(underTest.usesSonarCloud()).isTrue();
  }

  @Test
  public void ignore_first_change_event() {
    underTest.onChange(null, null, UNBOUND_SETTINGS);
    verifyZeroInteractions(settingsManager, fakeEngine);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  public void ignore_change_if_same_binding() {
    underTest.onChange(null, UNBOUND_SETTINGS, UNBOUND_SETTINGS);
    underTest.onChange(null, BOUND_SETTINGS, BOUND_SETTINGS);
    verifyZeroInteractions(settingsManager, fakeEngine);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  public void ignore_change_if_folder_binding_not_cached() {
    WorkspaceFolderWrapper folder = mockFileInABoundWorkspaceFolder();
    when(folder.getSettings()).thenReturn(UNBOUND_SETTINGS);

    underTest.onChange(folder, BOUND_SETTINGS, UNBOUND_SETTINGS);
    verifyZeroInteractions(settingsManager, fakeEngine);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  public void ignore_change_if_file_binding_not_cached() {
    mockFileOutsideFolder();

    underTest.onChange(null, BOUND_SETTINGS, UNBOUND_SETTINGS);
    verifyZeroInteractions(settingsManager, fakeEngine);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  public void test_unbind_folder() {
    WorkspaceFolderWrapper folder = mockFileInABoundWorkspaceFolder();

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    when(folder.getSettings()).thenReturn(UNBOUND_SETTINGS);
    underTest.onChange(folder, BOUND_SETTINGS, UNBOUND_SETTINGS);

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder);

    binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());

    assertThat(binding).isEmpty();
    verify(fakeEngine).stop(false);
    assertThat(logTester.logs()).contains("Workspace 'WorkspaceFolder[uri=" + workspaceFolderPath.toUri().toString() + ",name=<null>]' unbound");
  }

  @Test
  public void test_unbind_file() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND_SETTINGS);
    servers.put(SERVER_ID, GLOBAL_SETTINGS);
    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());
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
  public void test_rebind_folder_after_project_key_change() {
    WorkspaceFolderWrapper folder = mockFileInABoundWorkspaceFolder();
    when(foldersManager.getAll()).thenReturn(asList(folder));

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
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
      .contains("Resolved binding ProjectBinding[projectKey=myProject2,sqPathPrefix=sqPrefix2,idePathPrefix=idePrefix2] for folder " + workspaceFolderPath.toString());
  }

  @Test

  public void unbind_when_global_server_deleted() {
    Map<String, ServerConnectionSettings> servers = new HashMap<String, ServerConnectionSettings>();
    servers.put(SERVER_ID, GLOBAL_SETTINGS);
    WorkspaceSettings settingsWithServer1 = new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(), false, false);

    servers = new HashMap<String, ServerConnectionSettings>();
    servers.put(SERVER_ID2, GLOBAL_SETTINGS_DIFFERENT_SERVER_ID);
    WorkspaceSettings settingsWithServer2 = new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(), false, false);

    when(settingsManager.getCurrentSettings()).thenReturn(settingsWithServer1);

    WorkspaceFolderWrapper folder = mockFileInABoundWorkspaceFolder();
    when(foldersManager.getAll()).thenReturn(asList(folder));

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    when(settingsManager.getCurrentSettings()).thenReturn(settingsWithServer2);
    underTest.onChange(settingsWithServer1, settingsWithServer2);

    binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isEmpty();

    verify(fakeEngine).stop(anyBoolean());
    assertThat(logTester.logs())
      .contains("The specified serverId 'myServer' doesn't exist.", "Invalid binding for '" + workspaceFolderPath.toString() + "'");
  }

  @Test
  public void test_rebind_file_after_project_key_change() {
    mockFileOutsideFolder();
    when(settingsManager.getCurrentDefaultFolderSettings()).thenReturn(BOUND_SETTINGS);
    servers.put(SERVER_ID, GLOBAL_SETTINGS);
    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());
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
      .contains("Resolved binding ProjectBinding[projectKey=myProject2,sqPathPrefix=sqPrefix2,idePathPrefix=idePrefix2] for folder " + anotherFolderPath.toString());
  }

  @Test
  public void test_rebind_folder_after_server_id_change() {
    servers.put(SERVER_ID2, GLOBAL_SETTINGS_DIFFERENT_SERVER_ID);
    WorkspaceFolderWrapper folder = mockFileInABoundWorkspaceFolder();

    when(enginesFactory.createConnectedEngine(anyString()))
      .thenReturn(fakeEngine)
      .thenReturn(fakeEngine2);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
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
      .contains("Resolved binding ProjectBinding[projectKey=myProject2,sqPathPrefix=sqPrefix2,idePathPrefix=idePrefix2] for folder " + workspaceFolderPath.toString());
  }

  @Test
  public void shutdown_should_stop_all_servers() {
    mockFileInABoundWorkspaceFolder();
    mockFileInABoundWorkspaceFolder2();

    when(enginesFactory.createConnectedEngine("myServer"))
      .thenReturn(fakeEngine);
    when(enginesFactory.createConnectedEngine("myServer2"))
      .thenReturn(fakeEngine2);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    assertThat(logTester.logs())
      .contains("Starting connected SonarLint engine for 'myServer'...");

    Optional<ProjectBindingWrapper> binding2 = underTest.getBinding(fileInAWorkspaceFolderPath2.toUri());
    assertThat(binding2).isNotEmpty();

    assertThat(logTester.logs())
      .contains("Starting connected SonarLint engine for 'myServer2'...");

    verify(enginesFactory).createConnectedEngine("myServer");
    verify(enginesFactory).createConnectedEngine("myServer2");

    underTest.shutdown();

    verify(fakeEngine).stop(false);
    verify(fakeEngine2).stop(false);
  }

  @Test
  public void failure_during_stop_should_not_prevent_others_to_stop() {
    mockFileInABoundWorkspaceFolder();
    mockFileInABoundWorkspaceFolder2();

    when(enginesFactory.createConnectedEngine(anyString()))
      .thenReturn(fakeEngine)
      .thenReturn(fakeEngine2);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    Optional<ProjectBindingWrapper> binding2 = underTest.getBinding(fileInAWorkspaceFolderPath2.toUri());
    assertThat(binding2).isNotEmpty();

    doThrow(new RuntimeException("stop error")).when(fakeEngine).stop(anyBoolean());
    doThrow(new RuntimeException("stop error")).when(fakeEngine2).stop(anyBoolean());

    underTest.shutdown();

    assertThat(logTester.logs(LoggerLevel.ERROR))
      .contains("Unable to stop engine 'myServer'", "Unable to stop engine 'myServer2'");

    verify(fakeEngine).stop(false);
    verify(fakeEngine2).stop(false);
  }

  @Test
  public void update_all_project_bindings_update_already_started_servers() {
    WorkspaceFolderWrapper folder1 = mockFileInABoundWorkspaceFolder();
    WorkspaceFolderWrapper folder2 = mockFileInABoundWorkspaceFolder2();

    when(foldersManager.getAll()).thenReturn(asList(folder1, folder2));

    when(fakeEngine2.getProjectStorageStatus(PROJECT_KEY2)).thenReturn(projectStorageStatus2);

    when(enginesFactory.createConnectedEngine(anyString()))
      .thenReturn(fakeEngine)
      .thenReturn(fakeEngine2);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    Optional<ProjectBindingWrapper> binding2 = underTest.getBinding(fileInAWorkspaceFolderPath2.toUri());
    assertThat(binding2).isNotEmpty();

    underTest.updateAllBindings();

    verify(fakeEngine).update(any(), any());
    verify(fakeEngine).updateProject(any(), eq(PROJECT_KEY), any());
    verify(fakeEngine2).update(any(), any());
    verify(fakeEngine2).updateProject(any(), eq(PROJECT_KEY2), any());

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder1);
    verify(analysisManager).analyzeAllOpenFilesInFolder(folder2);
    verifyNoMoreInteractions(analysisManager);
  }

  @Test
  public void update_all_project_bindings_update_not_started_servers() {
    WorkspaceFolderWrapper folder1 = mockFileInABoundWorkspaceFolder();
    WorkspaceFolderWrapper folder2 = mockFileInABoundWorkspaceFolder2();

    when(foldersManager.getAll()).thenReturn(asList(folder1, folder2));

    when(fakeEngine2.getProjectStorageStatus(PROJECT_KEY2)).thenReturn(projectStorageStatus2);

    when(enginesFactory.createConnectedEngine(anyString()))
      .thenReturn(fakeEngine)
      .thenReturn(fakeEngine2);

    underTest.updateAllBindings();

    verify(fakeEngine).update(any(), any());
    verify(fakeEngine).updateProject(any(), eq(PROJECT_KEY), any());
    verify(fakeEngine2).update(any(), any());
    verify(fakeEngine2).updateProject(any(), eq(PROJECT_KEY2), any());

    verify(analysisManager).analyzeAllOpenFilesInFolder(folder1);
    verify(analysisManager).analyzeAllOpenFilesInFolder(folder2);
    verifyNoMoreInteractions(analysisManager);
  }

  private WorkspaceFolderWrapper mockFileInABoundWorkspaceFolder() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    folder.setSettings(BOUND_SETTINGS);
    servers.put(SERVER_ID, GLOBAL_SETTINGS);
    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);
    return folder;
  }

  private WorkspaceFolderWrapper mockFileInABoundWorkspaceFolder2() {
    WorkspaceFolderWrapper folder2 = mockFileInAFolder2();
    folder2.setSettings(BOUND_SETTINGS2);
    servers.put(SERVER_ID2, GLOBAL_SETTINGS_DIFFERENT_SERVER_ID);
    when(fakeEngine2.calculatePathPrefixes(eq(PROJECT_KEY2), any())).thenReturn(FAKE_BINDING2);
    return folder2;
  }

  private void mockFileOutsideFolder() {
    when(foldersManager.findFolderForFile(fileNotInAWorkspaceFolderPath.toUri())).thenReturn(Optional.empty());
  }

  private WorkspaceFolderWrapper mockFileInAFolder() {
    WorkspaceFolderWrapper folderWrapper = spy(new WorkspaceFolderWrapper(workspaceFolderPath.toUri(), new WorkspaceFolder(workspaceFolderPath.toUri().toString())));
    when(foldersManager.findFolderForFile(fileInAWorkspaceFolderPath.toUri())).thenReturn(Optional.of(folderWrapper));
    return folderWrapper;
  }

  private WorkspaceFolderWrapper mockFileInAFolder2() {
    WorkspaceFolderWrapper folderWrapper2 = spy(new WorkspaceFolderWrapper(workspaceFolderPath2.toUri(), new WorkspaceFolder(workspaceFolderPath2.toUri().toString())));
    when(foldersManager.findFolderForFile(fileInAWorkspaceFolderPath2.toUri())).thenReturn(Optional.of(folderWrapper2));
    return folderWrapper2;
  }
}
