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
package org.sonarsource.sonarlint.ls.notebooks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.IssuesCache;
import org.sonarsource.sonarlint.ls.IssuesCache.VersionedIssue;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

import static java.util.stream.Collectors.groupingBy;
import static org.sonarsource.sonarlint.ls.DiagnosticPublisher.prepareDiagnostic;

public class NotebookDiagnosticPublisher {
  private final SonarLintExtendedLanguageClient client;

  private final IssuesCache issuesCache;
  private final Map<URI, List<URI>> notebookCellsWithIssues = new HashMap<>();
  private OpenNotebooksCache openNotebooksCache;

  public NotebookDiagnosticPublisher(SonarLintExtendedLanguageClient client, IssuesCache issuesCache) {
    this.client = client;
    this.issuesCache = issuesCache;
  }

  public void setOpenNotebooksCache(OpenNotebooksCache openNotebooksCache) {
    this.openNotebooksCache = openNotebooksCache;
  }

  static Diagnostic convertCellIssue(Map.Entry<String, DelegatingCellIssue> entry) {
    var issue = entry.getValue();
    return prepareDiagnostic(issue, entry.getKey(), true, false);
  }

  public void publishNotebookDiagnostics(URI uri, VersionedOpenNotebook versionedOpenNotebook) {
    var p = new PublishDiagnosticsParams();

    Map<String, VersionedIssue> localIssues = issuesCache.get(uri);

    var localDiagnostics = localIssues.entrySet()
      .stream()
      .map(entry -> Map.entry(entry.getKey(), versionedOpenNotebook.toCellIssue(entry.getValue().issue())))
      .map(NotebookDiagnosticPublisher::convertCellIssue)
      .collect(groupingBy(diagnostic -> {
        var localIssue = localIssues.get(((DiagnosticPublisher.DiagnosticData) diagnostic.getData()).getEntryKey());
        var cellUri = URI.create("");
        if (localIssue != null && localIssue.issue() != null && localIssue.issue().getStartLine() != null) {
          // Better to not publish any diagnostics than to publish for wrong location
          cellUri = versionedOpenNotebook.getCellUri(localIssue.issue().getStartLine()).orElse(URI.create(""));
        }

        var cellsWithIssues = notebookCellsWithIssues.get(uri);
        if (cellsWithIssues != null && !cellsWithIssues.isEmpty()) {
          cellsWithIssues.add(cellUri);
        } else {
          var cells = new ArrayList<URI>();
          cells.add(cellUri);
          notebookCellsWithIssues.put(uri, cells);
        }
        return cellUri;
      }));

    localDiagnostics.forEach((cellUri, diagnostics) -> {
      p.setDiagnostics(diagnostics);
      p.setUri(cellUri.toString());
      client.publishDiagnostics(p);
    });
  }

  public void removeCellDiagnostics(URI cellUri) {
    var p = new PublishDiagnosticsParams();
    p.setDiagnostics(Collections.emptyList());
    p.setUri(cellUri.toString());
    client.publishDiagnostics(p);
  }

  public void cleanupDiagnostics(URI notebookUri) {
    var versionedOpenNotebook = openNotebooksCache.getFile(notebookUri);
    var cellsWithIssues = notebookCellsWithIssues.getOrDefault(notebookUri, List.of());
    versionedOpenNotebook.ifPresent(notebook -> notebook.getCellUris().forEach(cellUri -> {
      if (cellsWithIssues != null && !cellsWithIssues.contains(URI.create(cellUri))) {
        removeCellDiagnostics(URI.create(cellUri));
      }
    }));
  }

  public void cleanupCellsList(URI notebookUri) {
    this.notebookCellsWithIssues.remove(notebookUri);
  }
}
