/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.util.Preconditions;
import org.eclipse.xtext.xbase.lib.Pure;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.GetSupportedFilePatternsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.binding.GetBindingSuggestionParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.GetBindingSuggestionsResponse;

public interface SonarLintExtendedLanguageServer extends LanguageServer {

  @JsonRequest("sonarlint/listAllRules")
  CompletableFuture<Map<String, List<Rule>>> listAllRules();

  @JsonRequest("sonarlint/checkConnection")
  CompletableFuture<SonarLintExtendedLanguageClient.ConnectionCheckResult> checkConnection(ConnectionCheckParams connectionId);

  class ConnectionCheckParams {
    private String connectionId;

    public ConnectionCheckParams(String connectionId) {
      this.connectionId = connectionId;
    }

    public String getConnectionId() {
      return connectionId;
    }

    public void setConnectionId(String connectionId) {
      this.connectionId = connectionId;
    }
  }

  @JsonRequest("sonarlint/getRemoteProjectsForConnection")
  CompletableFuture<Map<String, String>> getRemoteProjectsForConnection(GetRemoteProjectsForConnectionParams getRemoteProjectsForConnectionParams);

  class GetRemoteProjectsForConnectionParams {
    private String connectionId;

    public GetRemoteProjectsForConnectionParams(String connectionId) {
      this.connectionId = connectionId;
    }

    public String getConnectionId() {
      return connectionId;
    }

    public void setConnectionId(String connectionId) {
      this.connectionId = connectionId;
    }
  }

  class DidClasspathUpdateParams {

    @NonNull
    private String projectUri;

    public DidClasspathUpdateParams() {
    }

    public DidClasspathUpdateParams(@NonNull final String projectUri) {
      this.projectUri = Preconditions.<String>checkNotNull(projectUri, "projectUri");
    }

    @Pure
    @NonNull
    public String getProjectUri() {
      return projectUri;
    }

    public void setProjectUri(@NonNull String projectUri) {
      this.projectUri = Preconditions.checkNotNull(projectUri, "projectUri");
    }

  }

  @JsonNotification("sonarlint/didClasspathUpdate")
  void didClasspathUpdate(DidClasspathUpdateParams params);

  /**
   * Possible server modes for the <code>redhat.vscode-java</code> Language Server
   * https://github.com/redhat-developer/vscode-java/blob/5642bf24b89202acf3911fe7a162b6dbcbeea405/src/settings.ts#L198
   */
  enum ServerMode {
    LIGHTWEIGHT("LightWeight"),
    HYBRID("Hybrid"),
    STANDARD("Standard");

    private final String serializedForm;

    ServerMode(String serializedForm) {
      this.serializedForm = serializedForm;
    }

    static ServerMode of(String serializedForm) {
      return Stream.of(values())
        .filter(m -> m.serializedForm.equals(serializedForm))
        .findFirst()
        .orElse(ServerMode.LIGHTWEIGHT);
    }
  }

  class DidJavaServerModeChangeParams {

    @NonNull
    private String serverMode;

    public DidJavaServerModeChangeParams() {
    }

    public DidJavaServerModeChangeParams(@NonNull final String serverMode) {
      this.serverMode = Preconditions.<String>checkNotNull(serverMode, "serverMode");
    }

    @Pure
    @NonNull
    public String getServerMode() {
      return serverMode;
    }

    public void setServerMode(@NonNull String serverMode) {
      this.serverMode = Preconditions.checkNotNull(serverMode, "serverMode");
    }

  }

  @JsonNotification("sonarlint/didJavaServerModeChange")
  void didJavaServerModeChange(DidJavaServerModeChangeParams params);

  class DidLocalBranchNameChangeParams {
    private String folderUri;
    @Nullable
    private String branchName;

    public DidLocalBranchNameChangeParams(String folderUri, @Nullable String branchName) {
      setFolderUri(folderUri);
      setBranchName(branchName);
    }

    public String getFolderUri() {
      return folderUri;
    }

    @CheckForNull
    public String getBranchName() {
      return branchName;
    }

    public void setBranchName(@Nullable String branchName) {
      this.branchName = branchName;
    }

    public void setFolderUri(String folderUri) {
      this.folderUri = folderUri;
    }
  }

  @JsonNotification("sonarlint/didLocalBranchNameChange")
  void didLocalBranchNameChange(DidLocalBranchNameChangeParams params);

  @JsonNotification("sonarlint/onTokenUpdate")
  void onTokenUpdate();

  class GetRemoteProjectsNamesParams {
    private String connectionId;
    private List<String> projectKeys;

    public GetRemoteProjectsNamesParams(String connectionId, List<String> projectKeys) {
      setConnectionId(connectionId);
      setProjectKeys(projectKeys);
    }

    public List<String> getProjectKeys() {
      return projectKeys;
    }

    public void setProjectKeys(List<String> projectKeys) {
      this.projectKeys = projectKeys;
    }

    public String getConnectionId() {
      return connectionId;
    }

    public void setConnectionId(String connectionId) {
      this.connectionId = connectionId;
    }
  }

  @JsonRequest("sonarlint/getRemoteProjectNames")
  CompletableFuture<Map<String, String>> getRemoteProjectNames(GetRemoteProjectsNamesParams params);

  class GetServerPathForTokenGenerationResponse {
    private String serverUrl;

    public GetServerPathForTokenGenerationResponse(String serverUrl) {
      setServerUrl(serverUrl);
    }

    public String getServerUrl() {
      return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
      this.serverUrl = serverUrl;
    }

  }

  class GetServerPathForTokenGenerationParams {
    String baseServerUrl;

    public GetServerPathForTokenGenerationParams(String baseServerUrl) {
      setBaseServerUrl(baseServerUrl);
    }

    public String getBaseServerUrl() {
      return baseServerUrl;
    }

    public void setBaseServerUrl(String baseServerUrl) {
      this.baseServerUrl = baseServerUrl;
    }
  }

  @JsonRequest("sonarlint/getServerPathForTokenGeneration")
  CompletableFuture<GetServerPathForTokenGenerationResponse> getServerPathForTokenGeneration(GetServerPathForTokenGenerationParams params);

  class ShowHotspotLocationsParams {
    String hotspotKey;
    String fileUri;

    public ShowHotspotLocationsParams(String hotspotKey) {
      setHotspotKey(hotspotKey);
    }

    public String getHotspotKey() {
      return hotspotKey;
    }

    public void setHotspotKey(String hotspotKey) {
      this.hotspotKey = hotspotKey;
    }
  }

  @JsonRequest("sonarlint/showHotspotLocations")
  CompletableFuture<Void> showHotspotLocations(ShowHotspotLocationsParams hotspotKey);

  class OpenHotspotInBrowserLsParams {
    private String hotspotId;
    private String fileUri;

    public OpenHotspotInBrowserLsParams(String hotspotId, String workspaceFolder) {
      this.hotspotId = hotspotId;
      this.fileUri = workspaceFolder;
    }

    public String getHotspotId() {
      return hotspotId;
    }

    public void setHotspotId(String hotspotId) {
      this.hotspotId = hotspotId;
    }

    public String getFileUri() {
      return fileUri;
    }

    public void setFileUri(String fileUri) {
      this.fileUri = fileUri;
    }
  }

  @JsonNotification("sonarlint/openHotspotInBrowser")
  void openHotspotInBrowser(OpenHotspotInBrowserLsParams params);

  class ShowHotspotRuleDescriptionParams {
    String ruleKey;
    String hotspotId;
    String fileUri;

    public ShowHotspotRuleDescriptionParams(String ruleKey, String hotspotId) {
      setRuleKey(ruleKey);
      setHotspotId(hotspotId);
    }

    public String getHotspotId() {
      return hotspotId;
    }

    public void setRuleKey(String ruleKey) {
      this.ruleKey = ruleKey;
    }

    public void setHotspotId(String hotspotId) {
      this.hotspotId = hotspotId;
    }

    public void setFileUri(String fileUri) {
      this.fileUri = fileUri;
    }
  }

  @JsonNotification("sonarlint/showHotspotRuleDescription")
  CompletableFuture<Void> showHotspotRuleDescription(ShowHotspotRuleDescriptionParams params);

  class HelpAndFeedbackLinkClickedNotificationParams {
    String id;

    public HelpAndFeedbackLinkClickedNotificationParams(String id) {
      this.id = id;
    }

    public String getId() {
      return id;
    }
  }

  @JsonNotification("sonarlint/helpAndFeedbackLinkClicked")
  CompletableFuture<Void> helpAndFeedbackLinkClicked(HelpAndFeedbackLinkClickedNotificationParams params);

  class ScanFolderForHotspotsParams {
    private final String folderUri;

    List<TextDocumentItem> documents;

    public ScanFolderForHotspotsParams(String folderUri, List<TextDocumentItem> documents) {
      this.folderUri = folderUri;
      this.documents = documents;
    }

    public String getFolderUri() {
      return folderUri;
    }

    public List<TextDocumentItem> getDocuments() {
      return documents;
    }
  }

  @JsonNotification("sonarlint/scanFolderForHotspots")
  CompletableFuture<Void> scanFolderForHotspots(ScanFolderForHotspotsParams params);

  @JsonNotification("sonarlint/forgetFolderHotspots")
  CompletableFuture<Void> forgetFolderHotspots();

  class FolderUriParams {
    private final String folderUri;

    public FolderUriParams(String folderUri) {
      this.folderUri = folderUri;
    }

    public String getFolderUri() {
      return folderUri;
    }
  }

  @JsonRequest("sonarlint/listSupportedFilePatterns")
  CompletableFuture<GetSupportedFilePatternsResponse> getFilePatternsForAnalysis(FolderUriParams params);

  @JsonRequest("sonarlint/getBindingSuggestion")
  CompletableFuture<GetBindingSuggestionsResponse> getBindingSuggestion(GetBindingSuggestionParams params);

  class CheckLocalDetectionSupportedResponse {
    boolean isSupported;
    @Nullable
    String reason;

    public CheckLocalDetectionSupportedResponse(boolean isSupported, @Nullable String reason) {
      this.isSupported = isSupported;
      this.reason = reason;
    }

    public boolean isSupported() {
      return isSupported;
    }

    @CheckForNull
    public String getReason() {
      return reason;
    }
  }

  @JsonRequest("sonarlint/checkLocalDetectionSupported")
  CompletableFuture<CheckLocalDetectionSupportedResponse> checkLocalDetectionSupported(FolderUriParams params);

}
