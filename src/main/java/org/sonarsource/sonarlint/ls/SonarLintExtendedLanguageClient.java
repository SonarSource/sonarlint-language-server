/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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

import com.google.gson.annotations.Expose;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;

public interface SonarLintExtendedLanguageClient extends LanguageClient {

  @JsonRequest("sonarlint/showRuleDescription")
  CompletableFuture<Void> showRuleDescription(ShowRuleDescriptionParams params);

  public static class ShowRuleDescriptionParams {
    @Expose
    private String key;
    @Expose
    private String name;
    @Expose
    private String htmlDescription;
    @Expose
    private String type;
    @Expose
    private String severity;

    public ShowRuleDescriptionParams(String ruleKey, String ruleName, @Nullable String htmlDescription, @Nullable String type, String severity) {
      this.key = ruleKey;
      this.name = ruleName;
      this.htmlDescription = htmlDescription;
      this.type = type;
      this.severity = severity;
    }

    public String getKey() {
      return key;
    }

    public String getName() {
      return name;
    }

    public String getHtmlDescription() {
      return htmlDescription;
    }

    public String getType() {
      return type;
    }

    public String getSeverity() {
      return severity;
    }

    @Override
    public int hashCode() {
      return Objects.hash(htmlDescription, key, name, severity, type);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof ShowRuleDescriptionParams)) {
        return false;
      }
      ShowRuleDescriptionParams other = (ShowRuleDescriptionParams) obj;
      return Objects.equals(htmlDescription, other.htmlDescription) && Objects.equals(key, other.key) && Objects.equals(name, other.name)
        && Objects.equals(severity, other.severity) && Objects.equals(type, other.type);
    }

  }

  /**
   * Fetch java configuration for a given file.
   * See: https://github.com/redhat-developer/vscode-java/commit/e29f6df2db016c514afd8d2b69462ad2ef1de867
   */
  @JsonRequest("sonarlint/getJavaConfig")
  CompletableFuture<GetJavaConfigResponse> getJavaConfig(String fileUri);

  public static class GetJavaConfigResponse {

    private String projectRoot;
    private String sourceLevel;
    private String[] classpath;
    private boolean isTest;

    public String getProjectRoot() {
      return projectRoot;
    }

    public void setProjectRoot(String projectRoot) {
      this.projectRoot = projectRoot;
    }

    public String getSourceLevel() {
      return sourceLevel;
    }

    public void setSourceLevel(String sourceLevel) {
      this.sourceLevel = sourceLevel;
    }

    public String[] getClasspath() {
      return classpath;
    }

    public void setClasspath(String[] classpath) {
      this.classpath = classpath;
    }

    public boolean isTest() {
      return isTest;
    }

    public void setTest(boolean isTest) {
      this.isTest = isTest;
    }

  }

}
