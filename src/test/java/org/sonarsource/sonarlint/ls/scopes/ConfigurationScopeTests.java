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
  void scopes_declared_by_a_sonar_config_are_not_bindable_by_the_client() {
    assertThat(scope(Origin.SONAR_CONFIG, BINDING).bindable()).isFalse();
    assertThat(scope(Origin.SETTINGS, null).bindable()).isTrue();
  }

  @Test
  void binding_suggestions_are_only_disabled_for_scopes_declared_by_a_sonar_config() {
    assertThat(scope(Origin.SONAR_CONFIG, null).bindingSuggestionDisabled()).isTrue();
    assertThat(scope(Origin.SETTINGS, null).bindingSuggestionDisabled()).isFalse();
  }

  @Test
  void the_binding_configuration_differs_when_the_binding_differs() {
    assertThat(scope(Origin.SETTINGS, BINDING).bindingConfigurationDiffersFrom(scope(Origin.SETTINGS, null))).isTrue();
    assertThat(scope(Origin.SETTINGS, BINDING).bindingConfigurationDiffersFrom(scope(Origin.SETTINGS, new ProjectBinding("sq", "other")))).isTrue();
  }

  @Test
  void the_binding_configuration_differs_when_only_the_binding_suggestions_differ() {
    assertThat(scope(Origin.SONAR_CONFIG, BINDING).bindingConfigurationDiffersFrom(scope(Origin.SETTINGS, BINDING))).isTrue();
  }

  @Test
  void the_binding_configuration_is_the_same_when_only_unrelated_attributes_differ() {
    var renamed = new ConfigurationScope(URI_A, "other name", "sub", Origin.SETTINGS, BINDING);

    assertThat(renamed.bindingConfigurationDiffersFrom(scope(Origin.SETTINGS, BINDING))).isFalse();
  }

  private static ConfigurationScope scope(Origin origin, ProjectBinding binding) {
    return new ConfigurationScope(URI_A, "name", ".", origin, binding);
  }
}
