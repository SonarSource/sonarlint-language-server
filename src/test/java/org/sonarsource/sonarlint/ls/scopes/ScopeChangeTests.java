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
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.connected.Origin;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeChangeTests {

  private static final URI URI_OF_SCOPE = URI.create("file:///repo/");
  private static final ProjectBinding BINDING = new ProjectBinding("connection", "project");

  @Test
  void a_scope_that_was_bound_and_is_not_lost_its_binding() {
    assertThat(new ScopeChange(scope(BINDING), scope(null)).lostItsBinding()).isTrue();
  }

  @Test
  void a_scope_that_is_rebound_or_newly_bound_did_not_lose_its_binding() {
    assertThat(new ScopeChange(scope(BINDING), scope(new ProjectBinding("connection", "other"))).lostItsBinding()).isFalse();
    assertThat(new ScopeChange(scope(null), scope(BINDING)).lostItsBinding()).isFalse();
  }

  @Test
  void a_scope_that_was_opened_or_closed_did_not_lose_its_binding() {
    assertThat(new ScopeChange(null, scope(null)).lostItsBinding()).isFalse();
    assertThat(new ScopeChange(scope(BINDING), null).lostItsBinding()).isFalse();
  }

  private static ConfigurationScope scope(ProjectBinding binding) {
    return new ConfigurationScope(URI_OF_SCOPE, "repo", ".", Origin.SETTINGS, binding);
  }
}
