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

import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

public class CleanAsYouCodeManager implements WorkspaceSettingsChangeListener {
  private final DiagnosticPublisher diagnosticPublisher;
  private final OpenFilesCache openFilesCache;
  private final BackendServiceFacade backendServiceFacade;

  public CleanAsYouCodeManager(DiagnosticPublisher diagnosticPublisher, OpenFilesCache openFilesCache,
    BackendServiceFacade backendServiceFacade) {
    this.diagnosticPublisher = diagnosticPublisher;
    this.openFilesCache = openFilesCache;
    this.backendServiceFacade = backendServiceFacade;
  }

  @Override
  public void onChange(@Nullable WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    diagnosticPublisher.setFocusOnNewCode(newValue.isFocusOnNewCode());
    if (oldValue != null && oldValue.isFocusOnNewCode() != newValue.isFocusOnNewCode()) {
      backendServiceFacade.getBackendService().toggleCleanAsYouCode();
      openFilesCache.getAll().forEach(f -> diagnosticPublisher.publishDiagnostics(f.getUri(), false));
    }
  }
}
