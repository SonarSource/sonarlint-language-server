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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.TextRange;

public class LocalCodeFile {

  private List<String> lines;

  private LocalCodeFile(URI uri) {
    var localFile = new File(uri);
    if (localFile.exists()) {
      try {
        // TODO Find the right character set to use?
        this.lines = Files.readAllLines(localFile.toPath(), StandardCharsets.UTF_8);
      } catch (IOException ioe) {
        this.lines = Collections.emptyList();
      }
    }
  }

  public String content() {
    return String.join("\n", lines)
      // strip ZWNBSP
      .replace("\ufeff", "");
  }

  @CheckForNull
  public String codeAt(@Nullable TextRange range) {
    if (range == null) {
      return null;
    }
    var lineIndex = range.getStartLine() - 1;
    if (lines.size() < lineIndex) {
      return null;
    } else {
      if (lines.get(lineIndex).length() < range.getStartLineOffset()) {
        return null;
      } else {
        var snippet = new StringBuilder();
        var maxLine = Math.min(lines.size() - 1, range.getEndLine() - 1);
        var startOffset = range.getStartLineOffset();
        do {
          var currentLine = lines.get(lineIndex);
          if (lineIndex == maxLine) {
            var endOffset = Math.min(currentLine.length(), range.getEndLineOffset());
            snippet.append(currentLine.substring(startOffset, endOffset));
          } else {
            snippet.append(currentLine.substring(startOffset)).append('\n');
            startOffset = 0;
          }
          lineIndex += 1;
        } while (lineIndex <= maxLine);
        return snippet.toString();
      }
    }
  }

  public static LocalCodeFile from(URI uri) {
    return new LocalCodeFile(uri);
  }
}
