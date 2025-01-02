/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttributeCategory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumLabelsMapperTests {

  @ParameterizedTest(name = "ResolutionStatus.{0} is mapped to `{1}`")
  @CsvSource({
    "ACCEPT        , Accepted",
    "FALSE_POSITIVE, False positive",
    "WONT_FIX      , Won't fix",
  })
  void resolutionStatusToLabel(String enumName, String expectedLabel) {
    assertThat(EnumLabelsMapper.resolutionStatusToLabel(ResolutionStatus.valueOf(enumName))).isEqualTo(expectedLabel);
  }

  @ParameterizedTest(name = "Label `{0}` is mapped to ResolutionStatus.{1}")
  @CsvSource({
    "Accepted      , ACCEPT",
    "False positive, FALSE_POSITIVE",
    "Won't fix     , WONT_FIX",
  })
  void resolutionStatusFromLabel(String label, String expectedEnumValue) {
    assertThat(EnumLabelsMapper.resolutionStatusFromLabel(label)).isEqualTo(ResolutionStatus.valueOf(expectedEnumValue));
    assertThatThrownBy(() -> EnumLabelsMapper.resolutionStatusFromLabel("Unknown")).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "CleanCodeAttribute.{0} is mapped to `{1}`")
  @CsvSource({
    "CONVENTIONAL, Not conventional",
    "FORMATTED   , Not formatted",
    "IDENTIFIABLE, Not identifiable",
    "CLEAR       , Not clear",
    "COMPLETE    , Not complete",
    "EFFICIENT   , Not efficient",
    "LOGICAL     , Not logical",
    "DISTINCT    , Not distinct",
    "FOCUSED     , Not focused",
    "MODULAR     , Not modular",
    "TESTED      , Not tested",
    "LAWFUL      , Not lawful",
    "RESPECTFUL  , Not respectful",
    "TRUSTWORTHY , Not trustworthy"
  })
  void cleanCodeAttributeToLabel(String enumName, String expectedLabel) {
    assertThat(EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.valueOf(enumName))).isEqualTo(expectedLabel);
  }

  @ParameterizedTest(name = "CleanCodeAttributeCategory.{0} is mapped to `{1}`")
  @CsvSource({
    "ADAPTABLE  , Adaptability",
    "CONSISTENT , Consistency",
    "INTENTIONAL, Intentionality",
    "RESPONSIBLE, Responsibility"
  })
  void testCleanCodeAttributeCategoryToLabel(String enumName, String expectedLabel) {
    assertThat(EnumLabelsMapper.cleanCodeAttributeCategoryToLabel(CleanCodeAttributeCategory.valueOf(enumName))).isEqualTo(expectedLabel);
  }

  @ParameterizedTest(name = "SoftwareQuality.{0} is mapped to `{1}`")
  @CsvSource({
    "MAINTAINABILITY, Maintainability",
    "RELIABILITY    , Reliability",
    "SECURITY       , Security"
  })
  void testSoftwareQualityToLabel(String enumName, String expectedLabel) {
    assertThat(EnumLabelsMapper.softwareQualityToLabel(SoftwareQuality.valueOf(enumName))).isEqualTo(expectedLabel);
  }

  @ParameterizedTest(name = "ImpactSeverity.{0} is mapped to `{1}`")
  @CsvSource({
    "INFO   , Info",
    "LOW    , Low",
    "MEDIUM , Medium",
    "HIGH   , High",
    "BLOCKER, Blocker",
  })
  void testImpactSeverityToLabel(String enumName, String expectedLabel) {
    assertThat(EnumLabelsMapper.impactSeverityToLabel(ImpactSeverity.valueOf(enumName))).isEqualTo(expectedLabel);
  }

}
