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
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;

/**
 * Told by the binding source that produced the scopes of a folder whether it bound the folder, each time it is asked.
 */
public interface BindingSourceObserver {

  void boundBySettings(WorkspaceFolderWrapper folder);

  void boundBySonarConfig(WorkspaceFolderWrapper folder, Path sonarConfigFile, int declaredBindingCount);

  void unbound(WorkspaceFolderWrapper folder);
}
