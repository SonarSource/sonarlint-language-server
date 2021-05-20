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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.client.api.common.ClientFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.ls.folders.InFolderClientInputFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;

public class FolderFileSystem implements ClientFileSystem {
  private final JavaConfigCache javaConfigCache;
  private final FileTypeClassifier fileTypeClassifier;
  private final WorkspaceFolderWrapper folder;

  public FolderFileSystem(WorkspaceFolderWrapper folder, JavaConfigCache javaConfigCache, FileTypeClassifier fileTypeClassifier) {
    this.folder = folder;
    this.javaConfigCache = javaConfigCache;
    this.fileTypeClassifier = fileTypeClassifier;
  }

  @Override
  public Stream<ClientInputFile> files(String suffix, InputFile.Type type) {
    WorkspaceFolderSettings settings = folder.getSettings();
    try {
      return Files.walk(folder.getRootPath())
        .filter(Files::isRegularFile)
        .filter(filePath -> filePath.toString().endsWith("." + suffix))
        .filter(filePath -> typeMatches(filePath.toUri(), type, settings))
        .map(filePath -> toClientInputFile(filePath, type));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot browse the files", e);
    }
  }

  @Override
  public Stream<ClientInputFile> files() {
    WorkspaceFolderSettings settings = folder.getSettings();
    try {
      return Files.walk(folder.getRootPath())
        .filter(Files::isRegularFile)
        .map(filePath -> toClientInputFile(filePath, isTestFile(settings, filePath.toUri()) ? InputFile.Type.TEST : InputFile.Type.MAIN));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot browse the files", e);
    }
  }

  private boolean typeMatches(URI uri, InputFile.Type type, WorkspaceFolderSettings settings) {
    return isTestType(type) == isTestFile(settings, uri);
  }

  private boolean isTestFile(WorkspaceFolderSettings settings, URI fileUri) {
    return fileTypeClassifier.isTest(settings, fileUri, javaConfigCache.getOrFetch(fileUri));
  }

  private static boolean isTestType(InputFile.Type type) {
    return type == InputFile.Type.TEST;
  }

  private ClientInputFile toClientInputFile(Path filePath, InputFile.Type type) {
    return new InFolderClientInputFile(
      filePath.toUri(),
      folder.getRootPath().relativize(filePath).toString(),
      isTestType(type));
  }
}
