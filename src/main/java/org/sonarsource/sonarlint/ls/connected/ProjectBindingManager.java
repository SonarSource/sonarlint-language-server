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
package org.sonarsource.sonarlint.ls.connected;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.domain.TaintIssue;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.progress.NoOpProgressFacade;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.util.FileUtils;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static org.sonarsource.sonarlint.ls.util.FileUtils.getFileRelativePath;
import static org.sonarsource.sonarlint.ls.util.Utils.uriHasFileScheme;

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
  private final ConcurrentMap<String, Optional<ConnectedSonarLintEngine>> connectedEngineCacheByConnectionId;
  private final SonarLintExtendedLanguageClient client;
  private final EnginesFactory enginesFactory;
  private AnalysisScheduler analysisManager;
  private Function<URI, Optional<String>> branchNameForFolderSupplier;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final DiagnosticPublisher diagnosticPublisher;
  private final BackendServiceFacade backendServiceFacade;
  private final OpenNotebooksCache openNotebooksCache;

  public ProjectBindingManager(EnginesFactory enginesFactory, WorkspaceFoldersManager foldersManager, SettingsManager settingsManager, SonarLintExtendedLanguageClient client,
    LanguageClientLogOutput globalLogOutput, TaintVulnerabilitiesCache taintVulnerabilitiesCache, DiagnosticPublisher diagnosticPublisher,
    BackendServiceFacade backendServiceFacade, OpenNotebooksCache openNotebooksCache) {
    this(enginesFactory, foldersManager, settingsManager, client, new ConcurrentHashMap<>(), globalLogOutput, new ConcurrentHashMap<>(),
      taintVulnerabilitiesCache, diagnosticPublisher, backendServiceFacade,
      openNotebooksCache);
  }

  public ProjectBindingManager(EnginesFactory enginesFactory, WorkspaceFoldersManager foldersManager, SettingsManager settingsManager, SonarLintExtendedLanguageClient client,
    ConcurrentMap<URI, Optional<ProjectBindingWrapper>> folderBindingCache, @Nullable LanguageClientLogOutput globalLogOutput,
    ConcurrentMap<String, Optional<ConnectedSonarLintEngine>> connectedEngineCacheByConnectionId, TaintVulnerabilitiesCache taintVulnerabilitiesCache,
    DiagnosticPublisher diagnosticPublisher, BackendServiceFacade backendServiceFacade,
    OpenNotebooksCache openNotebooksCache) {
    this.enginesFactory = enginesFactory;
    this.foldersManager = foldersManager;
    this.settingsManager = settingsManager;
    this.client = client;
    this.folderBindingCache = folderBindingCache;
    this.globalLogOutput = globalLogOutput;
    this.connectedEngineCacheByConnectionId = connectedEngineCacheByConnectionId;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.diagnosticPublisher = diagnosticPublisher;
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
  public Optional<ProjectBindingWrapper> getBinding(WorkspaceFolderWrapper folder) {
    return getBinding(Optional.of(folder), folder.getUri());
  }

  /**
   * Return the binding of the given file.
   *
   * @return empty if the file is unbound
   */
  public Optional<ProjectBindingWrapper> getBinding(URI fileUri) {
    if (!uriHasFileScheme(fileUri) || openNotebooksCache.isNotebook(fileUri)) {
      if (globalLogOutput != null) {
        globalLogOutput.log("Ignoring connected mode settings for unsupported URI: " + fileUri, ClientLogOutput.Level.DEBUG);
      }
      return Optional.empty();
    }
    var folder = foldersManager.findFolderForFile(fileUri);
    var cacheKey = folder.map(WorkspaceFolderWrapper::getUri).orElse(fileUri);
    return getBinding(folder, cacheKey);
  }

  public void getBindingAndRepublishTaints(WorkspaceFolderWrapper folder) {
    getBindingAndRepublishTaints(Optional.of(folder), folder.getUri());
  }

  public Optional<ProjectBindingWrapper> getBindingAndRepublishTaints(URI fileUri) {
    var folder = foldersManager.findFolderForFile(fileUri);
    var cacheKey = folder.map(WorkspaceFolderWrapper::getUri).orElse(fileUri);
    return getBindingAndRepublishTaints(folder, cacheKey);
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

  private Optional<ProjectBindingWrapper> getBindingAndRepublishTaints(Optional<WorkspaceFolderWrapper> folder, URI fileUri) {
    var maybeBinding = getBinding(folder, fileUri);
    maybeBinding.ifPresent(binding ->
      folder.ifPresent(actualFolder ->
        updateAllTaintIssuesForOneFolder(actualFolder, binding.getBinding(), binding.getConnectionId())));
    return maybeBinding;
  }

  public Optional<ConnectedSonarLintEngine> getStartedConnectedEngine(String connectionId) {
    return connectedEngineCacheByConnectionId.getOrDefault(connectionId, Optional.empty());
  }

  @CheckForNull
  private ProjectBindingWrapper computeProjectBinding(WorkspaceFolderSettings settings, Path folderRoot) {
    var connectionId = requireNonNull(settings.getConnectionId());
    var endpointParams = getEndpointParamsFor(connectionId);

    if (endpointParams == null) {
      LOG.error("Invalid binding for '{}'", folderRoot);
      return null;
    }
    var engineOpt = getOrCreateConnectedEngine(connectionId);
    if (engineOpt.isEmpty()) {
      return null;
    }
    var engine = engineOpt.get();
    var projectKey = requireNonNull(settings.getProjectKey());
    Supplier<String> branchProvider = () -> resolveBranchNameForFolder(folderRoot.toUri(), engine, projectKey);
    var httpClient = backendServiceFacade.getHttpClient(connectionId);
    syncAtStartup(engine, endpointParams, projectKey, branchProvider, httpClient);

    var ideFilePaths = FileUtils.allRelativePathsForFilesInTree(folderRoot);
    var projectBinding = engine.calculatePathPrefixes(projectKey, ideFilePaths);
    LOG.debug("Resolved binding {} for folder {}",
      ToStringBuilder.reflectionToString(projectBinding, ToStringStyle.SHORT_PREFIX_STYLE),
      folderRoot);
    var issueTrackerWrapper = new ServerIssueTrackerWrapper(engine, endpointParams, projectBinding, branchProvider, httpClient,
      backendServiceFacade, foldersManager);
    return new ProjectBindingWrapper(connectionId, projectBinding, engine, issueTrackerWrapper);
  }

  private static void syncAtStartup(ConnectedSonarLintEngine engine, EndpointParams endpointParams, String projectKey,
    Supplier<String> branchProvider, HttpClient httpClient) {
    try {
      engine.updateProject(endpointParams, httpClient, projectKey, null);
      engine.sync(endpointParams, httpClient, Set.of(projectKey), null);
      var currentBranchName = branchProvider.get();
      engine.syncServerIssues(endpointParams, httpClient, projectKey, currentBranchName, null);
      engine.syncServerTaintIssues(endpointParams, httpClient, projectKey, currentBranchName, null);
      engine.syncServerHotspots(endpointParams, httpClient, projectKey, currentBranchName, null);
    } catch (Exception exceptionDuringSync) {
      LOG.warn("Exception happened during initial sync with project " + projectKey, exceptionDuringSync);
    }
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

  public Optional<ConnectedSonarLintEngine> getOrCreateConnectedEngine(String connectionId) {
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
    LOG.debug("All files outside workspace are now unbound");
    stopUnusedEngines();
    analysisManager.analyzeAllOpenFilesInFolder(null);
  }

  private void unbindFolder(WorkspaceFolderWrapper folder) {
    folderBindingCache.put(folder.getUri(), Optional.empty());
    LOG.debug("Workspace '{}' unbound", folder);
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
      .map(backendServiceFacade::validateConnection)
      .ifPresent(validationFuture -> validationFuture.thenAccept(validationResult -> {
        var connectionCheckResult = validationResult.isSuccess() ? ConnectionCheckResult.success(id) : ConnectionCheckResult.failure(id, validationResult.getMessage());
        client.reportConnectionCheckResult(connectionCheckResult);
      }));
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

  public Map<String, Map<String, Set<String>>> getActiveConnectionsAndProjects() {
    var projectKeyByConnectionIdsToUpdate = new HashMap<String, Map<String, Set<String>>>();
    // Update all engines that are already started and cached, even if no folders are bound
    connectedEngineCacheByConnectionId.keySet().forEach(id -> projectKeyByConnectionIdsToUpdate.computeIfAbsent(id, i -> new HashMap<>()));
    // Start and update all engines that used in a folder binding, even if not yet started
    forEachBoundFolder((folder, folderSettings) -> {
      var connectionId = requireNonNull(folderSettings.getConnectionId());
      var projectKey = requireNonNull(folderSettings.getProjectKey());
      getOrCreateConnectedEngine(connectionId).ifPresent(engine -> projectKeyByConnectionIdsToUpdate.computeIfAbsent(connectionId, id -> new HashMap<>())
        .computeIfAbsent(projectKey, k -> new HashSet<>())
        .add(resolveBranchNameForFolder(folder == null ? null : folder.getUri(), engine, projectKey)));
    });
    return projectKeyByConnectionIdsToUpdate;
  }

  public void forEachBoundFolder(BiConsumer<WorkspaceFolderWrapper, WorkspaceFolderSettings> boundFolderConsumer) {
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

  public void updateAllTaintIssues() {
    forEachBoundFolder((folder, folderSettings) -> {
      if (folder == null) {
        return;
      }
      getBindingAndRepublishTaints(folder);
    });
  }

  private void updateAllTaintIssuesForOneFolder(@Nullable WorkspaceFolderWrapper folder, ProjectBinding binding, String connectionId) {
    getStartedConnectedEngine(connectionId).ifPresent(engine -> {
      var branchName = resolveBranchNameForFolder(folder == null ? null : folder.getUri(), engine, binding.projectKey());
      engine.getAllServerTaintIssues(binding, branchName)
        .stream()
        .map(ServerTaintIssue::getFilePath)
        .distinct()
        .forEach(this::updateTaintIssueCacheFromStorageForServerPath);
    });
  }

  private void updateTaintIssueCacheFromStorageForServerPath(String filePathFromEvent) {
    LOG.debug("Re-publishing taint vulnerabilities for \"{}\"", filePathFromEvent);
    serverPathToFileUri(filePathFromEvent).ifPresent(this::updateTaintIssueCacheFromStorageForFile);
  }

  public void updateTaintIssueCacheFromStorageForFile(URI fileUri) {
    var workspaceFolder = foldersManager.findFolderForFile(fileUri);
    if (workspaceFolder.isPresent()) {
      var baseDir = workspaceFolder.get().getUri();
      var filePath = FileUtils.toSonarQubePath(getFileRelativePath(Paths.get(baseDir), fileUri));
      Optional<ProjectBindingWrapper> folderBinding = folderBindingCache.get(baseDir);
      if (folderBinding.isPresent()) {
        ProjectBindingWrapper bindingWrapper = folderBinding.get();
        var engine = bindingWrapper.getEngine();
        var folder = foldersManager.findFolderForFile(fileUri);
        var folderUri = folder.isPresent() ? folder.get().getUri() : URI.create("");
        var branchName = this.resolveBranchNameForFolder(folderUri, engine, bindingWrapper.getBinding().projectKey());
        var serverTaintIssues = engine.getServerTaintIssues(bindingWrapper.getBinding(), branchName, filePath, false);
        var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(bindingWrapper.getConnectionId());
        var isSonarCloud = connectionSettings != null && connectionSettings.isSonarCloudAlias();
        taintVulnerabilitiesCache.reload(fileUri, TaintIssue.from(serverTaintIssues, isSonarCloud));
        diagnosticPublisher.publishDiagnostics(fileUri, false);
      }
    }
  }

  public Optional<URI> serverPathToFileUri(String serverPath) {
    return folderBindingCache.entrySet().stream()
      .filter(e -> e.getValue().isPresent())
      .map(e -> tryResolveLocalFile(serverPath, e.getKey(), e.getValue().get()))
      .flatMap(Optional::stream)
      .map(File::toPath)
      .map(Path::toUri)
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

  public void setBranchResolver(Function<URI, Optional<String>> getReferenceBranchNameForFolder) {
    this.branchNameForFolderSupplier = getReferenceBranchNameForFolder;
  }

  public String resolveBranchNameForFolder(@Nullable URI folder, ConnectedSonarLintEngine engine, String projectKey) {
    return branchNameForFolderSupplier.apply(folder).orElse(engine.getServerBranches(projectKey).getMainBranchName());
  }

  public Map<String, String> getRemoteProjects(@Nullable String maybeConnectionId) {
    var connectionId = SettingsManager.connectionIdOrDefault(maybeConnectionId);
    var endpointParams = getEndpointParamsFor(connectionId);
    if (endpointParams == null) {
      throw new IllegalArgumentException(String.format("No server configuration found with ID '%s'", connectionId));
    }
    var progress = new NoOpProgressFacade();
    var engine = getOrCreateConnectedEngine(connectionId)
      .orElseThrow(() -> new IllegalArgumentException(String.format("No connected engine found with ID '%s'", connectionId)));
    try {
      var httpClient = backendServiceFacade.getHttpClient(connectionId);
      return engine.downloadAllProjects(endpointParams, httpClient, progress.asCoreMonitor())
        .values()
        .stream()
        .collect(Collectors.toMap(ServerProject::getKey, ServerProject::getName));
    } catch (DownloadException downloadFailed) {
      throw new IllegalStateException(String.format("Failed to fetch list of projects from '%s'", connectionId), downloadFailed);
    }
  }
}
