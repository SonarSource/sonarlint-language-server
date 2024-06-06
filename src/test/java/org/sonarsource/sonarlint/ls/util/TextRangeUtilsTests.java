/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.sonarlint.ls.util;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TextRangeUtilsTests {

  @Test
  void testConvertIssue() {
    DelegatingFinding issue1 = mockIssueWithTextRange(0, 5, 15, 8);
    Range result1 = TextRangeUtils.convert(issue1);
    assertEquals(new Range(new Position(0, 0), new Position(0, 0)), result1);

    DelegatingFinding issue2 = mockIssueWithTextRange(5, 2, 10, 7);
    Range result2 = TextRangeUtils.convert(issue2);
    assertEquals(new Range(new Position(4, 2), new Position(9, 7)), result2);
  }

  @Test
  void testConvertTextRangeWithHash() {
    Range result1 = TextRangeUtils.convert(((TextRangeWithHash) null));
    assertEquals(new Range(new Position(0, 0), new Position(0, 0)), result1);

    var textRangeWithHash = mock(TextRangeWithHash.class);
    when(textRangeWithHash.getStartLine()).thenReturn(5);
    when(textRangeWithHash.getStartLineOffset()).thenReturn(2);
    when(textRangeWithHash.getEndLine()).thenReturn(10);
    when(textRangeWithHash.getEndLineOffset()).thenReturn(7);
    Range result2 = TextRangeUtils.convert(textRangeWithHash);
    assertEquals(new Range(new Position(4, 2), new Position(9, 7)), result2);
  }

  @Test
  void testConvertTextRangeWithHashDto() {
    Range result1 = TextRangeUtils.convert(((TextRangeWithHashDto) null));
    assertEquals(new Range(new Position(0, 0), new Position(0, 0)), result1);

    var textRangeWithHash = mock(TextRangeWithHashDto.class);
    when(textRangeWithHash.getStartLine()).thenReturn(5);
    when(textRangeWithHash.getStartLineOffset()).thenReturn(2);
    when(textRangeWithHash.getEndLine()).thenReturn(10);
    when(textRangeWithHash.getEndLineOffset()).thenReturn(7);
    Range result2 = TextRangeUtils.convert(textRangeWithHash);
    assertEquals(new Range(new Position(4, 2), new Position(9, 7)), result2);
  }

  @Test
  void testTextRangeWithHashDtoToTextRangeDto() {
    TextRangeDto result1 = TextRangeUtils.textRangeWithHashDtoToTextRangeDto(null);
    assertThat(result1.getStartLine()).isZero();
    assertThat(result1.getStartLineOffset()).isZero();
    assertThat(result1.getEndLine()).isZero();
    assertThat(result1.getEndLineOffset()).isZero();

    var textRangeWithHash = mock(TextRangeWithHashDto.class);
    when(textRangeWithHash.getStartLine()).thenReturn(5);
    when(textRangeWithHash.getStartLineOffset()).thenReturn(2);
    when(textRangeWithHash.getEndLine()).thenReturn(10);
    when(textRangeWithHash.getEndLineOffset()).thenReturn(7);
    TextRangeDto result2 = TextRangeUtils.textRangeWithHashDtoToTextRangeDto(textRangeWithHash);
    assertThat(result2.getStartLine()).isEqualTo(4);
    assertThat(result2.getStartLineOffset()).isEqualTo(2);
    assertThat(result2.getEndLine()).isEqualTo(9);
    assertThat(result2.getEndLineOffset()).isEqualTo(7);
  }

  @Test
  void testTextRangeDtoFromTextRange() {
    var result1 = TextRangeUtils.textRangeDtoFromTextRange(null);
    assertThat(result1).isNull();

    var textRange = mock(TextRange.class);
    when(textRange.getStartLine()).thenReturn(5);
    when(textRange.getStartLineOffset()).thenReturn(2);
    when(textRange.getEndLine()).thenReturn(10);
    when(textRange.getEndLineOffset()).thenReturn(7);

    TextRangeDto result2 = TextRangeUtils.textRangeDtoFromTextRange(textRange);
    assertThat(result2.getStartLine()).isEqualTo(5);
    assertThat(result2.getStartLineOffset()).isEqualTo(2);
    assertThat(result2.getEndLine()).isEqualTo(10);
    assertThat(result2.getEndLineOffset()).isEqualTo(7);
  }

  private DelegatingFinding mockIssueWithTextRange(Integer startLine, int startLineOffset, int endLine, int endLineOffset) {
    var issue = mock(DelegatingFinding.class);
    var textRange = new TextRangeDto(startLine, startLineOffset, endLine, endLineOffset);
    when(issue.getTextRange()).thenReturn(textRange);

    return issue;
  }
}
