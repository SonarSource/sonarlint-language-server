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

import org.sonarsource.sonarlint.core.clientapi.backend.issue.ResolutionStatus;

/**
 *  This is a temporary solution before the migration to the SonarLint
 *  out of the process is complete.
 *  In future there'll be created a mapper in the protocol module of SLCORE
 */
public class EnumLabelsMapper {

  private EnumLabelsMapper() {
    // static util
  }

  public static String resolutionStatusToLabel(ResolutionStatus resolutionStatus) {
    return switch (resolutionStatus) {
      case WONT_FIX -> "Won't fix";
      case FALSE_POSITIVE -> "False positive";
      case ACCEPT -> "Accept";
    };
  }

  public static ResolutionStatus resolutionStatusFromLabel(String label) {
    return switch (label) {
      case "Won't fix" -> ResolutionStatus.WONT_FIX;
      case "False positive" -> ResolutionStatus.FALSE_POSITIVE;
      case "Accept" -> ResolutionStatus.ACCEPT;
      default -> throw new IllegalArgumentException("Unknown issue resolution status label" + label);
    };
  }

}
