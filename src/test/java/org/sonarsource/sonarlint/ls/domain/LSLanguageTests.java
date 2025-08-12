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
package org.sonarsource.sonarlint.ls.domain;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class LSLanguageTests {

  @ParameterizedTest
  @MethodSource("shouldHaveMappingForLabelAndKey")
  void shouldHaveMappingForLabelAndKey(Language language, String label, String languageKey) {
    assertThat(LSLanguage.valueOf(language.name()).getLabel()).isEqualTo(label);
    assertThat(LSLanguage.valueOf(language.name()).getKey()).isEqualTo(languageKey);
  }

  private static Stream<Arguments> shouldHaveMappingForLabelAndKey() {
    return Stream.of(
      Arguments.of(Language.ABAP, "ABAP", "abap"),
      Arguments.of(Language.APEX, "Apex", "apex"),
      Arguments.of(Language.AZURERESOURCEMANAGER, "AzureResourceManager", "azureresourcemanager"),
      Arguments.of(Language.C, "C", "c"),
      Arguments.of(Language.CLOUDFORMATION, "CloudFormation", "cloudformation"),
      Arguments.of(Language.COBOL, "COBOL", "cobol"),
      Arguments.of(Language.CPP, "C++", "cpp"),
      Arguments.of(Language.CS, "C#", "cs"),
      Arguments.of(Language.CSS, "CSS", "css"),
      Arguments.of(Language.DOCKER, "Docker", "docker"),
      Arguments.of(Language.GO, "Go", "go"),
      Arguments.of(Language.GITHUBACTIONS, "GitHub Actions", "githubactions"),
      Arguments.of(Language.HTML, "HTML", "web"),
      Arguments.of(Language.IPYTHON, "IPython Notebooks", "ipynb"),
      Arguments.of(Language.JAVA, "Java", "java"),
      Arguments.of(Language.JS, "JavaScript", "js"),
      Arguments.of(Language.JSON, "JSON", "json"),
      Arguments.of(Language.JSP, "JSP", "jsp"),
      Arguments.of(Language.KOTLIN, "Kotlin", "kotlin"),
      Arguments.of(Language.KUBERNETES, "Kubernetes", "kubernetes"),
      Arguments.of(Language.OBJC, "Objective C", "objc"),
      Arguments.of(Language.PHP, "PHP", "php"),
      Arguments.of(Language.PLI, "PL/I", "pli"),
      Arguments.of(Language.PLSQL, "PL/SQL", "plsql"),
      Arguments.of(Language.PYTHON, "Python", "py"),
      Arguments.of(Language.RPG, "RPG", "rpg"),
      Arguments.of(Language.RUBY, "Ruby", "ruby"),
      Arguments.of(Language.SCALA, "Scala", "scala"),
      Arguments.of(Language.SECRETS, "Secrets", "secrets"),
      Arguments.of(Language.TEXT, "Text", "text"),
      Arguments.of(Language.SWIFT, "Swift", "swift"),
      Arguments.of(Language.TERRAFORM, "Terraform", "terraform"),
      Arguments.of(Language.TS, "TypeScript", "ts"),
      Arguments.of(Language.TSQL, "TSQL", "tsql"),
      Arguments.of(Language.VBNET, "VB.Net", "vbnet"),
      Arguments.of(Language.XML, "XML", "xml"),
      Arguments.of(Language.YAML, "YAML", "yaml")
    );
  }
}
