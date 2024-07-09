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

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.ServerMode;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.notebooks.NotebookDiagnosticPublisher;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForcedAnalysisCoordinatorTests {

  private ForcedAnalysisCoordinator underTest;
  private OpenFilesCache openFilesCache;
  private LanguageClientLogger lsLogOutput;
  private final BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  private final BackendService backendService = mock(BackendService.class);
  private final WorkspaceFoldersManager workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
  URI workspaceFolderUri = URI.create("file:///my/workspace/folder");

  @BeforeEach
  public void init() {
    lsLogOutput = mock(LanguageClientLogger.class);
    SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
    when(client.filterOutExcludedFiles(any()))
      .thenReturn(CompletableFuture.completedFuture(
        new SonarLintExtendedLanguageClient.FileUrisResult(List.of("file://Foo1.java", "file://Foo2.java"))));
    openFilesCache = new OpenFilesCache(lsLogOutput);
    OpenNotebooksCache openNotebooksCache = new OpenNotebooksCache(lsLogOutput, mock(NotebookDiagnosticPublisher.class));
    underTest = new ForcedAnalysisCoordinator(workspaceFoldersManager, mock(ProjectBindingManager.class), openFilesCache,
      openNotebooksCache, client, backendServiceFacade);
    when(backendServiceFacade.getBackendService()).thenReturn(backendService);
    when(workspaceFoldersManager.findFolderForFile(any())).thenReturn(Optional.of(new WorkspaceFolderWrapper(workspaceFolderUri,
      new WorkspaceFolder("file:///my/workspace/folder", "folder"), lsLogOutput)));

  }

  @Test
  void shouldScheduleAnalysisOfAllOpenJavaFilesWithoutIssueRefreshOnClasspathChange() {
    URI file1Uri = URI.create("file://Foo1.java");
    openFilesCache.didOpen(file1Uri, "java", "class Foo1 {}", 1);
    URI file2Uri = URI.create("file://Foo2.java");
    openFilesCache.didOpen(file2Uri, "java", "class Foo2 {}", 1);
    URI file3Uri = URI.create("file://Foo.js");
    openFilesCache.didOpen(file3Uri, "javascript", "alert();", 1);

    underTest.didClasspathUpdate();

    verify(backendService).analyzeFilesList(workspaceFolderUri.toString(), List.of(file1Uri, file2Uri));
  }

  @Test
  void shouldScheduleAnalysisOfAllOpenJavaFilesWithoutIssueRefreshOnJavaServerModeChangeToStandard() {
    URI file1Uri = URI.create("file://Foo1.java");
    openFilesCache.didOpen(file1Uri, "java", "class Foo1 {}", 1);
    URI file2Uri = URI.create("file://Foo2.java");
    openFilesCache.didOpen(file2Uri, "java", "class Foo2 {}", 1);
    URI file3Uri = URI.create("file://Foo.js");
    openFilesCache.didOpen(file3Uri, "javascript", "alert();", 1);

    underTest.didServerModeChange(ServerMode.STANDARD);

    verify(backendService).analyzeFilesList(workspaceFolderUri.toString(), List.of(file1Uri, file2Uri));
  }

  @Test
  void shouldNotScheduleAnalysisOnJavaServerModeChangeOutToStandard() {
    openFilesCache.didOpen(URI.create("file://Foo1.java"), "java", "class Foo1 {}", 1);
    openFilesCache.didOpen(URI.create("file://Foo2.java"), "java", "class Foo2 {}", 1);
    openFilesCache.didOpen(URI.create("file://Foo.js"), "javascript", "alert();", 1);

    underTest.didServerModeChange(ServerMode.LIGHTWEIGHT);

    verify(backendService, times(0)).analyzeFilesList(any(), any());
  }

}
