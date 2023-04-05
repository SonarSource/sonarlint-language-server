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

import com.google.gson.JsonPrimitive;
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
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;

public class IssuesCache {

  private final Map<URI, Map<String, VersionedIssue>> issuesPerIdPerFileURI = new ConcurrentHashMap<>();
  private final Map<URI, Map<String, VersionedIssue>> inProgressAnalysisIssuesPerIdPerFileURI = new ConcurrentHashMap<>();

  public void clear(URI fileUri) {
    issuesPerIdPerFileURI.remove(fileUri);
    inProgressAnalysisIssuesPerIdPerFileURI.remove(fileUri);
  }

  /**
   * Keep only the entries for the given set of files
   * @param openFilesUri the set of file URIs to keep
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
    inProgressAnalysisIssuesPerIdPerFileURI.computeIfAbsent(versionedOpenFile.getUri(), u -> new HashMap<>()).put(UUID.randomUUID().toString(),
      new VersionedIssue(issue, versionedOpenFile.getVersion()));
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

  public Optional<VersionedIssue> getIssueForDiagnostic(URI fileUri, Diagnostic d) {
    var issuesForFile = get(fileUri);
    return Optional.ofNullable(d.getData())
      .map(JsonPrimitive.class::cast)
      .map(JsonPrimitive::getAsString)
      .map(issuesForFile::get)
      .filter(Objects::nonNull);
  }

  public static class VersionedIssue {
    private final Issue issue;
    private final int documentVersion;

    public VersionedIssue(Issue issue, int documentVersion) {
      this.issue = issue;
      this.documentVersion = documentVersion;
    }

    public Issue getIssue() {
      return issue;
    }

    public int getDocumentVersion() {
      return documentVersion;
    }
  }

  public Map<String, VersionedIssue> get(URI fileUri) {
    return inProgressAnalysisIssuesPerIdPerFileURI.getOrDefault(fileUri, issuesPerIdPerFileURI.getOrDefault(fileUri, Map.of()));
  }
}
