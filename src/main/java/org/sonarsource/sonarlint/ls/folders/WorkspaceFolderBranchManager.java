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

import java.net.URISyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.serverapi.branches.ServerBranch;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.git.GitUtils;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class WorkspaceFolderBranchManager implements WorkspaceFolderLifecycleListener {

  private static final Logger LOG = Loggers.get(WorkspaceFolderBranchManager.class);

  private final Map<URI, String> localBranchNameByFolderUri;
  private final Map<URI, String> referenceBranchNameByFolderUri;
  private final SonarLintExtendedLanguageClient client;
  private final ProjectBindingManager bindingManager;

  public WorkspaceFolderBranchManager(SonarLintExtendedLanguageClient client, ProjectBindingManager bindingManager) {
    this.localBranchNameByFolderUri = new HashMap<>();
    this.referenceBranchNameByFolderUri = new HashMap<>();
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
          var configuration = bindingManager.getServerConfigurationFor(binding.getConnectionId());
          if (configuration != null) {
            var serverBranches = binding.getEngine().getServerBranches(configuration.getEndpointParams(), configuration.getHttpClient(), binding.getBinding());
            var serverBranchNames = serverBranches.stream().map(ServerBranch::getName).collect(Collectors.toSet());
            var git = GitUtils.getGitForDir(folderUri);
            if (serverBranchNames.contains(branchName)) {
              electedBranchName = branchName;
            } else if (git != null) {
              Optional<String> sqBranchNameOptional = GitUtils.electSQBranchForLocalBranch(branchName, git, serverBranches);
              electedBranchName = sqBranchNameOptional.orElse(null);
            }
          } else {
            LOG.error("Unable to get server endpoint parameters for binding " + binding.getConnectionId());
          }
        }
        client.setReferenceBranchNameForFolder(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.of(folderUri.toString(), electedBranchName));
        referenceBranchNameByFolderUri.put(folderUri, electedBranchName);
      }
    );
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
