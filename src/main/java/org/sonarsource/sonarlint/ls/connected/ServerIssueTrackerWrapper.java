/*
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

import com.google.common.collect.Streams;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.MatchWithServerSecurityHotspotsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ServerMatchedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;

import static java.util.function.Predicate.not;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus.FIXED;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus.SAFE;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.SECURITY_HOTSPOT;

public class ServerIssueTrackerWrapper {

  private final BackendServiceFacade backend;
  private final WorkspaceFoldersManager workspaceFoldersManager;

  ServerIssueTrackerWrapper(BackendServiceFacade backend, WorkspaceFoldersManager workspaceFoldersManager) {
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.backend = backend;
  }

  public void matchAndTrack(String filePath, Collection<RawIssueDto> issues, Consumer<DelegatingIssue> issueListener, boolean shouldFetchServerIssues) {
    if (issues.isEmpty()) {
      return;
    }

    getWorkspaceFolderUri(issues, workspaceFoldersManager).ifPresent(uri -> {
      var issueList = issues.stream().filter(issue -> !issue.getType().equals(SECURITY_HOTSPOT)).toList();
      var securityHotspotList = issues.stream().filter(issue -> issue.getType().equals(SECURITY_HOTSPOT)).toList();

      if (!issueList.isEmpty()) {
        matchAndTrackIssues(filePath, issueListener, shouldFetchServerIssues, issueList, uri);
      }

      if (!securityHotspotList.isEmpty()) {
        matchHotspots(filePath, issueListener, shouldFetchServerIssues, securityHotspotList, uri);
      }
    });
  }

  private void matchAndTrackIssues(String filePath, Consumer<DelegatingIssue> issueListener, boolean shouldFetchServerIssues,
    Collection<RawIssueDto> rawIssues, URI workspaceFolderUri) {
    var issuesByFilepath = getClientTrackedIssuesByIdeRelativePath(filePath, rawIssues);
    var trackWithServerIssuesResponse = backend.getBackendService().matchIssues(
      new TrackWithServerIssuesParams(workspaceFolderUri.toString(), issuesByFilepath, shouldFetchServerIssues)
    ).join();
    matchAndTrackIssues(Path.of(filePath), issueListener, rawIssues, trackWithServerIssuesResponse.getIssuesByIdeRelativePath());
  }

  private void matchHotspots(String filePath, Consumer<DelegatingIssue> issueListener, boolean shouldFetchServerIssues,
    Collection<RawIssueDto> rawIssues, URI workspaceFolderUri) {
    var issuesByFilepath = getClientTrackedIssuesByIdeRelativePath(filePath, rawIssues);
    var matchWithServerSecurityHotspotsResponse = backend.getBackendService()
      .matchHotspots(new MatchWithServerSecurityHotspotsParams(workspaceFolderUri.toString(), issuesByFilepath, shouldFetchServerIssues))
      .join();

    var securityHotspotsByIdeRelativePath = matchWithServerSecurityHotspotsResponse.getSecurityHotspotsByIdeRelativePath();
    var eitherList = securityHotspotsByIdeRelativePath.getOrDefault(Path.of(filePath), Collections.emptyList());
    Streams.zip(rawIssues.stream(), eitherList.stream(), (issue, either) -> {
        if (either.isLeft()) {
          var serverHotspot = either.getLeft();
          var hotspotSeverity = issue.getSeverity();
          return new DelegatingIssue(issue, serverHotspot.getId(), isResolved(serverHotspot.getStatus()),
            hotspotSeverity, serverHotspot.getServerKey(), serverHotspot.isOnNewCode(), serverHotspot.getStatus());
        } else {
          var localHotspot = either.getRight();
          return new DelegatingIssue(issue, localHotspot.getId(), false, true);
        }
      })
      .filter(not(DelegatingIssue::isResolved))
      .forEach(issueListener::accept);
  }

  public boolean isResolved(HotspotStatus status) {
    return status.equals(SAFE) || status.equals(FIXED);
  }

  private static void matchAndTrackIssues(Path filePath, Consumer<DelegatingIssue> issueListener, Collection<RawIssueDto> rawIssues,
    Map<Path, List<Either<ServerMatchedIssueDto, LocalOnlyIssueDto>>> issuesByIdeRelativePath) {
    //
    var eitherList = issuesByIdeRelativePath.getOrDefault(filePath, Collections.emptyList());
    Streams.zip(rawIssues.stream(), eitherList.stream(), (issue, either) -> {
        if (either.isLeft()) {
          var serverIssue = either.getLeft();
          var issueSeverity = serverIssue.getOverriddenSeverity() == null ? issue.getSeverity() : serverIssue.getOverriddenSeverity();
          return new DelegatingIssue(issue, serverIssue.getId(), serverIssue.isResolved(), issueSeverity, serverIssue.getServerKey(), serverIssue.isOnNewCode(), null);
        } else {
          var localIssue = either.getRight();
          return new DelegatingIssue(issue, localIssue.getId(), localIssue.getResolutionStatus() != null, true);
        }
      })
      .filter(not(DelegatingIssue::isResolved))
      .forEach(issueListener::accept);
  }


  @NotNull
  private static Map<Path, List<ClientTrackedFindingDto>> getClientTrackedIssuesByIdeRelativePath(String filePath, Collection<RawIssueDto> rawIssues) {
    var clientTrackedIssueDtos = rawIssues.stream().map(ServerIssueTrackerWrapper::createClientTrackedIssueDto).toList();
    return Map.of(Path.of(filePath), clientTrackedIssueDtos);
  }

  static Optional<URI> getWorkspaceFolderUri(Collection<RawIssueDto> issues, WorkspaceFoldersManager workspaceFoldersManager) {
    var anIssue = issues.stream().findFirst();
    if (anIssue.isPresent()) {
      var folderForFile = workspaceFoldersManager.findFolderForFile(Objects.requireNonNull(anIssue.get().getFileUri()));
      if (folderForFile.isPresent()) {
        return Optional.of(folderForFile.get().getUri());
      }
    }
    return Optional.empty();
  }

  @NotNull
  private static ClientTrackedFindingDto createClientTrackedIssueDto(RawIssueDto issue) {
//    TextRangeWithHashDto textRangeWithHashDto = null;
//    LineWithHashDto lineWithHashDto = null;
//    var textRange = issue.getTextRange();
//    if (issue.getFileUri() != null && textRange != null && inputFile != null) {
//      var fileLines = inputFile.contents().lines().toList();
//      var textRangeContent = getTextRangeContentOfFile(fileLines, textRange);
//      var lineContent = fileLines.get(textRange.getStartLine() - 1);
//      textRangeWithHashDto = new TextRangeWithHashDto(textRange.getStartLine(), textRange.getStartLineOffset(),
//        textRange.getEndLine(), textRange.getEndLineOffset(), Utils.hash(textRangeContent));
//      lineWithHashDto = new LineWithHashDto(textRange.getStartLine(), Utils.hash(lineContent));
//    }
//    return new ClientTrackedFindingDto(null, issue.getSeverity().toString(), textRangeWithHashDto, lineWithHashDto, issue.getRuleKey(), issue.getMessage());
    return null;
  }

}
