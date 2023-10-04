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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.ServerMatchedIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesResponse;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.tracking.DigestUtils;
import org.sonarsource.sonarlint.ls.AnalysisClientInputFile;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.folders.InFolderClientInputFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.connected.ServerIssueTrackerWrapper.createLineWithHashDto;
import static org.sonarsource.sonarlint.ls.connected.ServerIssueTrackerWrapper.getWorkspaceFolderUri;

class ServerIssueTrackerWrapperTests {

  @TempDir
  Path baseDir;

  private static int counter = 1;

  @Test
  void get_original_issues_when_there_are_no_server_issues() {
    var issue = mockIssue();
    var issues = List.of(issue);
    var localOnlyIssueDto = createLocalOnlyIssueDto();
    var trackIssuesResponse = getTrackWithServerIssuesResponse(List.of(Either.forRight(localOnlyIssueDto)));
    var tracker = newTracker(trackIssuesResponse);

    var result = matchAndTrack(tracker, "dummy", issues);
    assertThat(result).extracting("issue").isEqualTo(issues);
    assertThat(result).hasSize(1);
    assertThat(((DelegatingIssue) result.stream().findFirst().get()).getIssueId()).isEqualTo(localOnlyIssueDto.getId());
  }

  @Test
  void hide_resolved_server_issues() {
    var unresolved = mockIssue();
    var resolved = mockIssue();

    var issues = List.of(unresolved, resolved);
    var resolvedServerIssue = mockServerIssue(resolved);
    var serverIssues = List.of(mockServerIssue(unresolved), resolvedServerIssue);

    var engine = mock(ConnectedSonarLintEngine.class);
    when(engine.getServerIssues(any(), any(), any())).thenReturn(serverIssues);

    var trackIssuesResponse = getTrackWithServerIssuesResponse(List.of(
      Either.forRight(createLocalOnlyIssueDto()),
      Either.forRight(createLocalOnlyIssueDto()))
    );

    var tracker = newTracker(trackIssuesResponse);
    var trackedIssues = matchAndTrack(tracker, "dummy", issues);
    assertThat(trackedIssues).extracting("issue").containsOnlyElementsOf(issues);

    when(resolvedServerIssue.isResolved()).thenReturn(true);
    var trackIssuesResponse2 = getTrackWithServerIssuesResponse(List.of(
      Either.forRight(createLocalOnlyIssueDto()),
      Either.forLeft(createServerMatchedIssueDto(true)))
    );
    var tracker2 = newTracker(trackIssuesResponse2);
    var trackedIssues2 = matchAndTrack(tracker2, "dummy", issues);
    assertThat(trackedIssues2).extracting("issue").isEqualTo(List.of(unresolved));
  }

  @Test
  void get_severity_and_issue_type_from_matched_server_issue() {
    var serverIssueSeverity = IssueSeverity.BLOCKER;
    var serverIssueType = RuleType.BUG;
    var unmatched = mockIssue();
    var matched = mockIssue(serverIssueType, serverIssueSeverity);
    var issues = List.of(unmatched, matched);

    var matchedServerIssue = mockServerIssue(matched);
    when(matchedServerIssue.getUserSeverity()).thenReturn(serverIssueSeverity);
    when(matchedServerIssue.getType()).thenReturn(serverIssueType);
    var serverIssues = List.of(mockServerIssue(mockIssue()), matchedServerIssue);

    var engine = mock(ConnectedSonarLintEngine.class);
    when(engine.getServerIssues(any(), any(), any())).thenReturn(serverIssues);

    var trackIssuesResponse = getTrackWithServerIssuesResponse(List.of(
      Either.forRight(createLocalOnlyIssueDto()),
      Either.forLeft(createServerMatchedIssueDto(false))));
    var tracker = newTracker(engine, trackIssuesResponse);
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
    var engine = mock(ConnectedSonarLintEngine.class);

    var tracker = newTracker(null);
    matchAndTrack(tracker, "dummy", Collections.emptyList());
    verifyNoInteractions(engine);
  }

  @Test
  void fetch_server_issues_when_needed() {
    var issue = mockIssue();

    var issues = Collections.singleton(issue);

    var engine = mock(ConnectedSonarLintEngine.class);
    var trackIssuesResponse = getTrackWithServerIssuesResponse(List.of(Either.forLeft(createServerMatchedIssueDto(false))));
    var tracker = newTracker(engine, trackIssuesResponse);
    matchAndTrack(tracker, "dummy", issues, false);
    verify(engine).getServerHotspots(any(), any(), any());
    verifyNoMoreInteractions(engine);

    engine = mock(ConnectedSonarLintEngine.class);
    tracker = newTracker(engine, trackIssuesResponse);
    matchAndTrack(tracker, "dummy", issues, true);
    verify(engine).downloadAllServerHotspotsForFile(any(), any(), any(), any(), any(), any());
    verify(engine).getServerHotspots(any(), any(), any());
    verifyNoMoreInteractions(engine);
  }

  @Test
  void should_convert_issues_to_trackables(){
    var lineNumber = 2;
    var issue = mockIssue(RuleType.BUG, IssueSeverity.BLOCKER, lineNumber);
    var hotspot = mockIssue(RuleType.SECURITY_HOTSPOT, IssueSeverity.BLOCKER, lineNumber);

    var trackables = ServerIssueTrackerWrapper.toIssueTrackables(List.of(issue, hotspot));

    assertThat(trackables).hasSize(1);
    var trackableIssue = trackables.stream().findFirst().get();
    assertThat(trackableIssue.getLine()).isEqualTo(lineNumber);
    assertThat(trackableIssue.getLineHash()).isEqualTo(DigestUtils.digest("second line"));
  }

  @Test
  void should_convert_file_level_issues_to_trackables(){
    var issue = mockIssue(RuleType.BUG, IssueSeverity.BLOCKER, null);

    var trackables = ServerIssueTrackerWrapper.toIssueTrackables(List.of(issue));

    assertThat(trackables).hasSize(1);
    var trackableIssue = trackables.stream().findFirst().get();
    assertThat(trackableIssue.getLine()).isNull();
    assertThat(trackableIssue.getLineHash()).isNull();
  }

  @Test
  void createLineWithHashDtoTest() {
    var trackable = mock(Trackable.class);
    when(trackable.getLine()).thenReturn(1);
    when(trackable.getLineHash()).thenReturn("hash");
    var nullTrackable = mock(Trackable.class);
    when(nullTrackable.getLine()).thenReturn(null);

    var lineWithHashDto = createLineWithHashDto(trackable);
    var nullLineWithHashDto = createLineWithHashDto(nullTrackable);

    assertThat(lineWithHashDto.getNumber()).isEqualTo(1);
    assertThat(lineWithHashDto.getHash()).isEqualTo("hash");
    assertThat(nullLineWithHashDto).isNull();
  }

  @Test
  void toIssueTrackablesShouldFilterOutNotExpectedFileTypeTest() {
    var issue = mock(Issue.class);
    when(issue.getInputFile()).thenReturn(mock(InFolderClientInputFile.class));

    var noTrackables = ServerIssueTrackerWrapper.toIssueTrackables(List.of(issue));

    assertThat(noTrackables).isEmpty();
  }

  @Test
  void getWorkspaceFolderUriTest() {
    var noIssues = getWorkspaceFolderUri(Collections.emptyList(), mock(WorkspaceFoldersManager.class));
    var noInputFile = getWorkspaceFolderUri(List.of(mock(Issue.class)), mock(WorkspaceFoldersManager.class));
    var workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
    when(workspaceFoldersManager.findFolderForFile(any(URI.class))).thenReturn(Optional.empty());
    var noFolderForFile = getWorkspaceFolderUri(List.of(mock(Issue.class)), workspaceFoldersManager);

    assertThat(noIssues).isEmpty();
    assertThat(noInputFile).isEmpty();
    assertThat(noFolderForFile).isEmpty();
  }

  private Collection<Issue> matchAndTrack(ServerIssueTrackerWrapper tracker, String filePath, Collection<Issue> issues) {
    return matchAndTrack(tracker, filePath, issues, false);
  }

  private Collection<Issue> matchAndTrack(ServerIssueTrackerWrapper tracker, String filePath, Collection<Issue> issues,
    boolean shouldFetchServerIssues) {
    var recorded = new LinkedList<Issue>();
    tracker.matchAndTrack(filePath, issues, recorded::add, shouldFetchServerIssues);
    return recorded;
  }

  private ServerIssueTrackerWrapper newTracker(CompletableFuture<TrackWithServerIssuesResponse> trackIssuesResponse) {
    var engine = mock(ConnectedSonarLintEngine.class);
    return newTracker(engine, trackIssuesResponse);
  }

  private ServerIssueTrackerWrapper newTracker(ConnectedSonarLintEngine engine,
    CompletableFuture<TrackWithServerIssuesResponse> trackIssuesResponse) {
    var projectKey = "project1";
    var projectBinding = new ProjectBinding(projectKey, "", "");
    Supplier<String> branchSupplier = () -> "branchName";
    var backendServiceFacade = mock(BackendServiceFacade.class);
    var workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
    var workspaceFolderWrapper = mock(WorkspaceFolderWrapper.class);
    var settingsManager = mock(SettingsManager.class);
    var workspaceSettings = mock(WorkspaceSettings.class);
    var httpClient = mock(HttpClient.class);
    when(backendServiceFacade.getHttpClient(any())).thenReturn(httpClient);
    when(backendServiceFacade.matchIssues(any())).thenReturn(trackIssuesResponse);
    when(workspaceFolderWrapper.getUri()).thenReturn(URI.create("dummy"));
    when(workspaceFoldersManager.findFolderForFile(any())).thenReturn(Optional.of(workspaceFolderWrapper));
    when(workspaceSettings.isFocusOnNewCode()).thenReturn(true);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    return new ServerIssueTrackerWrapper(engine, new EndpointParams("https://sonarcloud.io", true, "known"), projectBinding,
      branchSupplier, httpClient, backendServiceFacade, workspaceFoldersManager);
  }

  // create uniquely identifiable issue
  private Issue mockIssue() {
    var issue = mock(Issue.class);

    // basic setup to prevent NPEs
    when(issue.getInputFile()).thenReturn(new AnalysisClientInputFile(URI.create("dummyFile"), baseDir.resolve("dummy").toString(),
      "first line \n " +
        "second line \n " +
        "third line", true, "java"));
    when(issue.getMessage()).thenReturn("dummy message " + (++counter));

    // make issue match by rule key + line + text range hash
    when(issue.getRuleKey()).thenReturn("dummy ruleKey" + (++counter));
    when(issue.getStartLine()).thenReturn(1);
    return issue;
  }

  private Issue mockIssue(RuleType type, IssueSeverity severity) {
    var issue = mockIssue();
    when(issue.getSeverity()).thenReturn(severity);
    when(issue.getType()).thenReturn(type);
    return issue;
  }

  private Issue mockIssue(RuleType type, IssueSeverity severity, @Nullable Integer startLine) {
    var issue = mockIssue(type, severity);
    when(issue.getStartLine()).thenReturn(startLine);
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

  @NotNull
  private static CompletableFuture<TrackWithServerIssuesResponse> getTrackWithServerIssuesResponse(List<Either<ServerMatchedIssueDto,
    LocalOnlyIssueDto>> issuesInResponse) {
    return CompletableFuture.completedFuture(new TrackWithServerIssuesResponse(Map.of("dummy", issuesInResponse)));
  }

  @NotNull
  private static ServerMatchedIssueDto createServerMatchedIssueDto(boolean isResolved) {
    return new ServerMatchedIssueDto(UUID.randomUUID(), "serverKey", 1L, isResolved, IssueSeverity.BLOCKER, RuleType.BUG, true);
  }

  @NotNull
  private static LocalOnlyIssueDto createLocalOnlyIssueDto() {
    return new LocalOnlyIssueDto(UUID.randomUUID(), null);
  }
}
