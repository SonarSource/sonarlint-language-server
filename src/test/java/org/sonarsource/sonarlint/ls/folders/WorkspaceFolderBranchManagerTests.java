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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBranches;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import testutils.ImmediateExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceFolderBranchManagerTests {

  private SonarLintExtendedLanguageClient client;
  private ProjectBindingManager bindingManager;
  private BackendServiceFacade backendServiceFacade;
  private WorkspaceFolderBranchManager underTest;

  @BeforeEach
  void setUp() {
    client = mock(SonarLintExtendedLanguageClient.class);
    bindingManager = mock(ProjectBindingManager.class);
    backendServiceFacade = mock(BackendServiceFacade.class);
    underTest = new WorkspaceFolderBranchManager(client, bindingManager, backendServiceFacade, new ImmediateExecutorService());
  }

  @Test
  void projectNotUnderScm() throws Exception {
    var folderUri = new URI("file:///some_dir");

    assertThat(underTest.getReferenceBranchNameForFolder(folderUri)).isEmpty();
  }

  @Test
  void didBranchNameChangeNoBinding() throws Exception {
    var folderUri = new URI("file:///some_dir");
    var branchName = "some/branch/name";

    underTest.didBranchNameChange(folderUri, branchName);

    var branchCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.class);
    verify(client).setReferenceBranchNameForFolder(branchCaptor.capture());
    var capturedValue = branchCaptor.getValue();
    assertThat(capturedValue.getFolderUri()).isEqualTo(folderUri.toString());
    assertThat(capturedValue.getBranchName()).isNull();
  }

  @Test
  void didBranchNameChangeBindingPresent(@TempDir Path gitProjectBasedir) throws Exception {
    String currentBranchName = "some/branch/name";
    createAndCheckoutBranch(gitProjectBasedir, currentBranchName);

    var folderUri = gitProjectBasedir.toUri();

    var bindingWrapper = mock(ProjectBindingWrapper.class);
    when(bindingManager.getBindingAndRepublishTaints(folderUri)).thenReturn(Optional.of(bindingWrapper));
    var engine = mock(ConnectedSonarLintEngine.class);
    when(bindingWrapper.getEngine()).thenReturn(engine);
    String projectKey = "project_key";
    when(bindingWrapper.getBinding()).thenReturn(new ProjectBinding(projectKey, null, null));
    when(engine.getServerBranches(projectKey)).thenReturn(new ProjectBranches(Set.of("main", currentBranchName), "main"));

    underTest.didBranchNameChange(folderUri, currentBranchName);

    var branchCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.class);
    verify(client).setReferenceBranchNameForFolder(branchCaptor.capture());
    var capturedValue = branchCaptor.getValue();
    assertThat(capturedValue.getFolderUri()).isEqualTo(folderUri.toString());
    assertThat(capturedValue.getBranchName()).isEqualTo(currentBranchName);
  }

  @Test
  void didBranchNameChangeShouldFallbackToMainBranchNameFromServer(@TempDir Path gitProjectBasedir) throws Exception {
    String currentBranchName = "not/on/server";
    createAndCheckoutBranch(gitProjectBasedir, currentBranchName);

    var folderUri = gitProjectBasedir.toUri();

    var bindingWrapper = mock(ProjectBindingWrapper.class);
    when(bindingManager.getBindingAndRepublishTaints(folderUri)).thenReturn(Optional.of(bindingWrapper));
    var engine = mock(ConnectedSonarLintEngine.class);
    when(bindingWrapper.getEngine()).thenReturn(engine);
    String projectKey = "project_key";
    when(bindingWrapper.getBinding()).thenReturn(new ProjectBinding(projectKey, null, null));
    when(engine.getServerBranches(projectKey)).thenReturn(new ProjectBranches(Set.of("server-main", "other-branch"), "server-main"));

    underTest.didBranchNameChange(folderUri, currentBranchName);

    var branchCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.class);
    verify(client).setReferenceBranchNameForFolder(branchCaptor.capture());
    var capturedValue = branchCaptor.getValue();
    assertThat(capturedValue.getFolderUri()).isEqualTo(folderUri.toString());
    assertThat(capturedValue.getBranchName()).isEqualTo("server-main");
  }

  @Test
  void didBranchNameChangeTriggersSync(@TempDir Path gitProjectBasedir) throws Exception {
    createAndCheckoutBranch(gitProjectBasedir, "branchName");
    var folderUri = gitProjectBasedir.toUri();
    var bindingWrapper = mock(ProjectBindingWrapper.class);
    when(bindingManager.getEndpointParamsFor("connectionId")).thenReturn(mock(EndpointParams.class));
    when(bindingManager.getBindingAndRepublishTaints(folderUri)).thenReturn(Optional.of(bindingWrapper));
    var engine = mock(ConnectedSonarLintEngine.class);
    when(bindingWrapper.getEngine()).thenReturn(engine);
    when(bindingWrapper.getConnectionId()).thenReturn("connectionId");
    String projectKey = "project_key";
    when(bindingWrapper.getBinding()).thenReturn(new ProjectBinding(projectKey, null, null));
    when(engine.getServerBranches(projectKey)).thenReturn(new ProjectBranches(Set.of("main", "branchName"), "main"));

    underTest.didBranchNameChange(folderUri, "branchName");

    verify(backendServiceFacade).notifyBackendOnBranchChanged(folderUri.toString(), "branchName");
  }

  private void createAndCheckoutBranch(Path gitProjectBasedir, String currentBranchName) throws IOException, GitAPIException {
    try (Git git = Git.init().setInitialBranch("main").setDirectory(gitProjectBasedir.toFile()).call()) {
      Path readme = gitProjectBasedir.resolve("README.txt");
      Files.createFile(readme);
      git.add().addFilepattern("README.txt").call();
      git.commit().setMessage("Initial commit").call();
      git.checkout().setName(currentBranchName).setCreateBranch(true).call();
    }
  }

}
