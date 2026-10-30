/*
 * SonarLint Language Server
 * Copyright (C) SonarSource Sàrl
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetProjectNamesByKeyResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.serverconnection.DownloadException;
import org.sonarsource.sonarlint.ls.ForcedAnalysisCoordinator;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ConnectionCheckResult;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScopeManager;
import org.sonarsource.sonarlint.ls.settings.EndpointParams;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.sonarsource.sonarlint.ls.util.Utils.interrupted;
import static org.sonarsource.sonarlint.ls.util.Utils.uriHasFileScheme;

/**
 * Keep a cache of project bindings. Files that are part of a workspace workspaceFolderPath will share the same binding.
 * Files that are opened alone will have their own binding.
 */
public class ProjectBindingManager implements WorkspaceSettingsChangeListener, WorkspaceFolderSettingsChangeListener {
  private final WorkspaceFoldersManager foldersManager;
  private final SettingsManager settingsManager;
  private final LanguageClientLogger globalLogOutput;
  private final SonarLintExtendedLanguageClient client;
  private ForcedAnalysisCoordinator analysisManager;
  private final BackendServiceFacade backendServiceFacade;
  private final OpenNotebooksCache openNotebooksCache;
  private final ConfigurationScopeManager scopeManager;

  public ProjectBindingManager(WorkspaceFoldersManager foldersManager, SettingsManager settingsManager, SonarLintExtendedLanguageClient client,
    LanguageClientLogger globalLogOutput, BackendServiceFacade backendServiceFacade, OpenNotebooksCache openNotebooksCache,
    ConfigurationScopeManager scopeManager) {
    this.scopeManager = scopeManager;
    this.foldersManager = foldersManager;
    this.settingsManager = settingsManager;
    this.client = client;
    this.globalLogOutput = globalLogOutput;
    this.backendServiceFacade = backendServiceFacade;
    this.openNotebooksCache = openNotebooksCache;
  }

  // Can't use constructor injection because of cyclic dependency
  public void setAnalysisManager(ForcedAnalysisCoordinator analysisManager) {
    this.analysisManager = analysisManager;
  }

  /**
   * Return the binding of the given folder.
   *
   * @return empty if the folder is unbound
   */
  public Optional<ProjectBinding> getBinding(WorkspaceFolderWrapper folder) {
    return scopeManager.bindingOf(folder);
  }

  /**
   * Return the binding of the given file.
   *
   * @return empty if the file is unbound
   */
  public Optional<ProjectBinding> getBinding(URI fileUri) {
    if (!isUriValidAndNotNotebook(fileUri)) return Optional.empty();
    return foldersManager.findFolderForFile(fileUri).flatMap(scopeManager::bindingOf);
  }

  private boolean isUriValidAndNotNotebook(URI fileUri) {
    if (!uriHasFileScheme(fileUri) || openNotebooksCache.isNotebook(fileUri)) {
      if (globalLogOutput != null) {
        globalLogOutput.log("Ignoring connected mode settings for unsupported URI: " + fileUri, LogLevel.DEBUG);
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
    return foldersManager.findFolderForFile(fileUri).flatMap(scopeManager::bindingIfExists);
  }

  /**
   * Compute the binding of the given folder from its settings. The scope manager caches it.
   */
  public Optional<ProjectBinding> resolveFolderBinding(WorkspaceFolderWrapper folder) {
    var settings = folder.getSettings();
    if (!settings.hasBinding()) {
      return Optional.empty();
    }
    return Optional.ofNullable(computeProjectBinding(settings, folder.getRootPath()));
  }

  @CheckForNull
  private ProjectBinding computeProjectBinding(WorkspaceFolderSettings settings, Path folderRoot) {
    var connectionId = requireNonNull(settings.getConnectionId());
    var endpointParams = getEndpointParamsFor(connectionId);
    if (endpointParams == null) {
      globalLogOutput.error(format("Invalid binding for '%s'", folderRoot));
      return null;
    }

    var projectKey = requireNonNull(settings.getProjectKey());
    globalLogOutput.debug(format("Resolved binding %s for folder %s", projectKey, folderRoot));
    return new ProjectBinding(connectionId, projectKey);
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
      globalLogOutput.error(format("The specified connection id '%s' doesn't exist.", connectionId));
      return null;
    }
    return serverConnectionSettings;
  }

  public boolean usesConnectedMode() {
    return scopeManager.bindings().findAny().isPresent();
  }

  public boolean usesSonarCloud() {
    return hasAnyBindingThatMatch(ServerConnectionSettings::isSonarCloudAlias);
  }

  private boolean hasAnyBindingThatMatch(Predicate<ServerConnectionSettings> predicate) {
    return scopeManager.bindings()
      .map(binding -> settingsManager.getCurrentSettings().getServerConnections().get(binding.connectionId()))
      .anyMatch(predicate);
  }

  @Override
  public void onChange(@Nullable WorkspaceFolderWrapper folder, @Nullable WorkspaceFolderSettings oldValue, WorkspaceFolderSettings newValue) {
    if (folder == null || oldValue == null) {
      return;
    }
    if (!bindingChanged(oldValue, newValue)) {
      return;
    }
    scopeManager.update(folder).thenAccept(changed -> {
      if (Boolean.TRUE.equals(changed)) {
        analysisManager.analyzeAllOpenFilesInFolder(folder);
      }
    });
  }

  private static boolean bindingChanged(WorkspaceFolderSettings oldValue, WorkspaceFolderSettings newValue) {
    return oldValue.hasBinding() != newValue.hasBinding()
      || (newValue.hasBinding()
        && (!Objects.equals(oldValue.getConnectionId(), newValue.getConnectionId()) || !Objects.equals(oldValue.getProjectKey(), newValue.getProjectKey())));
  }

  private void clearCaches(String connectionId) {
    scopeManager.forgetBindingsOf(connectionId);
  }

  private void handleDeletedConnections(@Nullable WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    if (oldValue != null && !oldValue.getServerConnections().isEmpty()) {
      List<String> deletedConnectionIds = new ArrayList<>();
      // there used to be connections
      oldValue.getServerConnections().forEach((id, value) -> {
        if (!newValue.getServerConnections().containsKey(id)) {
          // connection was deleted
          deletedConnectionIds.add(id);
        }
      });
      if (!deletedConnectionIds.isEmpty()) {
        client.removeBindingsForDeletedConnections(deletedConnectionIds);
      }
    }
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    handleDeletedConnections(oldValue, newValue);
    newValue.getServerConnections().forEach((id, value) -> {
      if (oldValue == null) {
        // initial sync
        this.validateConnection(id);
      } else {
        var oldConnection = oldValue.getServerConnections().get(id);
        if (oldConnection != null && !oldConnection.equals(value)) {
          // Settings of the connection have been changed. Remove all cached bindings and force close the engine
          clearCaches(id);
        }
        if (oldConnection == null || !oldConnection.equals(value)) {
          // New connection or changed settings. Validate connection
          validateConnection(id);
        }
      }
    });
  }

  public void validateConnection(String id) {
    try {
      Optional.ofNullable(getValidateConnectionParamsFor(id))
        .map(params -> backendServiceFacade.getBackendService().validateConnection(params))
        .ifPresent(validationFuture -> validationFuture.thenAccept(validationResult -> {
          var connectionCheckResult = validationResult.isSuccess() ? ConnectionCheckResult.success(id) : ConnectionCheckResult.failure(id, validationResult.getMessage());
          client.reportConnectionCheckResult(connectionCheckResult);
        }));
    } catch (IllegalStateException e) {
      // Handle null/empty token validation errors
      var connectionCheckResult = ConnectionCheckResult.failure(id, "Invalid credentials: " + e.getMessage());
      client.reportConnectionCheckResult(connectionCheckResult);
    }
  }

  public Optional<String> resolveBranchNameForFolder(URI folder) {
    var matchedSonarProjectBranch = backendServiceFacade.getBackendService().getMatchedSonarProjectBranch(folder.toString()).join().getMatchedSonarProjectBranch();
    return matchedSonarProjectBranch == null ? Optional.empty() : Optional.of(matchedSonarProjectBranch);
  }

  private Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> getTransientConnection(String connectionId) {
    var endpointParams = getEndpointParamsFor(connectionId);
    if (endpointParams == null) {
      throw new IllegalArgumentException(format("No server configuration found with ID '%s'", connectionId));
    }
    var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
    var region = connectionSettings.getRegion();
    var connectionParams = Utils.getValidateConnectionParamsForNewConnection(
      new SonarLintExtendedLanguageServer.ConnectionCheckParams(connectionSettings.getToken(),
        connectionSettings.getOrganizationKey(), connectionSettings.getServerUrl(), region != null ? region.toString() : null));
    return connectionParams.getTransientConnection();
  }

  public Map<String, String> getRemoteProjects(String connectionId) {
    var transientConnection = getTransientConnection(connectionId);
    try {
      var allProjectsResponse = backendServiceFacade.getBackendService().getAllProjects(transientConnection).get();
      return allProjectsResponse.getSonarProjects().stream().collect(Collectors.toMap(SonarProjectDto::getKey, SonarProjectDto::getName));
    } catch (DownloadException downloadFailed) {
      throw new IllegalStateException(format("Failed to fetch list of projects from '%s'", connectionId), downloadFailed);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      interrupted(e, globalLogOutput);
    }
    return Map.of();
  }

  public CompletableFuture<Map<String, String>> getRemoteProjectsByKeys(String connectionId, List<String> projectKeys) {
    var transientConnection = getTransientConnection(connectionId);
    try {
      return backendServiceFacade.getBackendService().getProjectNamesByKeys(transientConnection, projectKeys)
        .thenApply(GetProjectNamesByKeyResponse::getProjectNamesByKey);
    } catch (DownloadException downloadFailed) {
      throw new IllegalStateException(format("Failed to fetch list of projects from '%s'", connectionId), downloadFailed);
    }
  }
}
