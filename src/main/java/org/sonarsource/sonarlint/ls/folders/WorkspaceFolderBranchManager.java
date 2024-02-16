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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.util.GitUtils;
import org.sonarsource.sonarlint.ls.util.Utils;

public class WorkspaceFolderBranchManager implements WorkspaceFolderLifecycleListener {
  private final ExecutorService executorService;
  private final LanguageClientLogOutput logOutput;
  private final BackendServiceFacade backendServiceFacade;

  public WorkspaceFolderBranchManager(BackendServiceFacade backendServiceFacade,
    LanguageClientLogOutput logOutput) {
    this(backendServiceFacade, Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint Language Server Branch Manager",
      false)), logOutput);
  }

  WorkspaceFolderBranchManager(BackendServiceFacade backendServiceFacade,
    ExecutorService executorService, LanguageClientLogOutput logOutput) {
    this.backendServiceFacade = backendServiceFacade;
    this.executorService = executorService;
    this.logOutput = logOutput;
  }

  @Override
  public void added(WorkspaceFolderWrapper added) {
    var folderUri = added.getUri();
    backendServiceFacade.getBackendService().notifyBackendOnVscChange(folderUri.toString());
  }

  public void shutdown() {
    Utils.shutdownAndAwait(executorService, true);
  }

  public String matchSonarProjectBranch(String folderUri, String mainBranchName, Set<String> allBranchesNames, CancelChecker cancelChecker) {
    var repo = GitUtils.getRepositoryForDir(Paths.get(URI.create(folderUri)), logOutput);
    String electedBranchName = null;
    if (repo != null) {
      try (repo) {
        electedBranchName = GitUtils.electBestMatchingServerBranchForCurrentHead(repo, allBranchesNames, mainBranchName, logOutput);
      }
    }
    if (electedBranchName == null) {
      electedBranchName = mainBranchName;
    }
    return electedBranchName;
  }
}
