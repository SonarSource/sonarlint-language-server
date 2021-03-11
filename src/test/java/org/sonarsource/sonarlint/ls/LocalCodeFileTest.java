/*
 * SonarLint Language Server
 * Copyright (C) 2009-2021 SonarSource SA
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


import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCodeFileTest {

  private static LocalCodeFile underTest;

  @BeforeAll
  static void setUp() throws Exception {
    underTest = LocalCodeFile.from(LocalCodeFileTest.class.getResource("/sample.txt").toURI());
  }

  @Test
  void shouldNotFindNullRange() {
    assertThat(underTest.codeAt(null)).isNull();
  }

  @ParameterizedTest(name = "codeAt(range({0}, {1}, {2}, {3})) should be `{4}`")
  @MethodSource("argumentsForValidRange")
  void shouldReturnCodeAtValidRange(int startLine, int startLineOffset, int endLine, int endLineOffset, String expected) {
    assertThat(underTest.codeAt(range(startLine, startLineOffset, endLine, endLineOffset))).isEqualTo(expected);
  }

  private static Stream<Arguments> argumentsForValidRange() {
    return Stream.of(
      Arguments.of(1, 0, 1, 0, ""),
      Arguments.of(2, 0, 2, 11, "Second line"),
      Arguments.of(2, 3, 2, 15, "ond line"),
      Arguments.of(2, 3, 3, 5, "ond line\nThird"),
      Arguments.of(1, 3, 2, 15, "st line\nSecond line"),
      Arguments.of(1, 0, 3, 10, "First line\nSecond line\nThird line"),
      Arguments.of(3, 0, 5, 15, "Third line")
    );
  }

  @ParameterizedTest(name = "codeAt(range({0}, {1}, {2}, {3})) should be null")
  @MethodSource("argumentsForInvalidRange")
  void shouldNotFindInvalidRange(int startLine, int startLineOffset, int endLine, int endLineOffset) {
    assertThat(underTest.codeAt(range(startLine, startLineOffset, endLine, endLineOffset))).isNull();
  }

  private static Stream<Arguments> argumentsForInvalidRange() {
    return Stream.of(
      Arguments.of(5, 0, 5, 11),
      Arguments.of(2, 12, 2, 15)
    );
  }

  private static TextRange range(int startLine, int startLineOffset, int endLine, int endLineOffset) {
    return new TextRange(startLine, startLineOffset, endLine, endLineOffset);
  }
}
