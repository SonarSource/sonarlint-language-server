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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
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

    for (var i = 0; i < sortedChanges.size(); i++) {
      var textChange = sortedChanges.get(i);
      var rangeStartLine = textChange.getRange().getStart().getLine();
      var rangeEndLine = textChange.getRange().getEnd().getLine();

      currentLine = addUnchangedLinesAfterLastChanges(cellLines, modifiedLines, currentLine, rangeStartLine);
      var fixedText = getFixedText(cellLines, currentLine, textChange);

      if (!fixedText.isEmpty()) {
        if (i - 1 >= 0 && sortedChanges.get(i - 1).getRange().getStart().getLine() == rangeStartLine) {
          // this line has already been modified before, there is item inside modifiedLines
          modifiedLines.set(currentLine, fixedText);
        } else {
          modifiedLines.add(fixedText);
        }
      }
      currentLine = rangeEndLine + 1;
      if (thereIsAnotherChangeOnSameLine(sortedChanges, i, rangeStartLine)) {
        currentLine = currentLine - 1;
        cellLines.set(currentLine, modifiedLines.get(currentLine));
        // The line was modified, the original range that we got from the IDE needs to be adjusted
        sortedChanges.get(i + 1).setRange(adjustRange(sortedChanges.get(i + 1).getRange(), textChange));
      }
    }

    addUnchangedLinesBeforeFirstChange(currentLine, modifiedLines, cellLines);

    return String.join("\n", modifiedLines);
  }

  private static boolean thereIsAnotherChangeOnSameLine(ArrayList<TextDocumentContentChangeEvent> sortedChanges, int i, int rangeStartLine) {
    return i + 1 < sortedChanges.size() && sortedChanges.get(i + 1).getRange().getStart().getLine() == rangeStartLine;
  }

  static void addUnchangedLinesBeforeFirstChange(int currentLine, ArrayList<String> modifiedLines, List<String> cellLines) {
    while (currentLine < cellLines.size()) {
      modifiedLines.add(cellLines.get(currentLine));
      currentLine++;
    }
  }

  static int addUnchangedLinesAfterLastChanges(List<String> cellLines, ArrayList<String> modifiedLines, int currentLine, int rangeStartLine) {
    while (currentLine < rangeStartLine) {
      modifiedLines.add(cellLines.get(currentLine));
      currentLine++;
    }
    return currentLine;
  }

  static String getFixedText(List<String> cellLines, int currentLine, TextDocumentContentChangeEvent textChange) {
    var rangeStartLineOffset = textChange.getRange().getStart().getCharacter();
    var rangeEndLine = textChange.getRange().getEnd().getLine();
    var rangeEndLineOffset = textChange.getRange().getEnd().getCharacter();
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
    return replacementRange;
  }

  private static Range adjustRange(Range originalRange, TextDocumentContentChangeEvent previousTextChange) {
    var end = originalRange.getEnd();
    var previousChangeRangeLength = previousTextChange.getRange().getEnd().getCharacter() - previousTextChange.getRange().getStart().getCharacter();
    var previousChangeTextLength = previousTextChange.getText().length();
    var start = new Position(originalRange.getStart().getLine(), originalRange.getStart().getCharacter() - (previousChangeRangeLength - previousChangeTextLength));
    return new Range(start, end);
  }

//  public static String applyChangeToCellContent(TextDocumentItem originalCell, List<TextDocumentContentChangeEvent> textChanges) {
//    var cellLines = Arrays.asList(originalCell.getText().split("\n", -1));
//    var modifiedLines = new ArrayList<String>();
//
//    var sortedChanges = new ArrayList<>(textChanges);
//    sortedChanges.sort(
//      Comparator.<TextDocumentContentChangeEvent>comparingInt(change -> change.getRange().getStart().getLine())
//        .thenComparingInt(change -> change.getRange().getStart().getCharacter())
//    );
//
//    var currentLine = cellLines.size() - 1;
//
//    for(var i = sortedChanges.size() - 1; i >= 0; i--) {
//      var textChange = sortedChanges.get(i);
//
//      var rangeStartLine = textChange.getRange().getStart().getLine();
//      var rangeStartLineOffset = textChange.getRange().getStart().getCharacter();
//      var rangeEndLine = textChange.getRange().getEnd().getLine();
//      var rangeEndLineOffset = textChange.getRange().getEnd().getCharacter();
//
//      while (currentLine > rangeEndLine) {
//        modifiedLines.add(cellLines.get(currentLine));
//        currentLine--;
//      }
//      String replacementRange;
//
//      if (currentLine < cellLines.size()) {
//        replacementRange =
//          cellLines.get(currentLine).substring(0, rangeStartLineOffset) +
//            textChange.getText() +
//            cellLines.get(rangeEndLine).substring(rangeEndLineOffset);
//      } else {
//        // Newline added
//        replacementRange = textChange.getText();
//      }
//
//      if (!replacementRange.isEmpty()) {
//        modifiedLines.add(replacementRange);
//      }
//      if (i - 1 < 0 || (i - 1 >= 0 && sortedChanges.get(i-1).getRange().getStart().getLine() != currentLine)) {
//        currentLine = rangeStartLine - 1;
//      }
//    }
//
//    while (currentLine >= 0) {
//      modifiedLines.add(cellLines.get(currentLine));
//      currentLine--;
//    }
//
//    Collections.reverse(modifiedLines);
//
//    return String.join("\n", modifiedLines);
//  }

  public static TextRange fileTextRangeToCellTextRange(int fileStartLine, int fileStartLineOffset,
    int fileEndLine, int fileEndLineOffset, Map<Integer, Integer> virtualFileLineToCellLine) {
    var cellStartLine = virtualFileLineToCellLine.get(fileStartLine);
    var cellEndLine = virtualFileLineToCellLine.get(fileEndLine);

    return new TextRange(cellStartLine, fileStartLineOffset, cellEndLine, fileEndLineOffset);
  }
}
