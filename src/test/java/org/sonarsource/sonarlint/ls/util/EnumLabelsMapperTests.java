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
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ResolutionStatus;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnumLabelsMapperTests {

  @Test
  void resolutionStatusToLabel() {
    assertThat(EnumLabelsMapper.resolutionStatusToLabel(ResolutionStatus.ACCEPT)).isEqualTo("Accept");
    assertThat(EnumLabelsMapper.resolutionStatusToLabel(ResolutionStatus.FALSE_POSITIVE)).isEqualTo("False positive");
    assertThat(EnumLabelsMapper.resolutionStatusToLabel(ResolutionStatus.WONT_FIX)).isEqualTo("Won't fix");
  }

  @Test
  void resolutionStatusFromLabel() {
    assertThat(EnumLabelsMapper.resolutionStatusFromLabel("Accept")).isEqualTo(ResolutionStatus.ACCEPT);
    assertThat(EnumLabelsMapper.resolutionStatusFromLabel("False positive")).isEqualTo(ResolutionStatus.FALSE_POSITIVE);
    assertThat(EnumLabelsMapper.resolutionStatusFromLabel("Won't fix")).isEqualTo(ResolutionStatus.WONT_FIX);
    assertThrows(IllegalArgumentException.class, () -> EnumLabelsMapper.resolutionStatusFromLabel("Unknown"));
  }
}
