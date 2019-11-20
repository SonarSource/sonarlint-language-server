/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.UpdateResult;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.ls.SonarLintLanguageServer;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
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
  private final LanguageClientLogOutput clientLogOutput;
  private final Function<ConnectedGlobalConfiguration, ConnectedSonarLintEngine> engineFactory;
  private Path typeScriptPath;

  public ProjectBindingManager(WorkspaceFoldersManager foldersManager, SettingsManager settingsManager, LanguageClientLogOutput clientLogOutput) {
    this(foldersManager, settingsManager, clientLogOutput, ConnectedSonarLintEngineImpl::new);
  }

  // For testing
  ProjectBindingManager(WorkspaceFoldersManager foldersManager, SettingsManager settingsManager, LanguageClientLogOutput clientLogOutput,
    Function<ConnectedGlobalConfiguration, ConnectedSonarLintEngine> engineFactory) {
    this.foldersManager = foldersManager;
    this.settingsManager = settingsManager;
    this.clientLogOutput = clientLogOutput;
    this.engineFactory = engineFactory;
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
      engine = createConnectedEngine(serverId);
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

  private ConnectedSonarLintEngine createConnectedEngine(String serverId) {
    Map<String, String> extraProperties = new HashMap<>();
    if (typeScriptPath != null) {
      extraProperties.put(SonarLintLanguageServer.TYPESCRIPT_PATH_PROP, typeScriptPath.toString());
    }
    ConnectedGlobalConfiguration configuration = ConnectedGlobalConfiguration.builder()
      .setServerId(serverId)
      .setExtraProperties(extraProperties)
      .addExcludedCodeAnalyzers("abap", "cpp", "cobol", "java", "kotlin", "pli", "rpg", "ruby", "sonarscala", "swift", "tsql", "xml")
      .setLogOutput(clientLogOutput)
      .build();

    ConnectedSonarLintEngine engine = engineFactory.apply(configuration);

    LOG.debug("Connected SonarLint engine started for '{}'", serverId);
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
      if (folder != null && folderBindingCache.containsKey(folder.getUri())) {
        unbindFolder(folder);
      } else if (folder == null && !fileBindingCache.isEmpty()) {
        unbindFiles();
      }
    } else if (newValue.hasBinding() && (!Objects.equals(oldValue.getServerId(), newValue.getServerId()) || !Objects.equals(oldValue.getProjectKey(), newValue.getProjectKey()))) {
      if (folder != null && folderBindingCache.containsKey(folder.getUri())) {
        clearFolderBindingCache(folder);
      } else if (folder == null && !fileBindingCache.isEmpty()) {
        clearFilesBindingCache();
      }
    }
  }

  /**
   * Clear cache of binding, so that it gets recomputed during next analysis
   */
  private void clearFilesBindingCache() {
    fileBindingCache.clear();
    stopUnusedEngines();
    // TODO trigger analysis of all open files not part of any workspace folder
  }

  private void clearFolderBindingCache(WorkspaceFolderWrapper folder) {
    folderBindingCache.remove(folder.getUri());
    stopUnusedEngines();
    // TODO trigger analysis of all open files in the workspace folder
  }

  private void unbindFiles() {
    for (URI file : new HashSet<>(fileBindingCache.keySet())) {
      fileBindingCache.put(file, null);
    }

    LOG.debug("All files outside workspace are now unbound");
    stopUnusedEngines();
    // TODO trigger analysis of all open files not part of any workspace folder
  }

  private void unbindFolder(WorkspaceFolderWrapper folder) {
    folderBindingCache.put(folder.getUri(), null);
    LOG.debug("Workspace '{}' unbound", folder);
    stopUnusedEngines();
    // TODO trigger analysis of all open files in the workspace folder
  }

  private synchronized void stopUnusedEngines() {
    Set<String> usedServerIds = new HashSet<>();
    String serverId = settingsManager.getCurrentDefaultFolderSettings().getServerId();
    if (serverId != null) {
      usedServerIds.add(serverId);
    }
    foldersManager.getAll().forEach(w -> {
      String folderServerId = w.getSettings().getServerId();
      if (folderServerId != null) {
        usedServerIds.add(folderServerId);
      }
    });
    Set<String> startedEngines = new HashSet<>(connectedEngineCacheByServerId.keySet());
    for (String startedEngineId : startedEngines) {
      if (!usedServerIds.contains(startedEngineId)) {
        connectedEngineCacheByServerId.remove(startedEngineId).stop(false);
      }
    }
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    if (oldValue == null) {
      return;
    }
    // TODO Detect changes of server configuration and stop engines accordingly

  }

  public void shutdown() {
    connectedEngineCacheByServerId.entrySet().forEach(entry -> {
      try {
        entry.getValue().stop(false);
      } catch (Exception e) {
        LOG.error("Unable to stop engine '" + entry.getKey() + "'", e);
      }
    });
  }

  public void initialize(Path typeScriptPath) {
    this.typeScriptPath = typeScriptPath;
  }

  public void updateAllBindings() {
    // Start by updating all engines that are already started and cached
    connectedEngineCacheByServerId.entrySet().forEach(e -> {
      String serverId = e.getKey();
      ServerConfiguration serverConfiguration = createServerConfiguration(serverId);
      if (serverConfiguration != null) {
        e.getValue().update(serverConfiguration, null);
      }
    });
    WorkspaceFolderSettings defaultFolderSettings = settingsManager.getCurrentDefaultFolderSettings();
    updateBindingIfNecessary(defaultFolderSettings);

    foldersManager.getAll().forEach(w -> {
      updateBindingIfNecessary(w.getSettings());
    });

  }

  private void updateBindingIfNecessary(WorkspaceFolderSettings defaultFolderSettings) {
    if (defaultFolderSettings.hasBinding()) {
      String serverId = requireNonNull(defaultFolderSettings.getServerId());
      ServerConfiguration serverConfiguration = createServerConfiguration(serverId);
      if (!connectedEngineCacheByServerId.containsKey(serverId)) {
        startAndUpdateEngine(serverId, serverConfiguration);
      }
      if (serverConfiguration != null && connectedEngineCacheByServerId.containsKey(serverId)) {
        connectedEngineCacheByServerId.get(serverId).updateProject(serverConfiguration, requireNonNull(defaultFolderSettings.getProjectKey()), null);
      }
    }
  }

  private void startAndUpdateEngine(String serverId, @Nullable ServerConfiguration serverConfiguration) {
    ConnectedSonarLintEngine engine = createConnectedEngine(serverId);
    if (serverConfiguration != null) {
      try {
        updateGlobalStorageAndLogResults(serverConfiguration, engine);
      } catch (Exception e) {
        LOG.error("Error updating storage of the connected SonarLint engine '" + serverId + "'", e);
      }
    }
    connectedEngineCacheByServerId.put(serverId, engine);
  }

  private void updateGlobalStorageAndLogResults(ServerConfiguration serverConfiguration, ConnectedSonarLintEngine engine) {
    UpdateResult updateResult = engine.update(serverConfiguration, null);
    LOG.info("Global storage status: {}", updateResult.status());
  }
}
