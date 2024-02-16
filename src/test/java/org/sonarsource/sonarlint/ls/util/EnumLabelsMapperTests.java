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
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

  @Test
  void testLanguageToLanguageKey() {
    assertEquals("abap", EnumLabelsMapper.languageToLanguageKey(Language.ABAP));
    assertEquals("apex", EnumLabelsMapper.languageToLanguageKey(Language.APEX));
    assertEquals("azureresourcemanager", EnumLabelsMapper.languageToLanguageKey(Language.AZURERESOURCEMANAGER));
    assertEquals("c", EnumLabelsMapper.languageToLanguageKey(Language.C));
    assertEquals("cloudformation", EnumLabelsMapper.languageToLanguageKey(Language.CLOUDFORMATION));
    assertEquals("cobol", EnumLabelsMapper.languageToLanguageKey(Language.COBOL));
    assertEquals("cpp", EnumLabelsMapper.languageToLanguageKey(Language.CPP));
    assertEquals("cs", EnumLabelsMapper.languageToLanguageKey(Language.CS));
    assertEquals("css", EnumLabelsMapper.languageToLanguageKey(Language.CSS));
    assertEquals("docker", EnumLabelsMapper.languageToLanguageKey(Language.DOCKER));
    assertEquals("go", EnumLabelsMapper.languageToLanguageKey(Language.GO));
    assertEquals("web", EnumLabelsMapper.languageToLanguageKey(Language.HTML));
    assertEquals("ipynb", EnumLabelsMapper.languageToLanguageKey(Language.IPYTHON));
    assertEquals("java", EnumLabelsMapper.languageToLanguageKey(Language.JAVA));
    assertEquals("js", EnumLabelsMapper.languageToLanguageKey(Language.JS));
    assertEquals("json", EnumLabelsMapper.languageToLanguageKey(Language.JSON));
    assertEquals("jsp", EnumLabelsMapper.languageToLanguageKey(Language.JSP));
    assertEquals("kotlin", EnumLabelsMapper.languageToLanguageKey(Language.KOTLIN));
    assertEquals("kubernetes", EnumLabelsMapper.languageToLanguageKey(Language.KUBERNETES));
    assertEquals("objc", EnumLabelsMapper.languageToLanguageKey(Language.OBJC));
    assertEquals("php", EnumLabelsMapper.languageToLanguageKey(Language.PHP));
    assertEquals("pli", EnumLabelsMapper.languageToLanguageKey(Language.PLI));
    assertEquals("plsql", EnumLabelsMapper.languageToLanguageKey(Language.PLSQL));
    assertEquals("py", EnumLabelsMapper.languageToLanguageKey(Language.PYTHON));
    assertEquals("rpg", EnumLabelsMapper.languageToLanguageKey(Language.RPG));
    assertEquals("ruby", EnumLabelsMapper.languageToLanguageKey(Language.RUBY));
    assertEquals("scala", EnumLabelsMapper.languageToLanguageKey(Language.SCALA));
    assertEquals("secrets", EnumLabelsMapper.languageToLanguageKey(Language.SECRETS));
    assertEquals("swift", EnumLabelsMapper.languageToLanguageKey(Language.SWIFT));
    assertEquals("terraform", EnumLabelsMapper.languageToLanguageKey(Language.TERRAFORM));
    assertEquals("ts", EnumLabelsMapper.languageToLanguageKey(Language.TS));
    assertEquals("tsql", EnumLabelsMapper.languageToLanguageKey(Language.TSQL));
    assertEquals("vbnet", EnumLabelsMapper.languageToLanguageKey(Language.VBNET));
    assertEquals("xml", EnumLabelsMapper.languageToLanguageKey(Language.XML));
    assertEquals("yaml", EnumLabelsMapper.languageToLanguageKey(Language.YAML));
  }


  @Test
  void testLanguageToLabel() {
    assertEquals("ABAP", EnumLabelsMapper.languageToLabel(Language.ABAP));
    assertEquals("Apex", EnumLabelsMapper.languageToLabel(Language.APEX));
    assertEquals("AzureResourceManager", EnumLabelsMapper.languageToLabel(Language.AZURERESOURCEMANAGER));
    assertEquals("C", EnumLabelsMapper.languageToLabel(Language.C));
    assertEquals("CloudFormation", EnumLabelsMapper.languageToLabel(Language.CLOUDFORMATION));
    assertEquals("COBOL", EnumLabelsMapper.languageToLabel(Language.COBOL));
    assertEquals("C++", EnumLabelsMapper.languageToLabel(Language.CPP));
    assertEquals("C#", EnumLabelsMapper.languageToLabel(Language.CS));
    assertEquals("CSS", EnumLabelsMapper.languageToLabel(Language.CSS));
    assertEquals("Docker", EnumLabelsMapper.languageToLabel(Language.DOCKER));
    assertEquals("Go", EnumLabelsMapper.languageToLabel(Language.GO));
    assertEquals("HTML", EnumLabelsMapper.languageToLabel(Language.HTML));
    assertEquals("IPython Notebooks", EnumLabelsMapper.languageToLabel(Language.IPYTHON));
    assertEquals("Java", EnumLabelsMapper.languageToLabel(Language.JAVA));
    assertEquals("JavaScript", EnumLabelsMapper.languageToLabel(Language.JS));
    assertEquals("JSON", EnumLabelsMapper.languageToLabel(Language.JSON));
    assertEquals("JSP", EnumLabelsMapper.languageToLabel(Language.JSP));
    assertEquals("Kotlin", EnumLabelsMapper.languageToLabel(Language.KOTLIN));
    assertEquals("Kubernetes", EnumLabelsMapper.languageToLabel(Language.KUBERNETES));
    assertEquals("Objective C", EnumLabelsMapper.languageToLabel(Language.OBJC));
    assertEquals("PHP", EnumLabelsMapper.languageToLabel(Language.PHP));
    assertEquals("PL/I", EnumLabelsMapper.languageToLabel(Language.PLI));
    assertEquals("PL/SQL", EnumLabelsMapper.languageToLabel(Language.PLSQL));
    assertEquals("Python", EnumLabelsMapper.languageToLabel(Language.PYTHON));
    assertEquals("RPG", EnumLabelsMapper.languageToLabel(Language.RPG));
    assertEquals("Ruby", EnumLabelsMapper.languageToLabel(Language.RUBY));
    assertEquals("Scala", EnumLabelsMapper.languageToLabel(Language.SCALA));
    assertEquals("Secrets", EnumLabelsMapper.languageToLabel(Language.SECRETS));
    assertEquals("Swift", EnumLabelsMapper.languageToLabel(Language.SWIFT));
    assertEquals("Terraform", EnumLabelsMapper.languageToLabel(Language.TERRAFORM));
    assertEquals("TypeScript", EnumLabelsMapper.languageToLabel(Language.TS));
    assertEquals("TSQL", EnumLabelsMapper.languageToLabel(Language.TSQL));
    assertEquals("VB.Net", EnumLabelsMapper.languageToLabel(Language.VBNET));
    assertEquals("XML", EnumLabelsMapper.languageToLabel(Language.XML));
    assertEquals("YAML", EnumLabelsMapper.languageToLabel(Language.YAML));
  }


}
