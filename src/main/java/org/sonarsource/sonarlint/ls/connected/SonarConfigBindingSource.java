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
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarlint.ls.connected.ScopeBinding.Origin;
import org.sonarsource.sonarlint.ls.folders.SonarConfigProject;
import org.sonarsource.sonarlint.ls.folders.SonarConfigResolver;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

/**
 * The bindings declared in a {@code .sonar-config.json}: one scope per project the file declares for the folder. The
 * connection of each is the configured one that matches its server or organization.
 */
public class SonarConfigBindingSource implements BindingSource {

  private final SonarConfigResolver resolver;
  private final SettingsManager settingsManager;

  public SonarConfigBindingSource(SonarConfigResolver resolver, SettingsManager settingsManager) {
    this.resolver = resolver;
    this.settingsManager = settingsManager;
  }

  /**
   * The projects the config declares for the folder, without resolving their connections.
   */
  public List<SonarConfigProject> projectsOf(WorkspaceFolderWrapper folder) {
    return resolver.get(folder.getUri()).map(config -> config.allProjects()).orElse(List.of());
  }

  @Override
  public List<ScopeBinding> scopesOf(WorkspaceFolderWrapper folder) {
    var projects = projectsOf(folder);
    if (projects.isEmpty()) {
      return List.of();
    }
    var connections = settingsManager.getCurrentSettings().getServerConnections();
    return projects.stream().map(project -> toScope(project, connections)).toList();
  }

  public void forget(URI folderUri) {
    resolver.forget(folderUri);
  }

  private static ScopeBinding toScope(SonarConfigProject project, Map<String, ServerConnectionSettings> connections) {
    return new ScopeBinding(project.scopeUri(), project.directoryName(), project.relativePath(), project.projectKey(),
      SonarConfigConnectionMatcher.findConnectionId(connections, project), project.describeTarget(), Origin.SONAR_CONFIG);
  }
}
