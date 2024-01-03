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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.commons.Language;

public class AnalysisClientInputFile implements ClientInputFile {

  private final URI fileUri;
  private final String content;
  private final Language sqLanguage;
  private final String relativePath;
  private final boolean isTest;

  public AnalysisClientInputFile(URI uri, String relativePath, String content, boolean isTest, @Nullable String clientLanguageId) {
    this.relativePath = relativePath;
    this.fileUri = uri;
    this.content = content;
    this.isTest = isTest;
    this.sqLanguage = toSqLanguage(clientLanguageId);
  }

  @Override
  public Charset getCharset() {
    return StandardCharsets.UTF_8;
  }

  @Override
  public URI getClientObject() {
    return fileUri;
  }

  @Override
  public String getPath() {
    return Paths.get(fileUri).toString();
  }

  @Override
  public String relativePath() {
    return relativePath;
  }

  @Override
  public boolean isTest() {
    return isTest;
  }

  @Override
  public String contents() {
    return content;
  }

  @Override
  public InputStream inputStream() {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public Language language() {
    return sqLanguage;
  }

  @Override
  public URI uri() {
    return this.fileUri;
  }

  @CheckForNull
  private static Language toSqLanguage(@Nullable String clientLanguageId) {
    if (clientLanguageId == null) {
      return null;
    }
    // See https://microsoft.github.io/language-server-protocol/specification#textDocumentItem
    return switch (clientLanguageId) {
      case "javascript", "javascriptreact", "vue", "vue component", "babel es6 javascript" -> Language.JS;
      case "python" -> Language.PYTHON;
      case "typescript", "typescriptreact" -> Language.TS;
      case "html" -> Language.HTML;
      case "oraclesql" -> Language.PLSQL;
      case "apex", "apex-anon" ->
        // See https://github.com/forcedotcom/salesforcedx-vscode/blob/5e4b7715d1cb3d1ee2780780ed63f70f58e93b20/packages/salesforcedx-vscode-apex/package.json#L273
        Language.APEX;
      default ->
        // Other supported languages map to the same key as the one used in SonarQube/SonarCloud
        Language.forKey(clientLanguageId).orElse(null);
    };
  }
}
