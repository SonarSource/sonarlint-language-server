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
  private final ConcurrentMap<URI, Optional<ProjectBindingWrapper>> folderBindingCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<URI, Optional<ProjectBindingWrapper>> fileBindingCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Optional<ConnectedSonarLintEngine>> connectedEngineCacheByServerId = new ConcurrentHashMap<>();
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
    String serverId = requireNonNull(settings.getConnectionId());
    ServerConfiguration serverConfiguration = createServerConfiguration(serverId);
    if (serverConfiguration == null) {
      LOG.error("Invalid binding for '{}'", folderRoot);
      return null;
    }
    Optional<ConnectedSonarLintEngine> engineOpt = getOrCreateConnectedEngine(serverId, serverConfiguration, false);
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

  private Optional<ConnectedSonarLintEngine> getOrCreateConnectedEngine(String serverId, ServerConfiguration serverConfiguration, boolean forceUpdate) {
    return connectedEngineCacheByServerId.computeIfAbsent(serverId, s -> Optional.ofNullable(createConnectedEngineAndUpdateIfNeeded(serverId, serverConfiguration, forceUpdate)));
  }

  @CheckForNull
  private ConnectedSonarLintEngine createConnectedEngineAndUpdateIfNeeded(String serverId, ServerConfiguration serverConfiguration, boolean forceUpdate) {
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
    List<String> failedServerIds = new ArrayList<>();
    try {
      GlobalStorageStatus globalStorageStatus = engine.getGlobalStorageStatus();
      if (forceUpdate || globalStorageStatus == null || globalStorageStatus.isStale() || engine.getState() != State.UPDATED) {
        updateGlobalStorageAndLogResults(serverConfiguration, engine, failedServerIds, serverId);
      }
    } catch (Exception e) {
      LOG.error("Error updating storage of the connected SonarLint engine '" + serverId + "'", e);
    }
    if (!failedServerIds.isEmpty()) {
      client.showMessage(new MessageParams(MessageType.Error, "Binding update failed for the server: " + serverId + ". Look to the SonarLint output for details."));
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

  public boolean usesConnectedMode() {
    return Stream.concat(folderBindingCache.values().stream(), fileBindingCache.values().stream()).anyMatch(Optional::isPresent);
  }

  public boolean usesSonarCloud() {
    return Stream.concat(folderBindingCache.values().stream(), fileBindingCache.values().stream())
      .flatMap(o -> o.isPresent() ? Stream.of(o.get()) : Stream.empty())
      .anyMatch(binding -> settingsManager.getCurrentSettings().getServers().get(binding.getServerId()).isSonarCloudAlias());
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
    Set<String> startedEngines = new HashSet<>(connectedEngineCacheByServerId.keySet());
    for (String startedEngineId : startedEngines) {
      if (!usedServerIds.contains(startedEngineId)) {
        folderBindingCache.entrySet().removeIf(e -> e.getValue().isPresent() && e.getValue().get().getServerId().equals(startedEngineId));
        fileBindingCache.entrySet().removeIf(e -> e.getValue().isPresent() && e.getValue().get().getServerId().equals(startedEngineId));
        tryStopServer(startedEngineId, connectedEngineCacheByServerId.remove(startedEngineId));
      }
    }
  }

  private void collectUsedServerId(Set<String> usedServerIds, WorkspaceFolderSettings folderSettings) {
    if (folderSettings.hasBinding()) {
      String serverId = folderSettings.getConnectionId();
      if (serverId != null && settingsManager.getCurrentSettings().getServers().containsKey(serverId)) {
        usedServerIds.add(serverId);
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
    connectedEngineCacheByServerId.entrySet().forEach(entry -> tryStopServer(entry.getKey(), entry.getValue()));
  }

  private static void tryStopServer(String serverId, Optional<ConnectedSonarLintEngine> engine) {
    engine.ifPresent(e -> {
      try {
        e.stop(false);
      } catch (Exception ex) {
        LOG.error("Unable to stop engine '" + serverId + "'", ex);
      }
    });
  }

  public void updateAllBindings() {
    // Clear cached bindings to force rebind during next analysis
    folderBindingCache.clear();
    fileBindingCache.clear();
    Set<String> failedServerIds = new LinkedHashSet<>();
    // Start by updating all engines that are already started and cached
    connectedEngineCacheByServerId.forEach((serverId, value) -> {
      ServerConfiguration serverConfiguration = createServerConfiguration(serverId);
      if (serverConfiguration != null) {
        value.ifPresent(engine -> updateGlobalStorageAndLogResults(serverConfiguration, engine, failedServerIds, serverId));
      }
    });
    Map<String, Set<String>> updatedProjectsByServer = new HashMap<>();
    updateBindingIfNecessary(null, updatedProjectsByServer, failedServerIds);

    foldersManager.getAll().forEach(f -> updateBindingIfNecessary(f, updatedProjectsByServer, failedServerIds));
    if (failedServerIds.isEmpty()) {
      client.showMessage(new MessageParams(MessageType.Info, "All SonarLint bindings succesfully updated"));
    } else {
      String message = String.join(", ", failedServerIds);
      client.showMessage(new MessageParams(MessageType.Error, "Binding update failed for the following servers: " + message + ". Look to the SonarLint output for details."));
    }
  }

  private void updateBindingIfNecessary(@Nullable WorkspaceFolderWrapper folder, Map<String, Set<String>> updatedProjectsByServer, Collection<String> failedServerIds) {
    WorkspaceFolderSettings folderSettings = folder != null ? folder.getSettings() : settingsManager.getCurrentDefaultFolderSettings();
    Object folderId = folder != null ? folder.getRootPath() : "default folder";
    if (folderSettings.hasBinding()) {
      String serverId = requireNonNull(folderSettings.getConnectionId());
      String projectKey = requireNonNull(folderSettings.getProjectKey());
      Set<String> alreadyUpdatedProjects = updatedProjectsByServer.get(serverId);
      if (alreadyUpdatedProjects == null || !alreadyUpdatedProjects.contains(projectKey)) {
        ServerConfiguration serverConfiguration = createServerConfiguration(serverId);
        if (serverConfiguration == null) {
          LOG.error("Invalid binding for '{}'", folderId);
          return;
        }
        Optional<ConnectedSonarLintEngine> engineOpt = getOrCreateConnectedEngine(serverId, serverConfiguration, true);
        if (!engineOpt.isPresent()) {
          return;
        }
        try {
          engineOpt.get().updateProject(serverConfiguration, projectKey, null);
          updatedProjectsByServer.computeIfAbsent(serverId, s -> new HashSet<>()).add(projectKey);
        } catch(Exception updateFailed) {
          LOG.error("Binding update failed for folder '{}'", folderId, updateFailed);
          failedServerIds.add(serverId);
        }
      }
      analysisManager.analyzeAllOpenFilesInFolder(folder);
    }
  }

  private static void updateGlobalStorageAndLogResults(ServerConfiguration serverConfiguration, ConnectedSonarLintEngine engine, Collection<String> failedServerIds, String serverId) {
    try {
      UpdateResult updateResult = engine.update(serverConfiguration, null);
      LOG.info("Global storage status: {}", updateResult.status());
    } catch (Exception e) {
      LOG.error("Error updating storage of the connected SonarLint engine '" + serverId + "'", e);
      failedServerIds.add(serverId);
    }
  }
}
