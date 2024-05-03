/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ServerMatchedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.ls.Issue;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.connected.ServerIssueTrackerWrapper.getWorkspaceFolderUri;

class ServerIssueTrackerWrapperTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void get_original_issues_when_there_are_no_server_issues() {
    var issue = mockRawIssue(RuleType.BUG, IssueSeverity.BLOCKER);
    var issues = List.of(issue);
    var localOnlyIssueDto = createLocalOnlyIssueDto();
    var trackIssuesResponse = getTrackWithServerIssuesResponse(
      List.of(Either.forRight(localOnlyIssueDto)));
    var tracker = newTracker(trackIssuesResponse);

    var result = matchAndTrack(tracker, "dummy", issues);
    assertThat(result).extracting("issue").isEqualTo(issues);
    assertThat(result).hasSize(1);
    assertThat(result.stream().findFirst().get().getIssueId()).isEqualTo(localOnlyIssueDto.getId());
  }


  @Test
  void get_severity_and_issue_type_from_matched_server_issue() {
    var serverIssueSeverity = IssueSeverity.BLOCKER;
    var serverIssueType = RuleType.BUG;
    var unmatched = mockRawIssue(serverIssueType, serverIssueSeverity);
    var matched = mockRawIssue(serverIssueType, serverIssueSeverity);
    var issues = List.of(unmatched, matched);

    var trackIssuesResponse = getTrackWithServerIssuesResponse(List.of(
      Either.forRight(createLocalOnlyIssueDto()), Either.forLeft(createServerMatchedIssueDto(false))));
    var tracker = newTracker(trackIssuesResponse);
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
  void do_not_get_server_issues_when_there_are_no_local_issues() {
    var engine = mock(SonarLintAnalysisEngine.class);

    var tracker = newTracker(null);
    matchAndTrack(tracker, "dummy", Collections.emptyList());
    verifyNoInteractions(engine);
  }

  @Test
  void getWorkspaceFolderUriTest() {
    var noIssues = getWorkspaceFolderUri(Collections.emptyList(), mock(WorkspaceFoldersManager.class));
    var noInputFile = getWorkspaceFolderUri(List.of(mock(RawIssueDto.class)), mock(WorkspaceFoldersManager.class));
    var workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
    when(workspaceFoldersManager.findFolderForFile(any(URI.class))).thenReturn(Optional.empty());
    var noFolderForFile = getWorkspaceFolderUri(List.of(mock(RawIssueDto.class)), workspaceFoldersManager);

    assertThat(noIssues).isEmpty();
    assertThat(noInputFile).isEmpty();
    assertThat(noFolderForFile).isEmpty();
  }

  private Collection<Issue> matchAndTrack(ServerIssueTrackerWrapper tracker, String filePath, Collection<RawIssueDto> issues) {
    return matchAndTrack(tracker, filePath, issues, false);
  }

  private Collection<Issue> matchAndTrack(ServerIssueTrackerWrapper tracker, String filePath, Collection<RawIssueDto> issues,
    boolean shouldFetchServerIssues) {
    var recorded = new LinkedList<Issue>();
    tracker.matchAndTrack(filePath, issues, recorded::add, shouldFetchServerIssues);
    return recorded;
  }

  private ServerIssueTrackerWrapper newTracker(CompletableFuture<TrackWithServerIssuesResponse> trackIssuesResponse) {
    var backendServiceFacade = mock(BackendServiceFacade.class);
    var backendService = mock(BackendService.class);
    var workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
    var workspaceFolderWrapper = mock(WorkspaceFolderWrapper.class);
    var settingsManager = mock(SettingsManager.class);
    var workspaceSettings = mock(WorkspaceSettings.class);
    var openFilesCache = mock(OpenFilesCache.class);
    when(backendServiceFacade.getBackendService()).thenReturn(backendService);
    when(backendService.matchIssues(any())).thenReturn(trackIssuesResponse);
    when(workspaceFolderWrapper.getUri()).thenReturn(URI.create("dummy"));
    when(workspaceFoldersManager.findFolderForFile(any())).thenReturn(Optional.of(workspaceFolderWrapper));
    when(workspaceSettings.isFocusOnNewCode()).thenReturn(true);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    return new ServerIssueTrackerWrapper(backendServiceFacade, workspaceFoldersManager, openFilesCache);
  }

  private RawIssueDto mockRawIssue(RuleType type, IssueSeverity severity) {
    var issue = mock(RawIssueDto.class);
    when(issue.getRuleKey()).thenReturn("ruleKey");
    when(issue.getSeverity()).thenReturn(severity);
    when(issue.getType()).thenReturn(type);
    var fileUri = "fileUri";
    when(issue.getFileUri()).thenReturn(URI.create(fileUri));
    return issue;
  }

  @NotNull
  private static CompletableFuture<TrackWithServerIssuesResponse> getTrackWithServerIssuesResponse(List<Either<ServerMatchedIssueDto, LocalOnlyIssueDto>> issuesInResponse) {
    return CompletableFuture.completedFuture(new TrackWithServerIssuesResponse(Map.of(Path.of("dummy"), issuesInResponse)));
  }

  @NotNull
  private static ServerMatchedIssueDto createServerMatchedIssueDto(boolean isResolved) {
    return new ServerMatchedIssueDto(UUID.randomUUID(), "serverKey", 1L, isResolved,
      org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.BLOCKER,
      org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.BUG, true);
  }

  @NotNull
  private static LocalOnlyIssueDto createLocalOnlyIssueDto() {
    return new LocalOnlyIssueDto(UUID.randomUUID(), null);
  }
}
