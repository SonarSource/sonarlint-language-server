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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.util.FileUtils;

import static java.lang.String.format;
import static org.sonarsource.sonarlint.ls.util.Utils.pluralize;

public class TaintIssuesUpdater {

  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ProjectBindingManager bindingManager;
  private final LanguageClientLogger lsLogOutput;
  private final SettingsManager settingsManager;

  public TaintIssuesUpdater(ProjectBindingManager bindingManager, TaintVulnerabilitiesCache taintVulnerabilitiesCache,
    WorkspaceFoldersManager workspaceFoldersManager, LanguageClientLogger lsLogOutput, SettingsManager settingsManager) {
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.settingsManager = settingsManager;
    this.bindingManager = bindingManager;
    this.lsLogOutput = lsLogOutput;
  }

  public void updateTaintIssues(URI fileUri) {
    var bindingWrapperOptional = bindingManager.getBinding(fileUri);

    if (bindingWrapperOptional.isEmpty()) {
      return;
    }
    var folderForFile = workspaceFoldersManager.findFolderForFile(fileUri);
    if (folderForFile.isEmpty()) {
      return;
    }
    var bindingWrapper = bindingWrapperOptional.get();

    var binding = bindingWrapper.getBinding();
    var engine = bindingWrapper.getEngine();
    var branchName = bindingManager.resolveBranchNameForFolder(fileUri);
    if (StringUtils.isEmpty(branchName)) {
      branchName = "master";
    }
    var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(bindingWrapper.getConnectionId());
    var serverConfiguration = connectionSettings.getServerConfiguration();

    // sync taints
    engine.syncServerTaintIssues(serverConfiguration.getEndpointParams(),
      serverConfiguration.getHttpClient(), binding.projectKey(), branchName, null);

    // download taints
    var folderUri = folderForFile.get().getUri();
    var sqFilePath = FileUtils.toSonarQubePath(FileUtils.getFileRelativePath(Paths.get(folderUri), fileUri, lsLogOutput));
    engine.downloadAllServerTaintIssuesForFile(serverConfiguration.getEndpointParams(),
      serverConfiguration.getHttpClient(), binding,
      sqFilePath, branchName, null);
    var serverIssues = engine.getServerTaintIssues(binding, branchName, sqFilePath);

    // reload cache
    taintVulnerabilitiesCache.reload(fileUri, serverIssues);
    long foundVulnerabilities = taintVulnerabilitiesCache.getAsDiagnostics(fileUri).count();
    if (foundVulnerabilities > 0) {
      lsLogOutput
        .info(format("Fetched %s %s from %s", foundVulnerabilities,
          pluralize(foundVulnerabilities, "vulnerability", "vulnerabilities"), bindingWrapper.getConnectionId()));
    }

    // TODO should we call server issue tracker update?
    var serverIssueTracker = bindingWrapper.getServerIssueTracker();
    serverIssueTracker.update(serverConfiguration.getEndpointParams(), serverConfiguration.getHttpClient(), engine, binding,
     Collections.singleton(sqFilePath), branchName);
  }

}
