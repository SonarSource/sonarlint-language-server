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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The binding set in the {@code connectedMode.project} setting. A workspace folder has at most one, and files outside
 * any workspace folder get one from the default folder settings.
 */
public class FolderSettingsBindingSource implements BindingSource {

  private final SettingsManager settingsManager;
  private final LanguageClientLogger logOutput;
  private final ConcurrentMap<URI, Optional<ProjectBinding>> fileBindingCache = new ConcurrentHashMap<>();

  public FolderSettingsBindingSource(SettingsManager settingsManager, LanguageClientLogger logOutput) {
    this.settingsManager = settingsManager;
    this.logOutput = logOutput;
  }

  @Override
  public List<Scope> scopesOf(WorkspaceFolderWrapper folder) {
    if (!Utils.uriHasFileScheme(folder.getUri())) {
      return List.of();
    }
    return List.of(new Scope(folder.getUri(), folderName(folder), ".", Origin.SETTINGS, this));
  }

  @Override
  public Optional<ScopeBinding> bindingOf(WorkspaceFolderWrapper folder, Scope scope) {
    var settings = folder.getSettings();
    if (!settings.hasBinding()) {
      return Optional.empty();
    }
    return Optional.of(new ScopeBinding(requireNonNull(settings.getProjectKey()),
      ScopeBinding.lazily(() -> resolve(settings, folder.getRootPath()).map(ProjectBinding::connectionId).orElse(null)),
      format("connection '%s'", SettingsManager.connectionIdOrDefault(settings.getConnectionId()))));
  }

  public void onConnectionsChanged(Map<String, ServerConnectionSettings> previous, Map<String, ServerConnectionSettings> current) {
    removeStale(fileBindingCache, previous, current);
  }

  public Optional<ProjectBinding> bindingOfFile(URI fileUri) {
    return fileBindingCache.computeIfAbsent(fileUri, k -> resolve(settingsManager.getCurrentDefaultFolderSettings(), Paths.get(fileUri).getParent()));
  }

  public void onDefaultFolderSettingsChanged() {
    fileBindingCache.clear();
  }

  public Stream<ProjectBinding> cachedFileBindings() {
    return fileBindingCache.values().stream().flatMap(Optional::stream);
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

  private static void removeStale(ConcurrentMap<URI, Optional<ProjectBinding>> cache, Map<String, ServerConnectionSettings> previous,
    Map<String, ServerConnectionSettings> current) {
    cache.values().removeIf(binding -> binding.map(ProjectBinding::connectionId).map(id -> !Objects.equals(previous.get(id), current.get(id))).orElse(true));
  }

  private static String folderName(WorkspaceFolderWrapper folder) {
    return Optional.ofNullable(folder.getRootPath().getFileName()).map(Path::toString).orElse(folder.getUri().toString());
  }
}
