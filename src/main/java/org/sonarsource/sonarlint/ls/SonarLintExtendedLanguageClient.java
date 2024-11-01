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
package org.sonarsource.sonarlint.ls;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.JsonAdapter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleParamDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleParamDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SuggestConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.ChangesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.domain.MQRModeDetails;
import org.sonarsource.sonarlint.ls.domain.StandardModeDetails;

public interface SonarLintExtendedLanguageClient extends LanguageClient {

  @JsonNotification("sonarlint/suggestBinding")
  void suggestBinding(SuggestBindingParams binding);

  @JsonNotification("sonarlint/suggestConnection")
  void suggestConnection(SuggestConnectionParams suggestConnectionParams);

  @JsonRequest("sonarlint/listFilesInFolder")
  CompletableFuture<FindFileByNamesInScopeResponse> listFilesInFolder(FolderUriParams params);

  @JsonNotification("sonarlint/showSonarLintOutput")
  void showSonarLintOutput();

  @JsonNotification("sonarlint/openJavaHomeSettings")
  void openJavaHomeSettings();

  @JsonNotification("sonarlint/openPathToNodeSettings")
  void openPathToNodeSettings();

  @JsonNotification("sonarlint/doNotShowMissingRequirementsMessageAgain")
  void doNotShowMissingRequirementsMessageAgain();

  @JsonRequest("sonarlint/canShowMissingRequirementsNotification")
  CompletableFuture<Boolean> canShowMissingRequirementsNotification();

  @JsonNotification("sonarlint/openConnectionSettings")
  void openConnectionSettings(boolean isSonarCloud);

  @JsonNotification("sonarlint/removeBindingsForDeletedConnections")
  void removeBindingsForDeletedConnections(List<String> connectionIds);

  @JsonRequest("sonarlint/assistCreatingConnection")
  CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(CreateConnectionParams params);

  class AssistCreatingConnectionResponse {
    private final String newConnectionId;

    public AssistCreatingConnectionResponse(@NonNull String newConnectionId) {
      this.newConnectionId = newConnectionId;
    }

    @NonNull
    public String getNewConnectionId() {
      return newConnectionId;
    }
  }

  @JsonRequest("sonarlint/assistBinding")
  CompletableFuture<AssistBindingResponse> assistBinding(AssistBindingParams params);

  class AssistBindingResponse {
    private final String configurationScopeId;

    public AssistBindingResponse(@NonNull String configurationScopeId) {
      this.configurationScopeId = configurationScopeId;
    }

    @NonNull
    public String getConfigurationScopeId() {
      return configurationScopeId;
    }
  }

  record ShowFixSuggestionParams(String suggestionId, List<ChangesDto> textEdits, String fileUri) {
  }

  @JsonNotification("sonarlint/showFixSuggestion")
  void showFixSuggestion(ShowFixSuggestionParams params);

  @JsonNotification("sonarlint/showRuleDescription")
  void showRuleDescription(ShowRuleDescriptionParams params);

  class ShowHotspotParams {
    private final String message;
    private final String ideFilePath;
    private final String key;
    private final TextRangeDto textRange;
    private final String author;
    private final String status;
    @Nullable
    private final String resolution;
    private final HotspotRule rule;

    public ShowHotspotParams(String key, String message, String ideFilePath, TextRangeDto textRange, String author, String status,
      @Nullable String resolution, HotspotRule rule) {
      this.key = key;
      this.message = message;
      this.ideFilePath = ideFilePath;
      this.textRange = textRange;
      this.author = author;
      this.status = status;
      this.resolution = resolution;
      this.rule = rule;
    }

    public String getKey() {
      return this.key;
    }

    public String getMessage() {
      return this.message;
    }

    public String getIdeFilePath() {
      return this.ideFilePath;
    }

    public TextRangeDto getTextRange() {
      return this.textRange;
    }

    public String getAuthor() {
      return this.author;
    }

    public String getStatus() {
      return this.status;
    }

    @Nullable
    public String getResolution() {
      return this.resolution;
    }

    public HotspotRule getRule() {
      return this.rule;
    }

    public static class HotspotRule {
      private final String key;
      private final String name;
      private final String securityCategory;
      private final String vulnerabilityProbability;
      private final String riskDescription;
      private final String vulnerabilityDescription;
      private final String fixRecommendations;

      public HotspotRule(String key, String name, String securityCategory, String vulnerabilityProbability, String riskDescription,
        String vulnerabilityDescription, String fixRecommendations) {
        this.key = key;
        this.name = name;
        this.securityCategory = securityCategory;
        this.vulnerabilityProbability = vulnerabilityProbability;
        this.riskDescription = riskDescription;
        this.vulnerabilityDescription = vulnerabilityDescription;
        this.fixRecommendations = fixRecommendations;
      }

      public String getKey() {
        return this.key;
      }

      public String getName() {
        return this.name;
      }

      public String getSecurityCategory() {
        return this.securityCategory;
      }

      public String getVulnerabilityProbability() {
        return this.vulnerabilityProbability;
      }

      public String getRiskDescription() {
        return this.riskDescription;
      }

      public String getVulnerabilityDescription() {
        return this.vulnerabilityDescription;
      }

      public String getFixRecommendations() {
        return this.fixRecommendations;
      }
    }
  }

  @JsonNotification("sonarlint/showHotspot")
  void showHotspot(ShowHotspotParams showHotspotParams);

  @JsonNotification("sonarlint/showIssue")
  void showIssue(ShowAllLocationsCommand.Param showIssueParams);

  @JsonNotification("sonarlint/showIssueOrHotspot")
  void showIssueOrHotspot(ShowAllLocationsCommand.Param params);

  @JsonRequest("sonarlint/isIgnoredByScm")
  CompletableFuture<Boolean> isIgnoredByScm(String fileUri);

  class ShouldAnalyseFileCheckResult {
    boolean shouldBeAnalysed;
    String reason;

    public ShouldAnalyseFileCheckResult(boolean shouldBeAnalysed, @Nullable String reason) {
      this.shouldBeAnalysed = shouldBeAnalysed;
      this.reason = reason;
    }

    public boolean isShouldBeAnalysed() {
      return shouldBeAnalysed;
    }

    @CheckForNull
    public String getReason() {
      return reason;
    }
  }

  @JsonRequest("sonarlint/shouldAnalyseFile")
  CompletableFuture<ShouldAnalyseFileCheckResult> shouldAnalyseFile(SonarLintExtendedLanguageServer.UriParams fileUri);

  class FileUrisParams {
    Collection<String> fileUris;

    public FileUrisParams(Collection<String> fileUris) {
      this.fileUris = fileUris;
    }

    public Collection<String> getFileUris() {
      return fileUris;
    }
  }

  class FileUrisResult {
    Collection<String> fileUris;

    public FileUrisResult(Collection<String> fileUris) {
      this.fileUris = fileUris;
    }

    public Collection<String> getFileUris() {
      return fileUris;
    }
  }

  @JsonRequest("sonarlint/filterOutExcludedFiles")
  CompletableFuture<FileUrisResult> filterOutExcludedFiles(FileUrisParams params);

  @JsonNotification("sonarlint/maybeShowWiderLanguageSupportNotification")
  void maybeShowWiderLanguageSupportNotification(List<String> languageLabel);

  @JsonNotification("sonarlint/showNotificationForFirstSecretsIssue")
  void showFirstSecretDetectionNotification();

  class ShowRuleDescriptionParams {

    private static final String TAINT_RULE_REPO_SUFFIX = "security";
    @Expose
    private final String key;
    @Expose
    private final String name;
    @Expose
    private final String htmlDescription;
    @Expose
    private final RuleDescriptionTab[] htmlDescriptionTabs;
    @Expose
    private final String languageKey;
    @Expose
    private final boolean isTaint;
    @Expose
    private final RuleParameter[] parameters;
    @Expose
    @JsonAdapter(EitherStandardOrMQRAdapterFactory.class)
    private final Either<StandardModeDetails, MQRModeDetails> severityDetails;

    public ShowRuleDescriptionParams(String ruleKey, String ruleName, String htmlDescription, RuleDescriptionTab[] htmlDescriptionTabs,
      String languageKey, Collection<EffectiveRuleParamDto> params, Either<StandardModeDetails, MQRModeDetails> severityDetails) {
      this.key = ruleKey;
      this.name = ruleName;
      this.htmlDescription = htmlDescription;
      this.htmlDescriptionTabs = htmlDescriptionTabs;
      this.languageKey = languageKey;
      this.isTaint = ruleKey.contains(TAINT_RULE_REPO_SUFFIX);
      this.parameters = params.stream().map(p -> new RuleParameter(p.getName(), p.getDescription(), p.getDefaultValue())).toArray(RuleParameter[]::new);
      this.severityDetails = severityDetails;
    }

    public ShowRuleDescriptionParams(String ruleKey, String ruleName, String htmlDescription, RuleDescriptionTab[] htmlDescriptionTabs,
      String languageKey, Map<String, RuleParamDefinitionDto> params, Either<StandardModeDetails, MQRModeDetails> severityDetails) {
      this.key = ruleKey;
      this.name = ruleName;
      this.htmlDescription = htmlDescription;
      this.htmlDescriptionTabs = htmlDescriptionTabs;
      this.languageKey = languageKey;
      this.isTaint = ruleKey.contains(TAINT_RULE_REPO_SUFFIX);
      this.parameters = params.values().stream().map(v -> new RuleParameter(v.getName(), v.getDescription(), v.getDefaultValue())).toArray(RuleParameter[]::new);
      this.severityDetails = severityDetails;
    }

    public String getKey() {
      return key;
    }

    public String getLanguageKey() {
      return languageKey;
    }

    public String getName() {
      return name;
    }

    public String getType() {
      return severityDetails.isLeft() ?
        severityDetails.getLeft().getType() : null;
    }

    public String getSeverity() {
      return severityDetails.isLeft() ?
        severityDetails.getLeft().getSeverity() : null;
    }

    public boolean isTaint() {
      return isTaint;
    }

    public RuleParameter[] getParameters() {
      return parameters;
    }

    public String getHtmlDescription() {
      return htmlDescription;
    }

    public RuleDescriptionTab[] getHtmlDescriptionTabs() {
      return htmlDescriptionTabs;
    }

    public String getCleanCodeAttribute() {
      return severityDetails.isRight() ?
        severityDetails.getRight().getCleanCodeAttribute() : null;
    }

    public String getCleanCodeAttributeCategory() {
      return severityDetails.isRight() ?
        severityDetails.getRight().getCleanCodeAttributeCategory() : null;
    }

    public Map<String, String> getImpacts() {
      return severityDetails.isRight() ? severityDetails.getRight().getImpacts() : Map.of();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ShowRuleDescriptionParams that = (ShowRuleDescriptionParams) o;
      return isTaint == that.isTaint
        && Objects.equals(key, that.key)
        && Objects.equals(name, that.name)
        && Objects.equals(htmlDescription, that.htmlDescription)
        && Objects.equals(languageKey, that.languageKey)
        && Objects.equals(this.getCleanCodeAttribute(), that.getCleanCodeAttribute())
        && Objects.equals(this.getCleanCodeAttributeCategory(), that.getCleanCodeAttributeCategory())
        && Objects.equals(this.getImpacts(), that.getImpacts())
        && Arrays.equals(htmlDescriptionTabs, that.htmlDescriptionTabs)
        && Objects.equals(this.getType(), that.getType()) && Objects.equals(this.getSeverity(), that.getSeverity())
        && Arrays.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(key, name, htmlDescription, this.getType(), this.getSeverity(), isTaint, languageKey,
        this.getCleanCodeAttribute(), this.getCleanCodeAttributeCategory(), this.getImpacts());
      result = 31 * result + Arrays.hashCode(htmlDescriptionTabs);
      result = 31 * result + Arrays.hashCode(parameters);
      return result;
    }
  }

  class RuleDescriptionTab {

    @Expose
    private final String title;
    @Nullable
    @Expose
    private final RuleDescriptionTabNonContextual ruleDescriptionTabNonContextual;
    @Expose
    private final RuleDescriptionTabContextual[] ruleDescriptionTabContextual;
    @Expose
    private final boolean hasContextualInformation;
    @Expose
    private final String defaultContextKey;

    public RuleDescriptionTab(String title, RuleDescriptionTabContextual[] ruleDescriptionTabContextual, String defaultContextKey) {
      this.title = title;
      this.ruleDescriptionTabNonContextual = null;
      this.ruleDescriptionTabContextual = ruleDescriptionTabContextual;
      this.hasContextualInformation = true;
      this.defaultContextKey = defaultContextKey;
    }

    public RuleDescriptionTab(String title, RuleDescriptionTabNonContextual ruleDescriptionTabNonContextual) {
      this.title = title;
      this.ruleDescriptionTabContextual = new RuleDescriptionTabContextual[]{};
      this.ruleDescriptionTabNonContextual = ruleDescriptionTabNonContextual;
      this.hasContextualInformation = false;
      this.defaultContextKey = "";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RuleDescriptionTab that = (RuleDescriptionTab) o;
      return hasContextualInformation == that.hasContextualInformation
        && Objects.equals(title, that.title)
        && Objects.equals(ruleDescriptionTabNonContextual, that.ruleDescriptionTabNonContextual)
        && Arrays.equals(ruleDescriptionTabContextual, that.ruleDescriptionTabContextual)
        && Objects.equals(defaultContextKey, that.defaultContextKey);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(title, ruleDescriptionTabNonContextual, hasContextualInformation, defaultContextKey);
      result = 31 * result + Arrays.hashCode(ruleDescriptionTabContextual);
      return result;
    }

    public String getTitle() {
      return title;
    }

    public RuleDescriptionTabNonContextual getRuleDescriptionTabNonContextual() {
      return ruleDescriptionTabNonContextual;
    }

    public RuleDescriptionTabContextual[] getRuleDescriptionTabContextual() {
      return ruleDescriptionTabContextual;
    }

    public boolean hasContextualInformation() {
      return hasContextualInformation;
    }

    public String getDefaultContextKey() {
      return defaultContextKey;
    }
  }

  class RuleDescriptionTabContextual {
    @Expose
    private final String htmlContent;
    @Expose
    private final String contextKey;
    @Expose
    private final String displayName;

    public RuleDescriptionTabContextual(String htmlContent, String contextKey, String displayName) {
      this.htmlContent = htmlContent;
      this.contextKey = contextKey;
      this.displayName = displayName;
    }

    public String getHtmlContent() {
      return htmlContent;
    }

    public String getContextKey() {
      return contextKey;
    }

    public String getDisplayName() {
      return displayName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RuleDescriptionTabContextual that = (RuleDescriptionTabContextual) o;
      return Objects.equals(htmlContent, that.htmlContent) && Objects.equals(contextKey, that.contextKey) && Objects.equals(displayName,
        that.displayName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(htmlContent, contextKey, displayName);
    }
  }

  class RuleDescriptionTabNonContextual {
    @Expose
    private final String htmlContent;

    public RuleDescriptionTabNonContextual(String htmlContent) {
      this.htmlContent = htmlContent;
    }

    public String getHtmlContent() {
      return htmlContent;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RuleDescriptionTabNonContextual that = (RuleDescriptionTabNonContextual) o;
      return Objects.equals(htmlContent, that.htmlContent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(htmlContent);
    }
  }

  class FindFileByNamesInScopeResponse {
    private final List<FoundFileDto> foundFiles;

    public FindFileByNamesInScopeResponse(List<FoundFileDto> foundFiles) {
      this.foundFiles = foundFiles;
    }

    public List<FoundFileDto> getFoundFiles() {
      return foundFiles;
    }
  }

  class FoundFileDto {
    private final String fileName;
    private final String filePath;
    private final String content;

    public FoundFileDto(String fileName, String filePath, @Nullable String content) {
      this.fileName = fileName;
      this.filePath = filePath;
      this.content = content;
    }

    public String getFileName() {
      return fileName;
    }

    public String getFilePath() {
      return filePath;
    }

    @CheckForNull
    public String getContent() {
      return content;
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

  record CreateConnectionParams(boolean isSonarCloud, String serverUrlOrOrganisationKey, String token) {
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

  @JsonRequest("sonarlint/getTokenForServer")
  CompletableFuture<String> getTokenForServer(String serverUrlOrOrganization);

  @JsonNotification("sonarlint/publishSecurityHotspots")
  void publishSecurityHotspots(PublishDiagnosticsParams publishDiagnosticsParams);

  @JsonNotification("sonarlint/readyForTests")
  void readyForTests();

  class SslCertificateConfirmationParams {

    @Expose
    private final String issuedTo;
    @Expose
    private final String issuedBy;
    @Expose
    private final String validFrom;
    @Expose
    private final String validTo;
    @Expose
    private final String sha1Fingerprint;
    @Expose
    private final String sha256Fingerprint;
    @Expose
    private final String truststorePath;

    public SslCertificateConfirmationParams(String issuedTo, String issuedBy, String validFrom, String validTo,
      String sha1Fingerprint, String sha256Fingerprint, String truststorePath) {
      this.issuedTo = issuedTo;
      this.issuedBy = issuedBy;
      this.validFrom = validFrom;
      this.validTo = validTo;
      this.sha1Fingerprint = sha1Fingerprint;
      this.sha256Fingerprint = sha256Fingerprint;
      this.truststorePath = truststorePath;
    }

    public String getIssuedTo() {
      return issuedTo;
    }

    public String getIssuedBy() {
      return issuedBy;
    }

    public String getValidFrom() {
      return validFrom;
    }

    public String getValidTo() {
      return validTo;
    }

    public String getSha1Fingerprint() {
      return sha1Fingerprint;
    }

    public String getSha256Fingerprint() {
      return sha256Fingerprint;
    }

    public String getTruststorePath() {
      return truststorePath;
    }
  }

  @JsonRequest("sonarlint/askSslCertificateConfirmation")
  CompletableFuture<Boolean> askSslCertificateConfirmation(SslCertificateConfirmationParams params);

  class ShowSoonUnsupportedVersionMessageParams {

    public ShowSoonUnsupportedVersionMessageParams(String doNotShowAgainId, String text) {
      this.doNotShowAgainId = doNotShowAgainId;
      this.text = text;
    }

    @Expose
    private final String doNotShowAgainId;
    @Expose
    private final String text;

    public String getDoNotShowAgainId() {
      return doNotShowAgainId;
    }

    public String getText() {
      return text;
    }
  }

  @JsonNotification("sonarlint/showSoonUnsupportedVersionMessage")
  void showSoonUnsupportedVersionMessage(ShowSoonUnsupportedVersionMessageParams messageParams);

  class SubmitNewCodeDefinitionParams {

    String folderUri;
    String newCodeDefinitionOrMessage;
    boolean isSupported;

    public SubmitNewCodeDefinitionParams(String folderUri, String newCodeDefinitionOrMessage, boolean isSupported) {
      this.folderUri = folderUri;
      this.newCodeDefinitionOrMessage = newCodeDefinitionOrMessage;
      this.isSupported = isSupported;
    }

    public String getFolderUri() {
      return folderUri;
    }

    public String getNewCodeDefinitionOrMessage() {
      return newCodeDefinitionOrMessage;
    }

    public boolean isSupported() {
      return isSupported;
    }
  }

  class FolderUriParams {
    String folderUri;

    public FolderUriParams(String folderUri) {
      this.folderUri = folderUri;
    }

    public String getFolderUri() {
      return folderUri;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      FolderUriParams that = (FolderUriParams) o;
      return Objects.equals(folderUri, that.folderUri);
    }

    @Override
    public int hashCode() {
      return Objects.hash(folderUri);
    }
  }

  @JsonNotification("sonarlint/submitNewCodeDefinition")
  void submitNewCodeDefinition(SubmitNewCodeDefinitionParams params);
}
