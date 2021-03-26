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
package org.sonarsource.sonarlint.ls.folders;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.ls.LocalCodeFile;

public class InFolderClientInputFile implements ClientInputFile {
  private final URI fileUri;
  private final String relativePath;
  private final boolean isTest;

  public InFolderClientInputFile(URI uri, String relativePath, boolean isTest) {
    this.relativePath = relativePath;
    this.fileUri = uri;
    this.isTest = isTest;
  }

  @Override
  public String getPath() {
    return Paths.get(fileUri).toString();
  }

  @Override
  public boolean isTest() {
    return isTest;
  }

  @CheckForNull
  @Override
  public Charset getCharset() {
    return StandardCharsets.UTF_8;
  }

  @Override
  public URI getClientObject() {
    return fileUri;
  }

  @Override
  public InputStream inputStream() {
    return new ByteArrayInputStream(contents().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String contents() {
    return LocalCodeFile.from(fileUri).content();
  }

  @Override
  public String relativePath() {
    return relativePath;
  }

  @Override
  public URI uri() {
    return fileUri;
  }
}
