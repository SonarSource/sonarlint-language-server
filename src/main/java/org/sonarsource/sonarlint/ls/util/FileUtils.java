/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class FileUtils {

  private static final String PATH_SEPARATOR_PATTERN = Pattern.quote(File.separator);
  private static final String OS_NAME_PROPERTY = "os.name";
  private static final boolean WINDOWS = System.getProperty(OS_NAME_PROPERTY) != null && System.getProperty(OS_NAME_PROPERTY).startsWith("Windows");

  /**
   * How many times to retry a failing IO operation.
   */
  private static final int MAX_RETRIES = WINDOWS ? 20 : 0;

  private FileUtils() {
    // utility class, forbidden constructor
  }

  public static Collection<String> allRelativePathsForFilesInTree(Path dir) {
    if (!dir.toFile().exists()) {
      return Collections.emptySet();
    }
    Set<String> paths = new HashSet<>();
    try {
      Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          if (!isHidden(file)) {
            retry(() -> paths.add(toSonarQubePath(dir.relativize(file).toString())));
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          if (isHidden(dir)) {
            return FileVisitResult.SKIP_SUBTREE;
          } else {
            return FileVisitResult.CONTINUE;
          }
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new IllegalStateException("Unable to list files in directory " + dir, e);
    }
    return paths;
  }

  private static boolean isHidden(Path path) {
    return isHiddenByWindows(path) || isDotFile(path);
  }

  private static boolean isHiddenByWindows(Path path) {
    return WINDOWS && hasWindowsHiddenAttribute(path);
  }

  private static boolean hasWindowsHiddenAttribute(Path path) {
    try {
      var dosFileAttributes = Files.readAttributes(path, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      return dosFileAttributes.isHidden();
    } catch (UnsupportedOperationException | IOException e) {
      return path.toFile().isHidden();
    }
  }

  private static boolean isDotFile(Path path) {
    return path.getFileName().toString().startsWith(".");
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


  /**
   * On Windows, retries the provided IO operation a number of times, in an effort to make the operation succeed.
   *
   * Operations that might fail on Windows are file & directory move, as well as file deletion. These failures
   * are typically caused by the virus scanner and/or the Windows Indexing Service. These services tend to open a file handle
   * on newly created files in an effort to scan their content.
   *
   * @param runnable the runnable whose execution should be retried
   */
  static void retry(IORunnable runnable, int maxRetries) throws IOException {
    for (var retry = 0; retry < maxRetries; retry++) {
      try {
        runnable.run();
        return;
      } catch (AccessDeniedException e) {
        // Sleep a bit to give a chance to the virus scanner / Windows Indexing Service to release the opened file handle
        try {
          Thread.sleep(100);
        } catch (InterruptedException ie) {
          // Nothing else that meaningfully can be done here
          Thread.currentThread().interrupt();
        }
      }
    }

    // Give it a one last chance, and this time do not swallow the exception
    runnable.run();
  }

  static void retry(IORunnable runnable) throws IOException {
    retry(runnable, MAX_RETRIES);
  }

  @FunctionalInterface
  interface IORunnable {
    void run() throws IOException;
  }
}
