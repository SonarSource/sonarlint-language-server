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
import java.util.Optional;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.ls.folders.ResolvedSonarConfig;
import org.sonarsource.sonarlint.ls.folders.SonarConfigLoader;
import org.sonarsource.sonarlint.ls.folders.SonarConfigProject;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScope;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

import static java.lang.String.format;

public class SonarConfigBindingSource implements BindingSource {

  private final SonarConfigLoader loader;
  private final SettingsManager settingsManager;
  private final LanguageClientLogger logOutput;

  public SonarConfigBindingSource(SonarConfigLoader loader, SettingsManager settingsManager, LanguageClientLogger logOutput) {
    this.loader = loader;
    this.settingsManager = settingsManager;
    this.logOutput = logOutput;
  }

  @Override
  public List<ConfigurationScope> scopesOf(WorkspaceFolderWrapper folder) {
    return loader.load(folder.getUri()).flatMap(file -> file.resolveFor(folder.getUri())).map(ResolvedSonarConfig::allProjects).orElse(List.of()).stream()
      .map(project -> toScope(folder, project))
      .toList();
  }

  private ConfigurationScope toScope(WorkspaceFolderWrapper folder, SonarConfigProject project) {
    var name = project.scopeUri().equals(folder.getUri()) ? folder.getLspFolder().getName() : project.directoryName();
    var binding = bindingOf(project);
    var unresolved = binding == null ? unresolvedBindingOf(project) : null;
    var pathToCompileCommands = Optional.ofNullable(project.pathToCompileCommands()).orElseGet(() -> folder.getSettings().getPathToCompileCommands());
    return new ConfigurationScope(project.scopeUri(), name, project.relativePath(), Origin.SONAR_CONFIG, binding, unresolved, pathToCompileCommands);
  }

  private static ConfigurationScope.UnresolvedBinding unresolvedBindingOf(SonarConfigProject project) {
    var region = project.organization() != null ? project.cloudRegion() : null;
    return new ConfigurationScope.UnresolvedBinding(project.projectKey(), null, project.serverUrl(), project.organization(), region);
  }

  @CheckForNull
  private ProjectBinding bindingOf(SonarConfigProject project) {
    var connectionId = SonarConfigConnectionMatcher.findConnectionId(settingsManager.getCurrentSettings().getServerConnections(), project);
    if (connectionId == null) {
      logOutput.warn(format("No connection configured for %s, scope %s is unbound", project.describeTarget(), project.scopeUri()));
      return null;
    }
    return new ProjectBinding(connectionId, project.projectKey());
  }
}
