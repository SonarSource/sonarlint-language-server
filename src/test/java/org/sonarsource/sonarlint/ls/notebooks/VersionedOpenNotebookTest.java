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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.eclipse.lsp4j.NotebookCellArrayChange;
import org.eclipse.lsp4j.NotebookDocumentChangeEvent;
import org.eclipse.lsp4j.NotebookDocumentChangeEventCellStructure;
import org.eclipse.lsp4j.NotebookDocumentChangeEventCells;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.TextRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.SonarLintLanguageServer.PYTHON_LANGUAGE;

class VersionedOpenNotebookTest {

  @Test
  void shouldConcatenateCells() throws IOException {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    var clientInputFile = underTest.asInputFile(Path.of(URI.create("file:///some")));

    assertThat(clientInputFile.uri()).isEqualTo(tmpUri);
    assertThat(clientInputFile.contents()).isEqualTo("" +
      "cell1 line1\n" +
      "cell1 line2\n" +
      "\n" +
      "cell2 line1\n" +
      "cell2 line2\n" +
      "\n" +
      "cell3 line1\n" +
      "cell3 line2\n");
    assertThat(clientInputFile.isTest()).isFalse();
    assertThat(clientInputFile.language()).isNull();

    assertThat(underTest.getCellUri(0)).isEmpty();
    assertThat(underTest.getCellUri(1).get()).hasFragment("cell1");
    assertThat(underTest.getCellUri(2).get()).hasFragment("cell1");
    assertThat(underTest.getCellUri(3).get()).hasFragment("cell1");
    assertThat(underTest.getCellUri(4).get()).hasFragment("cell2");
    assertThat(underTest.getCellUri(5).get()).hasFragment("cell2");
    assertThat(underTest.getCellUri(6).get()).hasFragment("cell2");
    assertThat(underTest.getCellUri(7).get()).hasFragment("cell3");
    assertThat(underTest.getCellUri(8).get()).hasFragment("cell3");
    assertThat(underTest.getCellUri(9).get()).hasFragment("cell3");
    assertThat(underTest.getCellUri(10)).isEmpty();
  }

  @Test
  void shouldConvertToVersionedOpenFile() {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    var versionedOpenFile = underTest.asVersionedOpenFile();

    assertThat(versionedOpenFile.getVersion()).isEqualTo(underTest.getNotebookVersion());
    assertThat(versionedOpenFile.getUri()).isEqualTo(underTest.getUri());
    assertThat(versionedOpenFile.getContent()).isEqualTo("" +
      "cell1 line1\n" +
      "cell1 line2\n" +
      "\n" +
      "cell2 line1\n" +
      "cell2 line2\n" +
      "\n" +
      "cell3 line1\n" +
      "cell3 line2\n");
    assertThat(versionedOpenFile.getLanguageId()).isEqualTo(PYTHON_LANGUAGE);
    assertThat(versionedOpenFile.isNotebook()).isTrue();
  }

  @Test
  void shouldCorrectlyStoreCells() {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    assertThat(underTest.getCells()).hasSize(3);
    assertThat(underTest.getCells()).contains(tmpUri.toString() + "#cell1");
    assertThat(underTest.getCells()).contains(tmpUri.toString() + "#cell2");
    assertThat(underTest.getCells()).contains(tmpUri.toString() + "#cell3");
  }

  @Test
  void shouldCorrectlyMapFileLineToCellLine() {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    assertThat(underTest.getCellUri(1)).contains(URI.create(tmpUri + "#cell1"));
    assertThat(underTest.getCellUri(2)).contains(URI.create(tmpUri + "#cell1"));
    assertThat(underTest.getCellUri(3)).contains(URI.create(tmpUri + "#cell1"));
    assertThat(underTest.getCellUri(4)).contains(URI.create(tmpUri + "#cell2"));
    assertThat(underTest.getCellUri(5)).contains(URI.create(tmpUri + "#cell2"));
    assertThat(underTest.getCellUri(6)).contains(URI.create(tmpUri + "#cell2"));
    assertThat(underTest.getCellUri(7)).contains(URI.create(tmpUri + "#cell3"));
    assertThat(underTest.getCellUri(8)).contains(URI.create(tmpUri + "#cell3"));
    assertThat(underTest.getCellUri(9)).contains(URI.create(tmpUri + "#cell3"));
    assertThat(underTest.getCellUri(10)).isEmpty();
    assertThat(underTest.getCellUri(0)).isEmpty();
    assertThat(underTest.getCellUri(-4)).isEmpty();
  }

  @Test
  void shouldConvertToCellIssue() {
    Issue issue = mock(Issue.class);
    TextRange textRange = new TextRange(4, 0, 5, 5);

    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getMessage()).thenReturn("don't do this");
    when(issue.getRuleKey()).thenReturn("squid:123");
    when(issue.getTextRange()).thenReturn(textRange);
    when(issue.getStartLine()).thenReturn(4);
    when(issue.getStartLineOffset()).thenReturn(0);
    when(issue.getEndLine()).thenReturn(5);
    when(issue.getEndLineOffset()).thenReturn(5);
    when(issue.flows()).thenReturn(List.of(mock(Flow.class)));
    when(issue.getInputFile()).thenReturn(mock(ClientInputFile.class));

    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    var cellIssue = underTest.toCellIssue(issue);

    assertThat(cellIssue.getRuleKey()).isEqualTo(issue.getRuleKey());
    assertThat(cellIssue.getMessage()).isEqualTo(issue.getMessage());
    assertThat(cellIssue.getSeverity()).isEqualTo(issue.getSeverity());
    assertThat(cellIssue.getStartLine()).isEqualTo(1);
    assertThat(cellIssue.getStartLineOffset()).isZero();
    assertThat(cellIssue.getEndLine()).isEqualTo(2);
    assertThat(cellIssue.getEndLineOffset()).isEqualTo(5);
  }

  @Test
  void shouldConvertToCellIssueWhenIssueTextRangeIsNull() {
    Issue issue = mock(Issue.class);

    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getMessage()).thenReturn("don't do this");
    when(issue.getRuleKey()).thenReturn("squid:123");
    when(issue.getTextRange()).thenReturn(null);
    when(issue.getStartLine()).thenReturn(null);
    when(issue.getStartLineOffset()).thenReturn(null);
    when(issue.getEndLine()).thenReturn(null);
    when(issue.getEndLineOffset()).thenReturn(null);
    when(issue.flows()).thenReturn(List.of(mock(Flow.class)));
    when(issue.getInputFile()).thenReturn(mock(ClientInputFile.class));

    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    var cellIssue = underTest.toCellIssue(issue);

    assertThat(cellIssue.getRuleKey()).isEqualTo(issue.getRuleKey());
    assertThat(cellIssue.getMessage()).isEqualTo(issue.getMessage());
    assertThat(cellIssue.getSeverity()).isEqualTo(issue.getSeverity());
    assertThat(cellIssue.getStartLine()).isNull();
    assertThat(cellIssue.getStartLineOffset()).isNull();
    assertThat(cellIssue.getEndLine()).isNull();
    assertThat(cellIssue.getEndLineOffset()).isNull();
  }

  @Test
  void shouldHandleCellCreation() {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    var changeEvent = new NotebookDocumentChangeEvent();
    var changeEventCells = new NotebookDocumentChangeEventCells();
    var structureChange = new NotebookDocumentChangeEventCellStructure();
    var notebookCellArrayChange = mock(NotebookCellArrayChange.class);
    when(notebookCellArrayChange.getStart()).thenReturn(1);

    var newCell = new TextDocumentItem();
    newCell.setUri(tmpUri + "#cell4");
    newCell.setText("cell4 line1\n\n");

    structureChange.setDidOpen(List.of(newCell));
    structureChange.setDidClose(Collections.emptyList());
    structureChange.setArray(notebookCellArrayChange);
    changeEventCells.setStructure(structureChange);
    changeEvent.setCells(changeEventCells);

    underTest.didChange(2, changeEvent);

    assertThat(underTest.getNotebookVersion()).isEqualTo(2);
    assertThat(underTest.getCells()).hasSize(4);
    assertThat(underTest.getContent()).isEqualTo("" +
      "cell1 line1\n" +
      "cell1 line2\n" +
      "\n" +
      "cell4 line1\n\n" +
      "\n" +
      "cell2 line1\n" +
      "cell2 line2\n" +
      "\n" +
      "cell3 line1\n" +
      "cell3 line2\n");
    assertThat(underTest.getCellUri(4)).contains(URI.create(tmpUri + "#cell4"));
    assertThat(underTest.getCellUri(7)).contains(URI.create(tmpUri + "#cell2"));
  }

  @Test
  void shouldHandleCellDeletion() {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    var changeEvent = new NotebookDocumentChangeEvent();
    var changeEventCells = new NotebookDocumentChangeEventCells();
    var structureChange = new NotebookDocumentChangeEventCellStructure();
    var documentIdentifier = new TextDocumentIdentifier(tmpUri + "#cell2");
    var notebookCellArrayChange = mock(NotebookCellArrayChange.class);
    when(notebookCellArrayChange.getStart()).thenReturn(1);

    structureChange.setDidOpen(Collections.emptyList());
    structureChange.setDidClose(List.of(documentIdentifier));
    structureChange.setArray(notebookCellArrayChange);
    changeEventCells.setStructure(structureChange);
    changeEvent.setCells(changeEventCells);

    underTest.didChange(2, changeEvent);

    assertThat(underTest.getNotebookVersion()).isEqualTo(2);
    assertThat(underTest.getCells()).hasSize(2);
    assertThat(underTest.getContent()).isEqualTo("" +
      "cell1 line1\n" +
      "cell1 line2\n" +
      "\n" +
      "cell3 line1\n" +
      "cell3 line2\n");
    assertThat(underTest.getCellUri(2)).contains(URI.create(tmpUri + "#cell1"));
    assertThat(underTest.getCellUri(5)).contains(URI.create(tmpUri + "#cell3"));
  }

  @Test
  void shouldNotFailWhenRemovingNonexistentCell() {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    var changeEvent = new NotebookDocumentChangeEvent();
    var changeEventCells = new NotebookDocumentChangeEventCells();
    var structureChange = new NotebookDocumentChangeEventCellStructure();
    var documentIdentifier = new TextDocumentIdentifier(tmpUri + "#NA");
    var notebookCellArrayChange = mock(NotebookCellArrayChange.class);
    when(notebookCellArrayChange.getStart()).thenReturn(1);

    structureChange.setDidOpen(Collections.emptyList());
    structureChange.setDidClose(List.of(documentIdentifier));
    structureChange.setArray(notebookCellArrayChange);
    changeEventCells.setStructure(structureChange);
    changeEvent.setCells(changeEventCells);

    underTest.didChange(2, changeEvent);

    assertThat(underTest.getNotebookVersion()).isEqualTo(2);
    assertThat(underTest.getCells()).hasSize(3);
    assertThat(underTest.getContent()).isEqualTo("" +
      "cell1 line1\n" +
      "cell1 line2\n" +
      "\n" +
      "cell2 line1\n" +
      "cell2 line2\n" +
      "\n" +
      "cell3 line1\n" +
      "cell3 line2\n");
    assertThat(underTest.getCellUri(2)).contains(URI.create(tmpUri + "#cell1"));
    assertThat(underTest.getCellUri(5)).contains(URI.create(tmpUri + "#cell2"));
  }

  VersionedOpenNotebook createTestNotebookWithThreeCells(URI tmpUri) {
    var cell1 = new TextDocumentItem();
    cell1.setUri(tmpUri.toString() + "#cell1");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var cell2 = new TextDocumentItem();
    cell2.setUri(tmpUri.toString() + "#cell2");
    cell2.setText("cell2 line1\ncell2 line2\n");

    var cell3 = new TextDocumentItem();
    cell3.setUri(tmpUri.toString() + "#cell3");
    cell3.setText("cell3 line1\ncell3 line2\n");

    return VersionedOpenNotebook.create(tmpUri, 1, List.of(cell1, cell2, cell3), mock(NotebookDiagnosticPublisher.class));
  }

}
