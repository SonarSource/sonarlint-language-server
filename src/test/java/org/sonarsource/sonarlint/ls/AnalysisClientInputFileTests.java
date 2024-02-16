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
package org.sonarsource.sonarlint.ls;


import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisClientInputFileTests {

  @ParameterizedTest(name = "Should detect {0} as {1}")
  @MethodSource("provideParametersForLanguageDetection")
  void shouldDetectLanguage(String clientLanguageId, SonarLanguage expected) {
    assertThat(new AnalysisClientInputFile(null, null, "", false, clientLanguageId).language())
      .isEqualTo(expected);
  }

  private static Stream<Arguments> provideParametersForLanguageDetection() {
    return Stream.of(
      Arguments.of("javascript", SonarLanguage.JS),
      Arguments.of("javascriptreact", SonarLanguage.JS),
      Arguments.of("vue", SonarLanguage.JS),
      Arguments.of("vue component", SonarLanguage.JS),
      Arguments.of("babel es6 javascript", SonarLanguage.JS),

      Arguments.of("python", SonarLanguage.PYTHON),

      Arguments.of("typescript", SonarLanguage.TS),
      Arguments.of("typescriptreact", SonarLanguage.TS),

      Arguments.of("html", SonarLanguage.HTML),

      Arguments.of("oraclesql", SonarLanguage.PLSQL),
      Arguments.of("plsql", SonarLanguage.PLSQL),

      Arguments.of("apex", SonarLanguage.APEX),
      Arguments.of("apex-anon", SonarLanguage.APEX),

      Arguments.of("php", SonarLanguage.PHP),
      Arguments.of("java", SonarLanguage.JAVA),
      Arguments.of("c", SonarLanguage.C),
      Arguments.of("cpp", SonarLanguage.CPP),

      Arguments.of("yaml", SonarLanguage.YAML),

      Arguments.of("unknown", null)
    );
  }
}
