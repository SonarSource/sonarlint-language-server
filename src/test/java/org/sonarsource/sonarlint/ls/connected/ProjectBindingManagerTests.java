/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.api.utils.log.test.LogTesterJUnit5;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProjectBindingManagerTests {

  private static final String FILE_PHP = "fileInAWorkspaceFolderPath.php";
  private static final String PROJECT_KEY = "myProject";
  private static final ProjectBinding FAKE_BINDING = new ProjectBinding(PROJECT_KEY, "sqPrefix", "idePrefix");
  private static final String SERVER_ID = "myServer";
  private static final ServerConnectionSettings GLOBAL_SETTINGS = new ServerConnectionSettings(SERVER_ID, "http://foo", "token", null);
  private static final WorkspaceFolderSettings UNBOUND_SETTINGS = new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null);
  private static final WorkspaceFolderSettings BOUND_SETTINGS = new WorkspaceFolderSettings(SERVER_ID, PROJECT_KEY, Collections.emptyMap(), null);

  @RegisterExtension
  LogTesterJUnit5 logTester = new LogTesterJUnit5();

  @TempDir
  Path basedir;
  private Path workspaceFolderPath;
  private Path anotherFolderPath;
  private Path fileInAWorkspaceFolderPath;
  private Path fileNotInAWorkspaceFolderPath;
  private ProjectBindingManager underTest;
  private SettingsManager settingsManager = mock(SettingsManager.class);
  private WorkspaceFoldersManager foldersManager = mock(WorkspaceFoldersManager.class);
  private Map<String, ServerConnectionSettings> servers = new HashMap<String, ServerConnectionSettings>();
  private Function<ConnectedGlobalConfiguration, ConnectedSonarLintEngine> engineFactory = mock(Function.class);
  private ConnectedSonarLintEngine fakeEngine = mock(ConnectedSonarLintEngine.class);
  private GlobalStorageStatus globalStorageStatus = mock(GlobalStorageStatus.class);
  private ProjectStorageStatus projectStorageStatus = mock(ProjectStorageStatus.class);

  @BeforeEach
  public void prepare() throws IOException {
    workspaceFolderPath = basedir.resolve("myWorkspaceFolder");
    Files.createDirectories(workspaceFolderPath);
    anotherFolderPath = basedir.resolve("anotherFolder");
    Files.createDirectories(anotherFolderPath);
    fileInAWorkspaceFolderPath = workspaceFolderPath.resolve(FILE_PHP);
    Files.createFile(fileInAWorkspaceFolderPath);
    fileNotInAWorkspaceFolderPath = anotherFolderPath.resolve(FILE_PHP);
    Files.createFile(fileNotInAWorkspaceFolderPath);

    when(settingsManager.getCurrentSettings())
      .thenReturn(new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList()));

    when(engineFactory.apply(any(ConnectedGlobalConfiguration.class))).thenReturn(fakeEngine);
    when(globalStorageStatus.isStale()).thenReturn(false);
    when(fakeEngine.getState()).thenReturn(State.UPDATED);
    when(fakeEngine.getGlobalStorageStatus()).thenReturn(globalStorageStatus);
    when(projectStorageStatus.isStale()).thenReturn(false);
    when(fakeEngine.getProjectStorageStatus(PROJECT_KEY)).thenReturn(projectStorageStatus);

    underTest = new ProjectBindingManager(foldersManager, settingsManager, mock(LanguageClientLogOutput.class), engineFactory);
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
    when(folder.getSettings()).thenReturn(UNBOUND_SETTINGS);

    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();

    // Second call is cached
    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();
    verify(folder, times(1)).getSettings();
  }

  @Test
  public void get_binding_default_to_standalone_if_unknown_server_id() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    when(folder.getSettings()).thenReturn(BOUND_SETTINGS);
    // when(workspaceFolderPath.toString()).thenReturn("the workspaceFolderPath");

    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();

    assertThat(logTester.logs(LoggerLevel.ERROR))
      .containsOnly("Invalid binding for '" + workspaceFolderPath.toString() + "': the specified serverId '" + SERVER_ID + "' doesn't exist.");
    assertThat(underTest.usesConnectedMode()).isFalse();
  }

  @Test
  public void get_binding_default_to_standalone_if_server_fail_to_start() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    when(folder.getSettings()).thenReturn(BOUND_SETTINGS);
    servers.put(SERVER_ID, GLOBAL_SETTINGS);

    when(engineFactory.apply(any(ConnectedGlobalConfiguration.class))).thenThrow(new IllegalStateException("Unable to start"));

    assertThat(underTest.getBinding(fileInAWorkspaceFolderPath.toUri())).isEmpty();

    assertThat(logTester.logs(LoggerLevel.ERROR)).containsOnly("Error starting connected SonarLint engine for '" + SERVER_ID + "'");
  }

  @Test
  public void get_binding_should_not_update_if_storage_up_to_date() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    when(folder.getSettings()).thenReturn(BOUND_SETTINGS);

    servers.put(SERVER_ID, GLOBAL_SETTINGS);

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

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

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), argThat(set -> set.contains(FILE_PHP)))).thenReturn(FAKE_BINDING);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileNotInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    assertThat(binding.get().getEngine()).isEqualTo(fakeEngine);
    assertThat(binding.get().getServerId()).isEqualTo(SERVER_ID);
    assertThat(binding.get().getServerIssueTracker()).isNotNull();
    assertThat(binding.get().getBinding()).isEqualTo(FAKE_BINDING);
  }

  @Test
  public void get_binding_should_update_if_global_storage_missing() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    when(folder.getSettings()).thenReturn(BOUND_SETTINGS);

    servers.put(SERVER_ID, GLOBAL_SETTINGS);

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    when(fakeEngine.getGlobalStorageStatus()).thenReturn(null);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, times(1)).update(any(), any());
    verify(fakeEngine, never()).updateProject(any(), any(), any());
  }

  @Test
  public void get_binding_should_update_if_global_storage_is_stale() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    when(folder.getSettings()).thenReturn(BOUND_SETTINGS);

    servers.put(SERVER_ID, GLOBAL_SETTINGS);

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    when(globalStorageStatus.isStale()).thenReturn(true);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, times(1)).update(any(), any());
    verify(fakeEngine, never()).updateProject(any(), any(), any());
  }

  @Test
  public void get_binding_should_update_if_global_storage_need_updated() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    when(folder.getSettings()).thenReturn(BOUND_SETTINGS);

    servers.put(SERVER_ID, GLOBAL_SETTINGS);

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    when(fakeEngine.getState()).thenReturn(State.NEED_UPDATE);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, times(1)).update(any(), any());
    verify(fakeEngine, never()).updateProject(any(), any(), any());
  }

  @Test
  public void get_binding_should_update_if_global_storage_never_updated() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    when(folder.getSettings()).thenReturn(BOUND_SETTINGS);

    servers.put(SERVER_ID, GLOBAL_SETTINGS);

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    when(fakeEngine.getState()).thenReturn(State.NEVER_UPDATED);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, times(1)).update(any(), any());
    verify(fakeEngine, never()).updateProject(any(), any(), any());
  }

  @Test
  public void get_binding_should_not_update_if_already_updating() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    when(folder.getSettings()).thenReturn(BOUND_SETTINGS);

    servers.put(SERVER_ID, GLOBAL_SETTINGS);

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    when(fakeEngine.getState()).thenReturn(State.UPDATING);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, never()).update(any(), any());
    verify(fakeEngine, never()).updateProject(any(), any(), any());
  }

  @Test
  public void get_binding_should_update_if_project_storage_missing() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    when(folder.getSettings()).thenReturn(BOUND_SETTINGS);

    servers.put(SERVER_ID, GLOBAL_SETTINGS);

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

    when(fakeEngine.getProjectStorageStatus(PROJECT_KEY)).thenReturn(null);

    Optional<ProjectBindingWrapper> binding = underTest.getBinding(fileInAWorkspaceFolderPath.toUri());
    assertThat(binding).isNotEmpty();

    verify(fakeEngine, never()).update(any(), any());
    verify(fakeEngine, times(1)).updateProject(any(), eq(PROJECT_KEY), any());
  }

  @Test
  public void get_binding_should_update_if_project_storage_stale() {
    WorkspaceFolderWrapper folder = mockFileInAFolder();
    when(folder.getSettings()).thenReturn(BOUND_SETTINGS);

    servers.put(SERVER_ID, GLOBAL_SETTINGS);

    when(fakeEngine.calculatePathPrefixes(eq(PROJECT_KEY), any())).thenReturn(FAKE_BINDING);

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

    WorkspaceFolderWrapper folder = mockFileInAFolder();
    when(folder.getSettings()).thenReturn(BOUND_SETTINGS);

    servers.put(SERVER_ID, GLOBAL_SETTINGS);
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

  private void mockFileOutsideFolder() {
    when(foldersManager.findFolderForFile(fileNotInAWorkspaceFolderPath.toUri())).thenReturn(Optional.empty());
  }

  private WorkspaceFolderWrapper mockFileInAFolder() {
    WorkspaceFolderWrapper folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getRootPath()).thenReturn(workspaceFolderPath);
    when(foldersManager.findFolderForFile(fileInAWorkspaceFolderPath.toUri())).thenReturn(Optional.of(folderWrapper));
    return folderWrapper;
  }

}
