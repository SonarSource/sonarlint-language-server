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
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScope;

/**
 * The binding sources in order of precedence: the scopes of a folder are those of the first source that defines any.
 */
public class BindingSources {

  private final List<BindingSource> sources;

  public BindingSources(List<BindingSource> sources) {
    this.sources = List.copyOf(sources);
  }

  public List<ConfigurationScope> scopesOf(WorkspaceFolderWrapper folder) {
    return sources.stream()
      .map(source -> source.scopesOf(folder))
      .filter(scopes -> !scopes.isEmpty())
      .findFirst()
      .orElse(List.of());
  }
}
