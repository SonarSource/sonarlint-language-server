/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.IssuesCache;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.notebooks.NotebookDiagnosticPublisher.convertCellIssue;

class NotebookDiagnosticPublisherTests {
  NotebookDiagnosticPublisher notebookDiagnosticPublisher;
  SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  IssuesCache issuesCache = mock(IssuesCache.class);
  OpenNotebooksCache openNotebooksCache = mock(OpenNotebooksCache.class);

  @BeforeEach
  void setup() {
    notebookDiagnosticPublisher = new NotebookDiagnosticPublisher(client, issuesCache);
    notebookDiagnosticPublisher.setOpenNotebooksCache(openNotebooksCache);
  }

  @Test
  void shouldConvertCellIssue() {
    RaisedIssueDto raisedFindingDto = mock(RaisedIssueDto.class);
    DelegatingCellIssue cellIssue = mock(DelegatingCellIssue.class);
    TextRangeDto textRange = new TextRangeDto(4, 0, 5, 5);

    var issueKey = UUID.randomUUID().toString();
    when(cellIssue.getIssue()).thenReturn(raisedFindingDto);
    when(cellIssue.getMessage()).thenReturn("don't do this");
    when(cellIssue.getTextRange()).thenReturn(textRange);
    when(cellIssue.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));

    var diagnostic = convertCellIssue(new AbstractMap.SimpleImmutableEntry<>(issueKey, cellIssue));

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    assertThat(diagnostic.getRange().getStart().getLine()).isEqualTo(textRange.getStartLine() - 1);
    assertThat(((DiagnosticPublisher.DiagnosticData) diagnostic.getData()).getEntryKey()).isEqualTo(issueKey);
  }

  @Test
  void shouldRemoveCellDiagnostics() {
    var cellUri = URI.create("file:///some/notebook.ipynb#cell1");

    notebookDiagnosticPublisher.removeCellDiagnostics(cellUri);

    var p = new PublishDiagnosticsParams();
    p.setDiagnostics(Collections.emptyList());
    p.setUri(cellUri.toString());

    verify(client).publishDiagnostics(p);
  }

  @Test
  void shouldPublishDiagnostics() {
    var notebookUri = URI.create("file:///some/notebook.ipynb");

    var cell1 = new TextDocumentItem();
    cell1.setUri(notebookUri + "#cell1");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var cell2 = new TextDocumentItem();
    cell2.setUri(notebookUri + "#cell2");
    cell2.setText("cell2 line1\ncell2 line2\n");
    var fakeNotebook = VersionedOpenNotebook.create(notebookUri, 1, List.of(cell1, cell2), mock(NotebookDiagnosticPublisher.class));

    var issue1 = createFakeBlockerIssue();
    var issue2 = createFakeMinorIssue();
    var localIssues = Map.of(UUID.randomUUID().toString(), issue1,
      UUID.randomUUID().toString(), issue2);


    when(issuesCache.get(notebookUri)).thenReturn(localIssues);

    notebookDiagnosticPublisher.publishNotebookDiagnostics(notebookUri, fakeNotebook);

    verify(client, times(1)).publishDiagnostics(any(PublishDiagnosticsParams.class));
  }

  @Test
  void shouldCleanUpDiagnosticsForCellsWithNoIssues() {
    var notebookUri = URI.create("file:///some/notebook.ipynb");

    var cell1 = new TextDocumentItem();
    cell1.setUri(notebookUri + "#cell1");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var cell2 = new TextDocumentItem();
    cell2.setUri(notebookUri + "#cell2");
    cell2.setText("cell2 line1\ncell2 line2\n");
    var fakeNotebook = VersionedOpenNotebook.create(notebookUri, 1, List.of(cell1, cell2), mock(NotebookDiagnosticPublisher.class));


    var issue1 = createFakeBlockerIssue();
    var issue2 = createFakeMinorIssue();
    var localIssues = Map.of(UUID.randomUUID().toString(), issue1,
      UUID.randomUUID().toString(), issue2);


    when(issuesCache.get(notebookUri)).thenReturn(localIssues);
    when(openNotebooksCache.getFile(notebookUri)).thenReturn(Optional.of(fakeNotebook));

    // populate notebookCellsWithIssues map
    notebookDiagnosticPublisher.publishNotebookDiagnostics(notebookUri, fakeNotebook);

    notebookDiagnosticPublisher.cleanupDiagnosticsForCellsWithoutIssues(notebookUri);

    var p2 = new PublishDiagnosticsParams();
    p2.setDiagnostics(Collections.emptyList());
    p2.setUri(cell2.getUri());

    verify(client, times(1)).publishDiagnostics(p2);
  }

  @Test
  void shouldRemoveExistingDiagnosticsFromNotebook() {
    var notebookUri = URI.create("file:///some/notebook.ipynb");

    var cell1 = new TextDocumentItem();
    cell1.setUri(notebookUri + "#cell1");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var cell2 = new TextDocumentItem();
    cell2.setUri(notebookUri + "#cell2");
    cell2.setText("cell2 line1\ncell2 line2\n");
    var fakeNotebook = VersionedOpenNotebook.create(notebookUri, 1, List.of(cell1, cell2), mock(NotebookDiagnosticPublisher.class));


    var issue1 = createFakeBlockerIssue();
    var issue2 = createFakeMinorIssue();
    var localIssues = Map.of(UUID.randomUUID().toString(), issue1,
      UUID.randomUUID().toString(), issue2);


    when(issuesCache.get(notebookUri)).thenReturn(localIssues);
    when(openNotebooksCache.getFile(notebookUri)).thenReturn(Optional.of(fakeNotebook));

    // populate notebookCellsWithIssues map
    notebookDiagnosticPublisher.publishNotebookDiagnostics(notebookUri, fakeNotebook);

    notebookDiagnosticPublisher.removeAllExistingDiagnosticsForNotebook(notebookUri);

    var p1 = new PublishDiagnosticsParams();
    p1.setDiagnostics(Collections.emptyList());
    p1.setUri(cell1.getUri());

    verify(client, times(1)).publishDiagnostics(p1);
  }

  private DelegatingFinding createFakeBlockerIssue() {
    var issue = mock(RaisedIssueDto.class);
    TextRangeDto textRangeDto = new TextRangeDto(2, 0, 2, 5);
    TextRangeDto textRange = new TextRangeDto(2, 0, 2, 5);
    when(issue.getSeverityMode()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));
    when(issue.getPrimaryMessage()).thenReturn("don't do this");
    when(issue.getRuleKey()).thenReturn("squid:123");
    when(issue.getTextRange()).thenReturn(textRangeDto);
    when(issue.getFlows()).thenReturn(List.of(mock(IssueFlowDto.class)));

    return new DelegatingCellIssue(issue, URI.create("file:///my/folder/notebook.ipynb"), textRange, Collections.emptyList());
  }

  private DelegatingFinding createFakeMinorIssue() {
    var issue = mock(RaisedIssueDto.class);
    TextRangeDto textRangeDto = new TextRangeDto(1, 0, 1, 3);
    TextRangeDto textRange = new TextRangeDto(1, 0, 1, 3);

    when(issue.getSeverityMode()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.MINOR, RuleType.BUG)));
    when(issue.getPrimaryMessage()).thenReturn("don't do this please");
    when(issue.getRuleKey()).thenReturn("squid:122");
    when(issue.getTextRange()).thenReturn(textRangeDto);
    when(issue.getFlows()).thenReturn(List.of(mock(IssueFlowDto.class)));

    return new DelegatingCellIssue(issue, URI.create("file:///my/folder/notebook.ipynb"), textRange, Collections.emptyList());
  }
}
