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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.sonarsource.sonarlint.core.commons.TextRange;

public class NotebookUtils {

  private NotebookUtils() {
    // Static utility method only
  }

  public static String applyChangeToCellContent(TextDocumentItem originalCell, List<TextDocumentContentChangeEvent> textChanges) {
    var cellLines = Arrays.asList(originalCell.getText().split("\n", -1));
    var modifiedLines = new ArrayList<String>();

    var sortedChanges = new ArrayList<>(textChanges);
    sortedChanges.sort(
      Comparator.<TextDocumentContentChangeEvent>comparingInt(change -> change.getRange().getStart().getLine())
        .thenComparingInt(change -> change.getRange().getStart().getCharacter())
    );

    var currentLine = 0;

    for (var textChange: sortedChanges) {
      var rangeStartLine = textChange.getRange().getStart().getLine();
      var rangeStartLineOffset = textChange.getRange().getStart().getCharacter();
      var rangeEndLine = textChange.getRange().getEnd().getLine();
      var rangeEndLineOffset = textChange.getRange().getEnd().getCharacter();

      while (currentLine < rangeStartLine) {
        modifiedLines.add(cellLines.get(currentLine));
        currentLine++;
      }
      String replacementRange;
      if (currentLine < cellLines.size()) {
        replacementRange =
          cellLines.get(currentLine).substring(0, rangeStartLineOffset) +
            textChange.getText() +
            cellLines.get(rangeEndLine).substring(rangeEndLineOffset);
      } else {
        // Newline added
        replacementRange = textChange.getText();
      }

      if (!replacementRange.isEmpty()) {
        modifiedLines.add(replacementRange);
      }
      currentLine = rangeEndLine + 1;
    }

    while (currentLine < cellLines.size()) {
      modifiedLines.add(cellLines.get(currentLine));
      currentLine++;
    }

    return String.join("\n", modifiedLines);
  }

  public static TextRange fileTextRangeToCellTextRange(int fileStartLine, int fileStartLineOffset,
    int fileEndLine, int fileEndLineOffset, Map<Integer, Integer> virtualFileLineToCellLine) {
    var cellStartLine = virtualFileLineToCellLine.get(fileStartLine);
    var cellEndLine = virtualFileLineToCellLine.get(fileEndLine);

    return new TextRange(cellStartLine, fileStartLineOffset, cellEndLine, fileEndLineOffset);
  }
}
