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
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
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

  public static String languageToLanguageKey(Language language) {
    return switch (language) {
      case ABAP -> "abap";
      case APEX -> "apex";
      case AZURERESOURCEMANAGER -> "azureresourcemanager";
      case C -> "c";
      case CLOUDFORMATION -> "cloudformation";
      case COBOL -> "cobol";
      case CPP -> "cpp";
      case CS -> "cs";
      case CSS -> "css";
      case DOCKER -> "docker";
      case GO -> "go";
      case HTML -> "web";
      case IPYTHON -> "ipynb";
      case JAVA -> "java";
      case JS -> "js";
      case JSON -> "json";
      case JSP -> "jsp";
      case KOTLIN -> "kotlin";
      case KUBERNETES -> "kubernetes";
      case OBJC -> "objc";
      case PHP -> "php";
      case PLI -> "pli";
      case PLSQL -> "plsql";
      case PYTHON -> "py";
      case RPG -> "rpg";
      case RUBY -> "ruby";
      case SCALA -> "scala";
      case SECRETS -> "secrets";
      case SWIFT -> "swift";
      case TERRAFORM -> "terraform";
      case TS -> "ts";
      case TSQL -> "tsql";
      case VBNET -> "vbnet";
      case XML -> "xml";
      case YAML -> "yaml";
    };
  }

  public static String languageToLabel(Language language) {
    return switch (language) {
      case ABAP -> "ABAP";
      case APEX -> "Apex";
      case AZURERESOURCEMANAGER -> "AzureResourceManager";
      case C -> "C";
      case CLOUDFORMATION -> "CloudFormation";
      case COBOL -> "COBOL";
      case CPP -> "C++";
      case CS -> "C#";
      case CSS -> "CSS";
      case DOCKER -> "Docker";
      case GO -> "Go";
      case HTML -> "HTML";
      case IPYTHON -> "IPython Notebooks";
      case JAVA -> "Java";
      case JS -> "JavaScript";
      case JSON -> "JSON";
      case JSP -> "JSP";
      case KOTLIN -> "Kotlin";
      case KUBERNETES -> "Kubernetes";
      case OBJC -> "Objective C";
      case PHP -> "PHP";
      case PLI -> "PL/I";
      case PLSQL -> "PL/SQL";
      case PYTHON -> "Python";
      case RPG -> "RPG";
      case RUBY -> "Ruby";
      case SCALA -> "Scala";
      case SECRETS -> "Secrets";
      case SWIFT -> "Swift";
      case TERRAFORM -> "Terraform";
      case TS -> "TypeScript";
      case TSQL -> "TSQL";
      case VBNET -> "VB.Net";
      case XML -> "XML";
      case YAML -> "YAML";
    };
  }
}
