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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static org.sonarsource.sonarlint.ls.util.Utils.pluralize;

public class TaintIssuesUpdater {
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ProjectBindingManager bindingManager;
  private final DiagnosticPublisher diagnosticPublisher;
  private final ExecutorService asyncExecutor;
  private final LanguageClientLogger logOutput;

  public TaintIssuesUpdater(ProjectBindingManager bindingManager, TaintVulnerabilitiesCache taintVulnerabilitiesCache,
    WorkspaceFoldersManager workspaceFoldersManager, DiagnosticPublisher diagnosticPublisher, LanguageClientLogger logOutput) {
    this(bindingManager, taintVulnerabilitiesCache, workspaceFoldersManager, diagnosticPublisher,
      Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint Language Server Analysis Scheduler", false)), logOutput);
  }

  TaintIssuesUpdater(ProjectBindingManager bindingManager, TaintVulnerabilitiesCache taintVulnerabilitiesCache,
    WorkspaceFoldersManager workspaceFoldersManager, DiagnosticPublisher diagnosticPublisher, ExecutorService asyncExecutor,
    LanguageClientLogger logOutput) {
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.bindingManager = bindingManager;
    this.diagnosticPublisher = diagnosticPublisher;
    this.asyncExecutor = asyncExecutor;
    this.logOutput = logOutput;
  }

  public void updateTaintIssuesAsync(URI fileUri) {
    workspaceFoldersManager.findFolderForFile(fileUri)
      .ifPresent(workspaceFolderWrapper -> {
        if (workspaceFoldersManager.isReadyForAnalysis(workspaceFolderWrapper.getUri().toString())) {
          asyncExecutor.submit(() -> updateTaintIssues(fileUri));
        }
      });
  }

  private void updateTaintIssues(URI fileUri) {
    var bindingWrapperOptional = bindingManager.getBinding(fileUri);

    if (bindingWrapperOptional.isEmpty()) {
      return;
    }
    var folderForFile = workspaceFoldersManager.findFolderForFile(fileUri);
    if (folderForFile.isEmpty()) {
      return;
    }
    var bindingWrapper = bindingWrapperOptional.get();
    var connectionId = bindingWrapper.connectionId();
    long foundVulnerabilities = taintVulnerabilitiesCache.getAsDiagnostics(fileUri, diagnosticPublisher.isFocusOnNewCode()).count();
    if (foundVulnerabilities > 0) {
      logOutput.info(format("Fetched %s %s from %s", foundVulnerabilities,
        pluralize(foundVulnerabilities, "vulnerability", "vulnerabilities"), connectionId));
    }
    diagnosticPublisher.publishDiagnostics(fileUri, true);
  }

  public void shutdown() {
    Utils.shutdownAndAwait(asyncExecutor, true);
  }
}
