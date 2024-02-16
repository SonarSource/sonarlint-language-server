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

import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttributeCategory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;

/**
 * This is a temporary solution before the migration to the SonarLint
 * out of the process is complete.
 * In future there'll be created a mapper in the protocol module of SLCORE
 */
public class EnumLabelsMapper {

  private EnumLabelsMapper() {
    // static util
  }

  public static String resolutionStatusToLabel(ResolutionStatus resolutionStatus) {
    return switch (resolutionStatus) {
      case WONT_FIX -> "Won't fix";
      case FALSE_POSITIVE -> "False positive";
      case ACCEPT -> "Accepted";
    };
  }

  public static ResolutionStatus resolutionStatusFromLabel(String label) {
    return switch (label) {
      case "Won't fix" -> ResolutionStatus.WONT_FIX;
      case "False positive" -> ResolutionStatus.FALSE_POSITIVE;
      case "Accepted" -> ResolutionStatus.ACCEPT;
      default -> throw new IllegalArgumentException("Unknown issue resolution status label" + label);
    };
  }

  public static String cleanCodeAttributeToLabel(CleanCodeAttribute cleanCodeAttribute) {
    return switch (cleanCodeAttribute) {
      case CONVENTIONAL -> "Not conventional";
      case FORMATTED -> "Not formatted";
      case IDENTIFIABLE -> "Not identifiable";
      case CLEAR -> "Not clear";
      case COMPLETE -> "Not complete";
      case EFFICIENT -> "Not efficient";
      case LOGICAL -> "Not logical";
      case DISTINCT -> "Not distinct";
      case FOCUSED -> "Not focused";
      case MODULAR -> "Not modular";
      case TESTED -> "Not tested";
      case LAWFUL -> "Not lawful";
      case RESPECTFUL -> "Not respectful";
      case TRUSTWORTHY -> "Not trustworthy";
    };
  }

  public static String cleanCodeAttributeCategoryToLabel(CleanCodeAttributeCategory cleanCodeAttributeCategory) {
    return switch (cleanCodeAttributeCategory) {
      case ADAPTABLE -> "Adaptability";
      case CONSISTENT -> "Consistency";
      case INTENTIONAL -> "Intentionality";
      case RESPONSIBLE -> "Responsibility";
    };
  }

  public static String softwareQualityToLabel(SoftwareQuality softwareQuality) {
    return switch (softwareQuality) {
      case MAINTAINABILITY -> "Maintainability";
      case RELIABILITY -> "Reliability";
      case SECURITY -> "Security";
    };
  }

  public static String impactSeverityToLabel(ImpactSeverity softwareQuality) {
    return switch (softwareQuality) {
      case LOW -> "Low";
      case MEDIUM -> "Medium";
      case HIGH -> "High";
    };
  }

}
