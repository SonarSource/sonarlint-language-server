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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.NotebookDocumentChangeEvent;
import org.eclipse.lsp4j.NotebookDocumentChangeEventCellTextContent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.analysis.api.TextEdit;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.folders.InFolderClientInputFile;

import static org.sonarsource.sonarlint.ls.notebooks.NotebookUtils.applyChangeToCellContent;
import static org.sonarsource.sonarlint.ls.notebooks.NotebookUtils.fileTextRangeToCellTextRange;

public class VersionedOpenNotebook {

  static final String SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER = "#SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER\n";

  private final URI uri;
  private Integer notebookVersion;
  private Integer indexedNotebookVersion;
  private final LinkedHashMap<String, TextDocumentItem> cells = new LinkedHashMap<>();
  private final List<TextDocumentItem> orderedCells = new ArrayList<>();
  private final Map<Integer, TextDocumentItem> fileLineToCell = new HashMap<>();
  private final Map<Integer, Integer> virtualFileLineToCellLine = new HashMap<>();
  private final NotebookDiagnosticPublisher notebookDiagnosticPublisher;

  private VersionedOpenNotebook(URI uri, int version, List<TextDocumentItem> cells, NotebookDiagnosticPublisher notebookDiagnosticPublisher) {
    this.uri = uri;
    this.notebookVersion = version;
    cells.forEach(cell -> {
      this.cells.put(cell.getUri(), cell);
      this.orderedCells.add(cell);
    });
    this.notebookDiagnosticPublisher = notebookDiagnosticPublisher;
  }

  private void indexCellsByLineNumber() {
    if (notebookVersion.equals(indexedNotebookVersion)) {
      return;
    }
    var lineCount = 1;
    var cellCount = 1;
    for (var cell : orderedCells) {
      var cellLines = cell.getText().split("\n", -1);
      for (var cellLineCount = 1; cellLineCount <= cellLines.length; cellLineCount++) {
        fileLineToCell.put(lineCount, cell);
        virtualFileLineToCellLine.put(lineCount, cellLineCount);
        lineCount++;
        if (cellLineCount == cellLines.length && cellCount < orderedCells.size()) {
          fileLineToCell.put(lineCount, cell);
          virtualFileLineToCellLine.put(lineCount, cellLineCount);
          lineCount++;
        }
      }
      cellCount++;
    }
    indexedNotebookVersion = notebookVersion;
  }

  public static VersionedOpenNotebook create(URI baseUri, int version, List<TextDocumentItem> cells, NotebookDiagnosticPublisher notebookDiagnosticPublisher) {
    return new VersionedOpenNotebook(baseUri, version, cells, notebookDiagnosticPublisher);
  }

  public URI getUri() {
    return uri;
  }

  public VersionedOpenFile asVersionedOpenFile() {
    return new VersionedOpenFile(uri, Language.IPYTHON.getLanguageKey(), this.notebookVersion, getContent());
  }

  String getContent() {
    return orderedCells.stream().map(TextDocumentItem::getText)
      .collect(Collectors.joining("\n" + SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER));
  }

  public int getNotebookVersion() {
    return this.notebookVersion;
  }

  public Set<String> getCellUris() {
    return cells.keySet();
  }

  public Optional<URI> getCellUri(int lineNumber) {
    indexCellsByLineNumber();
    return Optional.ofNullable(fileLineToCell.get(lineNumber))
      .map(TextDocumentItem::getUri)
      .map(URI::create);
  }

  public DelegatingCellIssue toCellIssue(Issue issue) {
    indexCellsByLineNumber();
    var issueTextRange = issue.getTextRange();
    var originalQuickFixes = issue.quickFixes();
    var convertedQuickFixes = new ArrayList<QuickFix>();
    TextRange cellTextRange = null;
    if (issueTextRange != null) {
      cellTextRange = fileTextRangeToCellTextRange(issueTextRange.getStartLine(), issueTextRange.getStartLineOffset(),
        issueTextRange.getEndLine(), issueTextRange.getEndLineOffset(), virtualFileLineToCellLine);
    }
    if (originalQuickFixes != null && !originalQuickFixes.isEmpty()) {
      AtomicReference<URI> textEditCellUri = new AtomicReference<>();
      for (QuickFix quickFix : originalQuickFixes) {
        var newFileEdits = quickFix.inputFileEdits().stream().map(fileEdit -> {
          var newTextEdits = fileEdit.textEdits().stream().map(textEdit -> {
            textEditCellUri.set(URI.create(fileLineToCell.get(textEdit.range().getStartLine()).getUri()));
            var newTextRange = fileTextRangeToCellTextRange(textEdit.range().getStartLine(), textEdit.range().getStartLineOffset(),
              textEdit.range().getEndLine(), textEdit.range().getEndLineOffset(), virtualFileLineToCellLine);
            return new TextEdit(newTextRange, textEdit.newText());
          }).toList();
          var clientInputFile = new InFolderClientInputFile(textEditCellUri.get(), "", false);
          return new ClientInputFileEdit(clientInputFile, newTextEdits);
        }).toList();
        var convertedQuickFix = new QuickFix(newFileEdits, quickFix.message());
        convertedQuickFixes.add(convertedQuickFix);
      }
    }
    return new DelegatingCellIssue(issue, cellTextRange, convertedQuickFixes);
  }

  public void didChange(int version, NotebookDocumentChangeEvent changeEvent) {
    this.notebookVersion = version;
    if (changeEvent.getCells() != null && changeEvent.getCells().getStructure() != null && !changeEvent.getCells().getStructure().getDidClose().isEmpty()) {
      handleCellDeletion(changeEvent.getCells().getStructure().getDidClose());
    }
    if (changeEvent.getCells() != null && changeEvent.getCells().getStructure() != null && !changeEvent.getCells().getStructure().getDidOpen().isEmpty()) {
      handleCellCreation(changeEvent);
    }
    if (changeEvent.getCells() != null && changeEvent.getCells().getTextContent() != null && !changeEvent.getCells().getTextContent().isEmpty()) {
      handleContentChange(changeEvent.getCells().getTextContent());
    }
  }

  private void handleCellDeletion(List<TextDocumentIdentifier> removedCellIdentifiers) {
    removedCellIdentifiers.forEach(removedCell -> {
      var removedItem = cells.remove(removedCell.getUri());
      if (removedItem != null) {
        orderedCells.remove(removedItem);
        notebookDiagnosticPublisher.removeCellDiagnostics(URI.create(removedItem.getUri()));
      }
    });
  }

  private void handleCellCreation(NotebookDocumentChangeEvent changeEvent) {
    var insertionStart = new AtomicInteger(changeEvent.getCells().getStructure().getArray().getStart());
    changeEvent.getCells().getStructure().getDidOpen().forEach(newCell -> {
      cells.put(newCell.getUri(), newCell);
      orderedCells.add(insertionStart.getAndIncrement(), newCell);
    });
  }

  private void handleContentChange(List<NotebookDocumentChangeEventCellTextContent> textContents) {
    textContents.forEach(textContent -> {
      var changedCellUri = textContent.getDocument().getUri();
      var cell = cells.get(changedCellUri);
      cell.setVersion(textContent.getDocument().getVersion());

      cell.setText(applyChangeToCellContent(cell, textContent.getChanges()));
    });
  }
}
