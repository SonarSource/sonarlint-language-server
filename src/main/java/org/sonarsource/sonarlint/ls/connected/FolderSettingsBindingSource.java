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

import java.util.List;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScope;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
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
  public List<ConfigurationScope> scopesOf(WorkspaceFolderWrapper folder) {
    return List.of(new ConfigurationScope(folder.getUri(), folder.getLspFolder().getName(), ".", Origin.SETTINGS, bindingOf(folder)));
  }

  @CheckForNull
  private ProjectBinding bindingOf(WorkspaceFolderWrapper folder) {
    var settings = folder.getSettings();
    if (!Utils.uriHasFileScheme(folder.getUri()) || !settings.hasBinding()) {
      return null;
    }
    var connectionId = requireNonNull(settings.getConnectionId());
    if (settingsManager.connectionOf(connectionId) == null) {
      logOutput.error(format("Invalid binding for '%s'", folder.getRootPath()));
      return null;
    }
    var projectKey = requireNonNull(settings.getProjectKey());
    logOutput.debug(format("Resolved binding %s for folder %s", projectKey, folder.getRootPath()));
    return new ProjectBinding(connectionId, projectKey);
  }
}
