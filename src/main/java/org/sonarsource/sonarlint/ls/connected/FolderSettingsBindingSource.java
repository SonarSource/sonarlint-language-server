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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The binding set in the {@code connectedMode.project} setting. A workspace folder has at most one.
 */
public class FolderSettingsBindingSource implements BindingSource {

  private final SettingsManager settingsManager;
  private final LanguageClientLogger logOutput;

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

  private Optional<ProjectBinding> resolve(WorkspaceFolderSettings settings, Path folderRoot) {
    if (!settings.hasBinding()) {
      return Optional.empty();
    }
    var connectionId = requireNonNull(settings.getConnectionId());
    if (settingsManager.connectionOf(connectionId) == null) {
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
