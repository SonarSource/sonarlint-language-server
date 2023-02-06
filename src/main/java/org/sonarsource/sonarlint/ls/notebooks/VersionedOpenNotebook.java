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
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.TextDocumentItem;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.ls.AnalysisClientInputFile;
import org.sonarsource.sonarlint.ls.connected.DelegatingIssue;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.util.FileUtils;

public class VersionedOpenNotebook {

  private final URI uri;
  private final int version;
  private final List<TextDocumentItem> cells;
  private final Map<Integer, TextDocumentItem> fileLineToCell = new HashMap<>();
  private final Map<Integer, Integer> virtualFileLineToCellLine = new HashMap<>();

  private VersionedOpenNotebook(URI uri, int version, List<TextDocumentItem> cells) {
    this.uri = uri;
    this.version = version;
    this.cells = Collections.unmodifiableList(cells);
    indexCellsByLineNumber();
  }

  private void indexCellsByLineNumber() {
    var lineCount = 1;
    for (var cell: cells) {
      var cellLines = cell.getText().split("\n");
      for (var cellLineCount = 1; cellLineCount <= cellLines.length; cellLineCount ++) {
        fileLineToCell.put(lineCount, cell);
        virtualFileLineToCellLine.put(lineCount, cellLineCount);
        lineCount ++;
      }
    }
  }

  public static VersionedOpenNotebook create(URI baseUri, int version, List<TextDocumentItem> cells) {
    return new VersionedOpenNotebook(baseUri, version, cells);
  }

  public URI getUri() {
    return uri;
  }

  public ClientInputFile asInputFile(Path baseDir) {
    return new AnalysisClientInputFile(uri, FileUtils.getFileRelativePath(baseDir, uri), getContent(), false, "ipynb");
  }

  public VersionedOpenFile asVersionedOpenFile() {
    // TODO change to ipynb language
    return new VersionedOpenFile(uri, "python", this.version, getContent(), true);
  }

  String getContent() {
    return cells.stream().map(TextDocumentItem::getText)
      .collect(Collectors.joining("\n"));
  }

  public int getVersion() {
    return this.version;
  }

  public Optional<URI> getCellUri(int lineNumber) {
    return Optional.ofNullable(fileLineToCell.get(lineNumber))
      .map(TextDocumentItem::getUri)
      .map(URI::create);
  }

  public DelegatingCellIssue toCellIssue(Issue issue) {
    var fileStartLine = issue.getStartLine();
    var fileStartLineOffset = issue.getStartLineOffset();
    var fileEndLine = issue.getEndLine();
    var fileEndLineOffset = issue.getEndLineOffset();

    var cellStartLine = virtualFileLineToCellLine.get(fileStartLine);
    var cellEndLine = virtualFileLineToCellLine.get(fileEndLine);

    var cellTextRange = new TextRange(cellStartLine, fileStartLineOffset, cellEndLine, fileEndLineOffset);

    return new DelegatingCellIssue(issue, cellTextRange);
  }
}
