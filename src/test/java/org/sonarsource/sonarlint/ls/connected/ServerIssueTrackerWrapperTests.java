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
package org.sonarsource.sonarlint.ls.connected;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ServerIssueTrackerWrapperTests {

  @TempDir
  Path baseDir;

  private static int counter = 1;

  @Test
  void get_original_issues_when_there_are_no_server_issues() throws IOException {
    var issue = mockIssue();
    when(issue.getInputFile().getPath()).thenReturn(baseDir.resolve("dummy").toString());

    var issues = List.of(issue);
    var tracker = newTracker(baseDir);

    var result = matchAndTrack(tracker, "dummy", issues);
    assertThat(result).extracting("issue").isEqualTo(issues);
  }

  @Test
  void hide_resolved_server_issues() throws IOException {
    var dummyFilePath = baseDir.resolve("dummy").toString();

    var unresolved = mockIssue();
    when(unresolved.getInputFile().getPath()).thenReturn(dummyFilePath);
    var resolved = mockIssue();
    when(resolved.getInputFile().getPath()).thenReturn(dummyFilePath);

    var issues = List.of(unresolved, resolved);
    var resolvedServerIssue = mockServerIssue(resolved);
    var serverIssues = List.of(mockServerIssue(unresolved), resolvedServerIssue);

    var engine = mock(ConnectedSonarLintEngine.class);
    when(engine.getServerIssues(any(), any(), any())).thenReturn(serverIssues);

    var tracker = newTracker(baseDir, engine);
    var trackedIssues = matchAndTrack(tracker, "dummy", issues);
    assertThat(trackedIssues).extracting("issue").containsOnlyElementsOf(issues);

    when(resolvedServerIssue.isResolved()).thenReturn(true);
    var trackedIssues2 = matchAndTrack(tracker, "dummy", issues);
    assertThat(trackedIssues2).extracting("issue").isEqualTo(List.of(unresolved));
  }

  @Test
  void get_severity_and_issue_type_from_matched_server_issue() throws IOException {
    var dummyFilePath = baseDir.resolve("dummy").toString();

    var unmatched = mockIssue();
    when(unmatched.getInputFile().getPath()).thenReturn(dummyFilePath);
    var matched = mockIssue();
    when(matched.getInputFile().getPath()).thenReturn(dummyFilePath);
    var issues = List.of(unmatched, matched);

    var serverIssueSeverity = IssueSeverity.BLOCKER;
    var serverIssueType = RuleType.BUG;
    var matchedServerIssue = mockServerIssue(matched);
    when(matchedServerIssue.getUserSeverity()).thenReturn(serverIssueSeverity);
    when(matchedServerIssue.getType()).thenReturn(serverIssueType);
    var serverIssues = List.of(mockServerIssue(mockIssue()), matchedServerIssue);

    var engine = mock(ConnectedSonarLintEngine.class);
    when(engine.getServerIssues(any(), any(), any())).thenReturn(serverIssues);

    var tracker = newTracker(baseDir, engine);
    var trackedIssues = matchAndTrack(tracker, "dummy", issues);

    assertThat(trackedIssues).extracting("ruleKey")
      .containsOnly(unmatched.getRuleKey(), matched.getRuleKey());

    var combined = trackedIssues.stream()
      .filter(t -> t.getRuleKey().equals(matched.getRuleKey()))
      .findAny();
    assertThat(combined).isPresent()
      .get().extracting(Issue::getSeverity, Issue::getType)
      .containsExactly(serverIssueSeverity, serverIssueType);
  }

  @Test
  void do_not_get_server_issues_when_there_are_no_local_issues() throws IOException {
    var engine = mock(ConnectedSonarLintEngine.class);

    var tracker = newTracker(baseDir, engine);
    matchAndTrack(tracker, "dummy", Collections.emptyList());
    verifyNoInteractions(engine);
  }

  @Test
  void fetch_server_issues_when_needed() throws IOException {
    var dummyFilePath = baseDir.resolve("dummy").toString();

    var issue = mockIssue();
    when(issue.getInputFile().getPath()).thenReturn(dummyFilePath);

    var issues = Collections.singleton(issue);

    var engine = mock(ConnectedSonarLintEngine.class);
    var tracker = newTracker(baseDir, engine);
    matchAndTrack(tracker, "dummy", issues, false);
    verify(engine).getServerIssues(any(), any(), any());
    verify(engine).getServerHotspots(any(), any(), any());
    verifyNoMoreInteractions(engine);

    engine = mock(ConnectedSonarLintEngine.class);
    tracker = newTracker(baseDir, engine);
    matchAndTrack(tracker, "dummy", issues, true);
    verify(engine).downloadAllServerIssuesForFile(any(), any(), any(), any(), eq("branchName"), any());
    verify(engine).getServerIssues(any(), eq("branchName"), any());
    verify(engine).downloadAllServerHotspotsForFile(any(), any(), any(), any(), any(), any());
    verify(engine).getServerHotspots(any(), any(), any());
    verifyNoMoreInteractions(engine);
  }

  private Collection<Issue> matchAndTrack(ServerIssueTrackerWrapper tracker, String filePath, Collection<Issue> issues) {
    return matchAndTrack(tracker, filePath, issues, false);
  }

  private Collection<Issue> matchAndTrack(ServerIssueTrackerWrapper tracker, String filePath, Collection<Issue> issues, boolean shouldFetchServerIssues) {
    var recorded = new LinkedList<Issue>();
    tracker.matchAndTrack(filePath, issues, recorded::add, shouldFetchServerIssues);
    return recorded;
  }

  private ServerIssueTrackerWrapper newTracker(Path baseDir, ConnectedSonarLintEngine engine) {
    var projectKey = "project1";
    var projectBinding = new ProjectBinding(projectKey, "", "");
    Supplier<String> branchSupplier = () -> "branchName";
    return new ServerIssueTrackerWrapper(engine, new ServerConnectionSettings.EndpointParamsAndHttpClient(null, null), projectBinding, branchSupplier);
  }

  private ServerIssueTrackerWrapper newTracker(Path baseDir) {
    var engine = mock(ConnectedSonarLintEngine.class);
    return newTracker(baseDir, engine);
  }

  // create uniquely identifiable issue
  private Issue mockIssue() {
    var issue = mock(Issue.class);

    // basic setup to prevent NPEs
    when(issue.getInputFile()).thenReturn(mock(ClientInputFile.class));
    when(issue.getMessage()).thenReturn("dummy message " + (++counter));

    // make issue match by rule key + line + text range hash
    when(issue.getRuleKey()).thenReturn("dummy ruleKey" + (++counter));
    when(issue.getStartLine()).thenReturn(++counter);
    return issue;
  }

  // copy enough fields so that tracker finds a match
  private ServerIssue mockServerIssue(Issue issue) {
    var serverIssue = mock(LineLevelServerIssue.class);

    // basic setup to prevent NPEs
    when(serverIssue.getCreationDate()).thenReturn(Instant.ofEpochMilli(++counter));
    when(serverIssue.isResolved()).thenReturn(false);
    when(serverIssue.getLineHash()).thenReturn("dummy checksum " + (++counter));

    // if issue itself is a mock, need to extract value to variable first
    // as Mockito doesn't handle nested mocking inside mocking

    var message = issue.getMessage();
    when(serverIssue.getMessage()).thenReturn(message);

    // copy fields to match during tracking

    var ruleKey = issue.getRuleKey();
    when(serverIssue.getRuleKey()).thenReturn(ruleKey);

    var line = issue.getStartLine();
    when(serverIssue.getLine()).thenReturn(line);

    return serverIssue;
  }

}
