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

  @Test
  void shouldAddAndDeleteLastLine() {
    TextDocumentContentChangeEvent textChange = new TextDocumentContentChangeEvent();
    textChange.setText("\n");
    textChange.setRange(new Range(new Position(3, 9), new Position(3, 9)));

    String changedContent = NotebookUtils.applyChangeToCellContent(originalCell, textChange);
    String expectedNewContent = "print(\"hello\")\n" +
      "\n" +
      "a = True\n" +
      "b = False" +
      "\n";
    assertEquals(changedContent, expectedNewContent);
    originalCell.setText(changedContent);

    textChange.setText("");
    textChange.setRange(new Range(new Position(3, 9), new Position(4, 0)));

    changedContent = NotebookUtils.applyChangeToCellContent(originalCell, textChange);
    expectedNewContent = "print(\"hello\")\n" +
      "\n" +
      "a = True\n" +
      "b = False";
    assertEquals(changedContent, expectedNewContent);
  }
}