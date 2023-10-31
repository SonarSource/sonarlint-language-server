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
package org.sonarsource.sonarlint.ls.connected.events;

import java.net.URI;
import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.ServerHotspotEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.connected.notifications.TaintVulnerabilityRaisedNotification;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

import static org.sonarsource.sonarlint.ls.settings.SettingsManager.DEFAULT_CONNECTION_ID;

public class ServerSentEventsHandler implements ServerSentEventsHandlerService {
  private final ProjectBindingManager projectBindingManager;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final TaintVulnerabilityRaisedNotification taintVulnerabilityRaisedNotification;
  private final SettingsManager settingsManager;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final AnalysisScheduler analysisScheduler;

  public ServerSentEventsHandler(ProjectBindingManager projectBindingManager, TaintVulnerabilitiesCache taintVulnerabilitiesCache,
    TaintVulnerabilityRaisedNotification taintVulnerabilityRaisedNotification, SettingsManager settingsManager,
    WorkspaceFoldersManager workspaceFoldersManager, AnalysisScheduler analysisScheduler) {
    this.projectBindingManager = projectBindingManager;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.taintVulnerabilityRaisedNotification = taintVulnerabilityRaisedNotification;
    this.settingsManager = settingsManager;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.analysisScheduler = analysisScheduler;
  }

  @Override
  public void handleEvents(ServerEvent event) {
    if (event instanceof TaintVulnerabilityRaisedEvent) {
      handleTaintVulnerabilityRaisedEvent(event);
    } else if (event instanceof TaintVulnerabilityClosedEvent ||
      event instanceof IssueChangedEvent) {
      taintVulnerabilitiesCache.getAllFilesWithTaintIssues()
        .forEach(projectBindingManager::updateTaintIssueCacheFromStorageForFile);
    } else if (event instanceof ServerHotspotEvent serverHotspotEvent) {
      var fileUri = getFileUriFromEvent(serverHotspotEvent);
      fileUri.ifPresent(analysisScheduler::didReceiveHotspotEvent);
    }
  }

  private Optional<URI> getFileUriFromEvent(ServerHotspotEvent event) {
    var serverPath = event.getFilePath();
    return projectBindingManager.serverPathToFileUri(serverPath);
  }

  @Override
  public void handleTaintVulnerabilityRaisedEvent(ServerEvent event) {
    var taintVulnerabilityRaisedEvent = (TaintVulnerabilityRaisedEvent) event;
    var filePathFromEvent = taintVulnerabilityRaisedEvent.getMainLocation().getFilePath();
    var localFileUri = projectBindingManager.serverPathToFileUri(filePathFromEvent);
    var connectionId = DEFAULT_CONNECTION_ID;
    var currentBranch = "";
    if (localFileUri.isPresent()) {
      projectBindingManager.updateTaintIssueCacheFromStorageForFile(localFileUri.get());
      var bindingWrapper = projectBindingManager.getBinding(localFileUri.get());
      if (bindingWrapper.isPresent()) {
        var binding = bindingWrapper.get();
        connectionId = binding.getConnectionId();
        var folderUri = workspaceFoldersManager.findFolderForFile(localFileUri.get());
        if (folderUri.isPresent()) {
          currentBranch = projectBindingManager.resolveBranchNameForFolder(folderUri.get().getUri(), binding.getEngine(), taintVulnerabilityRaisedEvent.getProjectKey());
        }
      }
    }
    var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
    if (connectionSettings != null && !connectionSettings.isSmartNotificationsDisabled() && currentBranch.equals(taintVulnerabilityRaisedEvent.getBranchName())) {
      taintVulnerabilityRaisedNotification.showTaintVulnerabilityNotification(taintVulnerabilityRaisedEvent, connectionId, connectionSettings.isSonarCloudAlias());
    }
  }
}
