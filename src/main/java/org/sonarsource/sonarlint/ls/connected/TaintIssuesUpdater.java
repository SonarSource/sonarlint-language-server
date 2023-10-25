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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.domain.TaintIssue;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.util.FileUtils;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static org.sonarsource.sonarlint.ls.util.Utils.pluralize;

public class TaintIssuesUpdater {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ProjectBindingManager bindingManager;
  private final SettingsManager settingsManager;
  private final DiagnosticPublisher diagnosticPublisher;
  private final ExecutorService asyncExecutor;
  private final BackendServiceFacade backendServiceFacade;

  public TaintIssuesUpdater(ProjectBindingManager bindingManager, TaintVulnerabilitiesCache taintVulnerabilitiesCache,
    WorkspaceFoldersManager workspaceFoldersManager, SettingsManager settingsManager, DiagnosticPublisher diagnosticPublisher,
    BackendServiceFacade backendServiceFacade) {
    this(bindingManager, taintVulnerabilitiesCache, workspaceFoldersManager, settingsManager, diagnosticPublisher,
      Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint Language Server Analysis Scheduler", false)), backendServiceFacade);
  }

  TaintIssuesUpdater(ProjectBindingManager bindingManager, TaintVulnerabilitiesCache taintVulnerabilitiesCache, WorkspaceFoldersManager workspaceFoldersManager,
    SettingsManager settingsManager, DiagnosticPublisher diagnosticPublisher, ExecutorService asyncExecutor, BackendServiceFacade backendServiceFacade) {
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.settingsManager = settingsManager;
    this.bindingManager = bindingManager;
    this.diagnosticPublisher = diagnosticPublisher;
    this.asyncExecutor = asyncExecutor;
    this.backendServiceFacade = backendServiceFacade;
  }

  public void updateTaintIssuesAsync(URI fileUri) {
    asyncExecutor.submit(() -> updateTaintIssues(fileUri));
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
    var folderUri = folderForFile.get().getUri();

    var binding = bindingWrapper.getBinding();
    var engine = bindingWrapper.getEngine();
    var branchName = bindingManager.resolveBranchNameForFolder(folderUri, engine, binding.projectKey());
    var connectionId = bindingWrapper.getConnectionId();
    var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
    var endpointParams = connectionSettings.getEndpointParams();
    var httpClient = backendServiceFacade.getHttpClient(connectionId);

    // download taints
    var sqFilePath = FileUtils.toSonarQubePath(FileUtils.getFileRelativePath(Paths.get(folderUri), fileUri));
    engine.downloadAllServerTaintIssuesForFile(endpointParams, httpClient, binding, sqFilePath, branchName, null);
    var serverIssues = engine.getServerTaintIssues(binding, branchName, sqFilePath, false);

    // reload cache
    taintVulnerabilitiesCache.reload(fileUri, TaintIssue.from(serverIssues, connectionSettings.isSonarCloudAlias()));
    long foundVulnerabilities = taintVulnerabilitiesCache.getAsDiagnostics(fileUri, diagnosticPublisher.isFocusOnNewCode()).count();
    if (foundVulnerabilities > 0) {
      LOG.info(format("Fetched %s %s from %s", foundVulnerabilities,
        pluralize(foundVulnerabilities, "vulnerability", "vulnerabilities"), connectionId));
    }
    diagnosticPublisher.publishDiagnostics(fileUri, false);
  }

  public void shutdown() {
    Utils.shutdownAndAwait(asyncExecutor, true);
  }
}
