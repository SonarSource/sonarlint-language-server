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
import org.sonarsource.sonarlint.ls.folders.ResolvedSonarConfig;
import org.sonarsource.sonarlint.ls.folders.SonarConfigProject;
import org.sonarsource.sonarlint.ls.folders.SonarConfigResolver;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScope;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

import static java.lang.String.format;

public class SonarConfigBindingSource implements BindingSource {

  private final SonarConfigResolver resolver;
  private final SettingsManager settingsManager;
  private final LanguageClientLogger logOutput;

  public SonarConfigBindingSource(SonarConfigResolver resolver, SettingsManager settingsManager, LanguageClientLogger logOutput) {
    this.resolver = resolver;
    this.settingsManager = settingsManager;
    this.logOutput = logOutput;
  }

  @Override
  public List<ConfigurationScope> scopesOf(WorkspaceFolderWrapper folder) {
    return resolver.get(folder.getUri()).map(ResolvedSonarConfig::allProjects).orElse(List.of()).stream()
      .map(project -> toScope(folder, project))
      .toList();
  }

  private ConfigurationScope toScope(WorkspaceFolderWrapper folder, SonarConfigProject project) {
    var name = project.scopeUri().equals(folder.getUri()) ? folder.getLspFolder().getName() : project.directoryName();
    return new ConfigurationScope(project.scopeUri(), name, project.relativePath(), Origin.SONAR_CONFIG, bindingOf(project));
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
