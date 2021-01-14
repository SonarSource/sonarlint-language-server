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

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.UpdateResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.util.StringUtils;
import org.sonarsource.sonarlint.ls.AnalysisManager;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.progress.NoOpProgressFacade;
import org.sonarsource.sonarlint.ls.progress.ProgressFacade;
import org.sonarsource.sonarlint.ls.progress.ProgressManager;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

import static java.util.Objects.requireNonNull;

/**
 * Keep a cache of project bindings. Files that are part of a workspace workspaceFolderPath will share the same binding.
 * Files that are opened alone will have their own binding.
 */
public class ProjectBindingManager implements WorkspaceSettingsChangeListener, WorkspaceFolderSettingsChangeListener {

  private static final Logger LOG = Loggers.get(ProjectBindingManager.class);
  private static final String USER_AGENT = "SonarLint Language Server";

  private final WorkspaceFoldersManager foldersManager;
  private final SettingsManager settingsManager;
  private final ConcurrentMap<URI, Optional<ProjectBindingWrapper>> folderBindingCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<URI, Optional<ProjectBindingWrapper>> fileBindingCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Optional<ConnectedSonarLintEngine>> connectedEngineCacheByConnectionId = new ConcurrentHashMap<>();
  private final ProgressManager progressManager;
  private final LanguageClient client;
  private final EnginesFactory enginesFactory;
  private AnalysisManager analysisManager;

  public ProjectBindingManager(EnginesFactory enginesFactory, WorkspaceFoldersManager foldersManager, SettingsManager settingsManager, LanguageClient client,
    ProgressManager progressManager) {
    this.enginesFactory = enginesFactory;
    this.foldersManager = foldersManager;
    this.settingsManager = settingsManager;
    this.client = client;
    this.progressManager = progressManager;
  }

  // Can't use constructor injection because of cyclic dependency
  public void setAnalysisManager(AnalysisManager analysisManager) {
    this.analysisManager = analysisManager;
  }

  /**
   * Return the binding of the given file.
   * @return empty if the file is unbound
   */
  public Optional<ProjectBindingWrapper> getBinding(URI fileUri) {
    Optional<WorkspaceFolderWrapper> folder = foldersManager.findFolderForFile(fileUri);
    URI cacheKey = folder.map(WorkspaceFolderWrapper::getUri).orElse(fileUri);
    Map<URI, Optional<ProjectBindingWrapper>> bindingCache = folder.isPresent() ? folderBindingCache : fileBindingCache;
    return bindingCache.computeIfAbsent(cacheKey, k -> {
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

  @CheckForNull
  private ProjectBindingWrapper computeProjectBinding(WorkspaceFolderSettings settings, Path folderRoot) {
    String connectionId = requireNonNull(settings.getConnectionId());
    ServerConfiguration serverConfiguration = createServerConfiguration(connectionId);
    if (serverConfiguration == null) {
      LOG.error("Invalid binding for '{}'", folderRoot);
      return null;
    }
    Optional<ConnectedSonarLintEngine> engineOpt = getOrCreateConnectedEngine(connectionId, serverConfiguration, true, new NoOpProgressFacade());
    if (!engineOpt.isPresent()) {
      return null;
    }
    ConnectedSonarLintEngine engine = engineOpt.get();
    String projectKey = requireNonNull(settings.getProjectKey());
    ProjectStorageStatus projectStorageStatus = engine.getProjectStorageStatus(projectKey);
    if (projectStorageStatus == null || projectStorageStatus.isStale()) {
      engine.updateProject(serverConfiguration, projectKey, null);
    }
    Collection<String> ideFilePaths = FileUtils.allRelativePathsForFilesInTree(folderRoot);
    ProjectBinding projectBinding = engine.calculatePathPrefixes(projectKey, ideFilePaths);
    LOG.debug("Resolved binding {} for folder {}",
      ToStringBuilder.reflectionToString(projectBinding, ToStringStyle.SHORT_PREFIX_STYLE),
      folderRoot);
    ServerIssueTrackerWrapper issueTrackerWrapper = new ServerIssueTrackerWrapper(engine, serverConfiguration, projectBinding);
    return new ProjectBindingWrapper(connectionId, projectBinding, engine, issueTrackerWrapper);
  }

  @CheckForNull
  public ServerConfiguration createServerConfiguration(String connectionId) {
    ServerConnectionSettings serverConnectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
    if (serverConnectionSettings == null) {
      LOG.error("The specified connection id '{}' doesn't exist.", connectionId);
      return null;
    }
    return getServerConfiguration(serverConnectionSettings);
  }

  private Optional<ConnectedSonarLintEngine> getOrCreateConnectedEngine(
    String connectionId, ServerConfiguration serverConfiguration, boolean autoUpdate, ProgressFacade progress) {
    return connectedEngineCacheByConnectionId.computeIfAbsent(connectionId,
      s -> Optional.ofNullable(createConnectedEngineAndUpdateIfNeeded(connectionId, serverConfiguration, autoUpdate, progress)));
  }

  @CheckForNull
  private ConnectedSonarLintEngine createConnectedEngineAndUpdateIfNeeded(String connectionId, ServerConfiguration serverConfiguration, boolean autoUpdate,
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
        updateGlobalStorageAndLogResults(serverConfiguration, engine, failedServerIds, connectionId, progress);
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

  static ServerConfiguration getServerConfiguration(ServerConnectionSettings serverConnectionSettings) {
    return ServerConfiguration.builder()
      .url(serverConnectionSettings.getServerUrl())
      .token(serverConnectionSettings.getToken())
      .organizationKey(serverConnectionSettings.getOrganizationKey())
      .userAgent(USER_AGENT)
      .build();
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

  public void updateAllBindings(CancelChecker cancelToken, @Nullable Either<String, Number> workDoneToken) {
    progressManager.doWithProgress("Update bindings", workDoneToken, cancelToken, progress -> {
      // Clear cached bindings to force rebind during next analysis
      folderBindingCache.clear();
      fileBindingCache.clear();

      Map<String, Set<String>> projectKeyByConnectionIdsToUpdate = collectConnectionsAndProjectsToUpdate();

      Set<String> failedConnectionIds = tryUpdateConnectionsAndBoundProjectStorages(projectKeyByConnectionIdsToUpdate, progress);

      showOperationResult(failedConnectionIds);

      triggerAnalysisOfAllOpenFilesInBoundFolders(failedConnectionIds);

    });
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
      (connectionId, projetKeys) -> tryUpdateConnectionAndBoundProjectsStorages(projectKeyByConnectionIdsToUpdate, progress, failedConnectionIds, connectionId, projetKeys));
    return failedConnectionIds;
  }

  private void tryUpdateConnectionAndBoundProjectsStorages(Map<String, Set<String>> projectKeyByConnectionIdsToUpdate, ProgressFacade progress,
    Set<String> failedConnectionIds, String connectionId, Set<String> projetKeys) {
    progress.doInSubProgress(connectionId, 1.0f / projectKeyByConnectionIdsToUpdate.size(), subProgress -> {
      ServerConfiguration serverConfiguration = createServerConfiguration(connectionId);
      if (serverConfiguration == null) {
        failedConnectionIds.add(connectionId);
        return;
      }
      Optional<ConnectedSonarLintEngine> engineOpt = getOrCreateConnectedEngine(connectionId, serverConfiguration, false, subProgress);
      if (!engineOpt.isPresent()) {
        failedConnectionIds.add(connectionId);
        return;
      }
      subProgress.doInSubProgress("Update global storage", 0.5f, s -> updateGlobalStorageAndLogResults(serverConfiguration, engineOpt.get(), failedConnectionIds, connectionId, s));
      subProgress.doInSubProgress("Update projects storages", 0.5f, s -> tryUpdateBoundProjectsStorage(projetKeys, serverConfiguration, engineOpt.get(), s));
    });
  }

  private static void tryUpdateBoundProjectsStorage(Set<String> projetKeys, ServerConfiguration serverConfiguration, ConnectedSonarLintEngine engine,
    ProgressFacade progress) {
    projetKeys.forEach(projectKey -> progress.doInSubProgress(projectKey, 1.0f / projectKey.length(), subProgress -> {
      try {
        engine.updateProject(serverConfiguration, projectKey, subProgress.asCoreMonitor());
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

  private static void updateGlobalStorageAndLogResults(ServerConfiguration serverConfiguration, ConnectedSonarLintEngine engine, Collection<String> failedConnectionIds,
    String connectionId, ProgressFacade progress) {
    try {
      UpdateResult updateResult = engine.update(serverConfiguration, progress.asCoreMonitor());
      LOG.info("Local storage status for connection with id '{}': {}", connectionId, updateResult.status());
    } catch (CanceledException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("Error updating the local storage of the connection with id '" + connectionId + "'", e);
      failedConnectionIds.add(connectionId);
    }
  }

  Optional<ServerConfiguration> getServerConnectionSettingsForUrl(String url) {
    return settingsManager.getCurrentSettings().getServerConnections()
            .values()
            .stream()
            .filter(it -> StringUtils.equalsIgnoringTrailingSlash(it.getServerUrl(), url))
            .findFirst()
            .map(ProjectBindingManager::getServerConfiguration);
  }
}
