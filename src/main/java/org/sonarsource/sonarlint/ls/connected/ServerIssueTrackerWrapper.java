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

import com.google.common.collect.Streams;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.CheckForNull;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.ServerMatchedIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.issuetracking.CachingIssueTracker;
import org.sonarsource.sonarlint.core.issuetracking.InMemoryIssueTrackerCache;
import org.sonarsource.sonarlint.core.issuetracking.IssueTrackerCache;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.tracking.IssueTrackable;
import org.sonarsource.sonarlint.ls.AnalysisClientInputFile;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.util.function.Predicate.not;
import static org.sonarsource.sonarlint.ls.util.FileUtils.getTextRangeContentOfFile;

public class ServerIssueTrackerWrapper {

  private final ConnectedSonarLintEngine engine;
  private final EndpointParams endpointParams;
  private final ProjectBinding projectBinding;
  private final Supplier<String> getReferenceBranchNameForFolder;
  private final HttpClient httpClient;
  private final BackendServiceFacade backend;
  private final WorkspaceFoldersManager workspaceFoldersManager;

  private final IssueTrackerCache<Issue> issueTrackerCache;
  private final IssueTrackerCache<Issue> hotspotsTrackerCache;
  private final CachingIssueTracker cachingIssueTracker;
  private final CachingIssueTracker cachingHotspotsTracker;
  private final org.sonarsource.sonarlint.core.tracking.ServerIssueTracker tracker;

  ServerIssueTrackerWrapper(ConnectedSonarLintEngine engine, EndpointParams endpointParams,
    ProjectBinding projectBinding, Supplier<String> getReferenceBranchNameForFolder, HttpClient httpClient,
    BackendServiceFacade backend, WorkspaceFoldersManager workspaceFoldersManager) {
    this.engine = engine;
    this.endpointParams = endpointParams;
    this.projectBinding = projectBinding;
    this.getReferenceBranchNameForFolder = getReferenceBranchNameForFolder;
    this.httpClient = httpClient;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.backend = backend;
    this.issueTrackerCache = new InMemoryIssueTrackerCache();
    this.hotspotsTrackerCache = new InMemoryIssueTrackerCache();
    this.cachingIssueTracker = new CachingIssueTracker(issueTrackerCache);
    this.cachingHotspotsTracker = new CachingIssueTracker(hotspotsTrackerCache);
    this.tracker = new org.sonarsource.sonarlint.core.tracking.ServerIssueTracker(cachingHotspotsTracker);
  }

  public void matchAndTrack(String filePath, Collection<Issue> issues, IssueListener issueListener, boolean shouldFetchServerIssues) {
    if (issues.isEmpty()) {
      issueTrackerCache.put(filePath, Collections.emptyList());
      return;
    }

    var issueTrackables = toIssueTrackables(issues);
    cachingIssueTracker.matchAndTrackAsNew(filePath, issueTrackables);
    cachingHotspotsTracker.matchAndTrackAsNew(filePath, toHotspotTrackables(issues));

    if (shouldFetchServerIssues) {
      tracker.update(endpointParams, httpClient, engine, projectBinding,
        Collections.singleton(filePath), getReferenceBranchNameForFolder.get());
    } else {
      tracker.update(engine, projectBinding, getReferenceBranchNameForFolder.get(), Collections.singleton(filePath));
    }

    Optional<URI> workspaceFolderUri = getWorkspaceFolderUri(issues, workspaceFoldersManager);
    workspaceFolderUri.ifPresent(uri -> matchAndTrackIssues(filePath, issueListener, shouldFetchServerIssues, issueTrackables, uri));
    hotspotsTrackerCache.getLiveOrFail(filePath).stream()
      .filter(not(Trackable::isResolved))
      .forEach(trackable -> issueListener.handle(new DelegatingIssue(trackable)));
  }

  private void matchAndTrackIssues(String filePath, IssueListener issueListener, boolean shouldFetchServerIssues,
    Collection<Trackable> issueTrackables, URI workspaceFolderUri) {
    var issuesByFilepath = getClientTrackedIssuesByServerRelativePath(filePath, issueTrackables);
    var trackWithServerIssuesResponse = Utils.safelyGetCompletableFuture(backend.matchIssues(
      new TrackWithServerIssuesParams(workspaceFolderUri.toString(), issuesByFilepath, shouldFetchServerIssues)
    ));
    trackWithServerIssuesResponse.ifPresentOrElse(
      r -> matchAndTrackIssues(filePath, issueListener, issueTrackables, r.getIssuesByServerRelativePath()),
      () -> issueTrackables.stream().map(DelegatingIssue::new).forEach(issueListener::handle)
    );
  }

  private static void matchAndTrackIssues(String filePath, IssueListener issueListener, Collection<Trackable> currentTrackables, Map<String,
    List<Either<ServerMatchedIssueDto, LocalOnlyIssueDto>>> issuesByServerRelativePath) {
    var eitherList = issuesByServerRelativePath.getOrDefault(filePath, Collections.emptyList());
    Streams.zip(currentTrackables.stream(), eitherList.stream(), (issue, either) -> {
        if (either.isLeft()) {
          var serverIssue = either.getLeft();
          var issueSeverity = serverIssue.getOverriddenSeverity() == null ? issue.getSeverity() : serverIssue.getOverriddenSeverity();
          return new DelegatingIssue(issue, serverIssue.getId(), serverIssue.isResolved(), issueSeverity, serverIssue.getServerKey(), serverIssue.isOnNewCode());
        } else {
          var localIssue = either.getRight();
          return new DelegatingIssue(issue, localIssue.getId(), localIssue.getResolutionStatus() != null, true);
        }
      })
      .filter(not(DelegatingIssue::isResolved))
      .forEach(issueListener::handle);
  }


  @NotNull
  private static Map<String, List<ClientTrackedFindingDto>> getClientTrackedIssuesByServerRelativePath(String filePath, Collection<Trackable> issueTrackables) {
    var clientTrackedIssueDtos = issueTrackables.stream().map(ServerIssueTrackerWrapper::createClientTrackedIssueDto).toList();
    return Map.of(filePath, clientTrackedIssueDtos);
  }

  static Optional<URI> getWorkspaceFolderUri(Collection<Issue> issues, WorkspaceFoldersManager workspaceFoldersManager) {
    var anIssue = issues.stream().findFirst();
    if (anIssue.isPresent()) {
      var inputFile = anIssue.get().getInputFile();
      if (inputFile != null) {
        var folderForFile = workspaceFoldersManager.findFolderForFile(inputFile.uri());
        if (folderForFile.isPresent()) {
          return Optional.of(folderForFile.get().getUri());
        }
      }
    }
    return Optional.empty();
  }

  @NotNull
  private static ClientTrackedFindingDto createClientTrackedIssueDto(Trackable<Issue> issue) {
    return new ClientTrackedFindingDto(null, issue.getServerIssueKey(), createTextRangeWithHashDto(issue), createLineWithHashDto(issue), issue.getRuleKey(), issue.getMessage());
  }

  @CheckForNull
  static LineWithHashDto createLineWithHashDto(Trackable<Issue> issue) {
    return issue.getLine() != null ? new LineWithHashDto(issue.getLine(), issue.getLineHash()) : null;
  }

  @CheckForNull
  private static TextRangeWithHashDto createTextRangeWithHashDto(Trackable<Issue> issue) {
    var textRangeWithHash = issue.getTextRange();
    if (textRangeWithHash != null) {
      return new TextRangeWithHashDto(textRangeWithHash.getStartLine(), textRangeWithHash.getStartLineOffset(),
        textRangeWithHash.getEndLine(), textRangeWithHash.getEndLineOffset(), textRangeWithHash.getHash());
    }
    return null;
  }

  static Collection<Trackable> toIssueTrackables(Collection<Issue> issues) {
    return issues.stream()
      .filter(it -> it.getType() != RuleType.SECURITY_HOTSPOT)
      .map(issue -> {
        var inputFile = issue.getInputFile() instanceof AnalysisClientInputFile actualInputFile ? actualInputFile : null;
        if (inputFile != null) {
          var fileLines = inputFile.contents().lines().toList();
          var textRange = issue.getTextRange();
          var textRangeContent = getTextRangeContentOfFile(fileLines, textRange);
          var startLine = issue.getStartLine();
          var lineContent = startLine != null ? fileLines.get(startLine - 1) : null;
          return new IssueTrackable(issue, textRangeContent, lineContent);
        }
        return null;
      })
      .filter(Objects::nonNull)
      .map(Trackable.class::cast)
      .toList();
  }


  private static Collection<Trackable> toHotspotTrackables(Collection<Issue> issues) {
    return issues.stream().filter(it -> it.getType() == RuleType.SECURITY_HOTSPOT)
      .map(IssueTrackable::new).map(Trackable.class::cast).toList();
  }
}
