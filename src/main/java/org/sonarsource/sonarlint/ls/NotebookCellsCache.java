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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.TextDocumentItem;
import org.sonarsource.sonarlint.core.commons.TextRange;

public class NotebookCellsCache {

  Map<URI, Map<Integer, TextDocumentItem>> linesCache = new HashMap<>();
  Map<URI, String> fullContentCache = new HashMap<>();
  Map<URI, Map<Integer, Integer>> globalLineToCellLineForFile = new HashMap<>();


  public void populate(URI file, List<TextDocumentItem> cells) {
    Map<Integer, TextDocumentItem> index = new HashMap<>();
    globalLineToCellLineForFile.putIfAbsent(file, new HashMap<>());
    // TODO is it 1 or 0?
    var firstLineOfCell = 1;
    var mergedContent = new StringBuilder();
    for (TextDocumentItem cell : cells) {
      var cellContent = cell.getText();
      int cellSize = cellContent.split(System.lineSeparator()).length;
      for (int globalLineNum = firstLineOfCell, cellLineNum = 1; globalLineNum < firstLineOfCell + cellSize; globalLineNum++, cellLineNum++) {
        index.put(globalLineNum, cell);
        globalLineToCellLineForFile.get(file).put(globalLineNum, cellLineNum);
      }
      firstLineOfCell = firstLineOfCell + cellSize;
      mergedContent.append(cellContent).append(System.lineSeparator());
    }
    fullContentCache.put(file, mergedContent.toString());
    linesCache.put(file, index);
  }

  public TextRange getCellTextRange(URI file, TextRange textRange) {
    int cellStartLine = globalLineToCellLineForFile.get(file).get(textRange.getStartLine());
    int cellEndLine = globalLineToCellLineForFile.get(file).get(textRange.getEndLine());
    return new TextRange(cellStartLine, textRange.getStartLineOffset(), cellEndLine, textRange.getEndLineOffset());
  }

  public URI getCellUri(URI file, TextRange textRange) {
    return URI.create(linesCache.get(file).get(textRange.getStartLine()).getUri());
  }

  public String getFullContent(URI file) {
    return fullContentCache.get(file);
  }
}
