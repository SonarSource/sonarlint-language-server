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
package org.sonarsource.sonarlint.ls.file;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FolderFileSystemTests {
  private static final WorkspaceFolderSettings EMPTY_SETTINGS = new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null, null);
  @TempDir
  static Path tempFolder;
  private static Path pythonFile;

  @BeforeAll
  static void setUp() throws IOException {
    pythonFile = Files.createFile(tempFolder.resolve("file.py"));
  }

  @Test
  void should_provide_main_files_of_requested_suffix() {
    var fileTypeClassifier = mock(FileTypeClassifier.class);
    when(fileTypeClassifier.isTest(any(), any(), anyBoolean(), any())).thenReturn(false);
    var folderWrapper = new WorkspaceFolderWrapper(tempFolder.toUri(), new WorkspaceFolder(tempFolder.toString(), "My Folder"));
    folderWrapper.setSettings(EMPTY_SETTINGS);
    var folderFileSystem = new FolderFileSystem(folderWrapper, mock(JavaConfigCache.class), fileTypeClassifier);

    var files = folderFileSystem.files("py", InputFile.Type.MAIN);

    assertThat(files)
      .extracting(ClientInputFile::getPath, FolderFileSystemTests::getFileContents, ClientInputFile::isTest, ClientInputFile::getCharset, ClientInputFile::getClientObject,
        ClientInputFile::relativePath, ClientInputFile::uri)
      .containsExactly(tuple(pythonFile.toAbsolutePath().toString(), "", false, StandardCharsets.UTF_8, pythonFile.toUri(), "file.py", pythonFile.toUri()));
  }

  @Test
  void should_provide_test_files_of_requested_suffix() {
    var fileTypeClassifier = mock(FileTypeClassifier.class);
    when(fileTypeClassifier.isTest(any(), any(), anyBoolean(), any())).thenReturn(true);
    var folderWrapper = new WorkspaceFolderWrapper(tempFolder.toUri(), new WorkspaceFolder(tempFolder.toString(), "My Folder"));
    folderWrapper.setSettings(EMPTY_SETTINGS);
    var folderFileSystem = new FolderFileSystem(folderWrapper, mock(JavaConfigCache.class), fileTypeClassifier);

    var files = folderFileSystem.files("py", InputFile.Type.TEST);

    assertThat(files)
      .extracting(ClientInputFile::getPath, FolderFileSystemTests::getFileContents, ClientInputFile::isTest, ClientInputFile::getCharset, ClientInputFile::getClientObject,
        ClientInputFile::relativePath, ClientInputFile::uri)
      .containsExactly(tuple(pythonFile.toAbsolutePath().toString(), "", true, StandardCharsets.UTF_8, pythonFile.toUri(), "file.py", pythonFile.toUri()));
  }

  @Test
  void should_provide_all_main_files() {
    var fileTypeClassifier = mock(FileTypeClassifier.class);
    when(fileTypeClassifier.isTest(any(), any(), anyBoolean(), any())).thenReturn(false);
    var folderWrapper = new WorkspaceFolderWrapper(tempFolder.toUri(), new WorkspaceFolder(tempFolder.toString(), "My Folder"));
    folderWrapper.setSettings(EMPTY_SETTINGS);
    var folderFileSystem = new FolderFileSystem(folderWrapper, mock(JavaConfigCache.class), fileTypeClassifier);

    var files = folderFileSystem.files();

    assertThat(files)
      .extracting(ClientInputFile::getPath, FolderFileSystemTests::getFileContents, ClientInputFile::isTest, ClientInputFile::getCharset, ClientInputFile::getClientObject,
        ClientInputFile::relativePath, ClientInputFile::uri)
      .containsExactly(tuple(pythonFile.toAbsolutePath().toString(), "", false, StandardCharsets.UTF_8, pythonFile.toUri(), "file.py", pythonFile.toUri()));
  }

  @Test
  void should_provide_all_test_files() {
    var fileTypeClassifier = mock(FileTypeClassifier.class);
    when(fileTypeClassifier.isTest(any(), any(), anyBoolean(), any())).thenReturn(true);
    var folderWrapper = new WorkspaceFolderWrapper(tempFolder.toUri(), new WorkspaceFolder(tempFolder.toString(), "My Folder"));
    folderWrapper.setSettings(EMPTY_SETTINGS);
    var folderFileSystem = new FolderFileSystem(folderWrapper, mock(JavaConfigCache.class), fileTypeClassifier);

    var files = folderFileSystem.files();

    assertThat(files)
      .extracting(ClientInputFile::getPath, FolderFileSystemTests::getFileContents, ClientInputFile::isTest, ClientInputFile::getCharset, ClientInputFile::getClientObject,
        ClientInputFile::relativePath, ClientInputFile::uri)
      .containsExactly(tuple(pythonFile.toAbsolutePath().toString(), "", true, StandardCharsets.UTF_8, pythonFile.toUri(), "file.py", pythonFile.toUri()));
  }

  @Test
  void should_throw_an_exception_when_folder_does_not_exist() {
    var fileTypeClassifier = mock(FileTypeClassifier.class);
    when(fileTypeClassifier.isTest(any(), any(), anyBoolean(), any())).thenReturn(false);
    var folderWrapper = new WorkspaceFolderWrapper(URI.create("file:///wrong_path"), new WorkspaceFolder(tempFolder.toString(), "My Folder"));
    folderWrapper.setSettings(EMPTY_SETTINGS);
    var folderFileSystem = new FolderFileSystem(folderWrapper, mock(JavaConfigCache.class), fileTypeClassifier);

    var throwable = catchThrowable(folderFileSystem::files);
    var throwableWithFiltering = catchThrowable(() -> folderFileSystem.files("suffix", InputFile.Type.TEST));

    assertThat(throwable)
      .hasMessage("Cannot browse the files");
    assertThat(throwableWithFiltering)
      .hasMessage("Cannot browse the files");
  }

  private static String getFileContents(ClientInputFile file) {
    try {
      return file.contents();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

}
