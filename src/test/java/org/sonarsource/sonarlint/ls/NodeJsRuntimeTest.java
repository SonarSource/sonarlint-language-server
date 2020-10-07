/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.common.Version;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NodeJsRuntimeTest {

  @TempDir
  Path temp;

  private WorkspaceSettings settings;
  private SettingsManager settingsManager;
  private NodeJsHelper nodeJsHelper;

  private NodeJsRuntime underTest;

  @BeforeEach
  void setUp() {
    nodeJsHelper = mock(NodeJsHelper.class);
    settings = mock(WorkspaceSettings.class);
    settingsManager = mock(SettingsManager.class);
    when(settingsManager.getCurrentSettings()).thenReturn(settings);

    underTest = new NodeJsRuntime(settingsManager, () -> nodeJsHelper);
  }

  @Test
  void shouldLazyInitializeWithoutNodeSettings() {
    when(settings.pathToNodeExecutable()).thenReturn(null);

    assertThat(underTest.nodeVersion()).isNull();
    assertThat(underTest.getNodeJsPath()).isNull();
    assertThat(underTest.getNodeJsVersion()).isNull();

    verify(settings).pathToNodeExecutable();
    verify(nodeJsHelper, times(1)).detect(null);
  }

  @Test
  void shouldLazyInitializeWithNodeSettings() {
    when(settings.pathToNodeExecutable()).thenReturn(temp.getFileName().toString());
    when(nodeJsHelper.getNodeJsPath()).thenReturn(temp.getFileName());
    String versionString = "12.34.56";
    Version version = Version.create(versionString);
    when(nodeJsHelper.getNodeJsVersion()).thenReturn(version);

    assertThat(underTest.getNodeJsPath()).isEqualTo(temp.getFileName());
    assertThat(underTest.getNodeJsVersion()).isEqualTo(version);
    assertThat(underTest.nodeVersion()).isEqualTo(versionString);

    verify(settings).pathToNodeExecutable();
    verify(nodeJsHelper, times(1)).detect(temp.getFileName());
  }

  @Test
  void shouldKeepOldSettingsWhenNotChanged() {
    when(settings.pathToNodeExecutable()).thenReturn(null);
    WorkspaceSettings newSettings = mock(WorkspaceSettings.class);
    when(newSettings.pathToNodeExecutable()).thenReturn(null);

    assertThat(underTest.nodeVersion()).isNull();
    assertThat(underTest.getNodeJsPath()).isNull();
    assertThat(underTest.getNodeJsVersion()).isNull();

    underTest.onChange(settings, newSettings);

    assertThat(underTest.nodeVersion()).isNull();
    assertThat(underTest.getNodeJsPath()).isNull();
    assertThat(underTest.getNodeJsVersion()).isNull();

    verify(nodeJsHelper, times(1)).detect(null);
  }

  @Test
  void shouldRedetectWhenNodePathChanges() {
    when(settings.pathToNodeExecutable()).thenReturn(null);
    WorkspaceSettings newSettings = mock(WorkspaceSettings.class);
    String newPathToNodeSettings = temp.getFileName().toString();
    when(newSettings.pathToNodeExecutable()).thenReturn(newPathToNodeSettings);

    assertThat(underTest.nodeVersion()).isNull();
    assertThat(underTest.getNodeJsPath()).isNull();
    assertThat(underTest.getNodeJsVersion()).isNull();

    underTest.onChange(settings, newSettings);

    when(settings.pathToNodeExecutable()).thenReturn(newPathToNodeSettings);
    when(nodeJsHelper.getNodeJsPath()).thenReturn(temp.resolve("node"));
    String version = "12.34.56";
    when(nodeJsHelper.getNodeJsVersion()).thenReturn(Version.create(version));

    assertThat(underTest.nodeVersion()).isEqualTo(version);

    verify(nodeJsHelper, times(1)).detect(null);
    verify(nodeJsHelper, times(1)).detect(temp.getFileName());
  }
}
