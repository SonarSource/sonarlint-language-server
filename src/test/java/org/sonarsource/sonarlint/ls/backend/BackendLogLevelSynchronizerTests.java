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
package org.sonarsource.sonarlint.ls.backend;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BackendLogLevelSynchronizerTests {

  private final BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  private final BackendLogLevelSynchronizer underTest = new BackendLogLevelSynchronizer(backendServiceFacade);

  @Test
  void should_sync_when_verbose_flag_changes() {
    var oldSettings = workspaceSettings(false);
    var newSettings = workspaceSettings(true);

    underTest.onChange(oldSettings, newSettings);

    verify(backendServiceFacade).syncBackendLogLevel(true);
  }

  @Test
  void should_not_sync_when_verbose_flag_unchanged() {
    var settings = workspaceSettings(false);

    underTest.onChange(settings, workspaceSettings(false));

    verifyNoInteractions(backendServiceFacade);
  }

  private static WorkspaceSettings workspaceSettings(boolean showVerboseLogs) {
    return new WorkspaceSettings(false, null, null, null, null, showVerboseLogs, null, false, true, "", false);
  }
}
