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
package org.sonarsource.sonarlint.ls;

import com.google.gson.JsonObject;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.Diagnostic;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.ls.connected.DelegatingIssue;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;

import static org.sonarsource.sonarlint.ls.util.Utils.hotspotReviewStatusValueOfHotspotStatus;
import static org.sonarsource.sonarlint.ls.util.Utils.isDelegatingIssueWithServerIssueKey;

public class IssuesCache {

  private final Map<URI, Map<String, VersionedIssue>> issuesPerIdPerFileURI = new ConcurrentHashMap<>();
  private final Map<URI, Map<String, VersionedIssue>> inProgressAnalysisIssuesPerIdPerFileURI = new ConcurrentHashMap<>();

  public void clear(URI fileUri) {
    issuesPerIdPerFileURI.remove(fileUri);
    inProgressAnalysisIssuesPerIdPerFileURI.remove(fileUri);
  }

  /**
   * Keep only the entries for the given set of files
   *
   * @param openFiles the set of file URIs to keep
   * @return the set of file URIs that were removed
   */
  public Set<URI> keepOnly(Collection<VersionedOpenFile> openFiles) {
    var keysBeforeRemoval = new HashSet<>(issuesPerIdPerFileURI.keySet());
    var keysToRetain = openFiles.stream().map(VersionedOpenFile::getUri).collect(Collectors.toSet());
    issuesPerIdPerFileURI.keySet().retainAll(keysToRetain);
    inProgressAnalysisIssuesPerIdPerFileURI.keySet().retainAll(keysToRetain);
    keysBeforeRemoval.removeAll(issuesPerIdPerFileURI.keySet());
    return keysBeforeRemoval;
  }

  public void analysisStarted(VersionedOpenFile versionedOpenFile) {
    inProgressAnalysisIssuesPerIdPerFileURI.remove(versionedOpenFile.getUri());
  }

  public void reportIssue(VersionedOpenFile versionedOpenFile, Issue issue) {
    var issueId = getIssueId(issue);
    inProgressAnalysisIssuesPerIdPerFileURI.computeIfAbsent(versionedOpenFile.getUri(), u -> new HashMap<>()).put(issueId,
      new VersionedIssue(issue, versionedOpenFile.getVersion()));
  }

  private static String getIssueId(Issue issue) {
    String issueId = null;
    if (issue instanceof DelegatingIssue delegatingIssue) {
      var issueUuid = delegatingIssue.getIssueId();
      issueId = issueUuid != null ? issueUuid.toString() : null;
    }
    if (issueId == null) {
      issueId = UUID.randomUUID().toString();
    }
    return issueId;
  }

  public int count(URI f) {
    return get(f).size();
  }

  public void analysisFailed(VersionedOpenFile versionedOpenFile) {
    // Keep issues of the previous analysis
    inProgressAnalysisIssuesPerIdPerFileURI.remove(versionedOpenFile.getUri());
  }

  public void analysisSucceeded(VersionedOpenFile versionedOpenFile) {
    // Swap issues
    var newIssues = inProgressAnalysisIssuesPerIdPerFileURI.remove(versionedOpenFile.getUri());
    if (newIssues != null) {
      issuesPerIdPerFileURI.put(versionedOpenFile.getUri(), newIssues);
    } else {
      issuesPerIdPerFileURI.remove(versionedOpenFile.getUri());
    }
  }

  public void removeIssueWithServerKey(String fileUriStr, String key) {
    var fileUri = URI.create(fileUriStr);
    var issues = issuesPerIdPerFileURI.get(fileUri);
    if (issues != null) {
      var first = issues.entrySet()
        .stream()
        .filter(issueEntry -> isDelegatingIssueWithServerIssueKey(key, issueEntry) || isLocalIssueWithKey(key, issueEntry.getValue()))
        .map(Map.Entry::getKey)
        .findFirst();
      first.ifPresent(issues::remove);
    }
  }

  private static boolean isLocalIssueWithKey(String key, VersionedIssue versionedIssue) {
    return versionedIssue.issue() instanceof DelegatingIssue delegatingIssue
      && (key.equals(delegatingIssue.getIssueId().toString()));
  }

  public Optional<Map.Entry<String, VersionedIssue>> findIssuePerId(String fileUriStr, String serverIssueKey) {
    var fileUri = URI.create(fileUriStr);
    var issues = issuesPerIdPerFileURI.get(fileUri);
    if (issues != null) {
      return issues.entrySet()
        .stream()
        .filter(issueEntry -> isDelegatingIssueWithServerIssueKey(serverIssueKey, issueEntry))
        .findFirst();
    }
    return Optional.empty();
  }

  public void updateIssueStatus(String fileUriStr, String serverIssueKey, HotspotStatus newStatus) {
    var issuePerId = findIssuePerId(fileUriStr, serverIssueKey);
    if (issuePerId.isPresent()) {
      var versionedIssue = issuePerId.get().getValue();
      var delegatingIssue = (DelegatingIssue) versionedIssue.issue();
      var clonedDelegatingIssue = delegatingIssue.cloneWithNewStatus(hotspotReviewStatusValueOfHotspotStatus(newStatus));
      var clonedVersionedIssue = new VersionedIssue(clonedDelegatingIssue, versionedIssue.documentVersion);
      var issuesByKey = issuesPerIdPerFileURI.get(URI.create(fileUriStr));
      if (issuesByKey != null) {
        issuesByKey.put(issuePerId.get().getKey(), clonedVersionedIssue);
      }
    }
  }

  public Optional<VersionedIssue> getIssueForDiagnostic(URI fileUri, Diagnostic d) {
    var issuesForFile = get(fileUri);
    return Optional.ofNullable(d.getData())
      .map(JsonObject.class::cast)
      .map(jsonObject -> jsonObject.get("entryKey").getAsString())
      .map(issuesForFile::get)
      .filter(Objects::nonNull);
  }

  public record VersionedIssue(Issue issue, int documentVersion) {}

  public Map<String, VersionedIssue> get(URI fileUri) {
    return inProgressAnalysisIssuesPerIdPerFileURI.getOrDefault(fileUri, issuesPerIdPerFileURI.getOrDefault(fileUri, Map.of()));
  }
}
