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
package org.sonarsource.sonarlint.ls.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.TextRange;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.sonarsource.sonarlint.ls.util.FileUtils.getTextRangeContentOfFile;

class FileUtilsTests {

  @Test
  void allRelativePathsForFilesInTree_should_find_all_files(@TempDir Path basedir) {
    var deeplyNestedDir = basedir.resolve("a").resolve("b").resolve("c");
    assertThat(deeplyNestedDir.toFile().isDirectory()).isFalse();
    FileUtils.mkdirs(deeplyNestedDir);
    FileUtils.mkdirs(basedir.resolve(".git").resolve("refs"));
    FileUtils.mkdirs(basedir.resolve("a").resolve(".config"));

    createNewFile(basedir, ".gitignore");
    createNewFile(basedir.resolve(".git/refs"), "HEAD");
    createNewFile(basedir.resolve("a"), "a.txt");
    createNewFile(basedir.resolve("a/.config"), "test");
    createNewFile(basedir.resolve("a/b"), "b.txt");
    createNewFile(basedir.resolve("a/b/c"), "c.txt");

    var relativePaths = FileUtils.allRelativePathsForFilesInTree(basedir);
    assertThat(relativePaths).containsExactlyInAnyOrder(
      "a/a.txt",
      "a/b/b.txt",
      "a/b/c/c.txt");
  }

  @Test
  void allRelativePathsForFilesInTree_should_handle_non_existing_dir(@TempDir Path basedir) {
    var deeplyNestedDir = basedir.resolve("a").resolve("b").resolve("c");
    assertThat(deeplyNestedDir).doesNotExist();

    var relativePaths = FileUtils.allRelativePathsForFilesInTree(deeplyNestedDir);
    assertThat(relativePaths).isEmpty();
  }

  @Test
  void allRelativePathsForFilesInTree_should_throw_on_io_error(@TempDir Path basedir) {
    var deeplyNestedDir = basedir.resolve("a");
    FileUtils.mkdirs(deeplyNestedDir);
    SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        throw new IOException();
      }
    };
    assertThrows(IllegalStateException.class,
      () -> FileUtils.allRelativePathsForFilesInTree(deeplyNestedDir, visitor, new HashSet<>()));
  }

  @Test
  void toSonarQubePath_should_return_slash_separated_path() {
    var path = Paths.get("some").resolve("relative").resolve("path");
    assertThat(FileUtils.toSonarQubePath(path.toString())).isEqualTo("some/relative/path");
  }

  @Test
  void toSonarQubePath_should_just_return_string_back_on_valid_path() {
    assertThat(FileUtils.toSonarQubePath("valid/relative/path")).isEqualTo("valid/relative/path");
  }

  @Test
  void mkdirs(@TempDir Path temp) {
    var deeplyNestedDir = temp.resolve("a").resolve("b").resolve("c");
    assertThat(deeplyNestedDir).doesNotExist();
    if (deeplyNestedDir.toFile().mkdir()) {
      throw new IllegalStateException("creating nested dir should have failed");
    }

    FileUtils.mkdirs(deeplyNestedDir);
    assertThat(deeplyNestedDir).isDirectory();
  }

  @Test
  void mkdirs_should_fail_if_destination_is_a_file(@TempDir Path temp) {
    var file = createNewFile(temp, "foo").toPath();
    assertThrows(IllegalStateException.class, () -> {
      FileUtils.mkdirs(file);
    });
  }

  @Test
  void getTextRangeContentOfFileTest() {
    var content = "package devoxx.vulnerability;\n" +
      "\n" +
      "import java.sql.Connection;\n" +
      "import java.sql.SQLException;\n" +
      "import java.sql.Statement;\n" +
      "\n" +
      "public class InjectionVulnerability {\n" +
      "  final static String SELECT_SQL = \"select FNAME, LNAME, SSN from USERS where UNAME = \";\n" +
      "\n" +
      "  public InjectionVulnerability(String taintedString) throws SQLException {\n" +
      "    Connection con = DatabaseHelper.getJDBCConnection();\n" +
      "    Statement stmt = con.createStatement();\n" +
      "    int i = 0;\n" +
      "    stmt.execute(SELECT_SQL + taintedString); // Noncompliant\n" +
      "  }\n" +
      "}\n";

    var textRangeContent = getTextRangeContentOfFile(content.lines().collect(Collectors.toList()), new TextRange(12, 21, 12, 42));
    var multiLineTextRangeContent = getTextRangeContentOfFile(content.lines().collect(Collectors.toList()), new TextRange(12, 21, 14, 60));

    assertThat(getTextRangeContentOfFile(emptyList(), null)).isNull();
    assertThat(textRangeContent).isEqualTo("con.createStatement()");
    assertThat(multiLineTextRangeContent).isEqualTo("con.createStatement();" + System.lineSeparator()
      + "    int i = 0;" + System.lineSeparator() + "    stmt.execute(SELECT_SQL + taintedString); // Noncomplian");
  }

  @Test
  void shouldHandleOutOfBoundsTextRange(){
    var content = "package devoxx.vulnerability;\n" +
      "\n" +
      "// TODO implement the TODO bellow\n" +
      "// TODO implement this class";
    var textRangeContent = getTextRangeContentOfFile(content.lines().collect(Collectors.toList()), new TextRange(3, 0, 2, 666));
    var multiLineTextRangeContent = getTextRangeContentOfFile(content.lines().collect(Collectors.toList()), new TextRange(3, 0, 4, 666));

    assertThat(textRangeContent).isEqualTo("// TODO implement the TODO bellow" + System.lineSeparator());
    assertThat(multiLineTextRangeContent).isEqualTo("// TODO implement the TODO bellow" + System.lineSeparator()
    + "// TODO implement this class");
  }

  private File createNewFile(Path basedir, String filename) {
    var path = basedir.resolve(filename);
    try {
      return Files.createFile(path).toFile();
    } catch (IOException e) {
      fail("could not create file: " + path);
    }
    throw new IllegalStateException("should be unreachable");
  }
}
