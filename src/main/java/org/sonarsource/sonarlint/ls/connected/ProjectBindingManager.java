/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
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
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.ls.AnalysisManager;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
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
  private final Map<URI, ProjectBindingWrapper> folderBindingCache = new HashMap<>();
  private final Map<URI, ProjectBindingWrapper> fileBindingCache = new HashMap<>();
  private final Map<String, ConnectedSonarLintEngine> connectedEngineCacheByServerId = new HashMap<>();
  private final LanguageClient client;
  private final EnginesFactory enginesFactory;
  private AnalysisManager analysisManager;

  public ProjectBindingManager(EnginesFactory enginesFactory, WorkspaceFoldersManager foldersManager, SettingsManager settingsManager, LanguageClient client) {
    this.enginesFactory = enginesFactory;
    this.foldersManager = foldersManager;
    this.settingsManager = settingsManager;
    this.client = client;
  }

  // Can't use constructor injection because of cyclic dependency
  public void setAnalysisManager(AnalysisManager analysisManager) {
    this.analysisManager = analysisManager;
  }

  /**
   * Return the binding of the given file.
   * @return empty if the file is unbound
   */
  public synchronized Optional<ProjectBindingWrapper> getBinding(URI fileUri) {
    Optional<WorkspaceFolderWrapper> folder = foldersManager.findFolderForFile(fileUri);
    URI cacheKey = folder.map(WorkspaceFolderWrapper::getUri).orElse(fileUri);
    Map<URI, ProjectBindingWrapper> bindingCache = folder.isPresent() ? folderBindingCache : fileBindingCache;
    if (!bindingCache.containsKey(cacheKey)) {

      WorkspaceFolderSettings settings = folder.map(WorkspaceFolderWrapper::getSettings)
        .orElse(settingsManager.getCurrentDefaultFolderSettings());
      if (!settings.hasBinding()) {
        bindingCache.put(cacheKey, null);
      } else {
        Path folderRoot = folder.map(WorkspaceFolderWrapper::getRootPath).orElse(Paths.get(fileUri).getParent());
        ProjectBindingWrapper projectBindingWrapper = computeProjectBinding(settings, folderRoot);
        bindingCache.put(cacheKey, projectBindingWrapper);
      }
    }

    return Optional.ofNullable(bindingCache.get(cacheKey));
  }

  @CheckForNull
  private ProjectBindingWrapper computeProjectBinding(WorkspaceFolderSettings settings, Path folderRoot) {
    String serverId = requireNonNull(settings.getServerId());
    ServerConfiguration serverConfiguration = createServerConfiguration(serverId);
    if (serverConfiguration == null) {
      LOG.error("Invalid binding for '{}'", folderRoot);
      return null;
    }
    ConnectedSonarLintEngine engine = getOrCreateConnectedEngine(serverId, serverConfiguration);
    if (engine == null) {
      return null;
    }
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
    return new ProjectBindingWrapper(serverId, projectBinding, engine, issueTrackerWrapper);
  }

  @CheckForNull
  private ServerConfiguration createServerConfiguration(String serverId) {
    ServerConnectionSettings serverConnectionSettings = settingsManager.getCurrentSettings().getServers().get(serverId);
    if (serverConnectionSettings == null) {
      LOG.error("The specified serverId '{}' doesn't exist.", serverId);
      return null;
    }
    return getServerConfiguration(serverConnectionSettings);
  }

  @CheckForNull
  private synchronized ConnectedSonarLintEngine getOrCreateConnectedEngine(String serverId, ServerConfiguration serverConfiguration) {
    return connectedEngineCacheByServerId.computeIfAbsent(serverId, s -> createConnectedEngineAndUpdateIfNeeded(serverId, serverConfiguration));
  }

  @CheckForNull
  private ConnectedSonarLintEngine createConnectedEngineAndUpdateIfNeeded(String serverId, ServerConfiguration serverConfiguration) {
    LOG.debug("Starting connected SonarLint engine for '{}'...", serverId);

    ConnectedSonarLintEngine engine;
    try {
      engine = enginesFactory.createConnectedEngine(serverId);
      if (engine.getState() == State.UPDATING) {
        return engine;
      }
    } catch (Exception e) {
      LOG.error("Error starting connected SonarLint engine for '" + serverId + "'", e);
      return null;
    }

    try {
      GlobalStorageStatus globalStorageStatus = engine.getGlobalStorageStatus();
      if (globalStorageStatus == null || globalStorageStatus.isStale() || engine.getState() != State.UPDATED) {
        updateGlobalStorageAndLogResults(serverConfiguration, engine);
      }
    } catch (Exception e) {
      LOG.error("Error updating storage of the connected SonarLint engine '" + serverId + "'", e);
      return null;
    }
    return engine;
  }

  private static ServerConfiguration getServerConfiguration(ServerConnectionSettings serverConnectionSettings) {
    return ServerConfiguration.builder()
      .url(serverConnectionSettings.getServerUrl())
      .token(serverConnectionSettings.getToken())
      .organizationKey(serverConnectionSettings.getOrganizationKey())
      .userAgent(USER_AGENT)
      .build();
  }

  public synchronized boolean usesConnectedMode() {
    return Stream.concat(folderBindingCache.values().stream(), fileBindingCache.values().stream()).anyMatch(Objects::nonNull);
  }

  public synchronized boolean usesSonarCloud() {
    return Stream.concat(folderBindingCache.values().stream(), fileBindingCache.values().stream())
      .filter(Objects::nonNull)
      .anyMatch(binding -> settingsManager.getCurrentSettings().getServers().get(binding.getServerId()).isSonarCloudAlias());
  }

  @Override
  public synchronized void onChange(@CheckForNull WorkspaceFolderWrapper folder, @CheckForNull WorkspaceFolderSettings oldValue, WorkspaceFolderSettings newValue) {
    if (oldValue == null) {
      return;
    }
    if (oldValue.hasBinding() && !newValue.hasBinding()) {
      unbind(folder);
    } else if (newValue.hasBinding() && (!Objects.equals(oldValue.getServerId(), newValue.getServerId()) || !Objects.equals(oldValue.getProjectKey(), newValue.getProjectKey()))) {
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
    for (URI file : new HashSet<>(fileBindingCache.keySet())) {
      fileBindingCache.put(file, null);
    }

    LOG.debug("All files outside workspace are now unbound");
    stopUnusedEngines();
    analysisManager.analyzeAllOpenFilesInFolder(null);
  }

  private void unbindFolder(WorkspaceFolderWrapper folder) {
    folderBindingCache.put(folder.getUri(), null);
    LOG.debug("Workspace '{}' unbound", folder);
    stopUnusedEngines();
    analysisManager.analyzeAllOpenFilesInFolder(folder);
  }

  private synchronized void stopUnusedEngines() {
    Set<String> usedServerIds = new HashSet<>();
    WorkspaceFolderSettings folderSettings = settingsManager.getCurrentDefaultFolderSettings();
    collectUsedServerId(usedServerIds, folderSettings);
    foldersManager.getAll().forEach(w -> collectUsedServerId(usedServerIds, w.getSettings()));
    Set<String> startedEngines = new HashSet<>(connectedEngineCacheByServerId.keySet());
    for (String startedEngineId : startedEngines) {
      if (!usedServerIds.contains(startedEngineId)) {
        folderBindingCache.entrySet().removeIf(e -> e.getValue() != null && e.getValue().getServerId().equals(startedEngineId));
        fileBindingCache.entrySet().removeIf(e -> e.getValue() != null && e.getValue().getServerId().equals(startedEngineId));
        tryStopServer(startedEngineId, connectedEngineCacheByServerId.remove(startedEngineId));
      }
    }
  }

  private void collectUsedServerId(Set<String> usedServerIds, WorkspaceFolderSettings folderSettings) {
    if (folderSettings.hasBinding()) {
      String serverId = folderSettings.getServerId();
      if (serverId != null && settingsManager.getCurrentSettings().getServers().containsKey(serverId)) {
        usedServerIds.add(serverId);
      }
    }
  }

  @Override
  public synchronized void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    if (oldValue == null) {
      return;
    }
    stopUnusedEngines();
  }

  public void shutdown() {
    connectedEngineCacheByServerId.entrySet().forEach(entry -> tryStopServer(entry.getKey(), entry.getValue()));
  }

  private static void tryStopServer(String serverId, ConnectedSonarLintEngine engine) {
    try {
      engine.stop(false);
    } catch (Exception e) {
      LOG.error("Unable to stop engine '" + serverId + "'", e);
    }
  }

  public synchronized void updateAllBindings() {
    // Clear cached bindings to force rebind during next analysis
    folderBindingCache.clear();
    fileBindingCache.clear();

    // Start by updating all engines that are already started and cached
    connectedEngineCacheByServerId.entrySet().forEach(e -> {
      String serverId = e.getKey();
      ServerConfiguration serverConfiguration = createServerConfiguration(serverId);
      if (serverConfiguration != null) {
        e.getValue().update(serverConfiguration, null);
      }
    });
    updateBindingIfNecessary(null);

    foldersManager.getAll().forEach(this::updateBindingIfNecessary);

    client.showMessage(new MessageParams(MessageType.Info, "All SonarLint bindings succesfully updated"));
  }

  private void updateBindingIfNecessary(@Nullable WorkspaceFolderWrapper folder) {
    WorkspaceFolderSettings folderSettings = folder != null ? folder.getSettings() : settingsManager.getCurrentDefaultFolderSettings();
    if (folderSettings.hasBinding()) {
      String serverId = requireNonNull(folderSettings.getServerId());
      ServerConfiguration serverConfiguration = createServerConfiguration(serverId);
      if (!connectedEngineCacheByServerId.containsKey(serverId)) {
        startAndUpdateEngine(serverId, serverConfiguration);
      }
      if (serverConfiguration != null && connectedEngineCacheByServerId.containsKey(serverId)) {
        connectedEngineCacheByServerId.get(serverId).updateProject(serverConfiguration, requireNonNull(folderSettings.getProjectKey()), null);
      }
      analysisManager.analyzeAllOpenFilesInFolder(folder);
    }
  }

  private void startAndUpdateEngine(String serverId, @Nullable ServerConfiguration serverConfiguration) {
    ConnectedSonarLintEngine engine = enginesFactory.createConnectedEngine(serverId);
    if (serverConfiguration != null) {
      try {
        updateGlobalStorageAndLogResults(serverConfiguration, engine);
      } catch (Exception e) {
        LOG.error("Error updating storage of the connected SonarLint engine '" + serverId + "'", e);
      }
    }
    connectedEngineCacheByServerId.put(serverId, engine);
  }

  private static void updateGlobalStorageAndLogResults(ServerConfiguration serverConfiguration, ConnectedSonarLintEngine engine) {
    UpdateResult updateResult = engine.update(serverConfiguration, null);
    LOG.info("Global storage status: {}", updateResult.status());
  }
}
