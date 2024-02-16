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

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttributeCategory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnumLabelsMapperTests {

  @Test
  void resolutionStatusToLabel() {
    assertThat(EnumLabelsMapper.resolutionStatusToLabel(ResolutionStatus.ACCEPT)).isEqualTo("Accepted");
    assertThat(EnumLabelsMapper.resolutionStatusToLabel(ResolutionStatus.FALSE_POSITIVE)).isEqualTo("False positive");
    assertThat(EnumLabelsMapper.resolutionStatusToLabel(ResolutionStatus.WONT_FIX)).isEqualTo("Won't fix");
  }

  @Test
  void resolutionStatusFromLabel() {
    assertThat(EnumLabelsMapper.resolutionStatusFromLabel("Accepted")).isEqualTo(ResolutionStatus.ACCEPT);
    assertThat(EnumLabelsMapper.resolutionStatusFromLabel("False positive")).isEqualTo(ResolutionStatus.FALSE_POSITIVE);
    assertThat(EnumLabelsMapper.resolutionStatusFromLabel("Won't fix")).isEqualTo(ResolutionStatus.WONT_FIX);
    assertThrows(IllegalArgumentException.class, () -> EnumLabelsMapper.resolutionStatusFromLabel("Unknown"));
  }

  @Test
  void cleanCodeAttributeToLabel() {
    assertEquals("Not conventional", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.CONVENTIONAL));
    assertEquals("Not formatted", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.FORMATTED));
    assertEquals("Not identifiable", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.IDENTIFIABLE));
    assertEquals("Not clear", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.CLEAR));
    assertEquals("Not complete", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.COMPLETE));
    assertEquals("Not efficient", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.EFFICIENT));
    assertEquals("Not logical", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.LOGICAL));
    assertEquals("Not distinct", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.DISTINCT));
    assertEquals("Not focused", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.FOCUSED));
    assertEquals("Not modular", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.MODULAR));
    assertEquals("Not tested", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.TESTED));
    assertEquals("Not lawful", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.LAWFUL));
    assertEquals("Not respectful", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.RESPECTFUL));
    assertEquals("Not trustworthy", EnumLabelsMapper.cleanCodeAttributeToLabel(CleanCodeAttribute.TRUSTWORTHY));
  }

  @Test
  void testCleanCodeAttributeCategoryToLabel() {
    assertEquals("Adaptability", EnumLabelsMapper.cleanCodeAttributeCategoryToLabel(CleanCodeAttributeCategory.ADAPTABLE));
    assertEquals("Consistency", EnumLabelsMapper.cleanCodeAttributeCategoryToLabel(CleanCodeAttributeCategory.CONSISTENT));
    assertEquals("Intentionality", EnumLabelsMapper.cleanCodeAttributeCategoryToLabel(CleanCodeAttributeCategory.INTENTIONAL));
    assertEquals("Responsibility", EnumLabelsMapper.cleanCodeAttributeCategoryToLabel(CleanCodeAttributeCategory.RESPONSIBLE));
  }

  @Test
  void testSoftwareQualityToLabel() {
    assertEquals("Maintainability", EnumLabelsMapper.softwareQualityToLabel(SoftwareQuality.MAINTAINABILITY));
    assertEquals("Reliability", EnumLabelsMapper.softwareQualityToLabel(SoftwareQuality.RELIABILITY));
    assertEquals("Security", EnumLabelsMapper.softwareQualityToLabel(SoftwareQuality.SECURITY));
  }

  @Test
  void testImpactSeverityToLabel() {
    assertEquals("Low", EnumLabelsMapper.impactSeverityToLabel(ImpactSeverity.LOW));
    assertEquals("Medium", EnumLabelsMapper.impactSeverityToLabel(ImpactSeverity.MEDIUM));
    assertEquals("High", EnumLabelsMapper.impactSeverityToLabel(ImpactSeverity.HIGH));
  }

}
