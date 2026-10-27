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
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.ls.connected.ScopeBinding.Origin;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The binding set in the {@code connectedMode.project} setting. A workspace folder has at most one, and files outside
 * any workspace folder get one from the default folder settings. Resolved bindings are cached until invalidated.
 */
public class FolderSettingsBindingSource implements BindingSource {

  private final SettingsManager settingsManager;
  private final LanguageClientLogger logOutput;
  private final ConcurrentMap<URI, Optional<ProjectBinding>> folderBindingCache;
  private final ConcurrentMap<URI, Optional<ProjectBinding>> fileBindingCache = new ConcurrentHashMap<>();

  public FolderSettingsBindingSource(SettingsManager settingsManager, LanguageClientLogger logOutput) {
    this(settingsManager, logOutput, new ConcurrentHashMap<>());
  }

  public FolderSettingsBindingSource(SettingsManager settingsManager, LanguageClientLogger logOutput, ConcurrentMap<URI, Optional<ProjectBinding>> folderBindingCache) {
    this.settingsManager = settingsManager;
    this.logOutput = logOutput;
    this.folderBindingCache = folderBindingCache;
  }

  @Override
  public List<ScopeBinding> scopesOf(WorkspaceFolderWrapper folder) {
    var settings = folder.getRawSettings();
    if (settings == null || !settings.hasBinding()) {
      return List.of();
    }
    var connectionId = bindingOf(folder).map(ProjectBinding::connectionId).orElse(null);
    return List.of(new ScopeBinding(folder.getUri(), folderName(folder), ".", requireNonNull(settings.getProjectKey()), connectionId,
      format("connection '%s'", SettingsManager.connectionIdOrDefault(settings.getConnectionId())), Origin.SETTINGS));
  }

  public Optional<ProjectBinding> bindingOf(WorkspaceFolderWrapper folder) {
    return folderBindingCache.computeIfAbsent(folder.getUri(), k -> resolve(folder.getSettings(), folder.getRootPath()));
  }

  public Optional<ProjectBinding> bindingOfFile(URI fileUri) {
    return fileBindingCache.computeIfAbsent(fileUri, k -> resolve(settingsManager.getCurrentDefaultFolderSettings(), Paths.get(fileUri).getParent()));
  }

  public Optional<ProjectBinding> cachedBindingOf(WorkspaceFolderWrapper folder) {
    return folderBindingCache.getOrDefault(folder.getUri(), Optional.empty());
  }

  public Optional<ProjectBinding> cachedBindingOfFile(URI fileUri) {
    return fileBindingCache.getOrDefault(fileUri, Optional.empty());
  }

  public boolean hasCachedFolder(URI folderUri) {
    return folderBindingCache.containsKey(folderUri);
  }

  public boolean hasCachedFiles() {
    return !fileBindingCache.isEmpty();
  }

  public void forgetFolder(URI folderUri) {
    folderBindingCache.remove(folderUri);
  }

  public void forgetFiles() {
    fileBindingCache.clear();
  }

  public void markFolderUnbound(URI folderUri) {
    folderBindingCache.put(folderUri, Optional.empty());
  }

  public void markFilesUnbound() {
    fileBindingCache.replaceAll((uri, binding) -> Optional.empty());
  }

  public void forgetConnection(String connectionId) {
    folderBindingCache.entrySet().removeIf(e -> e.getValue().isPresent() && e.getValue().get().connectionId().equals(connectionId));
    fileBindingCache.entrySet().removeIf(e -> e.getValue().isPresent() && e.getValue().get().connectionId().equals(connectionId));
  }

  /**
   * Drops the cached results that found no binding, so that a connection added later is taken into account.
   */
  public void forgetUnresolved() {
    folderBindingCache.values().removeIf(Optional::isEmpty);
    fileBindingCache.values().removeIf(Optional::isEmpty);
  }

  public Optional<URI> cachedFolderBoundTo(String connectionId, String projectKey) {
    return folderBindingCache.entrySet().stream()
      .filter(e -> e.getValue().filter(binding -> binding.projectKey().equals(projectKey) && binding.connectionId().equals(connectionId)).isPresent())
      .map(Map.Entry::getKey)
      .findFirst();
  }

  public Stream<ProjectBinding> cachedBindings() {
    return Stream.concat(folderBindingCache.values().stream(), fileBindingCache.values().stream()).flatMap(Optional::stream);
  }

  @CheckForNull
  public ServerConnectionSettings connectionOf(@Nullable String maybeConnectionId) {
    var connectionId = SettingsManager.connectionIdOrDefault(maybeConnectionId);
    var serverConnectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
    if (serverConnectionSettings == null) {
      logOutput.error(format("The specified connection id '%s' doesn't exist.", connectionId));
      return null;
    }
    return serverConnectionSettings;
  }

  private Optional<ProjectBinding> resolve(WorkspaceFolderSettings settings, Path folderRoot) {
    if (!settings.hasBinding()) {
      return Optional.empty();
    }
    var connectionId = requireNonNull(settings.getConnectionId());
    if (connectionOf(connectionId) == null) {
      logOutput.error(format("Invalid binding for '%s'", folderRoot));
      return Optional.empty();
    }
    var projectKey = requireNonNull(settings.getProjectKey());
    logOutput.debug(format("Resolved binding %s for folder %s", projectKey, folderRoot));
    return Optional.of(new ProjectBinding(connectionId, projectKey));
  }

  private static String folderName(WorkspaceFolderWrapper folder) {
    return Optional.ofNullable(folder.getRootPath().getFileName()).map(Path::toString).orElse(folder.getUri().toString());
  }
}
