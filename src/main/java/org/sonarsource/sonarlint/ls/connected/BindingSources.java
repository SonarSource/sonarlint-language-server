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
import java.util.Map;
import java.util.Optional;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.util.URIUtils;

/**
 * The binding sources in order of precedence: the scopes of a folder are those of the first source that defines any.
 * Everything else is a selection over those scopes.
 */
public class BindingSources implements BindingSource {

  private final List<BindingSource> sources;

  public BindingSources(List<BindingSource> sources) {
    this.sources = List.copyOf(sources);
  }

  @Override
  public List<ScopeBinding> scopesOf(WorkspaceFolderWrapper folder) {
    return sources.stream()
      .map(source -> source.scopesOf(folder))
      .filter(scopes -> !scopes.isEmpty())
      .findFirst()
      .orElse(List.of());
  }

  /**
   * Like {@link #scopesOf}, but first waits for what each source it asks reads about the folder. The first source that
   * defines scopes ends the search, so the settings of the sources after it are not waited for.
   */
  public List<ScopeBinding> loadedScopesOf(WorkspaceFolderWrapper folder) {
    for (var source : sources) {
      source.awaitSettings(folder);
      var scopes = source.scopesOf(folder);
      if (!scopes.isEmpty()) {
        return scopes;
      }
    }
    return List.of();
  }

  public Optional<ProjectBinding> bindingOf(WorkspaceFolderWrapper folder, URI fileUri) {
    return deepestContaining(loadedScopesOf(folder), fileUri).flatMap(ScopeBinding::toProjectBinding);
  }

  public Optional<ProjectBinding> bindingOfScope(WorkspaceFolderWrapper folder, URI scopeUri) {
    return loadedScopesOf(folder).stream()
      .filter(scope -> scope.scopeUri().equals(scopeUri))
      .findFirst()
      .flatMap(ScopeBinding::toProjectBinding);
  }

  /**
   * The scope a file belongs to: the deepest declared scope containing it, otherwise the workspace folder itself.
   */
  public URI scopeUriOf(WorkspaceFolderWrapper folder, URI fileUri) {
    return deepestContaining(scopesOf(folder), fileUri).map(ScopeBinding::scopeUri).orElse(folder.getUri());
  }

  public List<WorkspaceFolder> subFolders(WorkspaceFolderWrapper folder) {
    return scopesOf(folder).stream()
      .filter(scope -> !scope.scopeUri().equals(folder.getUri()))
      .map(scope -> new WorkspaceFolder(scope.scopeUri().toString(), scope.name()))
      .toList();
  }

  @Override
  public void onConnectionsChanged(Map<String, ServerConnectionSettings> previous, Map<String, ServerConnectionSettings> current) {
    sources.forEach(source -> source.onConnectionsChanged(previous, current));
  }

  @Override
  public void onFolderSettingsChanged(URI folderUri) {
    sources.forEach(source -> source.onFolderSettingsChanged(folderUri));
  }

  @Override
  public void forget(URI folderUri) {
    sources.forEach(source -> source.forget(folderUri));
  }

  private static Optional<ScopeBinding> deepestContaining(List<ScopeBinding> scopes, URI fileUri) {
    return scopes.stream()
      .filter(scope -> URIUtils.isAncestor(scope.scopeUri(), fileUri))
      .max(Comparator.comparingInt(scope -> scope.scopeUri().getPath().length()));
  }
}
