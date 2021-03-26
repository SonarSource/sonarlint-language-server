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

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.client.api.common.ClientFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.ls.DefaultClientInputFile;
import org.sonarsource.sonarlint.ls.LocalCodeFile;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

public class SingleFileSystem implements ClientFileSystem {
  private final URI fileUri;
  private final Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse> javaConfigOpt;
  private final FileTypeClassifier fileTypeClassifier;

  public SingleFileSystem(URI fileUri, Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse> javaConfigOpt, FileTypeClassifier fileTypeClassifier) {
    this.fileUri = fileUri;
    this.javaConfigOpt = javaConfigOpt;
    this.fileTypeClassifier = fileTypeClassifier;
  }

  @Override
  public void files(String suffix, InputFile.Type type, Consumer<ClientInputFile> fileConsumer) {
    boolean isTestFile = fileTypeClassifier.isTest(null, fileUri, javaConfigOpt);
    if (type == InputFile.Type.TEST == isTestFile) {
      fileConsumer.accept(new DefaultClientInputFile(
        fileUri,
        fileUri.getPath(),
        LocalCodeFile.from(fileUri).content(),
        isTestFile,
        suffix));
    }
  }
}
