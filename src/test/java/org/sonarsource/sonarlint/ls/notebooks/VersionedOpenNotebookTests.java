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
import java.util.List;
import org.eclipse.lsp4j.NotebookCellArrayChange;
import org.eclipse.lsp4j.NotebookDocumentChangeEvent;
import org.eclipse.lsp4j.NotebookDocumentChangeEventCellStructure;
import org.eclipse.lsp4j.NotebookDocumentChangeEventCellTextContent;
import org.eclipse.lsp4j.NotebookDocumentChangeEventCells;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.analysis.api.TextEdit;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.TextRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.notebooks.VersionedOpenNotebook.SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER;

public class VersionedOpenNotebookTests {

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
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
      "cell2 line1\n" +
      "cell2 line2\n" +
      "\n" +
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
      "cell3 line1\n" +
      "cell3 line2\n");
    assertThat(versionedOpenFile.getLanguageId()).isEqualTo(Language.IPYTHON.getLanguageKey());
  }

  @Test
  void shouldNotAddDelimiterIfOnlyOneCell() {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var cell1 = new TextDocumentItem();
    cell1.setUri(tmpUri + "#cell1");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var underTest = VersionedOpenNotebook.create(tmpUri, 1, List.of(cell1), mock(NotebookDiagnosticPublisher.class));

    var versionedOpenFile = underTest.asVersionedOpenFile();

    assertThat(versionedOpenFile.getVersion()).isEqualTo(underTest.getNotebookVersion());
    assertThat(versionedOpenFile.getUri()).isEqualTo(underTest.getUri());
    assertThat(versionedOpenFile.getContent()).isEqualTo("" +
      "cell1 line1\n" +
      "cell1 line2\n");
    assertThat(versionedOpenFile.getLanguageId()).isEqualTo(Language.IPYTHON.getLanguageKey());
  }

  @Test
  void shouldCorrectlyStoreCells() {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    assertThat(underTest.getCellUris()).hasSize(3);
    assertThat(underTest.getCellUris()).contains(tmpUri + "#cell1");
    assertThat(underTest.getCellUris()).contains(tmpUri + "#cell2");
    assertThat(underTest.getCellUris()).contains(tmpUri + "#cell3");
  }

  @Test
  void shouldCorrectlyMapFileLineToCellLine() {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    assertThat(underTest.getCellUri(1)).contains(URI.create(tmpUri + "#cell1"));
    assertThat(underTest.getCellUri(2)).contains(URI.create(tmpUri + "#cell1"));
    assertThat(underTest.getCellUri(3)).contains(URI.create(tmpUri + "#cell1"));
    assertThat(underTest.getCellUri(4)).contains(URI.create(tmpUri + "#cell1"));
    assertThat(underTest.getCellUri(5)).contains(URI.create(tmpUri + "#cell2"));
    assertThat(underTest.getCellUri(6)).contains(URI.create(tmpUri + "#cell2"));
    assertThat(underTest.getCellUri(7)).contains(URI.create(tmpUri + "#cell2"));
    assertThat(underTest.getCellUri(8)).contains(URI.create(tmpUri + "#cell2"));
    assertThat(underTest.getCellUri(9)).contains(URI.create(tmpUri + "#cell3"));
    assertThat(underTest.getCellUri(10)).contains(URI.create(tmpUri + "#cell3"));
    assertThat(underTest.getCellUri(11)).contains(URI.create(tmpUri + "#cell3"));
    assertThat(underTest.getCellUri(12)).isEmpty();
    assertThat(underTest.getCellUri(0)).isEmpty();
    assertThat(underTest.getCellUri(-4)).isEmpty();
  }

  @Test
  void shouldConvertToCellIssue() {
    Issue issue = mock(Issue.class);
    TextRange textRange = new TextRange(5, 0, 6, 5);

    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getMessage()).thenReturn("don't do this");
    when(issue.getRuleKey()).thenReturn("squid:123");
    when(issue.getTextRange()).thenReturn(textRange);
    when(issue.getStartLine()).thenReturn(5);
    when(issue.getStartLineOffset()).thenReturn(0);
    when(issue.getEndLine()).thenReturn(6);
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
  void shouldConvertQuickFixWithSingleFileEdit() {
    var issue = mock(Issue.class);
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var fakeNotebook = createTestNotebookWithThreeCells(tmpUri);
    var quickFixTextRange = new TextRange(9, 0, 9, 2);

    var textEdit = mock(TextEdit.class);
    when(textEdit.newText()).thenReturn("");
    when(textEdit.range()).thenReturn(quickFixTextRange);
    var edit = mock(ClientInputFileEdit.class);
    when(edit.textEdits()).thenReturn(List.of(textEdit));
    var target = mock(ClientInputFile.class);
    when(target.uri()).thenReturn(fakeNotebook.getCellUri(quickFixTextRange.getStartLine()).get());
    when(edit.target()).thenReturn(target);
    var fix = mock(QuickFix.class);
    when(fix.message()).thenReturn("Fix the issue!");
    when(fix.inputFileEdits()).thenReturn(List.of(edit));
    when(issue.quickFixes()).thenReturn(List.of(fix));

    var delegatingCellIssue = fakeNotebook.toCellIssue(issue);

    assertThat(delegatingCellIssue.quickFixes()).hasSize(1);
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .message()).isEqualTo("Fix the issue!");
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(0).textEdits().get(0).range().getStartLine()).isEqualTo(1);
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(0).textEdits().get(0).range().getStartLineOffset()).isZero();
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(0).textEdits().get(0).range().getEndLine()).isEqualTo(1);
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(0).textEdits().get(0).range().getEndLineOffset()).isEqualTo(2);
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(0).target().uri()).hasToString(tmpUri + "#cell3");
  }

  @Test
  void shouldConvertQuickFixWithMultipleFileEdits() {
    var issue = mock(Issue.class);
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var fakeNotebook = createTestNotebookWithThreeCells(tmpUri);
    var quickFixTextRange1 = new TextRange(9, 0, 9, 2);
    var quickFixTextRange2 = new TextRange(5, 0, 6, 2);

    var textEdit1 = mock(TextEdit.class);
    when(textEdit1.newText()).thenReturn("");
    when(textEdit1.range()).thenReturn(quickFixTextRange1);
    var textEdit2 = mock(TextEdit.class);
    when(textEdit2.newText()).thenReturn("");
    when(textEdit2.range()).thenReturn(quickFixTextRange2);
    var edit1 = mock(ClientInputFileEdit.class);
    when(edit1.textEdits()).thenReturn(List.of(textEdit1));
    var edit2 = mock(ClientInputFileEdit.class);
    when(edit2.textEdits()).thenReturn(List.of(textEdit2));
    var target1 = mock(ClientInputFile.class);
    when(target1.uri()).thenReturn(fakeNotebook.getCellUri(quickFixTextRange1.getStartLine()).get());
    when(edit1.target()).thenReturn(target1);
    var target2 = mock(ClientInputFile.class);
    when(target2.uri()).thenReturn(fakeNotebook.getCellUri(quickFixTextRange2.getStartLine()).get());
    when(edit2.target()).thenReturn(target2);
    var fix = mock(QuickFix.class);
    when(fix.message()).thenReturn("Fix the issue!");
    when(fix.inputFileEdits()).thenReturn(List.of(edit1, edit2));
    when(issue.quickFixes()).thenReturn(List.of(fix));

    var delegatingCellIssue = fakeNotebook.toCellIssue(issue);

    assertThat(delegatingCellIssue.quickFixes()).hasSize(1);
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .message()).isEqualTo("Fix the issue!");

    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(0).textEdits().get(0).range().getStartLine()).isEqualTo(1);
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(0).textEdits().get(0).range().getStartLineOffset()).isZero();
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(0).textEdits().get(0).range().getEndLine()).isEqualTo(1);
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(0).textEdits().get(0).range().getEndLineOffset()).isEqualTo(2);
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(0).target().uri()).hasToString(tmpUri + "#cell3");

    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(1).textEdits().get(0).range().getStartLine()).isEqualTo(1);
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(1).textEdits().get(0).range().getStartLineOffset()).isZero();
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(1).textEdits().get(0).range().getEndLine()).isEqualTo(2);
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(1).textEdits().get(0).range().getEndLineOffset()).isEqualTo(2);
    assertThat(delegatingCellIssue.quickFixes().get(0)
      .inputFileEdits().get(1).target().uri()).hasToString(tmpUri + "#cell2");
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
    assertThat(underTest.getCellUris()).hasSize(4);
    assertThat(underTest.getContent()).isEqualTo("" +
      "cell1 line1\n" +
      "cell1 line2\n" +
      "\n" +
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
      "cell4 line1\n\n" +
      "\n" +
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
      "cell2 line1\n" +
      "cell2 line2\n" +
      "\n" +
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
      "cell3 line1\n" +
      "cell3 line2\n");
    assertThat(underTest.getCellUri(4)).contains(URI.create(tmpUri + "#cell1"));
    assertThat(underTest.getCellUri(5)).contains(URI.create(tmpUri + "#cell4"));
    assertThat(underTest.getCellUri(7)).contains(URI.create(tmpUri + "#cell4"));
    assertThat(underTest.getCellUri(9)).contains(URI.create(tmpUri + "#cell2"));
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
    assertThat(underTest.getCellUris()).hasSize(2);
    assertThat(underTest.getContent()).isEqualTo("" +
      "cell1 line1\n" +
      "cell1 line2\n" +
      "\n" +
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
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
    assertThat(underTest.getCellUris()).hasSize(3);
    assertThat(underTest.getContent()).isEqualTo("" +
      "cell1 line1\n" +
      "cell1 line2\n" +
      "\n" +
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
      "cell2 line1\n" +
      "cell2 line2\n" +
      "\n" +
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
      "cell3 line1\n" +
      "cell3 line2\n");
    assertThat(underTest.getCellUri(2)).contains(URI.create(tmpUri + "#cell1"));
    assertThat(underTest.getCellUri(5)).contains(URI.create(tmpUri + "#cell2"));
  }

  @Test
  void shouldHandleContentChange() {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    var changeEvent = new NotebookDocumentChangeEvent();
    var changeEventCells = new NotebookDocumentChangeEventCells();
    var textContents = new NotebookDocumentChangeEventCellTextContent();
    var documentIdentifier = new VersionedTextDocumentIdentifier();
    var change = new TextDocumentContentChangeEvent();

    documentIdentifier.setVersion(2);
    documentIdentifier.setUri(tmpUri + "#cell2");
    textContents.setDocument(documentIdentifier);
    change.setRange(new Range(new Position(1, 6), new Position(1, 10)));
    change.setText("hola");

    textContents.setChanges(List.of(change));
    changeEventCells.setTextContent(List.of(textContents));
    changeEvent.setCells(changeEventCells);

    underTest.didChange(2, changeEvent);

    assertThat(underTest.getNotebookVersion()).isEqualTo(2);
    assertThat(underTest.getContent()).isEqualTo("" +
      "cell1 line1\n" +
      "cell1 line2\n" +
      "\n" +
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
      "cell2 line1\n" +
      "cell2 hola2\n" +
      "\n" +
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
      "cell3 line1\n" +
      "cell3 line2\n");
  }
  @Test
  void shouldHandleMultiContentChange() {
    var tmpUri = URI.create("file:///some/notebook.ipynb");
    var underTest = createTestNotebookWithThreeCells(tmpUri);

    var changeEvent = new NotebookDocumentChangeEvent();
    var changeEventCells = new NotebookDocumentChangeEventCells();
    var textContents = new NotebookDocumentChangeEventCellTextContent();
    var documentIdentifier = new VersionedTextDocumentIdentifier();
    var change1 = new TextDocumentContentChangeEvent();
    var change2 = new TextDocumentContentChangeEvent();

    documentIdentifier.setVersion(2);
    documentIdentifier.setUri(tmpUri + "#cell2");
    textContents.setDocument(documentIdentifier);
    change1.setRange(new Range(new Position(1, 6), new Position(1, 10)));
    change1.setText("hola");

    change2.setRange(new Range(new Position(0, 0), new Position(0, 5)));
    change2.setText("bye");

    textContents.setChanges(List.of(change1, change2));
    changeEventCells.setTextContent(List.of(textContents));
    changeEvent.setCells(changeEventCells);

    underTest.didChange(2, changeEvent);

    assertThat(underTest.getNotebookVersion()).isEqualTo(2);
    assertThat(underTest.getContent()).isEqualTo("" +
      "cell1 line1\n" +
      "cell1 line2\n" +
      "\n" +
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
      "bye line1\n" +
      "cell2 hola2\n" +
      "\n" +
      SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER +
      "cell3 line1\n" +
      "cell3 line2\n");
  }

  public static VersionedOpenNotebook createTestNotebookWithThreeCells(URI tmpUri) {
    var cell1 = new TextDocumentItem();
    cell1.setUri(tmpUri + "#cell1");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var cell2 = new TextDocumentItem();
    cell2.setUri(tmpUri + "#cell2");
    cell2.setText("cell2 line1\ncell2 line2\n");

    var cell3 = new TextDocumentItem();
    cell3.setUri(tmpUri + "#cell3");
    cell3.setText("cell3 line1\ncell3 line2\n");

    return VersionedOpenNotebook.create(tmpUri, 1, List.of(cell1, cell2, cell3), mock(NotebookDiagnosticPublisher.class));
  }

}
