/*
 * SonarLint Language Server
 * Copyright (C) 2009-2021 SonarSource SA
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
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

public class WorkspaceFolderBranchManager implements WorkspaceFolderLifecycleListener {

  private static final Logger LOG = Loggers.get(WorkspaceFolderBranchManager.class);

  private final Map<URI, String> branchNameByFolderUri;
  private final SonarLintExtendedLanguageClient client;

  public WorkspaceFolderBranchManager(SonarLintExtendedLanguageClient client) {
    this.branchNameByFolderUri = new HashMap<>();
    this.client = client;
  }

  @Override
  public void added(WorkspaceFolderWrapper added) {
    var folderUri = added.getUri();
    client.getBranchNameForFolder(folderUri.toString())
      .thenAccept(branchName -> didBranchNameChange(folderUri, branchName));
  }

  @Override
  public void removed(WorkspaceFolderWrapper removed) {
    branchNameByFolderUri.remove(removed.getUri());
  }

  public void didBranchNameChange(URI folderUri, @Nullable String branchName) {
    if (branchName != null) {
      LOG.debug("Folder {} is now on branch {}.", folderUri, branchName);
    } else {
      LOG.debug("Folder {} is now on an unknown branch.", folderUri);
    }
    branchNameByFolderUri.put(folderUri, branchName);
  }

  /**
   * @param folderUri a workspace folder's URI
   * @return the current known local branch name for the folder, <code>null</code> if unknown
   */
  @CheckForNull
  public String getLocalBranchNameForFolder(URI folderUri) {
    return branchNameByFolderUri.get(folderUri);
  }
}
