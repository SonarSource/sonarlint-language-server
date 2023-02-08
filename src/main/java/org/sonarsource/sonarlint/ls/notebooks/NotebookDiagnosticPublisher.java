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
import java.util.Collections;
import java.util.Map;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.sonarsource.sonarlint.ls.IssuesCache;
import org.sonarsource.sonarlint.ls.IssuesCache.VersionedIssue;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.util.stream.Collectors.groupingBy;
import static org.sonarsource.sonarlint.ls.DiagnosticPublisher.message;
import static org.sonarsource.sonarlint.ls.DiagnosticPublisher.setSource;
import static org.sonarsource.sonarlint.ls.util.Utils.severity;

public class NotebookDiagnosticPublisher {
  private final SonarLintExtendedLanguageClient client;

  private final IssuesCache issuesCache;

  public NotebookDiagnosticPublisher(SonarLintExtendedLanguageClient client, IssuesCache issuesCache) {
    this.client = client;
    this.issuesCache = issuesCache;
  }

  static Diagnostic convertCellIssue(Map.Entry<String, DelegatingCellIssue> entry) {
    var issue = entry.getValue();
    var diagnostic = new Diagnostic();
    var severity = severity(issue.getSeverity());
    diagnostic.setSeverity(severity);

    var range = Utils.convert(issue);
    diagnostic.setRange(range);
    diagnostic.setCode(issue.getRuleKey());
    diagnostic.setMessage(message(issue));
    setSource(issue, diagnostic);
    diagnostic.setData(entry.getKey());

    return diagnostic;
  }

  public void publishNotebookDiagnostics(URI uri, VersionedOpenNotebook versionedOpenNotebook) {
    // TODO call this either at the end of analysis or during issue listener. Put in a set a list of cellUris that have issues.
    var p = new PublishDiagnosticsParams();

    Map<String, VersionedIssue> localIssues = issuesCache.get(uri);

    var localDiagnostics = localIssues.entrySet()
      .stream()
      .map(entry -> Map.entry(entry.getKey(), versionedOpenNotebook.toCellIssue(entry.getValue().getIssue())))
      .map(NotebookDiagnosticPublisher::convertCellIssue)
      .collect(groupingBy(diagnostic -> versionedOpenNotebook.getCellUri(localIssues.get(diagnostic.getData()).getIssue().getStartLine()).get()));

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
    // TODO call this at the end of analysis
    // check if, for the notebook, any uri does not have an issue and publish empty diags
  }
}
