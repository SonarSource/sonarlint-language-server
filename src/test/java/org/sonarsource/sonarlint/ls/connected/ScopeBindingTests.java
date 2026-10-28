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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.connected.ScopeBinding.Origin;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeBindingTests {

  private static final URI SCOPE = URI.create("file:///repo/");

  @Test
  void a_scope_with_a_connection_is_resolved_and_gives_its_project_binding() {
    var scope = ScopeBinding.of(SCOPE, "repo", ".", "projectKey", "connectionId", "target", Origin.SETTINGS);

    assertThat(scope.isResolved()).isTrue();
    assertThat(scope.toProjectBinding()).contains(new ProjectBinding("connectionId", "projectKey"));
  }

  @Test
  void a_scope_without_a_connection_is_not_resolved_and_has_no_project_binding() {
    var scope = ScopeBinding.of(SCOPE, "repo", ".", "projectKey", null, "target", Origin.SONAR_CONFIG);

    assertThat(scope.isResolved()).isFalse();
    assertThat(scope.connectionId()).isNull();
    assertThat(scope.toProjectBinding()).isEmpty();
  }

  @Test
  void lazily_resolves_once_whether_or_not_it_finds_a_connection() {
    var calls = new AtomicInteger();
    var found = ScopeBinding.lazily(() -> {
      calls.incrementAndGet();
      return "connectionId";
    });
    var missing = ScopeBinding.lazily(() -> {
      calls.incrementAndGet();
      return null;
    });

    assertThat(found.get()).isEqualTo("connectionId");
    assertThat(found.get()).isEqualTo("connectionId");
    assertThat(missing.get()).isNull();
    assertThat(missing.get()).isNull();

    assertThat(calls).hasValue(2);
  }

  @Test
  void a_lazy_scope_does_not_resolve_until_asked() {
    var calls = new AtomicInteger();
    var scope = new ScopeBinding(SCOPE, "repo", ".", "projectKey", ScopeBinding.lazily(() -> {
      calls.incrementAndGet();
      return "connectionId";
    }), "target", Origin.SETTINGS);

    assertThat(calls).hasValue(0);
    assertThat(scope.connectionId()).isEqualTo("connectionId");
    assertThat(calls).hasValue(1);
  }
}
