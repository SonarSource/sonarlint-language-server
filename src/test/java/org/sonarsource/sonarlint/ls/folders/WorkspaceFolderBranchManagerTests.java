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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBranches;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import testutils.ImmediateExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceFolderBranchManagerTests {

  @Test
  void projectNotUnderScm() throws Exception {
    var client = mock(SonarLintExtendedLanguageClient.class);
    var bindingManager = mock(ProjectBindingManager.class);
    var underTest = new WorkspaceFolderBranchManager(client, bindingManager, new ImmediateExecutorService());

    var folderUri = new URI("file:///some_dir");

    assertThat(underTest.getReferenceBranchNameForFolder(folderUri)).isNull();
  }

  @Test
  void didBranchNameChangeNoBinding() throws Exception {
    var client = mock(SonarLintExtendedLanguageClient.class);
    var bindingManager = mock(ProjectBindingManager.class);
    var underTest = new WorkspaceFolderBranchManager(client, bindingManager, new ImmediateExecutorService());

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

    try (Git git = Git.init().setInitialBranch("main").setDirectory(gitProjectBasedir.toFile()).call()) {
      Path readme = gitProjectBasedir.resolve("README.txt");
      Files.createFile(readme);
      git.add().addFilepattern("README.txt").call();
      git.commit().setMessage("Initial commit").call();
      git.checkout().setName(currentBranchName).setCreateBranch(true).call();
    }

    var client = mock(SonarLintExtendedLanguageClient.class);
    var bindingManager = mock(ProjectBindingManager.class);
    var underTest = new WorkspaceFolderBranchManager(client, bindingManager, new ImmediateExecutorService());

    var folderUri = gitProjectBasedir.toUri();

    var bindingWrapper = mock(ProjectBindingWrapper.class);
    when(bindingManager.getBinding(folderUri)).thenReturn(Optional.of(bindingWrapper));
    var engine = mock(ConnectedSonarLintEngine.class);
    when(bindingWrapper.getEngine()).thenReturn(engine);
    String projectKey = "project_key";
    when(bindingWrapper.getBinding()).thenReturn(new ProjectBinding(projectKey, null, null));
    when(engine.getServerBranches(projectKey)).thenReturn(new ProjectBranches(Set.of("main", currentBranchName), Optional.of("main")));

    underTest.didBranchNameChange(folderUri, currentBranchName);

    var branchCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.class);
    verify(client).setReferenceBranchNameForFolder(branchCaptor.capture());
    var capturedValue = branchCaptor.getValue();
    assertThat(capturedValue.getFolderUri()).isEqualTo(folderUri.toString());
    assertThat(capturedValue.getBranchName()).isEqualTo(currentBranchName);
  }

}
