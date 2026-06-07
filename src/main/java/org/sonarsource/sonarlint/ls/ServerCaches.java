/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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

import org.sonarsource.sonarlint.ls.connected.DependencyRisksCache;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.notebooks.NotebookDiagnosticPublisher;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;

/**
 * Groups cache and publisher instances used across the language server
 * to reduce the field count in {@link SonarLintLanguageServer}.
 */
class ServerCaches {

  final IssuesCache issuesCache;
  final HotspotsCache securityHotspotsCache;
  final OpenFilesCache openFilesCache;
  final OpenNotebooksCache openNotebooksCache;
  final DependencyRisksCache dependencyRisksCache;
  final JavaConfigCache javaConfigCache;
  final NotebookDiagnosticPublisher notebookDiagnosticPublisher;

  ServerCaches(IssuesCache issuesCache, HotspotsCache securityHotspotsCache,
    OpenFilesCache openFilesCache, OpenNotebooksCache openNotebooksCache,
    DependencyRisksCache dependencyRisksCache, JavaConfigCache javaConfigCache,
    NotebookDiagnosticPublisher notebookDiagnosticPublisher) {
    this.issuesCache = issuesCache;
    this.securityHotspotsCache = securityHotspotsCache;
    this.openFilesCache = openFilesCache;
    this.openNotebooksCache = openNotebooksCache;
    this.dependencyRisksCache = dependencyRisksCache;
    this.javaConfigCache = javaConfigCache;
    this.notebookDiagnosticPublisher = notebookDiagnosticPublisher;
  }
}
