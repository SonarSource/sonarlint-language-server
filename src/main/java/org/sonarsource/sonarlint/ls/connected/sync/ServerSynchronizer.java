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

import java.util.HashMap;
import java.util.HashSet;
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
import org.sonarsource.sonarlint.ls.progress.ProgressFacade;
import org.sonarsource.sonarlint.ls.progress.ProgressManager;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

import static java.util.Objects.requireNonNull;

public class ServerSynchronizer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final LanguageClient client;
  private final ProgressManager progressManager;
  private final ProjectBindingManager bindingManager;
  private final AnalysisScheduler analysisScheduler;
  private final Timer bindingUpdatesCheckerTimer = new Timer("Binding updates checker");

  public ServerSynchronizer(LanguageClient client, ProgressManager progressManager, ProjectBindingManager bindingManager, AnalysisScheduler analysisScheduler) {
    this.client = client;
    this.progressManager = progressManager;
    this.bindingManager = bindingManager;
    this.analysisScheduler = analysisScheduler;
    var syncPeriod = Long.parseLong(StringUtils.defaultIfBlank(System.getenv("SONARLINT_INTERNAL_SYNC_PERIOD"), "3600")) * 1000;
    bindingUpdatesCheckerTimer.scheduleAtFixedRate(new BindingUpdatesCheckerTask(), 10 * 1000L, syncPeriod);
  }

  public void updateAllBindings(CancelChecker cancelToken, @Nullable Either<String, Integer> workDoneToken) {
    progressManager.doWithProgress("Update bindings", workDoneToken, cancelToken, progress -> {
      // Clear cached bindings to force rebind during next analysis
      bindingManager.clearBindingCache();
      updateBindings(bindingManager.collectConnectionsAndProjectsToUpdate(), progress);
    });
  }

  private void updateBindings(Map<String, Set<String>> projectKeyByConnectionIdsToUpdate, ProgressFacade progress) {
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

  private Set<String> tryUpdateConnectionsAndBoundProjectStorages(Map<String, Set<String>> projectKeyByConnectionIdsToUpdate, ProgressFacade progress) {
    var failedConnectionIds = new LinkedHashSet<String>();
    projectKeyByConnectionIdsToUpdate.forEach(
      (connectionId, projectKeys) -> tryUpdateConnectionAndBoundProjectsStorages(projectKeyByConnectionIdsToUpdate, progress, failedConnectionIds, connectionId, projectKeys));
    return failedConnectionIds;
  }

  private void tryUpdateConnectionAndBoundProjectsStorages(Map<String, Set<String>> projectKeyByConnectionIdsToUpdate, ProgressFacade progress,
    Set<String> failedConnectionIds, String connectionId, Set<String> projectKeys) {
    progress.doInSubProgress(connectionId, 1.0f / projectKeyByConnectionIdsToUpdate.size(), subProgress -> {
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
        projectKeys, endpointParamsAndHttpClient, engineOpt.get(), s));
      subProgress.doInSubProgress("Sync projects storages", 0.5f, s -> syncOneEngine(
        connectionId, projectKeys, engineOpt.get(), s));
    });
  }

  private static void tryUpdateBoundProjectsStorage(Set<String> projectKeys, ServerConnectionSettings.EndpointParamsAndHttpClient endpointParamsAndHttpClient,
    ConnectedSonarLintEngine engine,
    ProgressFacade progress) {
    projectKeys.forEach(projectKey -> progress.doInSubProgress(projectKey, 1.0f / projectKey.length(), subProgress -> {
      try {
        engine.updateProject(endpointParamsAndHttpClient.getEndpointParams(), endpointParamsAndHttpClient.getHttpClient(), projectKey, subProgress.asCoreMonitor());
      } catch (CanceledException e) {
        throw e;
      } catch (Exception updateFailed) {
        LOG.error("Binding update failed for project key '{}'", projectKey, updateFailed);
      }
    }));
  }

  private void syncStorage() {
    var projectKeyPerConnectionId = new HashMap<String, Set<String>>();
    bindingManager.forEachBoundFolder((folder, settings) -> {
      var connectionId = requireNonNull(settings.getConnectionId());
      var projectKey = requireNonNull(settings.getProjectKey());
      projectKeyPerConnectionId
        .computeIfAbsent(connectionId, k -> new HashSet<>())
        .add(projectKey);
    });
    if (!projectKeyPerConnectionId.isEmpty()) {
      LOG.debug("Synchronizing storages...");
      projectKeyPerConnectionId.forEach((connectionId, projectKeys) -> bindingManager.getStartedConnectedEngine(connectionId)
        .ifPresent(engine -> syncOneEngine(connectionId, projectKeys, engine, null)));
    }
  }

  private void syncOneEngine(String connectionId, Set<String> projectKeys, ConnectedSonarLintEngine engine, @Nullable ProgressFacade progress) {
    try {
      var paramsAndHttpClient = bindingManager.getServerConfigurationFor(connectionId);
      if (paramsAndHttpClient == null) {
        return;
      }
      var progressMonitor = progress != null ? progress.asCoreMonitor() : null;
      engine.sync(paramsAndHttpClient.getEndpointParams(), paramsAndHttpClient.getHttpClient(), projectKeys, progressMonitor);
    } catch (Exception e) {
      LOG.error("Error while synchronizing storage", e);
    }
  }

  public void shutdown() {
    bindingUpdatesCheckerTimer.cancel();
  }

  private class BindingUpdatesCheckerTask extends TimerTask {
    @Override
    public void run() {
      syncStorage();
    }
  }
}
