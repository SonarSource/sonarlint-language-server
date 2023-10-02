/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
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

import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CleanAsYouCodeManagerTest {

  DiagnosticPublisher diagnosticPublisher = mock(DiagnosticPublisher.class);
  OpenFilesCache openFilesCache = mock(OpenFilesCache.class);
  BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);

  CleanAsYouCodeManager underTest = new CleanAsYouCodeManager(diagnosticPublisher, openFilesCache, backendServiceFacade);

  @Test
  void shouldSetFocusOnNewCodeToDiagnosticPublisherOnChange() {
    underTest.onChange(null,
      new WorkspaceSettings(true, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(),
        false, false, "", true));

    verify(diagnosticPublisher).setFocusOnNewCode(true);
  }

  @Test
  void shouldNotifyBackendAndRepublishDiagnosticsOnToggle() {
    BackendService backendService = mock(BackendService.class);
    when(backendServiceFacade.getBackendService()).thenReturn(backendService);
    var dummyFile1 = URI.create("dummyFile1");
    var dummyFile2 = URI.create("dummyFile2");
    when(openFilesCache.getAll())
      .thenReturn(List.of(new VersionedOpenFile(dummyFile1, "languageId", 0, "file content"),
        new VersionedOpenFile(dummyFile2, "languageId", 0, "file content")));

    underTest.onChange(
      new WorkspaceSettings(true, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(),
        false, false, "", false),
      new WorkspaceSettings(true, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(),
        false, false, "", true));

    verify(backendService).toggleCleanAsYouCode();
    verify(diagnosticPublisher).publishDiagnostics(dummyFile1, false);
    verify(diagnosticPublisher).publishDiagnostics(dummyFile2, false);
  }
}
