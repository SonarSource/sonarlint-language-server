package org.sonarsource.sonarlint.ls.notebooks;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotebookUtilsTest {
  TextDocumentItem originalCell;
  @BeforeEach
  void setup() {
    originalCell = new TextDocumentItem();
    originalCell.setText("print(\"hello\")\n" +
      "\n" +
      "a = True\n" +
      "b = False");
  }
  @Test
  void shouldApplySingleLineChangeOnTheFirstLine() {
    TextDocumentContentChangeEvent textChange = new TextDocumentContentChangeEvent();
    textChange.setText("a");
    textChange.setRange(new Range(new Position(0, 1), new Position(0, 4)));

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, textChange);
    var expectedNewContent = "pat(\"hello\")\n" +
      "\n" +
      "a = True\n" +
      "b = False";

    assertEquals(changedContent, expectedNewContent);
  }

  @Test
  void shouldApplySingleLineChangeOnTheSecondLine() {
    TextDocumentContentChangeEvent textChange = new TextDocumentContentChangeEvent();
    textChange.setText("c = 42");
    textChange.setRange(new Range(new Position(1, 0), new Position(1, 0)));

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, textChange);
    var expectedNewContent = "print(\"hello\")\n" +
      "c = 42\n" +
      "a = True\n" +
      "b = False";

    assertEquals(changedContent, expectedNewContent);
  }

  @Test
  void shouldApplySingleLineChangeAtTheEndOfFile() {
    TextDocumentContentChangeEvent textChange = new TextDocumentContentChangeEvent();
    textChange.setText("oo");
    textChange.setRange(new Range(new Position(3, 1), new Position(3, 1)));

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, textChange);
    var expectedNewContent = "print(\"hello\")\n" +
      "\n" +
      "a = True\n" +
      "boo = False";

    assertEquals(changedContent, expectedNewContent);
  }

  @Test
  void shouldApplyMultiLineDeletion() {
    TextDocumentContentChangeEvent textChange = new TextDocumentContentChangeEvent();
    textChange.setText("");
    textChange.setRange(new Range(new Position(1, 0), new Position(2, 8)));

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, textChange);
    var expectedNewContent = "print(\"hello\")\n" +
      "b = False";

    assertEquals(changedContent, expectedNewContent);
  }

  @Test
  void shouldApplyMultiLineChangeWithEmptyLine() {
    TextDocumentContentChangeEvent textChange = new TextDocumentContentChangeEvent();
    textChange.setText("\n");
    textChange.setRange(new Range(new Position(1, 0), new Position(2, 8)));

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, textChange);
    var expectedNewContent = "print(\"hello\")\n" +
      "\n" +
      "\n" +
      "b = False";

    assertEquals(changedContent, expectedNewContent);
  }
}