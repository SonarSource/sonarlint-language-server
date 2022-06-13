/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;

public interface SonarLintExtendedLanguageClient extends LanguageClient {

  @JsonNotification("sonarlint/showSonarLintOutput")
  void showSonarLintOutput();

  @JsonNotification("sonarlint/openJavaHomeSettings")
  void openJavaHomeSettings();

  @JsonNotification("sonarlint/openPathToNodeSettings")
  void openPathToNodeSettings();

  @JsonNotification("sonarlint/openConnectionSettings")
  void openConnectionSettings(boolean isSonarCloud);

  @JsonNotification("sonarlint/showRuleDescription")
  void showRuleDescription(ShowRuleDescriptionParams params);

  @JsonNotification("sonarlint/showHotspot")
  void showHotspot(ServerHotspot hotspot);

  @JsonNotification("sonarlint/showTaintVulnerability")
  void showTaintVulnerability(ShowAllLocationsCommand.Param params);

  @JsonRequest("sonarlint/isIgnoredByScm")
  CompletableFuture<Boolean> isIgnoredByScm(String fileUri);

  @JsonRequest("sonarlint/isOpenInEditor")
  CompletableFuture<Boolean> isOpenInEditor(String fileUri);

  @JsonNotification("sonarlint/showNotificationForFirstSecretsIssue")
  void showFirstSecretDetectionNotification();

  class ShowRuleDescriptionParams {
    @Expose
    private final String key;
    @Expose
    private final String name;
    @Expose
    private final String htmlDescription;
    @Expose
    private final String type;
    @Expose
    private final String severity;
    @Expose
    private final RuleParameter[] parameters;

    public ShowRuleDescriptionParams(String ruleKey, String ruleName, @Nullable String htmlDescription, @Nullable String type, String severity,
      Collection<StandaloneRuleParam> params) {
      this.key = ruleKey;
      this.name = ruleName;
      this.htmlDescription = htmlDescription;
      this.type = type;
      this.severity = severity;
      this.parameters = params.stream().map(p -> new RuleParameter(p.name(), p.description(), p.defaultValue())).toArray(RuleParameter[]::new);
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

    public RuleParameter[] getParameters() {
      return parameters;
    }

    @Override
    public int hashCode() {
      var result = Objects.hash(key, name, htmlDescription, type, severity);
      result = 31 * result + Arrays.hashCode(parameters);
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof ShowRuleDescriptionParams)) {
        return false;
      }
      var other = (ShowRuleDescriptionParams) obj;
      return Objects.equals(htmlDescription, other.htmlDescription) && Objects.equals(key, other.key) && Objects.equals(name, other.name)
        && Objects.equals(severity, other.severity) && Objects.equals(type, other.type) && Arrays.equals(parameters, other.parameters);
    }

  }

  class RuleParameter {
    @Expose
    final String name;
    @Expose
    final String description;
    @Expose
    final String defaultValue;

    public RuleParameter(String name, @Nullable String description, @Nullable String defaultValue) {
      this.name = name;
      this.description = description;
      this.defaultValue = defaultValue;
    }

    public String getName() {
      return name;
    }

    @CheckForNull
    public String getDescription() {
      return description;
    }

    @CheckForNull
    public String getDefaultValue() {
      return defaultValue;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      var that = (RuleParameter) o;
      return name.equals(that.name) &&
        Objects.equals(description, that.description) &&
        Objects.equals(defaultValue, that.defaultValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, description, defaultValue);
    }
  }

  /**
   * Fetch java configuration for a given file.
   * See: https://github.com/redhat-developer/vscode-java/commit/e29f6df2db016c514afd8d2b69462ad2ef1de867
   */
  @JsonRequest("sonarlint/getJavaConfig")
  CompletableFuture<GetJavaConfigResponse> getJavaConfig(String fileUri);

  class GetJavaConfigResponse {

    private String projectRoot;
    private String sourceLevel;
    private String[] classpath;
    private boolean isTest;
    private String vmLocation;

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

    @CheckForNull
    public String getVmLocation() {
      return vmLocation;
    }

    public void setVmLocation(String vmLocation) {
      this.vmLocation = vmLocation;
    }

  }

  @JsonNotification("sonarlint/browseTo")
  void browseTo(String link);

  @JsonRequest("sonarlint/getBranchNameForFolder")
  CompletableFuture<String> getBranchNameForFolder(String folderUri);

  class ReferenceBranchForFolder {
    private final String folderUri;
    @Nullable
    private final String branchName;

    private ReferenceBranchForFolder(String folderUri, @Nullable String branchName) {
      this.folderUri = folderUri;
      this.branchName = branchName;
    }

    public String getFolderUri() {
      return folderUri;
    }

    @CheckForNull
    public String getBranchName() {
      return branchName;
    }

    public static ReferenceBranchForFolder of(String folderUri, @Nullable String branchName) {
      return new ReferenceBranchForFolder(folderUri, branchName);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      var that = (ReferenceBranchForFolder) o;
      return folderUri.equals(that.folderUri) && Objects.equals(branchName, that.branchName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(folderUri, branchName);
    }
  }

  @JsonNotification("sonarlint/setReferenceBranchNameForFolder")
  void setReferenceBranchNameForFolder(ReferenceBranchForFolder newReferenceBranch);

  @JsonNotification("sonarlint/needCompilationDatabase")
  void needCompilationDatabase();

  @JsonNotification("sonarlint/reportConnectionCheckResult")
  void reportConnectionCheckResult(ConnectionCheckResult result);

  class ConnectionCheckResult {
    private final String connectionId;
    private final boolean success;
    @Nullable
    private final String reason;

    private ConnectionCheckResult(String connectionId, boolean success, @Nullable String reason) {
      this.connectionId = connectionId;
      this.success = success;
      this.reason = reason;
    }
    public static ConnectionCheckResult success(String connectionId) {
      return new ConnectionCheckResult(connectionId, true, null);
    }

    public static ConnectionCheckResult failure(String connectionId, String reason) {
      return new ConnectionCheckResult(connectionId, false, reason);
    }

    public String getConnectionId() {
      return connectionId;
    }

    public boolean isSuccess() {
      return success;
    }

    @CheckForNull
    public String getReason() {
      return reason;
    }
  }
}
