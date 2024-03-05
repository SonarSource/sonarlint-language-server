/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.util.URIUtils;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static org.sonarsource.sonarlint.ls.util.Utils.fixWindowsURIEncoding;
import static org.sonarsource.sonarlint.ls.util.Utils.interrupted;
import static org.sonarsource.sonarlint.ls.util.Utils.uriHasFileScheme;

/**
 * Keep a cache of project bindings. Files that are part of a workspace workspaceFolderPath will share the same binding.
 * Files that are opened alone will have their own binding.
 */
public class ProjectBindingManager implements WorkspaceSettingsChangeListener, WorkspaceFolderSettingsChangeListener {
  private final WorkspaceFoldersManager foldersManager;
  private final SettingsManager settingsManager;
  private final ConcurrentMap<URI, CountDownLatch> bindingUpdateQueue = new ConcurrentHashMap<>();
  private final ConcurrentMap<URI, Optional<ProjectBinding>> folderBindingCache;
  private final LanguageClientLogOutput globalLogOutput;
  private final ConcurrentMap<URI, Optional<ProjectBinding>> fileBindingCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Optional<SonarLintAnalysisEngine>> connectedEngineCacheByConnectionId;
  private final SonarLintExtendedLanguageClient client;
  private final EnginesFactory enginesFactory;
  private AnalysisScheduler analysisManager;
  private final BackendServiceFacade backendServiceFacade;
  private final OpenNotebooksCache openNotebooksCache;

  public ProjectBindingManager(EnginesFactory enginesFactory, WorkspaceFoldersManager foldersManager, SettingsManager settingsManager, SonarLintExtendedLanguageClient client,
    LanguageClientLogOutput globalLogOutput, BackendServiceFacade backendServiceFacade, OpenNotebooksCache openNotebooksCache) {
    this(enginesFactory, foldersManager, settingsManager, client, new ConcurrentHashMap<>(), globalLogOutput, new ConcurrentHashMap<>(),
      backendServiceFacade, openNotebooksCache);
  }

  public ProjectBindingManager(EnginesFactory enginesFactory, WorkspaceFoldersManager foldersManager, SettingsManager settingsManager, SonarLintExtendedLanguageClient client,
    ConcurrentMap<URI, Optional<ProjectBinding>> folderBindingCache, LanguageClientLogOutput globalLogOutput,
    ConcurrentMap<String, Optional<SonarLintAnalysisEngine>> connectedEngineCacheByConnectionId, BackendServiceFacade backendServiceFacade,
    OpenNotebooksCache openNotebooksCache) {
    this.enginesFactory = enginesFactory;
    this.foldersManager = foldersManager;
    this.settingsManager = settingsManager;
    this.client = client;
    this.folderBindingCache = folderBindingCache;
    this.globalLogOutput = globalLogOutput;
    this.connectedEngineCacheByConnectionId = connectedEngineCacheByConnectionId;
    this.backendServiceFacade = backendServiceFacade;
    this.openNotebooksCache = openNotebooksCache;
  }

  // Can't use constructor injection because of cyclic dependency
  public void setAnalysisManager(AnalysisScheduler analysisManager) {
    this.analysisManager = analysisManager;
  }

  public void clearBindingCache() {
    folderBindingCache.clear();
    fileBindingCache.clear();
  }

  /**
   * Return the binding of the given folder.
   *
   * @return empty if the folder is unbound
   */
  public Optional<ProjectBinding> getBinding(WorkspaceFolderWrapper folder) {
    return getBinding(Optional.of(folder), folder.getUri());
  }

  /**
   * Return the binding of the given file.
   *
   * @return empty if the file is unbound
   */
  public Optional<ProjectBinding> getBinding(URI fileUri) {
    if (!isUriValidAndNotNotebook(fileUri)) return Optional.empty();
    var folder = foldersManager.findFolderForFile(fileUri);
    var cacheKey = folder.map(WorkspaceFolderWrapper::getUri).orElse(fileUri);
    return getBinding(folder, cacheKey);
  }

  private boolean isUriValidAndNotNotebook(URI fileUri) {
    if (!uriHasFileScheme(fileUri) || openNotebooksCache.isNotebook(fileUri)) {
      if (globalLogOutput != null) {
        globalLogOutput.log("Ignoring connected mode settings for unsupported URI: " + fileUri, ClientLogOutput.Level.DEBUG);
      }
      return false;
    }
    return true;
  }

  /**
   * Return the binding of the given file.
   *
   * @return empty if the file is unbound
   */
  public Optional<ProjectBinding> getBindingIfExists(URI fileUri) {
    if (!isUriValidAndNotNotebook(fileUri)) return Optional.empty();
    var folder = foldersManager.findFolderForFile(fileUri);
    var cacheKey = folder.map(WorkspaceFolderWrapper::getUri).orElse(fileUri);
    return getBindingIfExists(folder, cacheKey);
  }

  private Optional<ProjectBinding> getBindingIfExists(Optional<WorkspaceFolderWrapper> folder, URI fileUri) {
    return folder.isPresent()
      ? folderBindingCache.getOrDefault(folder.get().getUri(), Optional.empty())
      : fileBindingCache.getOrDefault(fileUri, Optional.empty());
  }

  private Optional<ProjectBinding> getBinding(Optional<WorkspaceFolderWrapper> folder, URI fileUri) {
    var bindingCache = folder.isPresent() ? folderBindingCache : fileBindingCache;
    return bindingCache.computeIfAbsent(fileUri, k -> {
      var settings = folder.map(WorkspaceFolderWrapper::getSettings)
        .orElse(settingsManager.getCurrentDefaultFolderSettings());
      if (!settings.hasBinding()) {
        return Optional.empty();
      } else {
        var folderRoot = folder.map(WorkspaceFolderWrapper::getRootPath).orElse(Paths.get(fileUri).getParent());
        return Optional.ofNullable(computeProjectBinding(settings, folderRoot));
      }
    });
  }

  @CheckForNull
  private ProjectBinding computeProjectBinding(WorkspaceFolderSettings settings, Path folderRoot) {
    var connectionId = requireNonNull(settings.getConnectionId());
    var endpointParams = getEndpointParamsFor(connectionId);
    if (endpointParams == null) {
      globalLogOutput.error("Invalid binding for '%s'", folderRoot);
      return null;
    }
    var engineOpt = getOrCreateConnectedEngine(connectionId);
    if (engineOpt.isEmpty()) {
      return null;
    }
    var engine = engineOpt.get();
    var projectKey = requireNonNull(settings.getProjectKey());
    globalLogOutput.debug("Resolved binding %s for folder %s", projectKey, folderRoot);
    var issueTrackerWrapper = new ServerIssueTrackerWrapper(backendServiceFacade, foldersManager);
    return new ProjectBinding(connectionId, projectKey, engine, issueTrackerWrapper);
  }


  @CheckForNull
  public EndpointParams getEndpointParamsFor(@Nullable String connectionId) {
    return Optional.ofNullable(getServerConnectionSettingsFor(connectionId))
      .map(ServerConnectionSettings::getEndpointParams)
      .orElse(null);
  }

  @CheckForNull
  public ValidateConnectionParams getValidateConnectionParamsFor(@Nullable String connectionId) {
    return Optional.ofNullable(getServerConnectionSettingsFor(connectionId))
      .map(ServerConnectionSettings::getValidateConnectionParams)
      .orElse(null);
  }

  @CheckForNull
  public ServerConnectionSettings getServerConnectionSettingsFor(@Nullable String maybeConnectionId) {
    var connectionId = SettingsManager.connectionIdOrDefault(maybeConnectionId);
    var allConnections = settingsManager.getCurrentSettings().getServerConnections();
    var serverConnectionSettings = allConnections.get(connectionId);
    if (serverConnectionSettings == null) {
      globalLogOutput.error("The specified connection id '%s' doesn't exist.", connectionId);
      return null;
    }
    return serverConnectionSettings;
  }

  public Optional<SonarLintAnalysisEngine> getOrCreateConnectedEngine(String connectionId) {
    return connectedEngineCacheByConnectionId.computeIfAbsent(connectionId,
      s -> Optional.ofNullable(createConnectedEngine(connectionId)));
  }

  @CheckForNull
  private SonarLintAnalysisEngine createConnectedEngine(String connectionId) {
    globalLogOutput.debug("Starting connected SonarLint engine for '%s'...", connectionId);

    SonarLintAnalysisEngine engine;
    try {
      engine = enginesFactory.createEngine(connectionId);
    } catch (Exception e) {
      globalLogOutput.error("Error starting connected SonarLint engine for '" + connectionId + "'", e);
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

  public boolean smartNotificationsDisabled() {
    return hasAnyBindingThatMatch(ServerConnectionSettings::isSmartNotificationsDisabled);
  }

  private boolean hasAnyBindingThatMatch(Predicate<ServerConnectionSettings> predicate) {
    return Stream.concat(folderBindingCache.values().stream(), fileBindingCache.values().stream())
      .flatMap(Optional::stream)
      .map(binding -> settingsManager.getCurrentSettings().getServerConnections().get(binding.getConnectionId()))
      .anyMatch(predicate);
  }

  @Override
  public void onChange(@Nullable WorkspaceFolderWrapper folder, @Nullable WorkspaceFolderSettings oldValue, WorkspaceFolderSettings newValue) {
    if (oldValue == null) {
      return;
    }
    if (oldValue.hasBinding() && !newValue.hasBinding()) {
      unbind(folder);
    } else if (newValue.hasBinding()
      && (!Objects.equals(oldValue.getConnectionId(), newValue.getConnectionId()) || !Objects.equals(oldValue.getProjectKey(), newValue.getProjectKey()))) {
      forceRebindDuringNextAnalysis(folder);
      if (folder == null) return;
      var uri = fixWindowsURIEncoding(folder.getUri());
      bindingUpdateQueue.getOrDefault(uri, new CountDownLatch(1)).countDown();
      bindingUpdateQueue.remove(uri);
      var bindingConfigurationDto = new BindingConfigurationDto(newValue.getConnectionId(), newValue.getProjectKey(), false);
      var params = new DidUpdateBindingParams(folder.getUri().toString(), bindingConfigurationDto);
      backendServiceFacade.getBackendService().updateBinding(params);
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
    globalLogOutput.debug("All files outside workspace are now unbound");
    stopUnusedEngines();
    analysisManager.analyzeAllOpenFilesInFolder(null);
  }

  private void unbindFolder(WorkspaceFolderWrapper folder) {
    folderBindingCache.put(folder.getUri(), Optional.empty());
    globalLogOutput.debug("Workspace '%s' unbound", folder);
    stopUnusedEngines();
    analysisManager.analyzeAllOpenFilesInFolder(folder);
    var bindingConfigurationDto = new BindingConfigurationDto(null, null, false);
    var params = new DidUpdateBindingParams(folder.getUri().toString(), bindingConfigurationDto);
    backendServiceFacade.getBackendService().updateBinding(params);
  }

  private void stopUnusedEngines() {
    var usedServerIds = new HashSet<String>();
    var folderSettings = settingsManager.getCurrentDefaultFolderSettings();
    collectUsedServerId(usedServerIds, folderSettings);
    foldersManager.getAll().forEach(w -> collectUsedServerId(usedServerIds, w.getSettings()));
    var startedEngines = new HashSet<>(connectedEngineCacheByConnectionId.keySet());
    startedEngines.stream()
      .filter(not(usedServerIds::contains))
      .forEach(this::clearCachesAndStopEngine);
  }

  private void clearCachesAndStopEngine(String connectionId) {
    folderBindingCache.entrySet().removeIf(e -> e.getValue().isPresent() && e.getValue().get().getConnectionId().equals(connectionId));
    fileBindingCache.entrySet().removeIf(e -> e.getValue().isPresent() && e.getValue().get().getConnectionId().equals(connectionId));
    if (connectedEngineCacheByConnectionId.containsKey(connectionId)) {
      tryStopServer(connectionId, connectedEngineCacheByConnectionId.remove(connectionId));
    }
  }

  private void collectUsedServerId(Set<String> usedConnectionIds, WorkspaceFolderSettings folderSettings) {
    if (folderSettings.hasBinding()) {
      var connectionId = folderSettings.getConnectionId();
      if (connectionId != null && settingsManager.getCurrentSettings().getServerConnections().containsKey(connectionId)) {
        usedConnectionIds.add(connectionId);
      }
    }
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    newValue.getServerConnections().forEach((id, value) -> {
      if (oldValue == null) {
        // initial sync
        this.validateConnection(id);
      } else {
        var oldConnection = oldValue.getServerConnections().get(id);
        if (oldConnection != null && !oldConnection.equals(value)) {
          // Settings of the connection have been changed. Remove all cached bindings and force close the engine
          clearCachesAndStopEngine(id);
        }
        if (oldConnection == null || !oldConnection.equals(value)) {
          // New connection or changed settings. Validate connection
          validateConnection(id);
        }
      }
    });
    stopUnusedEngines();
  }

  public void validateConnection(String id) {
    Optional.ofNullable(getValidateConnectionParamsFor(id))
      .map(params -> backendServiceFacade.getBackendService().validateConnection(params))
      .ifPresent(validationFuture -> validationFuture.thenAccept(validationResult -> {
        var connectionCheckResult = validationResult.isSuccess() ? ConnectionCheckResult.success(id) : ConnectionCheckResult.failure(id, validationResult.getMessage());
        client.reportConnectionCheckResult(connectionCheckResult);
      }));
  }

  public void shutdown() {
    connectedEngineCacheByConnectionId.forEach(this::tryStopServer);
  }

  private void tryStopServer(String connectionId, Optional<SonarLintAnalysisEngine> engine) {
    engine.ifPresent(e -> {
      try {
        e.stop();
      } catch (Exception ex) {
        globalLogOutput.error("Unable to stop engine '" + connectionId + "'", ex);
      }
    });
  }


  public Optional<String> resolveBranchNameForFolder(URI folder) {
    var matchedSonarProjectBranch = backendServiceFacade.getBackendService().getMatchedSonarProjectBranch(folder.toString()).join().getMatchedSonarProjectBranch();
    return matchedSonarProjectBranch == null ? Optional.empty() : Optional.of(matchedSonarProjectBranch);
  }

  public Map<String, String> getRemoteProjects(@Nullable String maybeConnectionId) {
    var connectionId = SettingsManager.connectionIdOrDefault(maybeConnectionId);
    var endpointParams = getEndpointParamsFor(connectionId);
    if (endpointParams == null) {
      throw new IllegalArgumentException(String.format("No server configuration found with ID '%s'", connectionId));
    }
    try {
      var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
      var connectionParams = Utils.getValidateConnectionParamsForNewConnection(
        new SonarLintExtendedLanguageServer.ConnectionCheckParams(connectionSettings.getToken(),
          connectionSettings.getOrganizationKey(), connectionSettings.getServerUrl()));
      var allProjectsResponse = backendServiceFacade.getBackendService().getAllProjects(connectionParams.getTransientConnection()).get();
      return allProjectsResponse.getSonarProjects().stream().collect(Collectors.toMap(SonarProjectDto::getKey, SonarProjectDto::getName));
    } catch (DownloadException downloadFailed) {
      throw new IllegalStateException(String.format("Failed to fetch list of projects from '%s'", connectionId), downloadFailed);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      interrupted(e, globalLogOutput);
    }
    return Map.of();
  }

  public Optional<URI> fullFilePathFromRelative(Path ideFilePath, String connectionId, String projectKey) {
    AtomicReference<ProjectBinding> binding = new AtomicReference<>();
    AtomicReference<URI> folderUri = new AtomicReference<>();
    folderBindingCache.forEach((f, b) -> {
      if (b.isPresent()
        && b.get().getProjectKey().equals(projectKey)
        && b.get().getConnectionId().equals(connectionId)) {
        binding.set(b.get());
        folderUri.set(f);
      }
    });
    if (binding.get() != null) {
      return Optional.of(URIUtils.getFullFileUriFromFragments(folderUri.get().toString(), ideFilePath));
    }
    return Optional.empty();
  }
}
