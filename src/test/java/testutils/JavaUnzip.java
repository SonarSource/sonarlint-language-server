/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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
package testutils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.lang.String.format;

public class JavaUnzip {
  public static void javaUnzip(String zipFileName, File toDir) {
    File testRepos = new File("src/test/resources/test-repos");
    File zipFile = new File(testRepos, zipFileName);
    javaUnzip(zipFile, toDir);
  }

  private static void javaUnzip(File zip, File toDir) {
    try {
      try (ZipFile zipFile = new ZipFile(zip)) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          File to = new File(toDir, entry.getName());
          if (entry.isDirectory()) {
            forceMkdir(to);
          } else {
            File parent = to.getParentFile();
            forceMkdir(parent);

            Files.copy(zipFile.getInputStream(entry), to.toPath());
          }
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException(format("Fail to unzip %s to %s", zip, toDir), e);
    }
  }

  private static void forceMkdir(final File directory) throws IOException {
    if ((directory != null) && (!directory.mkdirs() && !directory.isDirectory())) {
      throw new IOException("Cannot create directory '" + directory + "'.");
    }
  }
}
