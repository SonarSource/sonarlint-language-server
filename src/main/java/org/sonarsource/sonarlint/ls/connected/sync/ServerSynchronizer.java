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
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.progress.ProgressFacade;
import org.sonarsource.sonarlint.ls.progress.ProgressManager;

public class ServerSynchronizer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final LanguageClient client;
  private final ProgressManager progressManager;
  private final ProjectBindingManager bindingManager;
  private final AnalysisScheduler analysisScheduler;
  private final Timer serverSyncTimer;
  private final BackendServiceFacade backendServiceFacade;

  public ServerSynchronizer(LanguageClient client, ProgressManager progressManager, ProjectBindingManager bindingManager,
    AnalysisScheduler analysisScheduler, BackendServiceFacade backendServiceFacade) {
    this(client, progressManager, bindingManager, analysisScheduler, new Timer("Binding updates checker"), backendServiceFacade);
  }

  ServerSynchronizer(LanguageClient client, ProgressManager progressManager, ProjectBindingManager bindingManager,
    AnalysisScheduler analysisScheduler, Timer serverSyncTimer, BackendServiceFacade backendServiceFacade) {
    this.client = client;
    this.progressManager = progressManager;
    this.bindingManager = bindingManager;
    this.analysisScheduler = analysisScheduler;
    this.backendServiceFacade = backendServiceFacade;
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

  private void updateBindings(Map<String, Map<String, Set<String>>> projectKeyByConnectionIdsToUpdate,
    ProgressFacade progress) {
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
        var httpClient = backendServiceFacade.getHttpClient(connectionId);
        tryUpdateConnectionAndBoundProjectsStorages(progress, failedConnectionIds, connectionId, projectKeys, progressFraction, httpClient);
      });
    return failedConnectionIds;
  }

  private void tryUpdateConnectionAndBoundProjectsStorages(ProgressFacade progress, Set<String> failedConnectionIds, String connectionId,
    Map<String, Set<String>> branchNamesByProjectKey, float progressFraction, HttpClient httpClient) {
    progress.doInSubProgress(connectionId, progressFraction, subProgress -> {
      var endpointParams = bindingManager.getEndpointParamsFor(connectionId);
      if (endpointParams == null) {
        failedConnectionIds.add(connectionId);
        return;
      }
      var engineOpt = bindingManager.getOrCreateConnectedEngine(connectionId);
      if (engineOpt.isEmpty()) {
        failedConnectionIds.add(connectionId);
        return;
      }
      subProgress.doInSubProgress("Update projects storages", 0.5f, s -> tryUpdateBoundProjectsStorage(
        branchNamesByProjectKey.keySet(), endpointParams, engineOpt.get(), httpClient, s));
      subProgress.doInSubProgress("Sync projects storages", 0.5f, s -> syncOneEngine(
        connectionId, branchNamesByProjectKey, engineOpt.get(), httpClient, s));
    });
  }

  private static void tryUpdateBoundProjectsStorage(Set<String> projectKeys, EndpointParams endpointParams,
    ConnectedSonarLintEngine engine, HttpClient httpClient, ProgressFacade progress) {
    projectKeys.forEach(projectKey -> progress.doInSubProgress(projectKey, 1.0f / projectKeys.size(), subProgress -> {
      try {
        engine.updateProject(endpointParams, httpClient, projectKey, subProgress.asCoreMonitor());
      } catch (CanceledException e) {
        throw e;
      } catch (Exception updateFailed) {
        LOG.error("Binding update failed for project key '{}'", projectKey, updateFailed);
      }
    }));
  }

  private void syncOneEngine(String connectionId, Map<String, Set<String>> branchNamesByProjectKey, ConnectedSonarLintEngine engine,
  HttpClient httpClient, @Nullable ProgressFacade progress) {
    try {
      var endpointParams = bindingManager.getEndpointParamsFor(connectionId);
      if (endpointParams == null) {
        return;
      }
      var progressMonitor = progress != null ? progress.asCoreMonitor() : null;
      engine.sync(endpointParams, httpClient, branchNamesByProjectKey.keySet(), progressMonitor);
      syncIssues(engine, endpointParams, branchNamesByProjectKey, httpClient, progressMonitor);
    } catch (Exception e) {
      LOG.error("Error while synchronizing storage", e);
    }
  }

  private static void syncIssues(ConnectedSonarLintEngine engine, EndpointParams endpointParams,
    Map<String, Set<String>> branchNamesByProjectKey,
    HttpClient httpClient,
    @Nullable ClientProgressMonitor progressMonitor) {
    branchNamesByProjectKey
      .forEach((projectKey, branchNames) -> branchNames.forEach(branchName -> syncIssuesForBranch(engine, endpointParams, projectKey, branchName, httpClient, progressMonitor)));
  }

  private static void syncIssuesForBranch(ConnectedSonarLintEngine engine, EndpointParams endpointParams, String projectKey,
    String branchName, HttpClient httpClient, @Nullable ClientProgressMonitor progressMonitor) {
    engine.syncServerIssues(endpointParams, httpClient, projectKey, branchName, progressMonitor);
    engine.syncServerTaintIssues(endpointParams, httpClient, projectKey, branchName, progressMonitor);
    engine.syncServerHotspots(endpointParams, httpClient, projectKey, branchName, progressMonitor);
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
          .ifPresent(engine -> {
            var httpClient = backendServiceFacade.getHttpClient(connectionId);
            syncOneEngine(connectionId, branchNamesByProjectKey, engine, httpClient, null);
          })
        );
        bindingManager.updateAllTaintIssues();
      }
    }
  }
}
