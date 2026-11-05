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

import java.net.URI;
import java.util.Objects;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.ls.connected.Origin;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;

/**
 * A configuration scope and what it is bound to; the binding is {@code null} while the scope is unbound. A scope that
 * is declared bound to something that cannot be resolved (e.g. a connection that does not exist) is unbound too, but
 * remembers what it was declared to be bound to.
 */
public record ConfigurationScope(URI uri, String name, String relativePath, Origin origin, @Nullable ProjectBinding binding,
  @Nullable UnresolvedBinding unresolved) {

  public ConfigurationScope(URI uri, String name, String relativePath, Origin origin, @Nullable ProjectBinding binding) {
    this(uri, name, relativePath, origin, binding, null);
  }

  public record UnresolvedBinding(String projectKey, String target) {
  }

  /**
   * Whether the user can change the binding from the IDE. It cannot when the binding comes from a file that is
   * maintained outside of the IDE.
   */
  public boolean editable() {
    return origin != Origin.SONAR_CONFIG;
  }

  public boolean bindingConfigurationDiffersFrom(ConfigurationScope other) {
    return !Objects.equals(binding, other.binding) || editable() != other.editable();
  }

  /**
   * Whether the clients have something new to show about this scope: its binding configuration, or what it was
   * declared bound to while it has no binding.
   */
  public boolean bindingStateDiffersFrom(ConfigurationScope other) {
    return bindingConfigurationDiffersFrom(other) || !Objects.equals(unresolved, other.unresolved);
  }
}
