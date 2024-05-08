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
package org.sonarsource.sonarlint.ls.folders;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import testutils.SonarLintLogTester;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModuleEventsProcessorTests {
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();
  private static final WorkspaceFolderSettings EMPTY_SETTINGS = new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null, null);
  private ModuleEventsProcessor underTest;
  private EnginesFactory enginesFactory;
  private WorkspaceFoldersManager foldersManager;
  private StandaloneEngineManager standaloneEngineManager;

  @BeforeEach
  void prepare() {
    enginesFactory = mock(EnginesFactory.class);
    foldersManager = mock(WorkspaceFoldersManager.class);
    standaloneEngineManager = mock(StandaloneEngineManager.class);
    var backendServiceFacade = mock(BackendServiceFacade.class);
    when(backendServiceFacade.getBackendService()).thenReturn(mock(BackendService.class));
    underTest = new ModuleEventsProcessor(foldersManager, new FileTypeClassifier(logTester.getLogger()),
      mock(JavaConfigCache.class), backendServiceFacade);
  }
//
//  @Test
//  void dontForwardFileEventToEngineWhenOutsideOfFolder() throws Exception {
//    var sonarLintEngine = mock(SonarLintAnalysisEngine.class);
//    when(enginesFactory.createEngine(null)).thenReturn(sonarLintEngine);
//
//    underTest.didChangeWatchedFiles(List.of(new FileEvent("uri", FileChangeType.Created)));
//
//    Thread.sleep(1000);
//
//    verifyNoInteractions(sonarLintEngine);
//  }
//
//  @Test
//  void forwardFileCreatedEventToEngineWhenInsideOfFolder() {
//    var fileEventArgumentCaptor = ArgumentCaptor.forClass(ClientModuleFileEvent.class);
//    var folderURI = URI.create("file:///folder");
//    var sonarLintEngine = mock(SonarLintAnalysisEngine.class);
//    when(enginesFactory.createEngine(null)).thenReturn(sonarLintEngine);
//    var folder = new WorkspaceFolderWrapper(folderURI, new WorkspaceFolder(folderURI.toString(), "folder"), logTester.getLogger());
//    folder.setSettings(EMPTY_SETTINGS);
//    when(foldersManager.findFolderForFile(any())).thenReturn(Optional.of(folder));
//    when(sonarLintEngine.fireModuleFileEvent(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
//    when(standaloneEngineManager.getOrCreateAnalysisEngine()).thenReturn(sonarLintEngine);
//
//    underTest.didChangeWatchedFiles(List.of(new FileEvent("file:///folder/file.py", FileChangeType.Created)));
//
//    verify(sonarLintEngine, Mockito.timeout(1000).times(1)).fireModuleFileEvent(eq(folderURI), fileEventArgumentCaptor.capture());
//    var fileEvent = fileEventArgumentCaptor.getValue();
//    assertThat(fileEvent.type()).isEqualTo(ModuleFileEvent.Type.CREATED);
//  }
//
//  @Test
//  void forwardFileModifiedEventToEngineWhenInsideOfFolder() {
//    var fileEventArgumentCaptor = ArgumentCaptor.forClass(ClientModuleFileEvent.class);
//    var folderURI = URI.create("file:///folder");
//    var sonarLintEngine = mock(SonarLintAnalysisEngine.class);
//    when(enginesFactory.createEngine(null)).thenReturn(sonarLintEngine);
//    var folder = new WorkspaceFolderWrapper(folderURI, new WorkspaceFolder(folderURI.toString(), "folder"), logTester.getLogger());
//    folder.setSettings(EMPTY_SETTINGS);
//    when(foldersManager.findFolderForFile(any())).thenReturn(Optional.of(folder));
//    when(sonarLintEngine.fireModuleFileEvent(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
//    when(standaloneEngineManager.getOrCreateAnalysisEngine()).thenReturn(sonarLintEngine);
//
//    underTest.didChangeWatchedFiles(List.of(new FileEvent("file:///folder/file.py", FileChangeType.Changed)));
//
//    verify(sonarLintEngine, Mockito.timeout(1000).times(1)).fireModuleFileEvent(eq(folderURI), fileEventArgumentCaptor.capture());
//    var fileEvent = fileEventArgumentCaptor.getValue();
//    assertThat(fileEvent.type()).isEqualTo(ModuleFileEvent.Type.MODIFIED);
//  }
//
//  @Test
//  void forwardFileDeletedEventToEngineWhenInsideOfFolder() {
//    var fileEventArgumentCaptor = ArgumentCaptor.forClass(ClientModuleFileEvent.class);
//    var folderURI = URI.create("file:///folder");
//    var sonarLintEngine = mock(SonarLintAnalysisEngine.class);
//    when(enginesFactory.createEngine(null)).thenReturn(sonarLintEngine);
//    var folder = new WorkspaceFolderWrapper(folderURI, new WorkspaceFolder(folderURI.toString(), "folder"), logTester.getLogger());
//    folder.setSettings(EMPTY_SETTINGS);
//    when(foldersManager.findFolderForFile(any())).thenReturn(Optional.of(folder));
//    when(sonarLintEngine.fireModuleFileEvent(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
//    when(standaloneEngineManager.getOrCreateAnalysisEngine()).thenReturn(sonarLintEngine);
//
//    underTest.didChangeWatchedFiles(List.of(new FileEvent("file:///folder/file.py", FileChangeType.Deleted)));
//
//    verify(sonarLintEngine, Mockito.timeout(1000).times(1)).fireModuleFileEvent(eq(folderURI), fileEventArgumentCaptor.capture());
//    var fileEvent = fileEventArgumentCaptor.getValue();
//    assertThat(fileEvent.type()).isEqualTo(ModuleFileEvent.Type.DELETED);
//  }

}
