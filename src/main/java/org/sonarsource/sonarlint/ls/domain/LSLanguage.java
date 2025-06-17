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

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum LSLanguage {
  ABAP("ABAP", "abap"),
  APEX("Apex", "apex"),
  AZURERESOURCEMANAGER("AzureResourceManager", "azureresourcemanager"),
  C("C", "c"),
  CLOUDFORMATION("CloudFormation", "cloudformation"),
  COBOL("COBOL", "cobol"),
  CPP("C++", "cpp"),
  CS("C#", "cs"),
  CSS("CSS", "css"),
  DOCKER("Docker", "docker"),
  GO("Go", "go"),
  HTML("HTML", "web"),
  IPYTHON("IPython Notebooks", "ipynb"),
  JAVA("Java", "java"),
  JS("JavaScript", "js"),
  JSON("JSON", "json"),
  JSP("JSP", "jsp"),
  KOTLIN("Kotlin", "kotlin"),
  KUBERNETES("Kubernetes", "kubernetes"),
  OBJC("Objective C", "objc"),
  PHP("PHP", "php"),
  PLI("PL/I", "pli"),
  PLSQL("PL/SQL", "plsql"),
  PYTHON("Python", "py"),
  RPG("RPG", "rpg"),
  RUBY("Ruby", "ruby"),
  SCALA("Scala", "scala"),
  SECRETS("Secrets", "secrets"),
  SWIFT("Swift", "swift"),
  TERRAFORM("Terraform", "terraform"),
  TS("TypeScript", "ts"),
  TSQL("TSQL", "tsql"),
  VBNET("VB.Net", "vbnet"),
  XML("XML", "xml"),
  YAML("YAML", "yaml");

  private final String label;
  private final String key;

  private static final Map<String, LSLanguage> byKey = Stream.of(values())
    .collect(Collectors.toMap(LSLanguage::getKey, Function.identity()));

  LSLanguage(String label, String key) {
    this.label = label;
    this.key = key;
  }

  public String getLabel() {
    return label;
  }

  public String getKey() {
    return key;
  }

  public static Optional<LSLanguage> forKey(String key) {
    return Optional.ofNullable(byKey.get(key));
  }
}
