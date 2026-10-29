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
import org.sonarsource.sonarlint.ls.folders.ResolvedSonarConfig;
import org.sonarsource.sonarlint.ls.folders.SonarConfigProject;
import org.sonarsource.sonarlint.ls.folders.SonarConfigResolver;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

public class SonarConfigBindingSource implements BindingSource {

  private final SonarConfigResolver resolver;
  private final SettingsManager settingsManager;

  public SonarConfigBindingSource(SonarConfigResolver resolver, SettingsManager settingsManager) {
    this.resolver = resolver;
    this.settingsManager = settingsManager;
  }

  @Override
  public List<Scope> scopesOf(WorkspaceFolderWrapper folder) {
    return projectsOf(folder).stream()
      .map(project -> new Scope(project.scopeUri(), project.directoryName(), project.relativePath(), Origin.SONAR_CONFIG, this))
      .toList();
  }

  @Override
  public Optional<ScopeBinding> bindingOf(WorkspaceFolderWrapper folder, Scope scope) {
    return projectsOf(folder).stream()
      .filter(project -> project.scopeUri().equals(scope.uri()))
      .findFirst()
      .map(this::toBinding);
  }

  private List<SonarConfigProject> projectsOf(WorkspaceFolderWrapper folder) {
    return resolver.get(folder.getUri()).map(ResolvedSonarConfig::allProjects).orElse(List.of());
  }

  private ScopeBinding toBinding(SonarConfigProject project) {
    return new ScopeBinding(project.projectKey(),
      ScopeBinding.lazily(() -> SonarConfigConnectionMatcher.findConnectionId(settingsManager.getCurrentSettings().getServerConnections(), project)),
      project.describeTarget());
  }
}
