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
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
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
  private String typeScriptPath;

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
  public Optional<ProjectBindingWrapper> getBinding(URI fileUri) {
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
        ProjectBindingWrapper projectBindingWrapper = computeProjectBinding(settings, folderRoot, false);
        bindingCache.put(cacheKey, projectBindingWrapper);
      }
    }

    return Optional.ofNullable(bindingCache.get(cacheKey));
  }

  @CheckForNull
  private ProjectBindingWrapper computeProjectBinding(WorkspaceFolderSettings settings, Path folderRoot, boolean forceUpdateStorage) {
    String serverId = settings.getServerId();
    ServerConnectionSettings serverConnectionSettings = settingsManager.getCurrentSettings().getServers().get(serverId);
    if (serverConnectionSettings == null) {
      LOG.error("Invalid binding for '{}': the specified serverId '{}' doesn't exist.", folderRoot, serverId);
      return null;
    }
    ServerConfiguration serverConfiguration = getServerConfiguration(serverConnectionSettings);
    ConnectedSonarLintEngine engine = connectedEngineCacheByServerId.computeIfAbsent(serverId, s -> createConnectedEngine(serverId, serverConfiguration, forceUpdateStorage));
    if (engine == null) {
      return null;
    }
    String projectKey = settings.getProjectKey();
    ProjectStorageStatus projectStorageStatus = engine.getProjectStorageStatus(projectKey);
    if (forceUpdateStorage || projectStorageStatus == null || projectStorageStatus.isStale()) {
      engine.updateProject(serverConfiguration, projectKey, null);
    }
    Collection<String> ideFilePaths = FileUtils.allRelativePathsForFilesInTree(folderRoot);
    ProjectBinding projectBinding = engine.calculatePathPrefixes(projectKey, ideFilePaths);
    LOG.debug("Resolved sqPathPrefix:{} / idePathPrefix:{} / for workspaceFolderPath {}",
      projectBinding.sqPathPrefix(),
      projectBinding.idePathPrefix(),
      folderRoot);
    ServerIssueTrackerWrapper issueTrackerWrapper = new ServerIssueTrackerWrapper(engine, serverConfiguration, projectBinding);
    return new ProjectBindingWrapper(serverId, projectBinding, engine, issueTrackerWrapper);
  }

  @CheckForNull
  private ConnectedSonarLintEngine createConnectedEngine(String serverId, ServerConfiguration serverConfiguration, boolean forceUpdateStorage) {
    LOG.debug("Starting connected SonarLint engine for '{}'...", serverId);

    try {
      Map<String, String> extraProperties = new HashMap<>();
      extraProperties.put(SonarLintLanguageServer.TYPESCRIPT_PATH_PROP, typeScriptPath);
      ConnectedGlobalConfiguration configuration = ConnectedGlobalConfiguration.builder()
        .setServerId(serverId)
        .setExtraProperties(extraProperties)
        .addExcludedCodeAnalyzers("abap", "cpp", "cobol", "java", "kotlin", "pli", "rpg", "ruby", "sonarscala", "swift", "tsql", "xml")
        .setLogOutput(clientLogOutput)
        .build();

      ConnectedSonarLintEngine engine = engineFactory.apply(configuration);

      LOG.debug("Connected SonarLint engine started for '{}'", serverId);
      if (engine.getState() == State.UPDATING) {
        return engine;
      }

      GlobalStorageStatus globalStorageStatus = engine.getGlobalStorageStatus();
      if (forceUpdateStorage || globalStorageStatus == null || globalStorageStatus.isStale() || engine.getState() != State.UPDATED) {
        engine.update(serverConfiguration, null);
      }

      return engine;
    } catch (Exception e) {
      LOG.error("Error starting connected SonarLint engine for '" + serverId + "'", e);
    }
    return null;
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
    return Stream.concat(folderBindingCache.values().stream(), fileBindingCache.values().stream()).anyMatch(Objects::nonNull);
  }

  public boolean usesSonarCloud() {
    return Stream.concat(folderBindingCache.values().stream(), fileBindingCache.values().stream())
      .filter(Objects::nonNull)
      .anyMatch(binding -> settingsManager.getCurrentSettings().getServers().get(binding.getServerId()).isSonarCloudAlias());
  }

  @Override
  public void onChange(@CheckForNull WorkspaceFolderWrapper folder, @CheckForNull WorkspaceFolderSettings oldValue, WorkspaceFolderSettings newValue) {
    if (oldValue == null) {
      return;
    }
    Map<URI, ProjectBindingWrapper> bindingCache = folder != null ? folderBindingCache : fileBindingCache;
    if (oldValue.hasBinding() && !newValue.hasBinding()) {
      if (folder != null && folderBindingCache.containsKey(folder.getUri())) {
        unbindFolder(folder);
      } else if (folder == null && !fileBindingCache.isEmpty()) {
        unbindFiles();
      }
    } else if (newValue.hasBinding() && (!Objects.equals(oldValue.getServerId(), newValue.getServerId()) || !Objects.equals(oldValue.getProjectKey(), newValue.getProjectKey()))) {
      if (folder != null && folderBindingCache.containsKey(folder.getUri())) {
        // Rebind the workspaceFolderPath
        ProjectBindingWrapper projectBindingWrapper = computeProjectBinding(newValue, folder.getRootPath(), true);
        bindingCache.put(folder.getUri(), projectBindingWrapper);
        // TODO trigger analysis of all open files in the workspace workspaceFolderPath
      } else if (folder == null && !fileBindingCache.isEmpty()) {
        // Rebind all open files
        for (URI file : new HashSet<>(fileBindingCache.keySet())) {
          ProjectBindingWrapper projectBindingWrapper = computeProjectBinding(newValue, Paths.get(file).getParent(), true);
          fileBindingCache.put(file, projectBindingWrapper);
        }
        // TODO trigger analysis of all open files not part of any workspace workspaceFolderPath
      }
    }
  }

  private void unbindFiles() {
    for (URI file : new HashSet<>(fileBindingCache.keySet())) {
      fileBindingCache.put(file, null);
    }

    LOG.debug("All files outside workspace are now unbound");
    // TODO trigger analysis of all open files not part of any workspace workspaceFolderPath
  }

  private void unbindFolder(WorkspaceFolderWrapper folder) {
    folderBindingCache.put(folder.getUri(), null);
    LOG.debug("Workspace workspaceFolderPath {} unbound", folder);
    // TODO trigger analysis of all open files in the workspace workspaceFolderPath
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

  public void initialize(String typeScriptPath) {
    this.typeScriptPath = typeScriptPath;
  }

  public void updateAllBindings() {
    // TODO Auto-generated method stub

  }
}
