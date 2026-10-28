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
import java.util.function.Supplier;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * What a binding source says about one configuration scope. Everything but the connection is known without the
 * settings; the connection is resolved when it is first asked for, because that waits until the settings are loaded.
 * It is {@code null} while the binding matches no configured connection; {@code target} says what the source wanted
 * to bind to.
 */
public record ScopeBinding(URI scopeUri, String name, String relativePath, String projectKey, Supplier<String> connection, String target, Origin origin) {

  public enum Origin {
    SETTINGS,
    SONAR_CONFIG
  }

  public static ScopeBinding of(URI scopeUri, String name, String relativePath, String projectKey, @Nullable String connectionId, String target, Origin origin) {
    return new ScopeBinding(scopeUri, name, relativePath, projectKey, () -> connectionId, target, origin);
  }

  public static Supplier<String> lazily(Supplier<String> resolution) {
    return new Supplier<>() {
      private boolean isResolved;
      private String connectionId;

      @Override
      public synchronized String get() {
        if (!isResolved) {
          connectionId = resolution.get();
          isResolved = true;
        }
        return connectionId;
      }
    };
  }

  @CheckForNull
  public String connectionId() {
    return connection.get();
  }

  public boolean isResolved() {
    return connectionId() != null;
  }

  public Optional<ProjectBinding> toProjectBinding() {
    return Optional.ofNullable(connectionId()).map(connectionId -> new ProjectBinding(connectionId, projectKey));
  }
}
