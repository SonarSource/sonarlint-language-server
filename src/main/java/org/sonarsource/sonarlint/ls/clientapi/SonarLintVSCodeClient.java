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
package org.sonarsource.sonarlint.ls.clientapi;

import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

public class SonarLintVSCodeClient implements SonarLintClient {

  private final SonarLintExtendedLanguageClient client;

  public SonarLintVSCodeClient(SonarLintExtendedLanguageClient client) {
    this.client = client;
  }

  @Override
  public void suggestBinding(SuggestBindingParams params) {
    // NOOP
  }

  @Override
  public CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInScope(FindFileByNamesInScopeParams params) {
    return null;
  }

  @Nullable
  @Override
  public HttpClient getHttpClient(String s) {
    return null;
  }

  @Nullable
  @Override
  public HttpClient getHttpClientNoAuth(String s) {
    return null;
  }

  @Override
  public void openUrlInBrowser(OpenUrlInBrowserParams params) {
    client.browseTo(params.getUrl());
  }

  @Override
  public void showMessage(org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams params) {
    // NOOP
  }

  @Override
  public CompletableFuture<org.sonarsource.sonarlint.core.clientapi.client.host.GetHostInfoResponse> getHostInfo() {
    return null;
  }

  @Override
  public void showHotspot(org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams params) {
    // NOOP
  }

  @Override
  public CompletableFuture<org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse>
      assistCreatingConnection(org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams params) {
    return null;
  }

  @Override
  public CompletableFuture<org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse>
      assistBinding(org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams params) {
    return null;
  }
}
