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
package org.sonarsource.sonarlint.ls.connected.sync;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.progress.ProgressFacade;
import org.sonarsource.sonarlint.ls.progress.ProgressManager;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

public class ServerSynchronizer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final LanguageClient client;
  private final ProgressManager progressManager;
  private final ProjectBindingManager bindingManager;
  private final AnalysisScheduler analysisScheduler;
  private final Timer serverSyncTimer;

  public ServerSynchronizer(LanguageClient client, ProgressManager progressManager, ProjectBindingManager bindingManager, AnalysisScheduler analysisScheduler) {
    this(client, progressManager, bindingManager, analysisScheduler, new Timer("Binding updates checker"));
  }

  ServerSynchronizer(LanguageClient client, ProgressManager progressManager, ProjectBindingManager bindingManager, AnalysisScheduler analysisScheduler, Timer serverSyncTimer) {
    this.client = client;
    this.progressManager = progressManager;
    this.bindingManager = bindingManager;
    this.analysisScheduler = analysisScheduler;
    var syncPeriod = Long.parseLong(StringUtils.defaultIfBlank(System.getenv("SONARLINT_INTERNAL_SYNC_PERIOD"), "3600")) * 1000;
    this.serverSyncTimer = serverSyncTimer;
    this.serverSyncTimer.scheduleAtFixedRate(new SyncTask(), syncPeriod, syncPeriod);
  }

  public void updateAllBindings(CancelChecker cancelToken, @Nullable Either<String, Integer> workDoneToken) {
    progressManager.doWithProgress("Update bindings", workDoneToken, cancelToken, progress -> {
      // Clear cached bindings to force rebind during next analysis
      bindingManager.clearBindingCache();
      updateBindings(bindingManager.getActiveConnectionsAndProjects(), progress);
      bindingManager.updateAllTaintIssues();
    });
  }

  private void updateBindings(Map<String, Map<String, Set<String>>> projectKeyByConnectionIdsToUpdate, ProgressFacade progress) {
    var failedConnectionIds = tryUpdateConnectionsAndBoundProjectStorages(projectKeyByConnectionIdsToUpdate, progress);
    showOperationResult(failedConnectionIds);
    triggerAnalysisOfAllOpenFilesInBoundFolders(failedConnectionIds);
  }

  private void triggerAnalysisOfAllOpenFilesInBoundFolders(Set<String> failedConnectionIds) {
    bindingManager.forEachBoundFolder((folder, folderSettings) -> {
      if (!failedConnectionIds.contains(folderSettings.getConnectionId())) {
        analysisScheduler.analyzeAllOpenFilesInFolder(folder);
      }
    });
  }

  private void showOperationResult(Set<String> failedConnectionIds) {
    if (failedConnectionIds.isEmpty()) {
      client.showMessage(new MessageParams(MessageType.Info, "All SonarLint bindings successfully updated"));
    } else {
      var connections = String.join(", ", failedConnectionIds);
      client.showMessage(
        new MessageParams(MessageType.Error, "Binding update failed for the following connection(s): " + connections + ". Look at the SonarLint output for details."));
    }
  }

  private Set<String> tryUpdateConnectionsAndBoundProjectStorages(Map<String, Map<String, Set<String>>> projectKeyByConnectionIdsToUpdate, ProgressFacade progress) {
    var failedConnectionIds = new LinkedHashSet<String>();
    projectKeyByConnectionIdsToUpdate.forEach(
      (connectionId, projectKeys) -> {
        var progressFraction = 1.0f / projectKeyByConnectionIdsToUpdate.size();
        tryUpdateConnectionAndBoundProjectsStorages(progress, failedConnectionIds, connectionId, projectKeys, progressFraction);
      });
    return failedConnectionIds;
  }

  private void tryUpdateConnectionAndBoundProjectsStorages(ProgressFacade progress, Set<String> failedConnectionIds, String connectionId,
    Map<String, Set<String>> branchNamesByProjectKey, float progressFraction) {
    progress.doInSubProgress(connectionId, progressFraction, subProgress -> {
      var endpointParamsAndHttpClient = bindingManager.getServerConfigurationFor(connectionId);
      if (endpointParamsAndHttpClient == null) {
        failedConnectionIds.add(connectionId);
        return;
      }
      var engineOpt = bindingManager.getOrCreateConnectedEngine(connectionId);
      if (engineOpt.isEmpty()) {
        failedConnectionIds.add(connectionId);
        return;
      }
      subProgress.doInSubProgress("Update projects storages", 0.5f, s -> tryUpdateBoundProjectsStorage(
        branchNamesByProjectKey.keySet(), endpointParamsAndHttpClient, engineOpt.get(), s));
      subProgress.doInSubProgress("Sync projects storages", 0.5f, s -> syncOneEngine(
        connectionId, branchNamesByProjectKey, engineOpt.get(), s));
    });
  }

  private static void tryUpdateBoundProjectsStorage(Set<String> projectKeys, ServerConnectionSettings.EndpointParamsAndHttpClient endpointParamsAndHttpClient,
    ConnectedSonarLintEngine engine,
    ProgressFacade progress) {
    projectKeys.forEach(projectKey -> progress.doInSubProgress(projectKey, 1.0f / projectKeys.size(), subProgress -> {
      try {
        engine.updateProject(endpointParamsAndHttpClient.getEndpointParams(), endpointParamsAndHttpClient.getHttpClient(), projectKey, subProgress.asCoreMonitor());
      } catch (CanceledException e) {
        throw e;
      } catch (Exception updateFailed) {
        LOG.error("Binding update failed for project key '{}'", projectKey, updateFailed);
      }
    }));
  }

  private void syncOneEngine(String connectionId, Map<String, Set<String>> branchNamesByProjectKey, ConnectedSonarLintEngine engine, @Nullable ProgressFacade progress) {
    try {
      var paramsAndHttpClient = bindingManager.getServerConfigurationFor(connectionId);
      if (paramsAndHttpClient == null) {
        return;
      }
      var progressMonitor = progress != null ? progress.asCoreMonitor() : null;
      engine.sync(paramsAndHttpClient.getEndpointParams(), paramsAndHttpClient.getHttpClient(), branchNamesByProjectKey.keySet(), progressMonitor);
      syncIssues(engine, paramsAndHttpClient, branchNamesByProjectKey, progressMonitor);
    } catch (Exception e) {
      LOG.error("Error while synchronizing storage", e);
    }
  }

  private static void syncIssues(ConnectedSonarLintEngine engine, ServerConnectionSettings.EndpointParamsAndHttpClient paramsAndHttpClient,
    Map<String, Set<String>> branchNamesByProjectKey,
    @Nullable ClientProgressMonitor progressMonitor) {
    branchNamesByProjectKey
      .forEach((projectKey, branchNames) -> branchNames.forEach(branchName -> syncIssuesForBranch(engine, paramsAndHttpClient, projectKey, branchName, progressMonitor)));
  }

  private static void syncIssuesForBranch(ConnectedSonarLintEngine engine, ServerConnectionSettings.EndpointParamsAndHttpClient paramsAndHttpClient, String projectKey,
    String branchName, @Nullable ClientProgressMonitor progressMonitor) {
    engine.syncServerIssues(paramsAndHttpClient.getEndpointParams(), paramsAndHttpClient.getHttpClient(), projectKey, branchName, progressMonitor);
    engine.syncServerTaintIssues(paramsAndHttpClient.getEndpointParams(), paramsAndHttpClient.getHttpClient(), projectKey, branchName, progressMonitor);
    engine.downloadAllServerHotspots(paramsAndHttpClient.getEndpointParams(), paramsAndHttpClient.getHttpClient(), projectKey, branchName, progressMonitor);
  }

  public void syncIssues(ProjectBindingWrapper binding, String branchName) {
    var connectionId = binding.getConnectionId();
    var paramsAndHttpClient = bindingManager.getServerConfigurationFor(connectionId);
    if (paramsAndHttpClient == null) {
      return;
    }
    syncIssuesForBranch(binding.getEngine(), paramsAndHttpClient, binding.getBinding().projectKey(), branchName, null);
  }

  public void shutdown() {
    serverSyncTimer.cancel();
  }

  private class SyncTask extends TimerTask {
    @Override
    public void run() {
      syncBoundProjects();
    }

    private void syncBoundProjects() {
      var projectsToSynchronize = bindingManager.getActiveConnectionsAndProjects();
      if (!projectsToSynchronize.isEmpty()) {
        LOG.debug("Synchronizing storages...");
        projectsToSynchronize.forEach((connectionId, branchNamesByProjectKey) -> bindingManager.getStartedConnectedEngine(connectionId)
          .ifPresent(engine -> syncOneEngine(connectionId, branchNamesByProjectKey, engine, null)));
        bindingManager.updateAllTaintIssues();
      }
    }
  }
}
