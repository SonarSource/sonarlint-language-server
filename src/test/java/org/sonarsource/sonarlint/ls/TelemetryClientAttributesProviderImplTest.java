/*
 * SonarLint Language Server
 * Copyright (C) 2009-2021 SonarSource SA
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
package org.sonarsource.sonarlint.ls;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryClientAttributesProviderImplTest {


  TelemetryClientAttributesProviderImpl underTest;

  @BeforeEach
  public void init() {
    NodeJsRuntime nodeJsRuntime = mock(NodeJsRuntime.class);
    when(nodeJsRuntime.nodeVersion()).thenReturn("nodeVersion");
    SettingsManager settingsManager = mock(SettingsManager.class);
    WorkspaceSettings workspaceSettings = mock(WorkspaceSettings.class);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    when(workspaceSettings.getExcludedRules()).thenReturn(Collections.emptyList());
    when(workspaceSettings.getIncludedRules()).thenReturn(Collections.emptyList());
    underTest = new TelemetryClientAttributesProviderImpl(settingsManager, mock(ProjectBindingManager.class), nodeJsRuntime, mock(StandaloneEngineManager.class));
  }

  @Test
  void testNodeVersion() {
    assertThat(underTest.nodeVersion()).isPresent();
    assertThat(underTest.nodeVersion()).contains("nodeVersion");
  }

  @Test
  void testServerConnection() {
    assertThat(underTest.useSonarCloud()).isFalse();
    assertThat(underTest.usesConnectedMode()).isFalse();
  }

  @Test
  void testDevNotifications() {
    assertThat(underTest.devNotificationsDisabled()).isFalse();
  }

  @Test
  void testTelemetry() {
    assertThat(underTest.getDefaultDisabledRules()).isEmpty();
    assertThat(underTest.getNonDefaultEnabledRules()).isEmpty();
  }

}
