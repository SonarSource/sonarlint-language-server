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

class ConfigurationScopeTests {

  private static final URI URI_A = URI.create("file:///repo/");
  private static final ProjectBinding BINDING = new ProjectBinding("sq", "project");

  @Test
  void the_binding_cannot_be_edited_when_it_comes_from_a_file_maintained_outside_of_the_ide() {
    assertThat(scope(Origin.SONAR_CONFIG, BINDING).editable()).isFalse();
    assertThat(scope(Origin.SETTINGS, BINDING).editable()).isTrue();
  }

  @Test
  void the_binding_configuration_differs_when_the_binding_differs() {
    assertThat(scope(Origin.SETTINGS, BINDING).bindingConfigurationDiffersFrom(scope(Origin.SETTINGS, null))).isTrue();
    assertThat(scope(Origin.SETTINGS, BINDING).bindingConfigurationDiffersFrom(scope(Origin.SETTINGS, new ProjectBinding("sq", "other")))).isTrue();
  }

  @Test
  void the_binding_configuration_differs_when_only_the_editability_differs() {
    assertThat(scope(Origin.SONAR_CONFIG, BINDING).bindingConfigurationDiffersFrom(scope(Origin.SETTINGS, BINDING))).isTrue();
  }

  @Test
  void the_binding_configuration_is_the_same_when_only_unrelated_attributes_differ() {
    var renamed = new ConfigurationScope(URI_A, "other name", "sub", Origin.SETTINGS, BINDING);

    assertThat(renamed.bindingConfigurationDiffersFrom(scope(Origin.SETTINGS, BINDING))).isFalse();
  }

  @Test
  void the_binding_state_differs_when_only_the_unresolved_binding_differs() {
    var unresolved = new ConfigurationScope(URI_A, "name", ".", Origin.SETTINGS, null, new ConfigurationScope.UnresolvedBinding("project", "a", null, null, null));
    var otherTarget = new ConfigurationScope(URI_A, "name", ".", Origin.SETTINGS, null, new ConfigurationScope.UnresolvedBinding("project", "b", null, null, null));

    assertThat(unresolved.bindingConfigurationDiffersFrom(otherTarget)).isFalse();
    assertThat(unresolved.bindingStateDiffersFrom(otherTarget)).isTrue();
    assertThat(unresolved.bindingStateDiffersFrom(scope(Origin.SETTINGS, null))).isTrue();
    assertThat(unresolved.bindingStateDiffersFrom(unresolved)).isFalse();
  }

  private static ConfigurationScope scope(Origin origin, ProjectBinding binding) {
    return new ConfigurationScope(URI_A, "name", ".", origin, binding);
  }
}
