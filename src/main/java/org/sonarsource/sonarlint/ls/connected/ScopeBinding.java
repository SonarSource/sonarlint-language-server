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
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * What a binding source says about one configuration scope. The connection is {@code null} while the binding cannot be
 * resolved to a configured connection; {@code target} says what the source wanted to bind to.
 */
public record ScopeBinding(URI scopeUri, String name, String relativePath, String projectKey, @Nullable String connectionId, String target, Origin origin) {

  public enum Origin {
    SETTINGS,
    SONAR_CONFIG
  }

  public boolean isResolved() {
    return connectionId != null;
  }

  public Optional<ProjectBinding> toProjectBinding() {
    return connectionId == null ? Optional.empty() : Optional.of(new ProjectBinding(connectionId, projectKey));
  }
}
