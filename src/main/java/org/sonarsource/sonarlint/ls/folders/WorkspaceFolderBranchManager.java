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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.branch.GitUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchResponse;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.util.Utils;

public class WorkspaceFolderBranchManager implements WorkspaceFolderLifecycleListener {
  private final Map<URI, Optional<String>> referenceBranchNameByFolderUri = new ConcurrentHashMap<>();
  private final SonarLintExtendedLanguageClient client;
  private final ProjectBindingManager bindingManager;
  private final ExecutorService executorService;
  private final LanguageClientLogOutput logOutput;
  private final BackendServiceFacade backendServiceFacade;

  public WorkspaceFolderBranchManager(SonarLintExtendedLanguageClient client, ProjectBindingManager bindingManager,
    BackendServiceFacade backendServiceFacade, LanguageClientLogOutput logOutput) {
    this(client, bindingManager, backendServiceFacade,
      Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint Language Server Branch Manager", false)), logOutput);
  }

  WorkspaceFolderBranchManager(SonarLintExtendedLanguageClient client, ProjectBindingManager bindingManager,
    BackendServiceFacade backendServiceFacade, ExecutorService executorService, LanguageClientLogOutput logOutput) {
    this.client = client;
    this.bindingManager = bindingManager;
    this.backendServiceFacade = backendServiceFacade;
    this.executorService = executorService;
    this.logOutput = logOutput;
  }

  @Override
  public void added(WorkspaceFolderWrapper added) {
    var folderUri = added.getUri();
    backendServiceFacade.notifyBackendOnVscChange(folderUri.toString());
  }

  /**
   * @param folderUri a workspace folder's URI
   * @return the current known reference branch name for the folder, or empty if unknown or not in connected mode
   */
  public Optional<String> getReferenceBranchNameForFolder(@Nullable URI folderUri) {
    if (folderUri == null) {
      return Optional.empty();
    }
    try {
      var uriWithoutTrailingSlash = StringUtils.removeEnd(folderUri.toString(), "/");
      return referenceBranchNameByFolderUri.getOrDefault(new URI(uriWithoutTrailingSlash), Optional.empty());
    } catch (URISyntaxException e) {
      logOutput.error(e.getMessage());
      return Optional.empty();
    }
  }

  public void shutdown() {
    Utils.shutdownAndAwait(executorService, true);
  }

  public String matchSonarProjectBranch(String folderUri, String mainBranchName, Set<String> allBranchesNames, CancelChecker cancelChecker) {
    var repo = GitUtils.getRepositoryForDir(Paths.get(folderUri));
    String electedBranchName = null;
    if (repo != null) {
      try (repo) {
        electedBranchName = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, allBranchesNames, mainBranchName);
      }
    }
    if (electedBranchName == null) {
      electedBranchName = mainBranchName;
    }
    return electedBranchName;
  }
}
