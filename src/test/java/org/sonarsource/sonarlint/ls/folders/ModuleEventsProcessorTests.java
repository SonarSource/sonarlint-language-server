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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModuleEventsProcessorTests {

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
    underTest = new ModuleEventsProcessor(standaloneEngineManager, foldersManager, mock(ProjectBindingManager.class), new FileTypeClassifier(), mock(JavaConfigCache.class));
  }

  @Test
  void dontForwardFileEventToEngineWhenOutsideOfFolder() throws Exception {
    var sonarLintEngine = mock(StandaloneSonarLintEngine.class);
    when(enginesFactory.createStandaloneEngine()).thenReturn(sonarLintEngine);

    underTest.didChangeWatchedFiles(List.of(new FileEvent("uri", FileChangeType.Created)));

    Thread.sleep(1000);

    verifyNoInteractions(sonarLintEngine);
  }

  @Test
  void forwardFileCreatedEventToEngineWhenInsideOfFolder() {
    var fileEventArgumentCaptor = ArgumentCaptor.forClass(ClientModuleFileEvent.class);
    var folderURI = URI.create("file:///folder");
    var sonarLintEngine = mock(StandaloneSonarLintEngine.class);
    when(enginesFactory.createStandaloneEngine()).thenReturn(sonarLintEngine);
    var folder = new WorkspaceFolderWrapper(folderURI, new WorkspaceFolder(folderURI.toString(), "folder"));
    folder.setSettings(EMPTY_SETTINGS);
    when(foldersManager.findFolderForFile(any())).thenReturn(Optional.of(folder));
    when(sonarLintEngine.fireModuleFileEvent(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    when(standaloneEngineManager.getOrCreateStandaloneEngine()).thenReturn(sonarLintEngine);

    underTest.didChangeWatchedFiles(List.of(new FileEvent("file:///folder/file.py", FileChangeType.Created)));

    verify(sonarLintEngine, Mockito.timeout(1000).times(1)).fireModuleFileEvent(eq(folderURI), fileEventArgumentCaptor.capture());
    var fileEvent = fileEventArgumentCaptor.getValue();
    assertThat(fileEvent.type()).isEqualTo(ModuleFileEvent.Type.CREATED);
  }

  @Test
  void forwardFileModifiedEventToEngineWhenInsideOfFolder() {
    var fileEventArgumentCaptor = ArgumentCaptor.forClass(ClientModuleFileEvent.class);
    var folderURI = URI.create("file:///folder");
    var sonarLintEngine = mock(StandaloneSonarLintEngine.class);
    when(enginesFactory.createStandaloneEngine()).thenReturn(sonarLintEngine);
    var folder = new WorkspaceFolderWrapper(folderURI, new WorkspaceFolder(folderURI.toString(), "folder"));
    folder.setSettings(EMPTY_SETTINGS);
    when(foldersManager.findFolderForFile(any())).thenReturn(Optional.of(folder));
    when(sonarLintEngine.fireModuleFileEvent(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    when(standaloneEngineManager.getOrCreateStandaloneEngine()).thenReturn(sonarLintEngine);

    underTest.didChangeWatchedFiles(List.of(new FileEvent("file:///folder/file.py", FileChangeType.Changed)));

    verify(sonarLintEngine, Mockito.timeout(1000).times(1)).fireModuleFileEvent(eq(folderURI), fileEventArgumentCaptor.capture());
    var fileEvent = fileEventArgumentCaptor.getValue();
    assertThat(fileEvent.type()).isEqualTo(ModuleFileEvent.Type.MODIFIED);
  }

  @Test
  void forwardFileDeletedEventToEngineWhenInsideOfFolder() {
    var fileEventArgumentCaptor = ArgumentCaptor.forClass(ClientModuleFileEvent.class);
    var folderURI = URI.create("file:///folder");
    var sonarLintEngine = mock(StandaloneSonarLintEngine.class);
    when(enginesFactory.createStandaloneEngine()).thenReturn(sonarLintEngine);
    var folder = new WorkspaceFolderWrapper(folderURI, new WorkspaceFolder(folderURI.toString(), "folder"));
    folder.setSettings(EMPTY_SETTINGS);
    when(foldersManager.findFolderForFile(any())).thenReturn(Optional.of(folder));
    when(sonarLintEngine.fireModuleFileEvent(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    when(standaloneEngineManager.getOrCreateStandaloneEngine()).thenReturn(sonarLintEngine);

    underTest.didChangeWatchedFiles(List.of(new FileEvent("file:///folder/file.py", FileChangeType.Deleted)));

    verify(sonarLintEngine, Mockito.timeout(1000).times(1)).fireModuleFileEvent(eq(folderURI), fileEventArgumentCaptor.capture());
    var fileEvent = fileEventArgumentCaptor.getValue();
    assertThat(fileEvent.type()).isEqualTo(ModuleFileEvent.Type.DELETED);
  }

}
