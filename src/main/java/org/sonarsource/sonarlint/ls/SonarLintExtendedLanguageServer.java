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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.util.Preconditions;
import org.eclipse.xtext.xbase.lib.Pure;

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
}
