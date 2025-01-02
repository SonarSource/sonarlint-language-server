/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CancellationException;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintCancelChecker;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static testutils.JavaUnzip.javaUnzip;

class WorkspaceFolderBranchManagerTest {
  private static WorkspaceFolderBranchManager underTest;

  @BeforeAll
  static void setUp() {
    var fakeClientLogger = mock(LanguageClientLogger.class);
    var backendServiceFacade = mock(BackendServiceFacade.class);
    underTest = new WorkspaceFolderBranchManager(backendServiceFacade, fakeClientLogger);
  }

  @Test
  void matchProjectBranch_shouldReturnTrueWhenCurrentBranch(@TempDir File projectDir) {
    javaUnzip("closest-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "closest-branch");

    var cancelChecker = new SonarLintCancelChecker(DummyCancelChecker::new);

    var matchProjectBranch = underTest.matchProjectBranch(path.toUri().toString(), "current_branch", cancelChecker);
    assertThat(matchProjectBranch).isTrue();
  }

  @Test
  void matchProjectBranch_shouldReturnFalseWhenCanceled(@TempDir File projectDir) {
    javaUnzip("closest-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "closest-branch");

    var cancelChecker = new SonarLintCancelChecker(new CanceledDummyCancelChecker());

    var matchProjectBranch = underTest.matchProjectBranch(path.toUri().toString(), "current_branch", cancelChecker);
    assertThat(matchProjectBranch).isFalse();
  }

  static class DummyCancelChecker implements CancelChecker {
    private final boolean canceled = false;

    @Override
    public void checkCanceled() {
      if (canceled) {
        throw new RuntimeException("Canceled");
      }
    }

    @Override
    public boolean isCanceled() {
      return canceled;
    }
  }

  static class CanceledDummyCancelChecker implements CancelChecker {
    @Override
    public void checkCanceled() {
      throw new CancellationException("Canceled");
    }

    @Override
    public boolean isCanceled() {
      return true;
    }
  }
}