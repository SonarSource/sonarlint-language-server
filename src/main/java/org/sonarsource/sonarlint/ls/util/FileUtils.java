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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;

public class FileUtils {
  private static final String PATH_SEPARATOR_PATTERN = Pattern.quote(File.separator);
  static final String OS_NAME_PROPERTY = "os.name";

  private FileUtils() {
    // utility class, forbidden constructor
  }

  /**
   * Converts path to format used by SonarQube
   *
   * @param path path string in the local OS
   * @return SonarQube path
   */
  public static String toSonarQubePath(String path) {
    if (File.separatorChar != '/') {
      return path.replaceAll(PATH_SEPARATOR_PATTERN, "/");
    }
    return path;
  }

  /**
   * Creates a directory by creating all nonexistent parent directories first.
   *
   * @param path the directory to create
   */
  public static void mkdirs(Path path) {
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create directory: " + path, e);
    }
  }

  public static String getFileRelativePath(Path baseDir, URI uri, LanguageClientLogOutput logOutput) {
    var path = Paths.get(uri);
    try {
      return baseDir.relativize(path).toString();
    } catch (IllegalArgumentException e) {
      // Possibly the file has not the same root as baseDir
      logOutput.debug("Unable to relativize " + uri + " to " + baseDir);
      return path.toString();
    }
  }

  public static String getTextRangeContentOfFile(List<String> contentLines, @Nullable TextRangeDto textRange) {
    if (textRange == null || contentLines.isEmpty()) return null;
    var startLine = textRange.getStartLine() - 1;
    var endLine = textRange.getEndLine() - 1;
    if (startLine == endLine) {
      var startLineContent = contentLines.get(startLine);
      var endLineOffset = Math.min(textRange.getEndLineOffset(), startLineContent.length());
      return startLineContent.substring(textRange.getStartLineOffset(), endLineOffset);
    }

    var contentBuilder = new StringBuilder();
    contentBuilder.append(contentLines.get(startLine).substring(textRange.getStartLineOffset()))
      .append(System.lineSeparator());
    for (int i = startLine + 1; i < endLine; i++) {
      contentBuilder.append(contentLines.get(i)).append(System.lineSeparator());
    }
    var endLineContent = contentLines.get(endLine);
    var endLineOffset = Math.min(textRange.getEndLineOffset(), endLineContent.length());
    contentBuilder.append(endLineContent, 0, endLineOffset);
    return contentBuilder.toString();
  }

}
