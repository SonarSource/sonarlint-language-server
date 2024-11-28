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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModuleEventsProcessorTest {
  ModuleEventsProcessor moduleEventsProcessor;
  WorkspaceFoldersManager workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
  FileTypeClassifier fileTypeClassifier = mock(FileTypeClassifier.class);
  JavaConfigCache javaConfigCache = mock(JavaConfigCache.class);
  BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  SettingsManager settingsManager = mock(SettingsManager.class);

  @BeforeEach
  void setUp() {
    moduleEventsProcessor = new ModuleEventsProcessor(workspaceFoldersManager, fileTypeClassifier, javaConfigCache, backendServiceFacade, settingsManager);
  }

  @Test
  void should_get_client_file_dto_outside_workspace_folder() {
    var content = "print('Hello, World!')";
    var testFile1 = new VersionedOpenFile(URI.create("file:///tmp/test.py"), "python", 1, content);

    var clientFileDto = moduleEventsProcessor.getClientFileDto(testFile1);

    assertThat(clientFileDto).isNotNull();
    assertThat(clientFileDto.getDetectedLanguage()).isEqualTo(Language.PYTHON);
    assertThat(clientFileDto.getContent()).isEqualTo(content);
    assertThat(clientFileDto.getConfigScopeId()).isEqualTo(BackendServiceFacade.ROOT_CONFIGURATION_SCOPE);
    assertThat(clientFileDto.getUri()).hasToString(testFile1.getUri().toString());
    assertThat(clientFileDto.getCharset()).isEqualTo(StandardCharsets.UTF_8.name());
  }

  @Test
  void should_get_client_file_dto_inside_workspace_folder() {
    var content = "print('Hello, World!')";
    var fileUri = URI.create("file:///tmp/test.py");
    var mockFolder = mock(WorkspaceFolderWrapper.class);
    when(workspaceFoldersManager.findFolderForFile(fileUri)).thenReturn(Optional.of(mockFolder));
    when(mockFolder.getRootPath()).thenReturn(Path.of(URI.create("file:///tmp/").getPath()));
    when(mockFolder.getUri()).thenReturn(URI.create("file:///tmp/"));
    when(mockFolder.getSettings()).thenReturn(null);
    var testFile1 = new VersionedOpenFile(fileUri, "python", 1, content);

    var clientFileDto = moduleEventsProcessor.getClientFileDto(testFile1);

    assertThat(clientFileDto).isNotNull();
    assertThat(clientFileDto.getDetectedLanguage()).isEqualTo(Language.PYTHON);
    assertThat(clientFileDto.getContent()).isEqualTo(content);
    assertThat(clientFileDto.getConfigScopeId()).isEqualTo("file:///tmp/");
    assertThat(clientFileDto.getUri()).hasToString(testFile1.getUri().toString());
    assertThat(clientFileDto.getCharset()).isEqualTo(StandardCharsets.UTF_8.name());
  }
}