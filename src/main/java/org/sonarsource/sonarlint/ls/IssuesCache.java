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
package org.sonarsource.sonarlint.ls;

import com.google.gson.JsonPrimitive;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.lsp4j.Diagnostic;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.ls.file.VersionnedOpenFile;

import static java.util.Collections.emptyMap;

public class IssuesCache {

  private final Map<URI, Map<String, VersionnedIssue>> issuesPerIdPerFileURI = new ConcurrentHashMap<>();
  private final Map<URI, Map<String, VersionnedIssue>> inProgressAnalysisIssuesPerIdPerFileURI = new ConcurrentHashMap<>();

  public void clear(URI fileUri) {
    issuesPerIdPerFileURI.remove(fileUri);
    inProgressAnalysisIssuesPerIdPerFileURI.remove(fileUri);
  }

  public void analysisStarted(VersionnedOpenFile versionnedOpenFile) {
    inProgressAnalysisIssuesPerIdPerFileURI.remove(versionnedOpenFile.getUri());
  }

  public void reportIssue(VersionnedOpenFile versionnedOpenFile, Issue issue) {
    inProgressAnalysisIssuesPerIdPerFileURI.computeIfAbsent(versionnedOpenFile.getUri(), u -> new HashMap<>()).put(UUID.randomUUID().toString(),
      new VersionnedIssue(issue, versionnedOpenFile.getVersion()));
  }

  public int count(URI f) {
    return Optional.ofNullable(issuesPerIdPerFileURI.get(f)).map(Map::size).orElse(0);
  }

  public void analysisFailed(VersionnedOpenFile versionnedOpenFile) {
    // Keep issues of the previous analysis
    inProgressAnalysisIssuesPerIdPerFileURI.remove(versionnedOpenFile.getUri());
  }

  public void analysisSucceeded(VersionnedOpenFile versionnedOpenFile) {
    // Swap issues
    var newIssues = inProgressAnalysisIssuesPerIdPerFileURI.remove(versionnedOpenFile.getUri());
    if (newIssues != null) {
      issuesPerIdPerFileURI.put(versionnedOpenFile.getUri(), newIssues);
    } else {
      issuesPerIdPerFileURI.remove(versionnedOpenFile.getUri());
    }
  }

  public Optional<VersionnedIssue> getIssueForDiagnostic(URI fileUri, Diagnostic d) {
    var issuesForFile = issuesPerIdPerFileURI.getOrDefault(fileUri, emptyMap());
    return Optional.ofNullable(d.getData())
      .map(JsonPrimitive.class::cast)
      .map(JsonPrimitive::getAsString)
      .map(issuesForFile::get)
      .filter(Objects::nonNull);
  }

  public static class VersionnedIssue {
    private final Issue issue;
    private final int documentVersion;

    public VersionnedIssue(Issue issue, int documentVersion) {
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

  public Map<String, VersionnedIssue> get(URI fileUri) {
    return Optional.ofNullable(issuesPerIdPerFileURI.get(fileUri)).orElse(Map.of());
  }
}
