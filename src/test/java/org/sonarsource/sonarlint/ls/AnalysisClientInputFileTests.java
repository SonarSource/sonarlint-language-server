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
package org.sonarsource.sonarlint.ls;


import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarlint.core.commons.Language;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisClientInputFileTests {

  @ParameterizedTest(name = "Should detect {0} as {1}")
  @MethodSource("provideParametersForLanguageDetection")
  void shouldDetectLanguage(String clientLanguageId, Language expected) {
    assertThat(new AnalysisClientInputFile(null, null, "", false, clientLanguageId).language())
      .isEqualTo(expected);
  }

  private static Stream<Arguments> provideParametersForLanguageDetection() {
    return Stream.of(
      Arguments.of("javascript", Language.JS),
      Arguments.of("javascriptreact", Language.JS),
      Arguments.of("vue", Language.JS),
      Arguments.of("vue component", Language.JS),
      Arguments.of("babel es6 javascript", Language.JS),

      Arguments.of("python", Language.PYTHON),

      Arguments.of("typescript", Language.TS),
      Arguments.of("typescriptreact", Language.TS),

      Arguments.of("html", Language.HTML),

      Arguments.of("oraclesql", Language.PLSQL),
      Arguments.of("plsql", Language.PLSQL),

      Arguments.of("apex", Language.APEX),
      Arguments.of("apex-anon", Language.APEX),

      Arguments.of("php", Language.PHP),
      Arguments.of("java", Language.JAVA),
      Arguments.of("c", Language.C),
      Arguments.of("cpp", Language.CPP),

      Arguments.of("yaml", Language.YAML),

      Arguments.of("unknown", null)
    );
  }
}
