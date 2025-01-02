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
package org.sonarsource.sonarlint.ls.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static testutils.JavaUnzip.javaUnzip;

class GitUtilsTests {

  private final LanguageClientLogger fakeClientLogger = mock(LanguageClientLogger.class);

  @Test
  void noGitRepoShouldBeNull(@TempDir File projectDir) {
    javaUnzip("no-git-repo.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "no-git-repo");
    Repository repo = GitUtils.getRepositoryForDir(path, fakeClientLogger);
    assertThat(repo).isNull();
  }

  @Test
  void gitRepoShouldBeNotNull(@TempDir File projectDir) {
    javaUnzip("dummy-git.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "dummy-git");
    try (Repository repo = GitUtils.getRepositoryForDir(path, fakeClientLogger)) {
      Set<String> serverCandidateNames = Set.of("foo", "bar", "master");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "master", fakeClientLogger);
      assertThat(branch).isEqualTo("master");
    }
  }

  @Test
  void shouldElectAnalyzedBranch(@TempDir File projectDir) {
    javaUnzip("analyzed-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "analyzed-branch");
    try (Repository repo = GitUtils.getRepositoryForDir(path, fakeClientLogger)) {
      Set<String> serverCandidateNames = Set.of("foo", "closest_branch", "master");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "master", fakeClientLogger);
      assertThat(branch).isEqualTo("closest_branch");
    }
  }

  @Test
  void shouldReturnNullIfNonePresentInLocalGit(@TempDir File projectDir) {
    javaUnzip("analyzed-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "analyzed-branch");
    try (Repository repo = GitUtils.getRepositoryForDir(path, fakeClientLogger)) {
      Set<String> serverCandidateNames = Set.of("unknown1", "unknown2", "unknown3");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "master", fakeClientLogger);
      assertThat(branch).isNull();
    }
  }

  @Test
  void shouldElectClosestBranch(@TempDir File projectDir) {
    javaUnzip("closest-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "closest-branch");

    try (Repository repo = GitUtils.getRepositoryForDir(path, fakeClientLogger)) {

      Set<String> serverCandidateNames = Set.of("foo", "closest_branch", "master");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "master", fakeClientLogger);
      assertThat(branch).isEqualTo("closest_branch");
    }
  }

  @Test
  void shouldElectClosestBranch_even_if_no_main_branch(@TempDir File projectDir) {
    javaUnzip("closest-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "closest-branch");

    try (Repository repo = GitUtils.getRepositoryForDir(path, fakeClientLogger)) {

      Set<String> serverCandidateNames = Set.of("foo", "closest_branch", "master");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, null, fakeClientLogger);
      assertThat(branch).isEqualTo("closest_branch");
    }
  }

  @Test
  void shouldElectMainBranchForNonAnalyzedChildBranch(@TempDir File projectDir) {
    javaUnzip("child-from-non-analyzed.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "child-from-non-analyzed");
    try (Repository repo = GitUtils.getRepositoryForDir(path, fakeClientLogger)) {

      Set<String> serverCandidateNames = Set.of("foo", "branch_to_analyze", "master");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "master", fakeClientLogger);
      assertThat(branch).isEqualTo("master");
    }
  }

  @Test
  void shouldReturnNullOnException() throws IOException {
    Repository repo = mock(Repository.class);
    RefDatabase db = mock(RefDatabase.class);
    when(repo.getRefDatabase()).thenReturn(db);
    when(db.exactRef(anyString())).thenThrow(new IOException());

    String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, Set.of("foo", "bar", "master"), "master", fakeClientLogger);

    assertThat(branch).isNull();
  }

  @Test
  void isCurrentBranch_shouldReturnTrueWhenCurrentBranch(@TempDir File projectDir) {
    javaUnzip("closest-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "closest-branch");

    boolean isCurrentBranch = GitUtils.isCurrentBranch(path.toUri().toString(), "current_branch", fakeClientLogger);
    assertThat(isCurrentBranch).isTrue();
  }

  @Test
  void isCurrentBranch_shouldReturnFalseWhenNotCurrentBranch(@TempDir File projectDir) {
    javaUnzip("closest-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "closest-branch");

    boolean isCurrentBranch = GitUtils.isCurrentBranch(path.toUri().toString(), "not_current_branch", fakeClientLogger);
    assertThat(isCurrentBranch).isFalse();
  }

  @Test
  void isCurrentBranch_shouldReturnFalseWhenNoRepo(@TempDir File projectDir) {
    javaUnzip("closest-branch.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "non-existent");

    boolean isCurrentBranch = GitUtils.isCurrentBranch(path.toUri().toString(), "not_current_branch", fakeClientLogger);
    assertThat(isCurrentBranch).isFalse();
  }

  @Test
  void shouldFavorCurrentBranchIfMultipleCandidates(@TempDir File projectDir) {
    // Both main and same-as-master branches are pointing to HEAD, but same-as-master is the currently checked out branch
    javaUnzip("two-branches-for-head.zip", projectDir);
    Path path = Paths.get(projectDir.getPath(), "two-branches-for-head");
    try (Repository repo = GitUtils.getRepositoryForDir(path, fakeClientLogger)) {

      Set<String> serverCandidateNames = Set.of("main", "same-as-master", "another");

      String branch = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, "main", fakeClientLogger);
      assertThat(branch).isEqualTo("same-as-master");
    }
  }

}
