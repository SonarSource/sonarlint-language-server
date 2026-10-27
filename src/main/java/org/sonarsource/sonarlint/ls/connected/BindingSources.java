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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.ls.folders.SonarConfigProject;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.util.URIUtils;

/**
 * The binding sources in order of precedence. Once a {@code .sonar-config.json} declares scopes for a folder it is the
 * only source for that folder; otherwise the folder's own setting applies. Only the config declares scopes below a
 * workspace folder.
 */
public class BindingSources implements BindingSource {

  private final SonarConfigBindingSource config;
  private final FolderSettingsBindingSource settings;

  public BindingSources(SonarConfigBindingSource config, FolderSettingsBindingSource settings) {
    this.config = config;
    this.settings = settings;
  }

  public SonarConfigBindingSource config() {
    return config;
  }

  public FolderSettingsBindingSource settings() {
    return settings;
  }

  @Override
  public List<ScopeBinding> scopesOf(WorkspaceFolderWrapper folder) {
    var configScopes = config.scopesOf(folder);
    return configScopes.isEmpty() ? settings.scopesOf(folder) : configScopes;
  }

  public Optional<ProjectBinding> bindingOf(WorkspaceFolderWrapper folder, URI fileUri) {
    var configScopes = config.scopesOf(folder);
    return configScopes.isEmpty() ? settings.bindingOf(folder) : deepestContaining(configScopes, fileUri).flatMap(ScopeBinding::toProjectBinding);
  }

  public Optional<ProjectBinding> bindingIfExists(WorkspaceFolderWrapper folder, URI fileUri) {
    var configScopes = config.scopesOf(folder);
    return configScopes.isEmpty() ? settings.cachedBindingOf(folder) : deepestContaining(configScopes, fileUri).flatMap(ScopeBinding::toProjectBinding);
  }

  public Optional<ProjectBinding> bindingOfScope(WorkspaceFolderWrapper folder, URI scopeUri) {
    var configScopes = config.scopesOf(folder);
    if (!configScopes.isEmpty()) {
      return configScopes.stream().filter(scope -> scope.scopeUri().equals(scopeUri)).findFirst().flatMap(ScopeBinding::toProjectBinding);
    }
    return scopeUri.equals(folder.getUri()) ? settings.bindingOf(folder) : Optional.empty();
  }

  /**
   * The scope a file belongs to: the deepest declared scope containing it, otherwise the workspace folder itself.
   */
  public URI scopeUriOf(WorkspaceFolderWrapper folder, URI fileUri) {
    return config.projectsOf(folder).stream()
      .map(SonarConfigProject::scopeUri)
      .filter(scopeUri -> URIUtils.isAncestor(scopeUri, fileUri))
      .max(Comparator.comparingInt(scopeUri -> scopeUri.getPath().length()))
      .orElse(folder.getUri());
  }

  public List<WorkspaceFolder> subFolders(WorkspaceFolderWrapper folder) {
    return config.projectsOf(folder).stream()
      .filter(project -> !project.scopeUri().equals(folder.getUri()))
      .map(project -> new WorkspaceFolder(project.scopeUri().toString(), project.directoryName()))
      .toList();
  }

  public void forget(URI folderUri) {
    config.forget(folderUri);
    settings.forgetFolder(folderUri);
  }

  private static Optional<ScopeBinding> deepestContaining(List<ScopeBinding> scopes, URI fileUri) {
    return scopes.stream()
      .filter(scope -> URIUtils.isAncestor(scope.scopeUri(), fileUri))
      .max(Comparator.comparingInt(scope -> scope.scopeUri().getPath().length()));
  }
}
