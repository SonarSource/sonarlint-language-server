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
import org.sonarsource.sonarlint.ls.AnalysisClientInputFile;
import org.sonarsource.sonarlint.ls.util.FileUtils;
import org.w3c.dom.Text;

public class OpenNotebook {

  private final URI uri;
  private final List<TextDocumentItem> cells;
  private final Map<Integer, TextDocumentItem> lineToCell;

  private OpenNotebook(URI uri, List<TextDocumentItem> cells) {
    this.uri = uri;
    this.cells = Collections.unmodifiableList(cells);
    lineToCell = indexCellsByLineNumber();
  }

  private Map<Integer, TextDocumentItem> indexCellsByLineNumber() {
    var lineToCellMutable = new HashMap<Integer, TextDocumentItem>();
    var lineCount = 0;
    for (var cell: cells) {
      var cellLines = cell.getText().split("\n");
      for (var cellLineCount = 0; cellLineCount < cellLines.length; cellLineCount ++) {
        lineToCellMutable.put(lineCount, cell);
        lineCount ++;
      }
    }
    return Collections.unmodifiableMap(lineToCellMutable);
  }

  public static OpenNotebook create(URI baseUri, List<TextDocumentItem> cells) {
    return new OpenNotebook(baseUri, cells);
  }

  public URI getUri() {
    return uri;
  }

  public ClientInputFile asInputFile(Path baseDir) {
    return new AnalysisClientInputFile(uri, FileUtils.getFileRelativePath(baseDir, uri), getContent(), false, "ipynb");
  }

  String getContent() {
    return cells.stream().map(TextDocumentItem::getText)
      .collect(Collectors.joining());
  }

  public Optional<URI> getCellUri(int lineNumber) {
    return Optional.ofNullable(lineToCell.get(lineNumber))
      .map(TextDocumentItem::getUri)
      .map(URI::create);
  }
}
