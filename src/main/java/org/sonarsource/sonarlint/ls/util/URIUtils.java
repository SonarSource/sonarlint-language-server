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
package org.sonarsource.sonarlint.ls.util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.apache.commons.io.FilenameUtils.separatorsToUnix;

public class URIUtils {

  private URIUtils() {
    // static stuff only
  }
  public static String uriStringWithTrailingSlash(String uriString) {
    return uriString.endsWith("/") ? uriString : uriString.concat("/");
  }

  public static URI getFullFileUriFromFragments(String workspaceFolderUriString, Path fileRelativePath) {
    return Paths.get(URI.create(uriStringWithTrailingSlash(workspaceFolderUriString))
      .resolve(URLEncoder.encode(separatorsToUnix(fileRelativePath.toString()), StandardCharsets.UTF_8)))
      .toUri();
  }

}
