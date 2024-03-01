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
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.sonarsource.sonarlint.ls.util.URIUtils.getFullFileUriFromFragments;
import static org.sonarsource.sonarlint.ls.util.URIUtils.uriStringWithTrailingSlash;

class URIUtilsTest {

  @Test
  void shouldAddTrailingSlash() {
    var uriStringNoTrailingSlash = "file:///my/workspace/folder";

    assertEquals("file:///my/workspace/folder/", uriStringWithTrailingSlash(uriStringNoTrailingSlash));
  }

  @Test
  void shouldNotAddTrailingSlash() {
    var uriStringTrailingSlash = "file:///my/workspace/folder/";

    assertEquals(uriStringTrailingSlash, uriStringWithTrailingSlash(uriStringTrailingSlash));
  }
  @Test
  void shouldHandleSpaceInFileName() {
    var workspaceFolderUriString = "file:///my/workspace/folder";
    var ideFilePath = Path.of("src/my file.py");

    var result = getFullFileUriFromFragments(workspaceFolderUriString, ideFilePath);

    assertEquals(URI.create("file:///my/workspace/folder/src/my+file.py"), result);
  }

  @Test
  void shouldHandleSpaceInFolderName() {
    var workspaceFolderUriString = "file:///my+workspace/folder";
    var ideFilePath = Path.of("src/myFile.py");

    var result = getFullFileUriFromFragments(workspaceFolderUriString, ideFilePath);

    assertEquals(URI.create("file:///my+workspace/folder/src/myFile.py"), result);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldMergeToFullUri() {
    var workspaceFolderUriString = "file:///my/workspace/folder";
    var ideFilePath = Path.of("src/myFile.py");

    var result = getFullFileUriFromFragments(workspaceFolderUriString, ideFilePath);

    assertEquals(URI.create("file:///my/workspace/folder/src/myFile.py"), result);
  }

  @Test
  void shouldMergeToFullUriWindows() {
    var workspaceFolderUriString = "file:///c:/my/workspace/folder";
    var ideFilePath = Path.of("src\\myFile.py");

    var result = getFullFileUriFromFragments(workspaceFolderUriString, ideFilePath);

    assertEquals(URI.create("file:///c:/my/workspace/folder/src/myFile.py"), result);
  }

}