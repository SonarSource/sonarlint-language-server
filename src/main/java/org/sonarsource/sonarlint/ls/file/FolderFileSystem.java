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
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.client.api.common.ClientFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.ls.DefaultClientInputFile;
import org.sonarsource.sonarlint.ls.LocalCodeFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.java.JavaConfigProvider;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;

public class FolderFileSystem implements ClientFileSystem {
  private final JavaConfigProvider javaConfig;
  private final FileTypeClassifier fileTypeClassifier;
  private final WorkspaceFolderWrapper folder;

  public FolderFileSystem(WorkspaceFolderWrapper folder, JavaConfigProvider javaConfig, FileTypeClassifier fileTypeClassifier) {
    this.folder = folder;
    this.javaConfig = javaConfig;
    this.fileTypeClassifier = fileTypeClassifier;
  }

  @Override
  public void files(String suffix, InputFile.Type type, Consumer<ClientInputFile> fileConsumer) {
    WorkspaceFolderSettings settings = folder.getSettings();
    try (Stream<Path> files = Files.walk(folder.getRootPath())) {
        files.filter(filePath -> filePath.toString().endsWith("." + suffix))
        .filter(filePath -> typeMatches(filePath.toUri(), type, settings))
        .map(filePath -> toClientInputFile(filePath, suffix, type))
        .forEach(fileConsumer);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot browse the files", e);
    }
  }

  private boolean typeMatches(URI uri, InputFile.Type type, WorkspaceFolderSettings settings) {
    boolean isTestFile = fileTypeClassifier.isTest(settings, uri, javaConfig.getConfig(uri));
    return isTestType(type) == isTestFile;
  }

  private boolean isTestType(InputFile.Type type) {
    return type == InputFile.Type.TEST;
  }

  private ClientInputFile toClientInputFile(Path filePath, String language, InputFile.Type type) {
    URI fileUri = filePath.toUri();
    return new DefaultClientInputFile(
      fileUri,
      folder.getRootPath().relativize(filePath).toString(),
      LocalCodeFile.from(fileUri).content(),
      type == InputFile.Type.TEST,
      language);
  }
}
