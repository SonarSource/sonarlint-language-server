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

import java.util.List;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotebookUtilsTests {
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
    var textChange = newChange(0, 1, 0, 4, "a");

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(textChange));
    var expectedNewContent = "pat(\"hello\")\n" +
      "\n" +
      "a = True\n" +
      "b = False";

    assertThat(changedContent).isEqualTo(expectedNewContent);
  }

  @Test
  void shouldApplySingleLineChangeOnTheSecondLine() {
    var textChange = newChange(1, 0, 1, 0, "c = 42");

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(textChange));
    var expectedNewContent = "print(\"hello\")\n" +
      "c = 42\n" +
      "a = True\n" +
      "b = False";

    assertThat(changedContent).isEqualTo(expectedNewContent);
  }

  @Test
  void shouldApplyTwoSingleLineChangesOnDifferentLines() {
    var firstChange = newChange(3, 4, 3, 9, "0");
    var secondChange = newChange(2, 4, 2, 8, "1");

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(firstChange, secondChange));
    var expectedNewContent = "print(\"hello\")\n" +
      "\n" +
      "a = 1\n" +
      "b = 0";

    assertThat(changedContent).isEqualTo(expectedNewContent);
  }

  @Test
  void shouldApplyTwoSingleLineChangesOnDifferentLinesAndKeepMiddleRangeUnchanged() {
    var firstChange = newChange(0, 7, 0, 12, "hi");
    var secondChange = newChange(3, 4, 3, 9, "0");

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(firstChange, secondChange));
    var expectedNewContent = "print(\"hi\")\n" +
      "\n" +
      "a = True\n" +
      "b = 0";

    assertThat(changedContent).isEqualTo(expectedNewContent);
  }

  @Test
  void shouldApplyTwoSingleLineChangesOnSameLine() {
    var firstChange = newChange(2, 4, 2, 8, "0");
    var secondChange = newChange(2, 0, 2, 1, "c");

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(firstChange, secondChange));
    var expectedNewContent = "print(\"hello\")\n" +
      "\n" +
      "c = 0\n" +
      "b = False";

    assertThat(changedContent).isEqualTo(expectedNewContent);
  }

  @Test
  void shouldApplyThreeSingleLineChangesOnSameLine() {
    var textChange1 = newChange(0, 1, 0, 4, "a");
    var textChange2 = newChange(0, 0, 0, 1, "d");
    var textChange3 = newChange(0, 7, 0, 9, "hello");

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(textChange1, textChange2, textChange3));
    var expectedNewContent = "dat(\"hellollo\")\n" +
      "\n" +
      "a = True\n" +
      "b = False";

    assertThat(changedContent).isEqualTo(expectedNewContent);
  }

  @Test
  void shouldApplySingleLineChangeAtTheEndOfFile() {
    var textChange = newChange(3, 1 ,3, 1, "oo");

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(textChange));
    var expectedNewContent = "print(\"hello\")\n" +
      "\n" +
      "a = True\n" +
      "boo = False";

    assertThat(changedContent).isEqualTo(expectedNewContent);
  }

  @Test
  void shouldApplyMultiLineDeletion() {
    var textChange = newChange(1, 0, 2, 8, "");

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(textChange));
    var expectedNewContent = "print(\"hello\")\n" +
      "\n" +
      "b = False";

    assertThat(changedContent).isEqualTo(expectedNewContent);
  }

  @Test
  void shouldApplyMultiLineChangeWithEmptyLine() {
    var textChange = newChange(1, 0, 2, 8, "\n");

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(textChange));
    var expectedNewContent = "print(\"hello\")\n" +
      "\n" +
      "\n" +
      "b = False";

    assertThat(changedContent).isEqualTo(expectedNewContent);
  }

  @Test
  void shouldAddAndDeleteLastLine() {
    var textChange = newChange(3, 9, 3, 9, "\n");

    String changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(textChange));
    String expectedNewContent = "print(\"hello\")\n" +
      "\n" +
      "a = True\n" +
      "b = False" +
      "\n";
    assertThat(changedContent).isEqualTo(expectedNewContent);
    originalCell.setText(changedContent);

    textChange = newChange(3, 9, 4, 0, "");

    changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(textChange));
    expectedNewContent = "print(\"hello\")\n" +
      "\n" +
      "a = True\n" +
      "b = False";
    assertThat(changedContent).isEqualTo(expectedNewContent);
  }

  @Test
  void shouldDeleteFirstLine() {
    var textChange = newChange(0, 0, 0, 14, "");

    var changedContent = NotebookUtils.applyChangeToCellContent(originalCell, List.of(textChange));
    var expectedNewContent = "\n" +
      "\n" +
      "a = True\n" +
      "b = False";

    assertThat(changedContent).isEqualTo(expectedNewContent);
  }

  private static TextDocumentContentChangeEvent newChange(int startLine, int startLineOffset, int endLine, int endLineOffset, String replacement) {
    var textChange = new TextDocumentContentChangeEvent();
    textChange.setRange(new Range(new Position(startLine, startLineOffset), new Position(endLine, endLineOffset)));
    textChange.setText(replacement);
    return textChange;
  }
}
