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
package org.sonarsource.sonarlint.ls.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.java.JavaConfigProvider;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FolderFileSystemTest {
  @TempDir
  static Path tempFolder;

  @BeforeAll
  static void setUp() throws IOException {
    Files.createFile(tempFolder.resolve("file.py"));
  }

  @Test
  void should_provide_files_of_requested_type_and_suffix() {
    List<ClientInputFile> files = new ArrayList<>();
    FileTypeClassifier fileTypeClassifier = mock(FileTypeClassifier.class);
    when(fileTypeClassifier.isTest(any(), any(), any())).thenReturn(false);
    WorkspaceFolderWrapper folderWrapper = new WorkspaceFolderWrapper(tempFolder.toUri(), new WorkspaceFolder(tempFolder.toString(), "My Folder"));
    folderWrapper.setSettings(new WorkspaceFolderSettings(null,null, Collections.emptyMap(), null));
    FolderFileSystem folderFileSystem = new FolderFileSystem(folderWrapper, mock(JavaConfigProvider.class), fileTypeClassifier);

    folderFileSystem.files("py", InputFile.Type.MAIN, files::add);

    assertThat(files).hasSize(1);
  }

}
