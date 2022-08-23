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
package org.sonarsource.sonarlint.ls.connected;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
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
import org.sonarsource.sonarlint.ls.util.FileUtils;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;

/**
 * Keep a cache of project bindings. Files that are part of a workspace workspaceFolderPath will share the same binding.
 * Files that are opened alone will have their own binding.
 */
public class ProjectBindingManager implements WorkspaceSettingsChangeListener, WorkspaceFolderSettingsChangeListener {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final WorkspaceFoldersManager foldersManager;
  private final SettingsManager settingsManager;
  private final ConcurrentMap<URI, Optional<ProjectBindingWrapper>> folderBindingCache;
  private final LanguageClientLogOutput globalLogOutput;
  private final ConcurrentMap<URI, Optional<ProjectBindingWrapper>> fileBindingCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Optional<ConnectedSonarLintEngine>> connectedEngineCacheByConnectionId = new ConcurrentHashMap<>();
  private final ProgressManager progressManager;
  private final SonarLintExtendedLanguageClient client;
  private final EnginesFactory enginesFactory;
  private AnalysisScheduler analysisManager;
  private final long syncPeriod;
  private final Timer bindingUpdatesCheckerTimer = new Timer("Binding updates checker");
  private Function<URI, String> branchNameForFolderSupplier;

  public ProjectBindingManager(EnginesFactory enginesFactory, WorkspaceFoldersManager foldersManager, SettingsManager settingsManager,
    SonarLintExtendedLanguageClient client, ProgressManager progressManager, LanguageClientLogOutput globalLogOutput) {
    this(enginesFactory, foldersManager, settingsManager, client, progressManager, new ConcurrentHashMap<>(), globalLogOutput);
    bindingUpdatesCheckerTimer.scheduleAtFixedRate(new BindingUpdatesCheckerTask(), 10 * 1000L, syncPeriod);
  }

  public ProjectBindingManager(EnginesFactory enginesFactory, WorkspaceFoldersManager foldersManager, SettingsManager settingsManager,
    SonarLintExtendedLanguageClient client, ProgressManager progressManager,
    ConcurrentMap<URI, Optional<ProjectBindingWrapper>> folderBindingCache, @Nullable LanguageClientLogOutput globalLogOutput) {
    this.enginesFactory = enginesFactory;
    this.foldersManager = foldersManager;
    this.settingsManager = settingsManager;
    this.client = client;
    this.progressManager = progressManager;
    this.folderBindingCache = folderBindingCache;
    this.globalLogOutput = globalLogOutput;
    this.syncPeriod = Long.parseLong(StringUtils.defaultIfBlank(System.getenv("SONARLINT_INTERNAL_SYNC_PERIOD"), "3600")) * 1000;
  }

  // Can't use constructor injection because of cyclic dependency
  public void setAnalysisManager(AnalysisScheduler analysisManager) {
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
    var folder = foldersManager.findFolderForFile(fileUri);
    var cacheKey = folder.map(WorkspaceFolderWrapper::getUri).orElse(fileUri);
    return getBinding(folder, cacheKey);
  }

  private Optional<ProjectBindingWrapper> getBinding(Optional<WorkspaceFolderWrapper> folder, URI fileUri) {
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

  private Optional<ConnectedSonarLintEngine> getStartedConnectedEngine(String connectionId) {
    return connectedEngineCacheByConnectionId.getOrDefault(connectionId, Optional.empty());
  }

  void syncStorage() {
    var projectKeysPerConnectionId = new HashMap<String, Set<String>>();
    forEachBoundFolder((folder, settings) -> {
      var connectionId = requireNonNull(settings.getConnectionId());
      var projectKey = requireNonNull(settings.getProjectKey());
      projectKeysPerConnectionId.computeIfAbsent(connectionId, k -> new HashSet<>()).add(projectKey);
    });
    if (!projectKeysPerConnectionId.isEmpty()) {
      LOG.debug("Synchronizing storages...");
      projectKeysPerConnectionId.forEach((connectionId, projectKeys) -> getStartedConnectedEngine(connectionId)
        .ifPresent(engine -> syncOneEngine(connectionId, projectKeys, engine, null)));
    }
  }

  private void syncOneEngine(String connectionId, Set<String> projectKeys, ConnectedSonarLintEngine engine, @Nullable ProgressFacade progress) {
    try {
      var paramsAndHttpClient = getServerConfigurationFor(connectionId);
      if (paramsAndHttpClient == null) {
        return;
      }
      engine.sync(paramsAndHttpClient.getEndpointParams(), paramsAndHttpClient.getHttpClient(), projectKeys, progress != null ? progress.asCoreMonitor() : null);
    } catch (Exception e) {
      LOG.error("Error while synchronizing storage", e);
    }
  }

  @CheckForNull
  private ProjectBindingWrapper computeProjectBinding(WorkspaceFolderSettings settings, Path folderRoot) {
    var connectionId = requireNonNull(settings.getConnectionId());
    var endpointParamsAndHttpClient = getServerConfigurationFor(connectionId);

    if (endpointParamsAndHttpClient == null) {
      LOG.error("Invalid binding for '{}'", folderRoot);
      return null;
    }
    var engineOpt = getOrCreateConnectedEngine(connectionId);
    if (engineOpt.isEmpty()) {
      return null;
    }
    var engine = engineOpt.get();
    var projectKey = requireNonNull(settings.getProjectKey());
    engine.updateProject(endpointParamsAndHttpClient.getEndpointParams(), endpointParamsAndHttpClient.getHttpClient(), projectKey, null);
    engine.sync(endpointParamsAndHttpClient.getEndpointParams(), endpointParamsAndHttpClient.getHttpClient(), Set.of(projectKey), null);

    var ideFilePaths = FileUtils.allRelativePathsForFilesInTree(folderRoot);
    var projectBinding = engine.calculatePathPrefixes(projectKey, ideFilePaths);
    LOG.debug("Resolved binding {} for folder {}",
      ToStringBuilder.reflectionToString(projectBinding, ToStringStyle.SHORT_PREFIX_STYLE),
      folderRoot);
    Supplier<String> branchProvider = () -> this.branchNameForFolderSupplier.apply(folderRoot.toUri());
    var issueTrackerWrapper = new ServerIssueTrackerWrapper(engine, endpointParamsAndHttpClient, projectBinding, branchProvider);
    return new ProjectBindingWrapper(connectionId, projectBinding, engine, issueTrackerWrapper);
  }

  @CheckForNull
  public EndpointParamsAndHttpClient getServerConfigurationFor(@Nullable String connectionId) {
    return Optional.ofNullable(getServerConnectionSettingsFor(connectionId))
      .map(ServerConnectionSettings::getServerConfiguration)
      .orElse(null);
  }

  @CheckForNull
  private ServerConnectionSettings getServerConnectionSettingsFor(@Nullable String maybeConnectionId) {
    var connectionId = SettingsManager.connectionIdOrDefault(maybeConnectionId);
    var allConnections = settingsManager.getCurrentSettings().getServerConnections();
    var serverConnectionSettings = allConnections.get(connectionId);
    if (serverConnectionSettings == null) {
      LOG.error("The specified connection id '{}' doesn't exist.", connectionId);
      return null;
    }
    return serverConnectionSettings;
  }

  private Optional<ConnectedSonarLintEngine> getOrCreateConnectedEngine(String connectionId) {
    return connectedEngineCacheByConnectionId.computeIfAbsent(connectionId,
      s -> Optional.ofNullable(createConnectedEngine(connectionId)));
  }

  @CheckForNull
  private ConnectedSonarLintEngine createConnectedEngine(String connectionId) {
    LOG.debug("Starting connected SonarLint engine for '{}'...", connectionId);

    ConnectedSonarLintEngine engine;
    try {
      var serverConnectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
      engine = enginesFactory.createConnectedEngine(connectionId, serverConnectionSettings);
    } catch (Exception e) {
      LOG.error("Error starting connected SonarLint engine for '" + connectionId + "'", e);
      return null;
    }
    subscribeForServerEvents(connectionId, engine);
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

  public void subscribeForServerEvents(String connectionId) {
    var engine = getStartedConnectedEngine(connectionId);
    engine.ifPresent(e -> subscribeForServerEvents(connectionId, e));
  }

  private void subscribeForServerEvents(String connectionId, ConnectedSonarLintEngine engine) {
    var configuration = getServerConfigurationFor(connectionId);
    if (configuration != null) {
      engine.subscribeForEvents(configuration.getEndpointParams(), configuration.getHttpClient(),
        getProjectKeysBoundTo(connectionId), event -> {}, globalLogOutput);
    }
  }

  public void subscribeForServerEvents(List<WorkspaceFolderWrapper> added, List<WorkspaceFolderWrapper> removed) {
    var impactedConnectionsIds = added.stream().map(this::getBinding)
      .filter(Optional::isPresent).map(Optional::get).map(ProjectBindingWrapper::getConnectionId).collect(Collectors.toCollection(HashSet::new));
    impactedConnectionsIds.addAll(removed.stream().map(this::getBinding)
      .filter(Optional::isPresent).map(Optional::get).map(ProjectBindingWrapper::getConnectionId).collect(Collectors.toSet()));
    impactedConnectionsIds.forEach(this::subscribeForServerEvents);
  }

  private Set<String> getProjectKeysBoundTo(String connectionId) {
    Set<String> projectKeys = new HashSet<>();
    forEachBoundFolder((folder, settings) -> {
      if (folder != null && connectionId.equals(folder.getSettings().getConnectionId())) {
        projectKeys.add(folder.getSettings().getProjectKey());
      }
    });
    return projectKeys;
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
    if (oldValue == null) {
      return;
    }
    newValue.getServerConnections().forEach((id, value) -> {
      var oldConnection = oldValue.getServerConnections().get(id);
      if (oldConnection != null && !oldConnection.equals(value)) {
        // Settings of the connection have been changed. Remove all cached bindings and force close the engine
        clearCachesAndStopEngine(id);
      }
      if (oldConnection == null || !oldConnection.equals(value)) {
        // New connection or changed settings. Validate connection
        validateConnection(id);
      }
    });
    stopUnusedEngines();
  }

  void validateConnection(String id) {
    Optional.ofNullable(getServerConfigurationFor(id))
      .map(EndpointParamsAndHttpClient::validateConnection)
      .ifPresent(validationFuture -> validationFuture.thenAccept(validationResult -> {
        var connectionCheckResult = validationResult.success() ?
          ConnectionCheckResult.success(id) :
          ConnectionCheckResult.failure(id, validationResult.message());
        client.reportConnectionCheckResult(connectionCheckResult);
      }));
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

  private void updateBindings(Map<String, Set<String>> projectKeyByConnectionIdsToUpdate, ProgressFacade progress) {
    var failedConnectionIds = tryUpdateConnectionsAndBoundProjectStorages(projectKeyByConnectionIdsToUpdate, progress);
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
      var endpointParamsAndHttpClient = getServerConfigurationFor(connectionId);
      if (endpointParamsAndHttpClient == null) {
        failedConnectionIds.add(connectionId);
        return;
      }
      var engineOpt = getOrCreateConnectedEngine(connectionId);
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

  private static void tryUpdateBoundProjectsStorage(Set<String> projectKeys, EndpointParamsAndHttpClient endpointParamsAndHttpClient, ConnectedSonarLintEngine engine,
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

  private Map<String, Set<String>> collectConnectionsAndProjectsToUpdate() {
    var projectKeyByConnectionIdsToUpdate = new HashMap<String, Set<String>>();
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
    var defaultFolderSettings = settingsManager.getCurrentDefaultFolderSettings();
    if (defaultFolderSettings.hasBinding()) {
      boundFolderConsumer.accept(null, defaultFolderSettings);
    }
    foldersManager.getAll().forEach(f -> {
      var settings = f.getSettings();
      if (settings.hasBinding()) {
        boundFolderConsumer.accept(f, settings);
      }
    });

  }

  Optional<EndpointParamsAndHttpClient> getServerConnectionSettingsForUrl(String url) {
    return settingsManager.getCurrentSettings().getServerConnections()
      .values()
      .stream()
      .filter(it -> equalsIgnoringTrailingSlash(it.getServerUrl(), url))
      .findFirst()
      .map(ServerConnectionSettings::getServerConfiguration);
  }

  private static boolean equalsIgnoringTrailingSlash(String aString, String anotherString) {
    return withTrailingSlash(aString).equals(withTrailingSlash(anotherString));
  }

  private static String withTrailingSlash(String str) {
    if (!str.endsWith("/")) {
      return str + '/';
    }
    return str;
  }

  public Optional<URI> serverPathToFileUri(String serverPath) {
    return folderBindingCache.entrySet().stream()
      .filter(e -> e.getValue().isPresent())
      .map(e -> tryResolveLocalFile(serverPath, e.getKey(), e.getValue().get()))
      .flatMap(Optional::stream)
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

  public void setBranchResolver(Function<URI, String> getReferenceBranchNameForFolder) {
    this.branchNameForFolderSupplier = getReferenceBranchNameForFolder;
  }

  public String resolveBranchNameForFolder(URI folder) {
    return branchNameForFolderSupplier.apply(folder);
  }

  private class BindingUpdatesCheckerTask extends TimerTask {
    @Override
    public void run() {
      syncStorage();
    }
  }

  public Map<String, String> getRemoteProjects(@Nullable String maybeConnectionId) {
    var connectionId = SettingsManager.connectionIdOrDefault(maybeConnectionId);
    var serverConfiguration = getServerConfigurationFor(connectionId);
    if (serverConfiguration == null) {
      throw new IllegalArgumentException(String.format("No server configuration found with ID '%s'", connectionId));
    }
    var progress = new NoOpProgressFacade();
    var engine = getOrCreateConnectedEngine(connectionId)
      .orElseThrow(() -> new IllegalArgumentException(String.format("No connected engine found with ID '%s'", connectionId)));
    try {
      return engine.downloadAllProjects(serverConfiguration.getEndpointParams(), serverConfiguration.getHttpClient(), progress.asCoreMonitor())
        .values()
        .stream()
        .collect(Collectors.toMap(ServerProject::getKey, ServerProject::getName));
    } catch(DownloadException downloadFailed) {
      throw new IllegalStateException(String.format("Failed to fetch list of projects from '%s'", connectionId), downloadFailed);
    }
  }
}
