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
package org.sonarsource.sonarlint.ls;

import com.google.gson.JsonObject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.Diagnostic;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;
import org.sonarsource.sonarlint.ls.connected.DelegatingIssue;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;

import static org.sonarsource.sonarlint.ls.util.Utils.isDelegatingIssueWithServerIssueKey;

public class IssuesCache {

  private final Map<URI, Map<String, DelegatingFinding>> issuesPerIdPerFileURI = new ConcurrentHashMap<>();

  public void clear(URI fileUri) {
    issuesPerIdPerFileURI.remove(fileUri);
  }

  public void analysisStarted(VersionedOpenFile versionedOpenFile) {
    issuesPerIdPerFileURI.remove(versionedOpenFile.getUri());
  }

  public void reportIssues(Map<URI, List<RaisedFindingDto>> issuesByFileUri) {
    issuesByFileUri.forEach((fileUri, issues) -> issuesPerIdPerFileURI.computeIfAbsent(fileUri, u -> new ConcurrentHashMap<>())
      .putAll(issues.stream().collect(Collectors.toMap(i -> i.getId().toString(), i -> new DelegatingIssue(i, fileUri)))));
  }

  public int count(URI f) {
    return get(f).size();
  }

  public void removeFindingWithServerKey(String fileUriStr, String key) {
    var fileUri = URI.create(fileUriStr);
    var issues = issuesPerIdPerFileURI.get(fileUri);
    if (issues != null) {
      var first = issues.entrySet()
        .stream()
        .filter(issueEntry -> isDelegatingIssueWithServerIssueKey(key, issueEntry.getValue()) || isLocalIssueWithKey(key, issueEntry.getValue()))
        .map(Map.Entry::getKey)
        .findFirst();
      first.ifPresent(issues::remove);
    }
  }

  private static boolean isLocalIssueWithKey(String key, DelegatingFinding findingDto) {
    return key.equals(findingDto.getIssueId().toString());
  }

  public Optional<DelegatingFinding> getIssueForDiagnostic(URI fileUri, Diagnostic d) {
    var issuesForFile = get(fileUri);
    return Optional.ofNullable(d.getData())
      .map(JsonObject.class::cast)
      .map(jsonObject -> jsonObject.get("entryKey").getAsString())
      .map(issuesForFile::get);
  }

  public Map<String, DelegatingFinding> get(URI fileUri) {
    return issuesPerIdPerFileURI.getOrDefault(fileUri, Map.of());
  }
}
