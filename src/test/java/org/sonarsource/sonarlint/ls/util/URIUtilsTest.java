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
package org.sonarsource.sonarlint.ls.util;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.ls.util.URIUtils.getFullFileUriFromFragments;
import static org.sonarsource.sonarlint.ls.util.URIUtils.uriStringWithTrailingSlash;

class URIUtilsTest {

  @Test
  void shouldAddTrailingSlash() {
    var uriStringNoTrailingSlash = "file:///my/workspace/folder";

    assertThat(uriStringWithTrailingSlash(uriStringNoTrailingSlash)).isEqualTo("file:///my/workspace/folder/");
  }

  @Test
  void shouldNotAddTrailingSlash() {
    var uriStringTrailingSlash = "file:///my/workspace/folder/";

    assertThat(uriStringWithTrailingSlash(uriStringTrailingSlash)).isEqualTo(uriStringTrailingSlash);
  }

  @ParameterizedTest(name = "URI built from folder path `{0}` and relative file path `{1}` should be `{2}`")
  @CsvSource({
    "file:///my/workspace/folder,src/myFile.py,file:///my/workspace/folder/src/myFile.py",
    "file:///my/workspace/folder,src/my file.py,file:///my/workspace/folder/src/my%20file.py",
    "file:///my%20workspace/folder,src/myFile.py,file:///my%20workspace/folder/src/myFile.py",
  })
  @DisabledOnOs(OS.WINDOWS)
  void shouldBuildFullFileUriFromFragments(String workspaceFolderUriString, String relativePath, String expectedURI) {
    var ideFilePath = Paths.get(relativePath);

    var result = getFullFileUriFromFragments(workspaceFolderUriString, ideFilePath);

    assertThat(result).isEqualTo(URI.create(expectedURI));
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void shouldMergeToFullUriWindows() {
    var workspaceFolderUriString = "file:///c:/my/workspace/folder";
    var ideFilePath = Path.of("src\\myFile.py");

    var result = getFullFileUriFromFragments(workspaceFolderUriString, ideFilePath);

    assertThat(result).isEqualTo(URI.create("file:///c:/my/workspace/folder/src/myFile.py"));
  }

}