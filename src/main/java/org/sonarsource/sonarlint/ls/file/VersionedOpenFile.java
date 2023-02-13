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

import java.net.URI;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Represent a versioned open file and its immutable metadata in the editor.
 */
@Immutable
public class VersionedOpenFile {
  private final URI uri;
  private final String languageId;
  private final int version;
  private final String content;

  public VersionedOpenFile(URI uri, String languageId, int version, String content) {
    this.uri = uri;
    this.languageId = languageId;
    this.version = version;
    this.content = content;
  }

  public URI getUri() {
    return uri;
  }

  public String getLanguageId() {
    return languageId;
  }

  public int getVersion() {
    return version;
  }

  public String getContent() {
    return content;
  }

  public boolean isJava() {
    return "java".equals(languageId);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }

  public boolean isCOrCpp() {
    return "c".equals(languageId) || "cpp".equals(languageId);
  }

}
