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
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

/**
 * Where a binding can come from. A source describes the scopes it defines for a workspace folder; it is empty when the
 * source does not apply to the folder. The hooks let a source drop what it kept.
 */
public interface BindingSource {

  /**
   * Never waits for the settings: the connection of each scope is resolved lazily.
   */
  List<ScopeBinding> scopesOf(WorkspaceFolderWrapper folder);

  /**
   * Waits until what the source reads about the folder has been loaded. The callers that resolve a binding use it,
   * the lookups of a scope do not.
   */
  default void awaitSettings(WorkspaceFolderWrapper folder) {
  }

  default void onConnectionsChanged(Map<String, ServerConnectionSettings> previous, Map<String, ServerConnectionSettings> current) {
  }

  default void onFolderSettingsChanged(URI folderUri) {
  }

  default void forget(URI folderUri) {
  }
}
