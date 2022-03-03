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
import java.util.concurrent.Executors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.vcs.GitUtils;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;

public class WorkspaceFolderBranchManager implements WorkspaceFolderLifecycleListener {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Map<URI, String> localBranchNameByFolderUri = new ConcurrentHashMap<>();
  private final Map<URI, String> referenceBranchNameByFolderUri = new ConcurrentHashMap<>();
  private final SonarLintExtendedLanguageClient client;
  private final ProjectBindingManager bindingManager;

  public WorkspaceFolderBranchManager(SonarLintExtendedLanguageClient client, ProjectBindingManager bindingManager) {
    this.client = client;
    this.bindingManager = bindingManager;
  }

  @Override
  public void added(WorkspaceFolderWrapper added) {
    var folderUri = added.getUri();
    client.getBranchNameForFolder(folderUri.toString())
      .thenAccept(branchName -> didBranchNameChange(folderUri, branchName));
  }

  @Override
  public void removed(WorkspaceFolderWrapper removed) {
    localBranchNameByFolderUri.remove(removed.getUri());
  }

  public void didBranchNameChange(URI folderUri, @Nullable String branchName) {
    if (branchName != null) {
      LOG.debug("Folder {} is now on branch {}.", folderUri, branchName);
    } else {
      LOG.debug("Folder {} is now on an unknown branch.", folderUri);
      return;
    }
    localBranchNameByFolderUri.put(folderUri, branchName);
    var executorService = Executors.newSingleThreadExecutor();
    executorService.submit(() -> {
      Optional<ProjectBindingWrapper> bindingOptional = bindingManager.getBinding(folderUri);
      String electedBranchName = null;
      if (bindingOptional.isPresent()) {
        ProjectBindingWrapper binding = bindingOptional.get();
        var serverBranches = binding.getEngine().getServerBranches(binding.getBinding().projectKey());
        var serverBranchNames = serverBranches.getBranchNames();
        var repo = GitUtils.getRepositoryForDir(Paths.get(folderUri));
        if (repo != null) {
          try (var repoToClose = repo) {
            electedBranchName = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverBranchNames, serverBranches.getMainBranchName().orElse(null));
          }
        }
      }
      client.setReferenceBranchNameForFolder(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.of(folderUri.toString(), electedBranchName));
      referenceBranchNameByFolderUri.put(folderUri, electedBranchName);
    });
  }

  /**
   * @param folderUri a workspace folder's URI
   * @return the current known local branch name for the folder, <code>null</code> if unknown
   */
  @CheckForNull
  public String getLocalBranchNameForFolder(URI folderUri) {
    return localBranchNameByFolderUri.get(folderUri);
  }

  /**
   * @param folderUri a workspace folder's URI
   * @return the current known reference branch name for the folder, <code>null</code> if unknown or not in connected mode
   */
  @CheckForNull
  public String getReferenceBranchNameForFolder(URI folderUri) {
    try {
      var uriWithoutTrailingSlash = StringUtils.removeEnd(folderUri.toString(), "/");
      return referenceBranchNameByFolderUri.get(new URI(uriWithoutTrailingSlash));
    } catch (URISyntaxException e) {
      LOG.error(e.getMessage());
    }
    return null;
  }
}
