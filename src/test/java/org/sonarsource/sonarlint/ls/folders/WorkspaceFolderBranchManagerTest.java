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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.serverapi.branches.ServerBranch;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkspaceFolderBranchManagerTest {

  @Test
  void didBranchNameChangeNoBinding() throws Exception {

    var notificationLatch = new CountDownLatch(1);
    var client = mock(SonarLintExtendedLanguageClient.class);
    when(client.setReferenceBranchNameForFolder(any())).then(invocation -> {
      notificationLatch.countDown();
      return CompletableFuture.completedFuture(null);
    });
    var bindingManager = mock(ProjectBindingManager.class);
    var underTest = new WorkspaceFolderBranchManager(client, bindingManager);

    var folderUri = new URI("file:///some_dir");
    var branchName = "some/branch/name";

    underTest.didBranchNameChange(folderUri, branchName);

    notificationLatch.await(1, TimeUnit.SECONDS);

    var branchCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.class);
    verify(client).setReferenceBranchNameForFolder(branchCaptor.capture());
    var capturedValue = branchCaptor.getValue();
    assertThat(capturedValue.getFolderUri()).isEqualTo(folderUri.toString());
    assertThat(capturedValue.getBranchName()).isNull();
  }

  @Test
  void didBranchNameChangeBindingPresent() throws Exception {

    var notificationLatch = new CountDownLatch(1);
    var client = mock(SonarLintExtendedLanguageClient.class);
    when(client.setReferenceBranchNameForFolder(any())).then(invocation -> {
      notificationLatch.countDown();
      return CompletableFuture.completedFuture(null);
    });
    var bindingManager = mock(ProjectBindingManager.class);
    var underTest = new WorkspaceFolderBranchManager(client, bindingManager);

    var folderUri = new URI("file:///some_dir");
    var branchName = "some/branch/name";

    var bindingWrapper = mock(ProjectBindingWrapper.class);
    var configId = "configId";
    when(bindingManager.getBinding(folderUri)).thenReturn(Optional.of(bindingWrapper));
    when(bindingWrapper.getConnectionId()).thenReturn(configId);
    var config = mock(ServerConnectionSettings.EndpointParamsAndHttpClient.class);
    when(bindingManager.getServerConfigurationFor(configId)).thenReturn(config);
    var engine = mock(ConnectedSonarLintEngine.class);
    when(bindingWrapper.getEngine()).thenReturn(engine);
    when(engine.getServerBranches(any(), any(), any())).thenReturn(Set.of(
      new ServerBranch("main", true),
      new ServerBranch("some/branch/name", false))
    );

    underTest.didBranchNameChange(folderUri, branchName);

    notificationLatch.await(1, TimeUnit.SECONDS);

    var branchCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.class);
    verify(client).setReferenceBranchNameForFolder(branchCaptor.capture());
    var capturedValue = branchCaptor.getValue();
    assertThat(capturedValue.getFolderUri()).isEqualTo(folderUri.toString());
    assertThat(capturedValue.getBranchName()).isEqualTo("some/branch/name");
  }

}
