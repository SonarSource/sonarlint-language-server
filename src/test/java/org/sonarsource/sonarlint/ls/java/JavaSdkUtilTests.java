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
package org.sonarsource.sonarlint.ls.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSdkUtilTests {

  @Test
  void testOpenJdk8Linux(@TempDir Path tmp) throws IOException {
    Path javaHome = tmp.resolve("openjdk8");
    // lib folder is not included
    fakeFile(javaHome, "lib/dt.jar");
    fakeFile(javaHome, "lib/tools.jar");
    // jre/lib is included
    Path charsets = fakeFile(javaHome, "jre/lib/charsets.jar");
    Path resources = fakeFile(javaHome, "jre/lib/resources.jar");
    Path rt = fakeFile(javaHome, "jre/lib/rt.jar");
    // jre/lib/ext is included
    Path zipfs = fakeFile(javaHome, "jre/lib/ext/zipfs.jar");
    Path dnsns = fakeFile(javaHome, "jre/lib/ext/dnsns.jar");
    // Only JARs included
    fakeFile(javaHome, "jre/lib/ext/meta-index");

    assertThat(JavaSdkUtil.getJdkClassesRoots(javaHome, false)).containsExactlyInAnyOrder(charsets, resources, rt, zipfs, dnsns);
  }

  @Test
  void testOracleJdk7Linux(@TempDir Path tmp) throws IOException {
    Path javaHome = tmp.resolve("jdk7");
    // jre/lib is included
    Path charsets = fakeFile(javaHome, "jre/lib/charsets.jar");
    Path resources = fakeFile(javaHome, "jre/lib/resources.jar");
    Path rt = fakeFile(javaHome, "jre/lib/rt.jar");
    // alt-rt excluded
    fakeFile(javaHome, "jre/lib/alt-rt.jar");

    assertThat(JavaSdkUtil.getJdkClassesRoots(javaHome, false)).containsExactlyInAnyOrder(charsets, resources, rt);
  }

  @Test
  void testOracleJdk6Linux(@TempDir Path tmp) throws IOException {
    Path javaHome = tmp.resolve("jdk7");
    // jre/lib is included
    Path charsets = fakeFile(javaHome, "jre/lib/charsets.jar");
    Path resources = fakeFile(javaHome, "jre/lib/resources.jar");
    Path rt = fakeFile(javaHome, "jre/lib/rt.jar");
    // alt-string excluded
    fakeFile(javaHome, "jre/lib/alt-string.jar");

    assertThat(JavaSdkUtil.getJdkClassesRoots(javaHome, false)).containsExactlyInAnyOrder(charsets, resources, rt);
  }

  @Test
  void testOpenJdk8Mac(@TempDir Path tmp) throws IOException {
    Path javaHome = tmp.resolve("JavaVirtualMachines/1.8.0.jdk/Contents/Home");
    // lib is included
    Path charsets = fakeFile(javaHome, "jre/lib/charsets.jar");
    Path resources = fakeFile(javaHome, "jre/lib/resources.jar");
    Path rt = fakeFile(javaHome, "jre/lib/rt.jar");
    // lib/ext is included
    Path zipfs = fakeFile(javaHome, "jre/lib/ext/zipfs.jar");
    Path dnsns = fakeFile(javaHome, "jre/lib/ext/dnsns.jar");

    assertThat(JavaSdkUtil.getJdkClassesRoots(javaHome, true)).containsExactlyInAnyOrder(charsets, resources, rt, zipfs, dnsns);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void testAppleJdk6Mac(@TempDir Path tmp) throws IOException {
    Path javaHome = tmp.resolve("JavaVirtualMachines/1.6.0.jdk/Contents/Home");
    // ../Classes folder is included
    Path classes = fakeFile(javaHome, "../Classes/classes.jar");
    Path jsse = fakeFile(javaHome, "../Classes/jsse.jar");
    // rt.jar often symlink to classes.jar
    Files.createDirectories(javaHome.resolve("lib"));
    Path rtSymlink = javaHome.resolve("lib/rt.jar");
    Files.createSymbolicLink(rtSymlink, classes);

    // We should not have both rtSymlink and classes. Here rtSymlink is returned first
    assertThat(JavaSdkUtil.getJdkClassesRoots(javaHome, true)).containsExactlyInAnyOrder(rtSymlink, jsse);
  }

  @Test
  void testOpenJdk11(@TempDir Path tmp) throws IOException {
    Path javaHome = tmp.resolve("openjdk11");
    Path jfsRt = fakeFile(javaHome, "lib/jrt-fs.jar");
    assertThat(JavaSdkUtil.getJdkClassesRoots(javaHome, false)).containsExactlyInAnyOrder(jfsRt);
  }

  private Path fakeFile(Path baseDir, String filePath) throws IOException {
    Path file = baseDir.resolve(filePath).normalize();
    Files.createDirectories(file.getParent());
    Files.createFile(file);
    return file;
  }

}
