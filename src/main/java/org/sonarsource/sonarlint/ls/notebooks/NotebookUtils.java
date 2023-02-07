package org.sonarsource.sonarlint.ls.notebooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;

public class NotebookUtils {
  public static String applyChangeToCellContent(TextDocumentItem originalCell, TextDocumentContentChangeEvent textChange) {
    var cellLines = Arrays.asList(originalCell.getText().split("\n"));
    var modifiedLines = new ArrayList<String>();

    var rangeStartLine = textChange.getRange().getStart().getLine();
    var rangeStartLineOffset = textChange.getRange().getStart().getCharacter();
    var rangeEndLine = textChange.getRange().getEnd().getLine();
    var rangeEndLineOffset = textChange.getRange().getEnd().getCharacter();

    var currentLine = 0;
    while (currentLine < rangeStartLine) {
      modifiedLines.add(cellLines.get(currentLine));
      currentLine++;
    }
    var replacementRange =
      cellLines.get(currentLine).substring(0, rangeStartLineOffset) +
      textChange.getText() +
      cellLines.get(rangeEndLine).substring(rangeEndLineOffset);
    if (!replacementRange.isEmpty()) {
      modifiedLines.add(replacementRange);
    }
    currentLine = rangeEndLine + 1;
    while (currentLine < cellLines.size()) {
      modifiedLines.add(cellLines.get(currentLine));
      currentLine++;
    }
    return String.join("\n", modifiedLines);
  }
}
