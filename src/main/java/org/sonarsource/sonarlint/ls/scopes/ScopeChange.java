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
package org.sonarsource.sonarlint.ls.scopes;

import javax.annotation.Nullable;

/**
 * What an update did to one scope: {@code before} is null for a scope that was opened, {@code after} for one that was closed.
 */
public record ScopeChange(@Nullable ConfigurationScope before, @Nullable ConfigurationScope after) {

  public boolean lostItsBinding() {
    return before != null && before.binding() != null && after != null && after.binding() == null;
  }
}
