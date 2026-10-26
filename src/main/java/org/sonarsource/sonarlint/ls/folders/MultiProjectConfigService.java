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
package org.sonarsource.sonarlint.ls.folders;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.SubProjectScopeInfo;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.SubProjectScopesChangedParams;
import org.sonarsource.sonarlint.ls.backend.BackendService.ChildScope;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

import static java.lang.String.format;

/**
 * Reads a {@code .sonar-config.json} file at the root of a workspace folder and registers each declared
 * sub-project as a child {@link ConfigurationScopeDto} of that folder's own scope, so that a single opened
 * folder can contain several independently-bound SonarQube Server projects.
 */
public class MultiProjectConfigService implements WorkspaceSettingsChangeListener {

  public static final String SONAR_CONFIG_FILE_NAME = ".sonar-config.json";
  private static final String DEFAULT_CLOUD_REGION = "EU";

  private final SettingsManager settingsManager;
  private final BackendServiceFacade backendServiceFacade;
  private final SonarLintExtendedLanguageClient client;
  private final LanguageClientLogger logOutput;
  private final Gson gson = new Gson();

  private final Map<URI, List<SubProjectScope>> subProjectsByParentFolder = new ConcurrentHashMap<>();
  private final Map<URI, SubProjectScope> rootBindingByFolder = new ConcurrentHashMap<>();
  private final Set<URI> registeredFolders = ConcurrentHashMap.newKeySet();

  public MultiProjectConfigService(SettingsManager settingsManager, BackendServiceFacade backendServiceFacade, SonarLintExtendedLanguageClient client,
    LanguageClientLogger logOutput) {
    this.settingsManager = settingsManager;
    this.backendServiceFacade = backendServiceFacade;
    this.client = client;
    this.logOutput = logOutput;
  }

  public void onFolderAdded(WorkspaceFolderWrapper folder) {
    var folderPath = folder.getRootPath().toAbsolutePath().normalize();
    var configPath = findSonarConfigFile(folderPath);
    if (configPath == null) {
      logOutput.info(format("[multi-project] No '%s' found at or above %s (up to the git root), folder registered normally", SONAR_CONFIG_FILE_NAME, folderPath));
      return;
    }
    var configFile = readSonarConfigFile(configPath);
    if (configFile == null || configFile.projects == null) {
      logOutput.warn(format("[multi-project] '%s' at %s was found but could not be parsed into a 'projects' array", SONAR_CONFIG_FILE_NAME, configPath));
      return;
    }
    logOutput.info(format("[multi-project] Found '%s' at %s declaring %d project entr(y/ies)", SONAR_CONFIG_FILE_NAME, configPath, configFile.projects.size()));
    var configDir = configPath.getParent();
    var children = new ArrayList<SubProjectScope>();
    SubProjectScope rootEntry = null;
    Path rootEntryPath = null;
    for (var entry : configFile.projects) {
      if (entry.projectKey == null || entry.path == null || (entry.serverUrl == null && entry.organization == null)) {
        logOutput.warn(format("[multi-project] Ignoring invalid entry in '%s' for folder %s: projectKey=%s path=%s serverUrl=%s organization=%s (need either serverUrl or organization)",
          SONAR_CONFIG_FILE_NAME, folder.getUri(), entry.projectKey, entry.path, entry.serverUrl, entry.organization));
        continue;
      }
      var entryPath = configDir.resolve(entry.path).normalize();
      if (folderPath.startsWith(entryPath)) {
        if (rootEntryPath == null || entryPath.getNameCount() > rootEntryPath.getNameCount()) {
          logOutput.info(format("[multi-project] Entry projectKey=%s path=%s contains the folder -> its own configuration scope is bound directly, no child scope needed",
            entry.projectKey, entry.path));
          rootEntry = new SubProjectScope(folder.getUri(), entry.projectKey, entry.serverUrl, entry.organization, entry.region, ".");
          rootEntryPath = entryPath;
        }
      } else if (entryPath.startsWith(folderPath)) {
        var relativePath = folderPath.relativize(entryPath).toString().replace('\\', '/');
        logOutput.info(format("[multi-project] Entry projectKey=%s path=%s is nested in the folder -> child scope %s", entry.projectKey, entry.path, entryPath.toUri()));
        children.add(new SubProjectScope(entryPath.toUri(), entry.projectKey, entry.serverUrl, entry.organization, entry.region, relativePath));
      } else {
        logOutput.info(format("[multi-project] Entry projectKey=%s path=%s is unrelated to folder %s, skipped", entry.projectKey, entry.path, folderPath));
      }
    }
    if (children.isEmpty() && rootEntry == null) {
      logOutput.info(format("[multi-project] '%s' at %s has no entry matching folder %s, folder registered normally", SONAR_CONFIG_FILE_NAME, configPath, folderPath));
      return;
    }
    if (rootEntry != null) {
      rootBindingByFolder.put(folder.getUri(), rootEntry);
    }
    if (!children.isEmpty()) {
      subProjectsByParentFolder.put(folder.getUri(), children);
    }
  }

  /**
   * Creates the child scopes already bound, like {@code addWorkspaceFolders} does for folders. Must run after the
   * folder's own scope exists and off the LSP handshake thread, since the binding lookup blocks on settings.
   */
  public void registerSubProjectScopes(URI folderUri) {
    var scopes = subProjectsByParentFolder.getOrDefault(folderUri, List.of());
    if (scopes.isEmpty() && !rootBindingByFolder.containsKey(folderUri)) {
      return;
    }
    var connections = settingsManager.getCurrentSettings().getServerConnections();
    if (!scopes.isEmpty()) {
      var scopesById = scopes.stream().collect(Collectors.toMap(scope -> scope.scopeUri().toString(), Function.identity()));
      var children = scopes.stream().map(scope -> new ChildScope(scope.scopeUri().toString(), scope.projectKey())).toList();
      backendServiceFacade.getBackendService().addSubProjectScopes(folderUri.toString(), children,
        child -> Optional.ofNullable(findConnectionId(connections, scopesById.get(child.id()))).map(connectionId -> new ProjectBinding(connectionId, child.name())));
      logOutput.info(format("[multi-project] Registered %d sub-project scope(s) under %s from '%s'", children.size(), folderUri, SONAR_CONFIG_FILE_NAME));
    }
    registeredFolders.add(folderUri);
    notifyClientOfScopes(folderUri, connections);
  }

  /**
   * A folder whose {@code .sonar-config.json} declares sub-projects is a pure container: its own scope must stay
   * unbound so the real bindings live only on its children. Consulted by {@link
   * org.sonarsource.sonarlint.ls.connected.ProjectBindingManager} when building that folder's own {@code
   * ConfigurationScopeDto} binding, so the "no suggestions" state is baked into the scope at creation time —
   * sending a follow-up {@code updateBinding} call instead would race the scope's own (later, asynchronous)
   * creation and likely arrive before the scope even exists.
   */
  public boolean isContainerFolder(URI folderUri) {
    return subProjectsByParentFolder.containsKey(folderUri) && !rootBindingByFolder.containsKey(folderUri);
  }

  /**
   * A folder that is itself at or below a project declared in {@code .sonar-config.json} (possibly in an ancestor
   * directory) needs no child scope for it: the folder's own configuration scope IS the project, so it must be bound
   * directly. Returns the
   * resolved binding if a connection for its {@code serverUrl} is currently known, otherwise an unbound-with-
   * suggestions-disabled placeholder so SonarLint's own auto-binding-suggestion engine doesn't guess a different
   * project for it while waiting — consulted by {@link WorkspaceFoldersManager#getBindingProvider()} at scope-
   * creation time, same as {@link #isContainerFolder}.
   */
  public Optional<ProjectBinding> getRootBinding(URI folderUri) {
    var entry = rootBindingByFolder.get(folderUri);
    if (entry == null) {
      return Optional.empty();
    }
    var connections = settingsManager.getCurrentSettings().getServerConnections();
    var connectionId = findConnectionId(connections, entry);
    return Optional.of(connectionId != null ? new ProjectBinding(connectionId, entry.projectKey()) : new ProjectBinding(null, null));
  }

  public void onFolderRemoved(URI folderUri) {
    var removed = subProjectsByParentFolder.remove(folderUri);
    if (removed != null) {
      removed.forEach(scope -> backendServiceFacade.getBackendService().removeWorkspaceFolder(scope.scopeUri().toString()));
    }
    rootBindingByFolder.remove(folderUri);
    registeredFolders.remove(folderUri);
  }

  /**
   * Resolve the effective configuration scope id for a file inside the given folder: the deepest declared
   * sub-project scope containing it, or the folder's own scope id when no sub-project matches.
   */
  public String resolveConfigScopeId(WorkspaceFolderWrapper folder, URI fileUri) {
    var scopes = subProjectsByParentFolder.get(folder.getUri());
    if (scopes == null || scopes.isEmpty()) {
      return folder.getUri().toString();
    }
    return scopes.stream()
      .filter(scope -> WorkspaceFoldersManager.isAncestor(scope.scopeUri(), fileUri))
      .max(Comparator.comparingInt(scope -> scope.scopeUri().getPath().length()))
      .map(scope -> scope.scopeUri().toString())
      .orElseGet(() -> folder.getUri().toString());
  }

  /**
   * Return the sub-project binding for a file, if its folder has a registered {@code .sonar-config.json} sub-project
   * containing it AND that sub-project currently resolves to a configured connection. Used by {@link
   * org.sonarsource.sonarlint.ls.connected.ProjectBindingManager} to override the folder-level binding for files that
   * belong to a sub-project rather than the folder itself.
   */
  public Optional<ProjectBinding> getSubProjectBinding(WorkspaceFolderWrapper folder, URI fileUri) {
    var scopes = subProjectsByParentFolder.getOrDefault(folder.getUri(), List.of());
    var rootEntry = rootBindingByFolder.get(folder.getUri());
    if (scopes.isEmpty() && rootEntry == null) {
      return Optional.empty();
    }
    var connections = settingsManager.getCurrentSettings().getServerConnections();
    var matchingScope = scopes.stream()
      .filter(scope -> WorkspaceFoldersManager.isAncestor(scope.scopeUri(), fileUri))
      .max(Comparator.comparingInt(scope -> scope.scopeUri().getPath().length()))
      .orElse(rootEntry);
    if (matchingScope == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(findConnectionId(connections, matchingScope))
      .map(connectionId -> new ProjectBinding(connectionId, matchingScope.projectKey()));
  }

  /**
   * Rebinds the scopes that already exist in the backend, so connections added or edited after startup take effect.
   */
  private void rebindUnboundScopes(Map<String, ServerConnectionSettings> connections) {
    logOutput.info(format("[multi-project] Attempting to (re)bind sub-project scopes against %d known connection(s): %s",
      connections.size(), connections.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue().getServerUrl()).toList()));
    registeredFolders.forEach(folderUri -> {
      subProjectsByParentFolder.getOrDefault(folderUri, List.of()).forEach(scope -> bindScope(connections, scope));
      Optional.ofNullable(rootBindingByFolder.get(folderUri)).ifPresent(entry -> bindScope(connections, entry));
      notifyClientOfScopes(folderUri, connections);
    });
  }

  private void bindScope(Map<String, ServerConnectionSettings> connections, SubProjectScope scope) {
    var connectionId = findConnectionId(connections, scope);
    if (connectionId != null) {
      logOutput.info(format("[multi-project] Matched connection '%s' for %s -> binding scope %s to projectKey=%s",
        connectionId, scope.describeTarget(), scope.scopeUri(), scope.projectKey()));
      backendServiceFacade.getBackendService()
        .updateBinding(new DidUpdateBindingParams(scope.scopeUri().toString(), new BindingConfigurationDto(connectionId, scope.projectKey(), true)));
    } else {
      logOutput.warn(format("[multi-project] No connection configured for %s, scope %s stays unbound", scope.describeTarget(), scope.scopeUri()));
    }
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    if (registeredFolders.isEmpty()) {
      return;
    }
    logOutput.info("[multi-project] Settings changed, re-checking sub-project scope bindings");
    rebindUnboundScopes(newValue.getServerConnections());
  }

  private void notifyClientOfScopes(URI folderUri, Map<String, ServerConnectionSettings> connections) {
    var rootEntry = rootBindingByFolder.get(folderUri);
    var scopeInfos = Stream.concat(
      rootEntry != null ? Stream.of(rootEntry) : Stream.<SubProjectScope>empty(),
      subProjectsByParentFolder.getOrDefault(folderUri, List.of()).stream())
      .map(scope -> new SubProjectScopeInfo(scope.scopeUri().toString(), scope.projectKey(),
        findConnectionId(connections, scope), scope.relativePath()))
      .toList();
    client.subProjectScopesChanged(new SubProjectScopesChangedParams(folderUri.toString(), scopeInfos));
  }

  @CheckForNull
  private static String findConnectionId(Map<String, ServerConnectionSettings> connections, SubProjectScope scope) {
    return connections.entrySet().stream()
      .filter(e -> matches(e.getValue(), scope))
      .map(Map.Entry::getKey)
      .findFirst()
      .orElse(null);
  }

  private static boolean matches(ServerConnectionSettings connection, SubProjectScope scope) {
    if (scope.serverUrl() != null) {
      return normalize(connection.getServerUrl()).equals(normalize(scope.serverUrl()));
    }
    if (!connection.isSonarCloudAlias() || connection.getOrganizationKey() == null) {
      return false;
    }
    var connectionRegion = connection.getRegion() != null ? connection.getRegion().name() : DEFAULT_CLOUD_REGION;
    var scopeRegion = scope.region() != null ? scope.region() : DEFAULT_CLOUD_REGION;
    return connection.getOrganizationKey().equalsIgnoreCase(scope.organization()) && connectionRegion.equalsIgnoreCase(scopeRegion);
  }

  private static String normalize(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  /**
   * Nearest {@code .sonar-config.json} at or above the folder, searching upwards no further than the enclosing git
   * root. Outside a git repository only the folder itself is checked, so unrelated files higher up are never picked.
   */
  @CheckForNull
  private static Path findSonarConfigFile(Path folderPath) {
    var gitRoot = findGitRoot(folderPath);
    for (var dir = folderPath; dir != null; dir = dir.getParent()) {
      var candidate = dir.resolve(SONAR_CONFIG_FILE_NAME);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      if (gitRoot == null || dir.equals(gitRoot)) {
        return null;
      }
    }
    return null;
  }

  @CheckForNull
  private static Path findGitRoot(Path folderPath) {
    for (var dir = folderPath; dir != null; dir = dir.getParent()) {
      if (Files.exists(dir.resolve(".git"))) {
        return dir;
      }
    }
    return null;
  }

  @CheckForNull
  private SonarConfigFile readSonarConfigFile(Path configPath) {
    try {
      var content = Files.readString(configPath);
      return gson.fromJson(content, SonarConfigFile.class);
    } catch (IOException | JsonSyntaxException e) {
      logOutput.warn(format("Failed to read '%s': %s", configPath, e.getMessage()));
      return null;
    }
  }

  private record SubProjectScope(URI scopeUri, String projectKey, @Nullable String serverUrl, @Nullable String organization, @Nullable String region,
    String relativePath) {
    String describeTarget() {
      return serverUrl != null ? "serverUrl=" + serverUrl : format("organization=%s region=%s", organization, region != null ? region : DEFAULT_CLOUD_REGION);
    }
  }

  private static class SonarConfigFile {
    @Nullable
    List<ProjectEntry> projects;
  }

  private static class ProjectEntry {
    @Nullable
    String projectKey;
    @Nullable
    String path;
    @Nullable
    String serverUrl;
    @Nullable
    String organization;
    @Nullable
    String region;
  }
}
