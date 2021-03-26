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
import java.nio.file.Paths;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;

public class FileTypeClassifier {
  private static final Logger LOG = Loggers.get(FileTypeClassifier.class);
  private final FileLanguageCache fileLanguageCache;

  public FileTypeClassifier(FileLanguageCache fileLanguageCache) {
    this.fileLanguageCache = fileLanguageCache;
  }

  public boolean isTest(@Nullable WorkspaceFolderSettings settings, URI fileUri, Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse> javaConfig) {
    if (fileLanguageCache.isJava(fileUri) && javaConfig.map(SonarLintExtendedLanguageClient.GetJavaConfigResponse::isTest).orElse(false)) {
      LOG.debug("Classified as test by vscode-java");
      return true;
    }
    if (settings != null && settings.getTestMatcher().matches(Paths.get(fileUri))) {
      LOG.debug("Classified as test by configured 'testFilePattern' setting");
      return true;
    }
    return false;
  }
}
