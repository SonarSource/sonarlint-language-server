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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.vcs.GitUtils;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.sync.ServerSynchronizer;
import org.sonarsource.sonarlint.ls.util.Utils;

public class WorkspaceFolderBranchManager implements WorkspaceFolderLifecycleListener {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String MASTER_BRANCH = "master";

  private final Map<URI, Optional<String>> referenceBranchNameByFolderUri = new ConcurrentHashMap<>();
  private final SonarLintExtendedLanguageClient client;
  private final ProjectBindingManager bindingManager;
  private final ServerSynchronizer serverSynchronizer;
  private final ExecutorService executorService;

  public WorkspaceFolderBranchManager(SonarLintExtendedLanguageClient client, ProjectBindingManager bindingManager, ServerSynchronizer serverSynchronizer) {
    this(client, bindingManager, serverSynchronizer, Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint Language Server Branch Manager", false)));
  }

  WorkspaceFolderBranchManager(SonarLintExtendedLanguageClient client, ProjectBindingManager bindingManager, ServerSynchronizer serverSynchronizer,
    ExecutorService executorService) {
    this.client = client;
    this.bindingManager = bindingManager;
    this.serverSynchronizer = serverSynchronizer;
    this.executorService = executorService;
  }

  @Override
  public void added(WorkspaceFolderWrapper added) {
    var folderUri = added.getUri();
    client.getBranchNameForFolder(folderUri.toString())
      .thenAccept(branchName -> didBranchNameChange(folderUri, branchName));
  }

  public void didBranchNameChange(URI folderUri, @Nullable String branchName) {
    if (branchName != null) {
      LOG.debug("Folder {} is now on branch {}.", folderUri, branchName);
    } else {
      LOG.debug("Folder {} is now on an unknown branch.", folderUri);
      return;
    }
    executorService.submit(() -> {
      Optional<ProjectBindingWrapper> bindingOptional = bindingManager.getBinding(folderUri);
      String electedBranchName = null;
      if (bindingOptional.isPresent()) {
        ProjectBindingWrapper binding = bindingOptional.get();
        var serverBranches = binding.getEngine().getServerBranches(binding.getBinding().projectKey());
        var serverBranchNames = serverBranches.getBranchNames();
        var repo = GitUtils.getRepositoryForDir(Paths.get(folderUri));
        if (repo != null) {
          try (repo) {
            electedBranchName = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverBranchNames, serverBranches.getMainBranchName());
          }
        }
        serverSynchronizer.syncIssues(binding, electedBranchName != null ? electedBranchName : MASTER_BRANCH);
      }
      client.setReferenceBranchNameForFolder(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.of(folderUri.toString(), electedBranchName));
      referenceBranchNameByFolderUri.put(folderUri, Optional.ofNullable(electedBranchName));
    });
  }

  /**
   * @param folderUri a workspace folder's URI
   * @return the current known reference branch name for the folder, <code>master</code> if unknown or not in connected mode
   */
  public String getReferenceBranchNameForFolder(URI folderUri) {
    try {
      var uriWithoutTrailingSlash = StringUtils.removeEnd(folderUri.toString(), "/");
      return referenceBranchNameByFolderUri.getOrDefault(new URI(uriWithoutTrailingSlash), Optional.empty()).orElse(MASTER_BRANCH);
    } catch (URISyntaxException e) {
      LOG.error(e.getMessage());
    }
    return MASTER_BRANCH;
  }

  public void shutdown() {
    Utils.shutdownAndAwait(executorService, true);
  }
}
