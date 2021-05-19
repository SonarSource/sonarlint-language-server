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
package org.sonarsource.sonarlint.ls.connected;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.UpdateResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.util.StringUtils;
import org.sonarsource.sonarlint.ls.AnalysisManager;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.connected.notifications.BindingUpdateNotification;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.progress.NoOpProgressFacade;
import org.sonarsource.sonarlint.ls.progress.ProgressFacade;
import org.sonarsource.sonarlint.ls.progress.ProgressManager;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings.EndpointParamsAndHttpClient;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;

/**
 * Keep a cache of project bindings. Files that are part of a workspace workspaceFolderPath will share the same binding.
 * Files that are opened alone will have their own binding.
 */
public class ProjectBindingManager implements WorkspaceSettingsChangeListener, WorkspaceFolderSettingsChangeListener {

  private static final Logger LOG = Loggers.get(ProjectBindingManager.class);
  private static final long ONE_DAY = 24L * 60L * 60L * 1000L;

  private final WorkspaceFoldersManager foldersManager;
  private final SettingsManager settingsManager;
  private final Map<URI, Optional<ProjectBindingWrapper>> folderBindingCache;
  private final BindingUpdateNotification bindingUpdateNotification;
  private final ConcurrentMap<URI, Optional<ProjectBindingWrapper>> fileBindingCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Optional<ConnectedSonarLintEngine>> connectedEngineCacheByConnectionId = new ConcurrentHashMap<>();
  private final ProgressManager progressManager;
  private final LanguageClient client;
  private final EnginesFactory enginesFactory;
  private AnalysisManager analysisManager;
  private final Timer bindingUpdatesCheckerTimer = new Timer("Binding updates checker");

  public ProjectBindingManager(EnginesFactory enginesFactory, WorkspaceFoldersManager foldersManager, SettingsManager settingsManager, LanguageClient client,
    ProgressManager progressManager) {
    this(enginesFactory, foldersManager, settingsManager, client, progressManager, new ConcurrentHashMap<>(), new BindingUpdateNotification(client));
    bindingUpdatesCheckerTimer.scheduleAtFixedRate(new BindingUpdatesCheckerTask(), 10 * 1000L, ONE_DAY);
  }

  public ProjectBindingManager(EnginesFactory enginesFactory, WorkspaceFoldersManager foldersManager, SettingsManager settingsManager, LanguageClient client,
    ProgressManager progressManager, Map<URI, Optional<ProjectBindingWrapper>> folderBindingCache, BindingUpdateNotification bindingUpdateNotification) {
    this.enginesFactory = enginesFactory;
    this.foldersManager = foldersManager;
    this.settingsManager = settingsManager;
    this.client = client;
    this.progressManager = progressManager;
    this.folderBindingCache = folderBindingCache;
    this.bindingUpdateNotification = bindingUpdateNotification;
  }

  // Can't use constructor injection because of cyclic dependency
  public void setAnalysisManager(AnalysisManager analysisManager) {
    this.analysisManager = analysisManager;
  }

  /**
   * Return the binding of the given folder.
   *
   * @return empty if the folder is unbound
   */
  public Optional<ProjectBindingWrapper> getBinding(WorkspaceFolderWrapper folder) {
    return getBinding(Optional.of(folder), folder.getUri());
  }

  /**
   * Return the binding of the given file.
   *
   * @return empty if the file is unbound
   */
  public Optional<ProjectBindingWrapper> getBinding(URI fileUri) {
    Optional<WorkspaceFolderWrapper> folder = foldersManager.findFolderForFile(fileUri);
    URI cacheKey = folder.map(WorkspaceFolderWrapper::getUri).orElse(fileUri);
    return getBinding(folder, cacheKey);
  }

  private Optional<ProjectBindingWrapper> getBinding(Optional<WorkspaceFolderWrapper> folder, URI fileUri) {
    Map<URI, Optional<ProjectBindingWrapper>> bindingCache = folder.isPresent() ? folderBindingCache : fileBindingCache;
    return bindingCache.computeIfAbsent(fileUri, k -> {
      WorkspaceFolderSettings settings = folder.map(WorkspaceFolderWrapper::getSettings)
        .orElse(settingsManager.getCurrentDefaultFolderSettings());
      if (!settings.hasBinding()) {
        return Optional.empty();
      } else {
        Path folderRoot = folder.map(WorkspaceFolderWrapper::getRootPath).orElse(Paths.get(fileUri).getParent());
        return Optional.ofNullable(computeProjectBinding(settings, folderRoot));
      }
    });
  }

  private Optional<ConnectedSonarLintEngine> getStartedConnectedEngine(String connectionId) {
    return connectedEngineCacheByConnectionId.getOrDefault(connectionId, Optional.empty());
  }

  void checkForBindingUpdates() {
    LOG.debug("Checking binding updates");
    forEachBoundFolder((folder, settings) -> {
      String connectionId = requireNonNull(settings.getConnectionId());
      getStartedConnectedEngine(connectionId)
        .filter(engine -> engine.getState() != State.UPDATING)
        .ifPresent(engine -> {
          String projectKey = requireNonNull(settings.getProjectKey());
          EndpointParamsAndHttpClient paramsAndHttpClient = getServerConfigurationFor(connectionId);
          if (paramsAndHttpClient == null) {
            return;
          }
          try {
            if (hasGlobalUpdates(engine, paramsAndHttpClient) || hasProjectUpdates(engine, paramsAndHttpClient, projectKey)) {
              bindingUpdateNotification.notifyBindingUpdateAvailable(projectKey)
                .thenAccept(updateAccepted -> {
                  if (TRUE.equals(updateAccepted)) {
                    updateBinding(connectionId, projectKey);
                  }
                });
            }
          } catch (Exception e) {
            LOG.error("Error while checking for binding updates", e);
          }
        });
    });
  }

  private static boolean hasProjectUpdates(ConnectedSonarLintEngine engine, EndpointParamsAndHttpClient requestParams, String projectKey) {
    return engine.checkIfProjectStorageNeedUpdate(requestParams.getEndpointParams(), requestParams.getHttpClient(), projectKey, null).needUpdate();
  }

  private static boolean hasGlobalUpdates(ConnectedSonarLintEngine engine, EndpointParamsAndHttpClient requestParams) {
    return engine.checkIfGlobalStorageNeedUpdate(requestParams.getEndpointParams(), requestParams.getHttpClient(), null).needUpdate();
  }

  @CheckForNull
  private ProjectBindingWrapper computeProjectBinding(WorkspaceFolderSettings settings, Path folderRoot) {
    String connectionId = requireNonNull(settings.getConnectionId());
    EndpointParamsAndHttpClient endpointParamsAndHttpClient = getServerConfigurationFor(connectionId);
    if (endpointParamsAndHttpClient == null) {
      LOG.error("Invalid binding for '{}'", folderRoot);
      return null;
    }
    Optional<ConnectedSonarLintEngine> engineOpt = getOrCreateConnectedEngine(connectionId, endpointParamsAndHttpClient, true, new NoOpProgressFacade());
    if (!engineOpt.isPresent()) {
      return null;
    }
    ConnectedSonarLintEngine engine = engineOpt.get();
    String projectKey = requireNonNull(settings.getProjectKey());
    ProjectStorageStatus projectStorageStatus = engine.getProjectStorageStatus(projectKey);
    if (projectStorageStatus == null || projectStorageStatus.isStale()) {
      engine.updateProject(endpointParamsAndHttpClient.getEndpointParams(), endpointParamsAndHttpClient.getHttpClient(), projectKey, false, null);
    }
    Collection<String> ideFilePaths = FileUtils.allRelativePathsForFilesInTree(folderRoot);
    ProjectBinding projectBinding = engine.calculatePathPrefixes(projectKey, ideFilePaths);
    LOG.debug("Resolved binding {} for folder {}",
      ToStringBuilder.reflectionToString(projectBinding, ToStringStyle.SHORT_PREFIX_STYLE),
      folderRoot);
    ServerIssueTrackerWrapper issueTrackerWrapper = new ServerIssueTrackerWrapper(engine, endpointParamsAndHttpClient, projectBinding);
    return new ProjectBindingWrapper(connectionId, projectBinding, engine, issueTrackerWrapper);
  }

  @CheckForNull
  public EndpointParamsAndHttpClient getServerConfigurationFor(String connectionId) {
    ServerConnectionSettings serverConnectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
    if (serverConnectionSettings == null) {
      LOG.error("The specified connection id '{}' doesn't exist.", connectionId);
      return null;
    }
    return serverConnectionSettings.getServerConfiguration();
  }

  private Optional<ConnectedSonarLintEngine> getOrCreateConnectedEngine(
    String connectionId, EndpointParamsAndHttpClient endpointParamsAndHttpClient, boolean autoUpdate, ProgressFacade progress) {
    return connectedEngineCacheByConnectionId.computeIfAbsent(connectionId,
      s -> Optional.ofNullable(createConnectedEngineAndUpdateIfNeeded(connectionId, endpointParamsAndHttpClient, autoUpdate, progress)));
  }

  @CheckForNull
  private ConnectedSonarLintEngine createConnectedEngineAndUpdateIfNeeded(String connectionId, EndpointParamsAndHttpClient endpointParamsAndHttpClient, boolean autoUpdate,
    ProgressFacade progress) {
    LOG.debug("Starting connected SonarLint engine for '{}'...", connectionId);

    ConnectedSonarLintEngine engine;
    try {
      engine = enginesFactory.createConnectedEngine(connectionId);
      if (engine.getState() == State.UPDATING) {
        return engine;
      }
    } catch (Exception e) {
      LOG.error("Error starting connected SonarLint engine for '" + connectionId + "'", e);
      return null;
    }
    List<String> failedServerIds = new ArrayList<>();
    try {
      GlobalStorageStatus globalStorageStatus = engine.getGlobalStorageStatus();
      if (autoUpdate && (globalStorageStatus == null || globalStorageStatus.isStale() || engine.getState() != State.UPDATED)) {
        updateGlobalStorageAndLogResults(endpointParamsAndHttpClient, engine, failedServerIds, connectionId, progress);
      }
    } catch (Exception e) {
      LOG.error("Error updating storage of the connected SonarLint engine '" + connectionId + "'", e);
    }
    if (!failedServerIds.isEmpty()) {
      client.showMessage(new MessageParams(MessageType.Error, "Binding update failed for the server: " + connectionId + ". Look to the SonarLint output for details."));
      return null;
    }
    return engine;
  }

  public boolean usesConnectedMode() {
    return Stream.concat(folderBindingCache.values().stream(), fileBindingCache.values().stream()).anyMatch(Optional::isPresent);
  }

  public boolean usesSonarCloud() {
    return hasAnyBindingThatMatch(ServerConnectionSettings::isSonarCloudAlias);
  }

  public boolean devNotificationsDisabled() {
    return hasAnyBindingThatMatch(ServerConnectionSettings::isDevNotificationsDisabled);
  }

  private boolean hasAnyBindingThatMatch(Predicate<ServerConnectionSettings> predicate) {
    return Stream.concat(folderBindingCache.values().stream(), fileBindingCache.values().stream())
      .flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
      .map(binding -> settingsManager.getCurrentSettings().getServerConnections().get(binding.getConnectionId()))
      .anyMatch(predicate);
  }

  @Override
  public void onChange(@CheckForNull WorkspaceFolderWrapper folder, @CheckForNull WorkspaceFolderSettings oldValue, WorkspaceFolderSettings newValue) {
    if (oldValue == null) {
      return;
    }
    if (oldValue.hasBinding() && !newValue.hasBinding()) {
      unbind(folder);
    } else if (newValue.hasBinding()
      && (!Objects.equals(oldValue.getConnectionId(), newValue.getConnectionId()) || !Objects.equals(oldValue.getProjectKey(), newValue.getProjectKey()))) {
      forceRebindDuringNextAnalysis(folder);
    }
  }

  private void forceRebindDuringNextAnalysis(@Nullable WorkspaceFolderWrapper folder) {
    if (folder != null && folderBindingCache.containsKey(folder.getUri())) {
      clearFolderBindingCache(folder);
    } else if (folder == null && !fileBindingCache.isEmpty()) {
      clearFilesBindingCache();
    }
  }

  private void unbind(@Nullable WorkspaceFolderWrapper folder) {
    if (folder != null && folderBindingCache.containsKey(folder.getUri())) {
      unbindFolder(folder);
    } else if (folder == null && !fileBindingCache.isEmpty()) {
      unbindFiles();
    }
  }

  /**
   * Clear cache of binding, so that it gets recomputed during next analysis
   */
  private void clearFilesBindingCache() {
    fileBindingCache.clear();
    stopUnusedEngines();
    analysisManager.analyzeAllOpenFilesInFolder(null);
  }

  private void clearFolderBindingCache(WorkspaceFolderWrapper folder) {
    folderBindingCache.remove(folder.getUri());
    stopUnusedEngines();
    analysisManager.analyzeAllOpenFilesInFolder(folder);
  }

  private void unbindFiles() {
    fileBindingCache.replaceAll((uri, binding) -> Optional.empty());
    LOG.debug("All files outside workspace are now unbound");
    stopUnusedEngines();
    analysisManager.analyzeAllOpenFilesInFolder(null);
  }

  private void unbindFolder(WorkspaceFolderWrapper folder) {
    folderBindingCache.put(folder.getUri(), Optional.empty());
    LOG.debug("Workspace '{}' unbound", folder);
    stopUnusedEngines();
    analysisManager.analyzeAllOpenFilesInFolder(folder);
  }

  private void stopUnusedEngines() {
    Set<String> usedServerIds = new HashSet<>();
    WorkspaceFolderSettings folderSettings = settingsManager.getCurrentDefaultFolderSettings();
    collectUsedServerId(usedServerIds, folderSettings);
    foldersManager.getAll().forEach(w -> collectUsedServerId(usedServerIds, w.getSettings()));
    Set<String> startedEngines = new HashSet<>(connectedEngineCacheByConnectionId.keySet());
    for (String startedEngineId : startedEngines) {
      if (!usedServerIds.contains(startedEngineId)) {
        folderBindingCache.entrySet().removeIf(e -> e.getValue().isPresent() && e.getValue().get().getConnectionId().equals(startedEngineId));
        fileBindingCache.entrySet().removeIf(e -> e.getValue().isPresent() && e.getValue().get().getConnectionId().equals(startedEngineId));
        tryStopServer(startedEngineId, connectedEngineCacheByConnectionId.remove(startedEngineId));
      }
    }
  }

  private void collectUsedServerId(Set<String> usedConnectionIds, WorkspaceFolderSettings folderSettings) {
    if (folderSettings.hasBinding()) {
      String connectionId = folderSettings.getConnectionId();
      if (connectionId != null && settingsManager.getCurrentSettings().getServerConnections().containsKey(connectionId)) {
        usedConnectionIds.add(connectionId);
      }
    }
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    if (oldValue == null) {
      return;
    }
    stopUnusedEngines();
  }

  public void shutdown() {
    connectedEngineCacheByConnectionId.forEach(ProjectBindingManager::tryStopServer);
    bindingUpdatesCheckerTimer.cancel();
  }

  private static void tryStopServer(String connectionId, Optional<ConnectedSonarLintEngine> engine) {
    engine.ifPresent(e -> {
      try {
        e.stop(false);
      } catch (Exception ex) {
        LOG.error("Unable to stop engine '" + connectionId + "'", ex);
      }
    });
  }

  public void updateAllBindings(CancelChecker cancelToken, @Nullable Either<String, Integer> workDoneToken) {
    progressManager.doWithProgress("Update bindings", workDoneToken, cancelToken, progress -> {
      // Clear cached bindings to force rebind during next analysis
      folderBindingCache.clear();
      fileBindingCache.clear();
      updateBindings(collectConnectionsAndProjectsToUpdate(), progress);
    });
  }

  CompletableFuture<Void> updateBinding(String connectionId, String projectKey) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      progressManager.doWithProgress("Update binding for " + projectKey, null, cancelToken,
        progress -> updateBindings(Collections.singletonMap(connectionId, Collections.singleton(projectKey)), progress));
      return null;
    });
  }

  private void updateBindings(Map<String, Set<String>> projectKeyByConnectionIdsToUpdate, ProgressFacade progress) {
    Set<String> failedConnectionIds = tryUpdateConnectionsAndBoundProjectStorages(projectKeyByConnectionIdsToUpdate, progress);
    showOperationResult(failedConnectionIds);
    triggerAnalysisOfAllOpenFilesInBoundFolders(failedConnectionIds);
  }

  private void triggerAnalysisOfAllOpenFilesInBoundFolders(Set<String> failedConnectionIds) {
    forEachBoundFolder((folder, folderSettings) -> {
      if (!failedConnectionIds.contains(folderSettings.getConnectionId())) {
        analysisManager.analyzeAllOpenFilesInFolder(folder);
      }
    });
  }

  private void showOperationResult(Set<String> failedConnectionIds) {
    if (failedConnectionIds.isEmpty()) {
      client.showMessage(new MessageParams(MessageType.Info, "All SonarLint bindings succesfully updated"));
    } else {
      String connections = String.join(", ", failedConnectionIds);
      client.showMessage(
        new MessageParams(MessageType.Error, "Binding update failed for the following connection(s): " + connections + ". Look at the SonarLint output for details."));
    }
  }

  private Set<String> tryUpdateConnectionsAndBoundProjectStorages(Map<String, Set<String>> projectKeyByConnectionIdsToUpdate, ProgressFacade progress) {
    Set<String> failedConnectionIds = new LinkedHashSet<>();
    projectKeyByConnectionIdsToUpdate.forEach(
      (connectionId, projectKeys) -> tryUpdateConnectionAndBoundProjectsStorages(projectKeyByConnectionIdsToUpdate, progress, failedConnectionIds, connectionId, projectKeys));
    return failedConnectionIds;
  }

  private void tryUpdateConnectionAndBoundProjectsStorages(Map<String, Set<String>> projectKeyByConnectionIdsToUpdate, ProgressFacade progress,
    Set<String> failedConnectionIds, String connectionId, Set<String> projectKeys) {
    progress.doInSubProgress(connectionId, 1.0f / projectKeyByConnectionIdsToUpdate.size(), subProgress -> {
      EndpointParamsAndHttpClient endpointParamsAndHttpClient = getServerConfigurationFor(connectionId);
      if (endpointParamsAndHttpClient == null) {
        failedConnectionIds.add(connectionId);
        return;
      }
      Optional<ConnectedSonarLintEngine> engineOpt = getOrCreateConnectedEngine(connectionId, endpointParamsAndHttpClient, false, subProgress);
      if (!engineOpt.isPresent()) {
        failedConnectionIds.add(connectionId);
        return;
      }
      subProgress.doInSubProgress("Update global storage", 0.5f, s -> updateGlobalStorageAndLogResults(
        endpointParamsAndHttpClient, engineOpt.get(), failedConnectionIds, connectionId, s));
      subProgress.doInSubProgress("Update projects storages", 0.5f, s -> tryUpdateBoundProjectsStorage(
        projectKeys, endpointParamsAndHttpClient, engineOpt.get(), s));
    });
  }

  private static void tryUpdateBoundProjectsStorage(Set<String> projectKeys, EndpointParamsAndHttpClient endpointParamsAndHttpClient, ConnectedSonarLintEngine engine,
    ProgressFacade progress) {
    projectKeys.forEach(projectKey -> progress.doInSubProgress(projectKey, 1.0f / projectKey.length(), subProgress -> {
      try {
        engine.updateProject(endpointParamsAndHttpClient.getEndpointParams(), endpointParamsAndHttpClient.getHttpClient(), projectKey, true, subProgress.asCoreMonitor());
      } catch (CanceledException e) {
        throw e;
      } catch (Exception updateFailed) {
        LOG.error("Binding update failed for project key '{}'", projectKey, updateFailed);
      }
    }));
  }

  private Map<String, Set<String>> collectConnectionsAndProjectsToUpdate() {
    Map<String, Set<String>> projectKeyByConnectionIdsToUpdate = new HashMap<>();
    // Update all engines that are already started and cached, even if no folders are bound
    connectedEngineCacheByConnectionId.keySet().forEach(id -> projectKeyByConnectionIdsToUpdate.computeIfAbsent(id, i -> new HashSet<>()));
    // Start and update all engines that used in a folder binding, even if not yet started
    forEachBoundFolder((folder, folderSettings) -> {
      String connectionId = requireNonNull(folderSettings.getConnectionId());
      String projectKey = requireNonNull(folderSettings.getProjectKey());
      projectKeyByConnectionIdsToUpdate.computeIfAbsent(connectionId, id -> new HashSet<>()).add(projectKey);
    });
    return projectKeyByConnectionIdsToUpdate;
  }

  private void forEachBoundFolder(BiConsumer<WorkspaceFolderWrapper, WorkspaceFolderSettings> boundFolderConsumer) {
    WorkspaceFolderSettings defaultFolderSettings = settingsManager.getCurrentDefaultFolderSettings();
    if (defaultFolderSettings.hasBinding()) {
      boundFolderConsumer.accept(null, defaultFolderSettings);
    }
    foldersManager.getAll().forEach(f -> {
      WorkspaceFolderSettings settings = f.getSettings();
      if (settings.hasBinding()) {
        boundFolderConsumer.accept(f, settings);
      }
    });

  }

  private static void updateGlobalStorageAndLogResults(EndpointParamsAndHttpClient endpointParamsAndHttpClient,
    ConnectedSonarLintEngine engine, Collection<String> failedConnectionIds,
    String connectionId, ProgressFacade progress) {
    try {
      UpdateResult updateResult = engine.update(endpointParamsAndHttpClient.getEndpointParams(), endpointParamsAndHttpClient.getHttpClient(), progress.asCoreMonitor());
      LOG.info("Local storage status for connection with id '{}': {}", connectionId, updateResult.status());
    } catch (CanceledException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("Error updating the local storage of the connection with id '" + connectionId + "'", e);
      failedConnectionIds.add(connectionId);
    }
  }

  Optional<EndpointParamsAndHttpClient> getServerConnectionSettingsForUrl(String url) {
    return settingsManager.getCurrentSettings().getServerConnections()
      .values()
      .stream()
      .filter(it -> StringUtils.equalsIgnoringTrailingSlash(it.getServerUrl(), url))
      .findFirst()
      .map(ServerConnectionSettings::getServerConfiguration);
  }

  public Optional<URI> serverPathToFileUri(String serverPath) {
    return folderBindingCache.entrySet().stream()
      .filter(e -> e.getValue().isPresent())
      .map(e -> tryResolveLocalFile(serverPath, e.getKey(), e.getValue().get()))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(File::toURI)
      .findFirst();
  }

  private static Optional<File> tryResolveLocalFile(String serverPath, URI folderUri, ProjectBindingWrapper binding) {
    return binding.getBinding()
      .serverPathToIdePath(serverPath)
      // Try to resolve local path in matching folder
      .map(Paths.get(folderUri)::resolve)
      .map(Path::toFile)
      .filter(File::exists);
  }

  private class BindingUpdatesCheckerTask extends TimerTask {
    @Override
    public void run() {
      checkForBindingUpdates();
    }
  }
}
