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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import testutils.ImmediateExecutorService;
import testutils.SonarLintLogTester;

import static java.net.URI.create;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager.isAncestor;

class WorkspaceFoldersManagerTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();
  ProjectBindingManager bindingManager;
  BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  BackendService backendService = mock(BackendService.class);

  private final WorkspaceFoldersManager underTest = new WorkspaceFoldersManager(new ImmediateExecutorService(), backendServiceFacade);

  @BeforeEach
  void prepare() {
    bindingManager = mock(ProjectBindingManager.class);
    when(bindingManager.getBinding(URI.create(""))).thenReturn(Optional.of(mock(ProjectBindingWrapper.class)));
    when(backendServiceFacade.getBackendService()).thenReturn(backendService);
    when(backendService.getConfigScopeDto(any(), any()))
      .thenReturn(new ConfigurationScopeDto("id", BackendServiceFacade.ROOT_CONFIGURATION_SCOPE, true, "name",
        new BindingConfigurationDto("connectionId", "projectKey", true)));
    underTest.setBindingManager(bindingManager);
  }

  @Test
  void findFolderForFile_returns_correct_folder_when_exists() {
    var basedir = Paths.get("path/to/base").toAbsolutePath();
    var file = basedir.resolve("some/sub/file.java");

    var workspaceFolder = mockWorkspaceFolder(basedir.toUri());
    underTest.initialize(List.of(
      mockWorkspaceFolder(Paths.get("other/path").toAbsolutePath().toUri()),
      workspaceFolder,
      mockWorkspaceFolder(Paths.get("other/path2").toAbsolutePath().toUri())));

    var findFolderForFile = underTest.findFolderForFile(file.toUri());
    assertThat(findFolderForFile).isPresent()
      .get().extracting(WorkspaceFolderWrapper::getRootPath).isEqualTo(basedir);
  }

  @Test
  void findFolderForFile_returns_empty_when_no_folder_matched() {
    var basedir = Paths.get("path/to/base").toAbsolutePath();
    var file = basedir.resolve("some/sub/file.java");
    var workspaceRoot = Paths.get("other/path");

    var workspaceFolder = mockWorkspaceFolder(workspaceRoot.toUri());
    underTest.initialize(List.of(workspaceFolder,
      mockWorkspaceFolder(Paths.get("other/path2").toAbsolutePath().toUri())));

    assertThat(underTest.findFolderForFile(file.toUri())).isEmpty();
  }

  @Test
  void findBaseDir_finds_deepest_nested_folder() {
    var basedir = Paths.get("path/to/base").toAbsolutePath();
    var subFolder = basedir.resolve("sub");
    var file = subFolder.resolve("file.java").toUri();

    underTest.initialize(List.of(
      mockWorkspaceFolder(subFolder.toUri()),
      mockWorkspaceFolder(basedir.toUri())));

    var findFolderForFile = underTest.findFolderForFile(file);
    assertThat(findFolderForFile).isPresent()
      .get().extracting(WorkspaceFolderWrapper::getRootPath).isEqualTo(subFolder);
  }

  @Test
  void initialize_does_not_crash_when_no_folders() {
    underTest.initialize(null);

    assertThat(underTest.getAll()).isEmpty();
  }

  @Test
  void testURIAncestor() {
    assertThat(isAncestor(create("file:///foo"), create("file:///foo"))).isTrue();
    assertThat(isAncestor(create("file:///foo"), create("file:///foo/bar.txt"))).isTrue();
    assertThat(isAncestor(create("file:///foo/bar"), create("file:///foo/bar.txt"))).isFalse();
    assertThat(isAncestor(create("file:///foo/bar"), create("file:///foo/bar2"))).isFalse();

    // Non file scheme
    assertThat(isAncestor(create("ftp://ftp.example.com/foo"), create("ftp://ftp.example.com/foo"))).isTrue();
    assertThat(isAncestor(create("ftp://ftp.example.com/foo"), create("ftp://ftp.example.com/foo/bar.txt"))).isTrue();
    assertThat(isAncestor(create("ftp://ftp.example.com/foo/bar"), create("ftp://ftp.example.com/foo/bar.txt"))).isFalse();
    assertThat(isAncestor(create("ftp://ftp.example.com/foo/bar"), create("ftp://ftp.example.com/foo/bar2"))).isFalse();
    assertThat(isAncestor(create("ftp://ftp.example.com/foo/bar"), create("ftp://ftp.example.com/bar.txt"))).isFalse();

    // Windows
    assertThat(isAncestor(create("file:///C:/Documents%20and%20Settings/davris"), create("file:///C:/Documents%20and%20Settings/davris/FileSchemeURIs.doc"))).isTrue();

    // Corner cases
    // Not the same scheme
    assertThat(isAncestor(create("ftp:///foo"), create("file:///foo"))).isFalse();
    // Not the same host
    assertThat(isAncestor(create("file://laptop/My%20Documents"), create("file://laptop2/My%20Documents/FileSchemeURIs.doc"))).isFalse();
    // Not the same port
    assertThat(isAncestor(create("file://laptop:8080/My%20Documents"), create("file://laptop:8081/My%20Documents/FileSchemeURIs.doc"))).isFalse();
    // Opaque
    URI emailUri = create("mailto:a@b.com");
    URI fileUri = create("file:///foo");
    assertThrows(IllegalArgumentException.class, () -> isAncestor(emailUri, fileUri));
    assertThrows(IllegalArgumentException.class, () -> isAncestor(fileUri, emailUri));
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
    // Fail on Linux with IllegalArgumentException: URI has an authority component
  void testURIAncestor_UNC_path() {
    assertThat(isAncestor(create("file://laptop/My%20Documents"), create("file://laptop/My%20Documents/FileSchemeURIs.doc"))).isTrue();
  }

  @Test
  void register_new_folder() {
    underTest.initialize(Collections.emptyList());
    assertThat(underTest.getAll()).isEmpty();

    var basedir = Paths.get("path/to/base").toAbsolutePath();
    var workspaceFolder = mockWorkspaceFolder(basedir.toUri());

    underTest.didChangeWorkspaceFolders(new WorkspaceFoldersChangeEvent(List.of(workspaceFolder), Collections.emptyList()));

    assertThat(underTest.getAll()).extracting(WorkspaceFolderWrapper::getRootPath).containsExactly(basedir);
    assertThat(logTester.logs()).containsExactly("Processing didChangeWorkspaceFolders event",
      "Folder WorkspaceFolder[name=<null>,uri=" + basedir.toUri() + "] added");

    logTester.clear();

    // Should never occurs
    underTest.didChangeWorkspaceFolders(new WorkspaceFoldersChangeEvent(List.of(workspaceFolder), Collections.emptyList()));

    assertThat(underTest.getAll()).extracting(WorkspaceFolderWrapper::getRootPath).containsExactly(basedir);
    assertThat(logTester.logs()).containsExactly("Processing didChangeWorkspaceFolders event",
      "Registered workspace folder WorkspaceFolder[name=<null>,uri=" + basedir.toUri() + "] was already added");
  }

  @Test
  void unregister_folder() {
    var basedir = Paths.get("path/to/base").toAbsolutePath();
    var workspaceFolder = mockWorkspaceFolder(basedir.toUri());

    underTest.initialize(List.of(workspaceFolder));
    assertThat(underTest.getAll()).extracting(WorkspaceFolderWrapper::getRootPath).containsExactly(basedir);

    logTester.clear();

    underTest.didChangeWorkspaceFolders(new WorkspaceFoldersChangeEvent(Collections.emptyList(), List.of(workspaceFolder)));

    assertThat(underTest.getAll()).isEmpty();
    assertThat(logTester.logs()).containsExactly("Processing didChangeWorkspaceFolders event",
      "Folder WorkspaceFolder[name=<null>,uri=" + basedir.toUri() + "] removed");

    logTester.clear();

    // Should never occur
    underTest.didChangeWorkspaceFolders(new WorkspaceFoldersChangeEvent(Collections.emptyList(), List.of(workspaceFolder)));

    assertThat(underTest.getAll()).isEmpty();
    assertThat(logTester.logs()).containsExactly("Processing didChangeWorkspaceFolders event",
      "Unregistered workspace folder was missing: " + basedir.toUri());
  }

  private static WorkspaceFolder mockWorkspaceFolder(URI uri) {
    return new WorkspaceFolder(uri.toString());
  }
}
