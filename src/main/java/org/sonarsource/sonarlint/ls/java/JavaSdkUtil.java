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
package org.sonarsource.sonarlint.ls.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.SystemUtils;

/**
 * Adapted from https://github.com/JetBrains/intellij-community/blob/83c8bf83bddbde6e4fa0d4ab6294a43387089e09/jps/model-impl/src/org/jetbrains/jps/model/java/impl/JavaSdkUtil.java
 */
public final class JavaSdkUtil {

  private static final String LIB_JRT_FS_JAR = "lib/jrt-fs.jar";
  private static final String ENDORSED = "endorsed";

  private JavaSdkUtil() {
    // utility class
  }

  public static List<Path> getJdkClassesRoots(Path home) {
    return getJdkClassesRoots(home, SystemUtils.IS_OS_MAC);
  }

  static List<Path> getJdkClassesRoots(Path home, boolean isMac) {
    if (isModularRuntime(home)) {
      return List.of(home.resolve(LIB_JRT_FS_JAR));
    }

    return new ArrayList<>(collectJars(home, isMac));
  }

  private static List<Path> collectJars(Path home, boolean isMac) {
    var rootFiles = new ArrayList<Path>();

    var jarDirs = collectJarDirs(home, isMac);

    var duplicatePathFilter = new HashSet<Path>();
    for (var jarDir : jarDirs) {
      if (Files.isDirectory(jarDir)) {
        var jarFiles = listFiles(jarDir, p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar"));
        for (var jarFile: jarFiles) {
          var jarFileName = jarFile.getFileName().toString();
          if (jarFileName.equals("alt-rt.jar") || jarFileName.equals("alt-string.jar")) {
            // filter out alternative implementations
            continue;
          }
          var realPath = toRealPathOrNull(jarFile);
          if (realPath == null || !duplicatePathFilter.add(realPath)) {
            // filter out duplicate (symbolically linked) .jar files commonly found in OS X JDK distributions
            continue;
          }
          rootFiles.add(jarFile);
        }
      }
    }

    return rootFiles;
  }

  private static Path[] collectJarDirs(Path home, boolean isMac) {
    Path[] jarDirs;
    if (isMac) {
      Path openJdkRtJar = home.resolve("jre/lib/rt.jar");
      if (Files.isRegularFile(openJdkRtJar)) {
        var libDir = home.resolve("lib");
        var classesDir = openJdkRtJar.getParent();
        var libExtDir = openJdkRtJar.getParent().resolve("ext");
        var libEndorsedDir = libDir.resolve(ENDORSED);
        jarDirs = new Path[] {libEndorsedDir, libDir, classesDir, libExtDir};
      } else {
        var libDir = home.resolve("lib");
        var classesDir = home.getParent().resolve("Classes");
        var libExtDir = libDir.resolve("ext");
        var libEndorsedDir = libDir.resolve(ENDORSED);
        jarDirs = new Path[] {libEndorsedDir, libDir, classesDir, libExtDir};
      }
    } else {
      var libDir = home.resolve("jre/lib");
      var libExtDir = libDir.resolve("ext");
      var libEndorsedDir = libDir.resolve(ENDORSED);
      jarDirs = new Path[] {libEndorsedDir, libDir, libExtDir};
    }
    return jarDirs;
  }

  private static boolean isModularRuntime(Path home) {
    return Files.exists(home.resolve(LIB_JRT_FS_JAR));
  }

  private static Set<Path> listFiles(Path dir, Predicate<Path> filter) {
    try (Stream<Path> stream = Files.walk(dir, 1)) {
      return stream
        .filter(filter)
        .collect(Collectors.toSet());
    } catch (IOException e) {
      return Collections.emptySet();
    }
  }

  @CheckForNull
  private static Path toRealPathOrNull(Path file) {
    try {
      return file.toRealPath();
    } catch (IOException e) {
      return null;
    }
  }
}
