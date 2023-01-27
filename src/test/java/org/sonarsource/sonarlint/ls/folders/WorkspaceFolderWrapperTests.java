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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceFolderWrapperTests {

  @Test
  void testEqualsAndHashCode() {
    WorkspaceFolderWrapper folder1 = new WorkspaceFolderWrapper(URI.create("file://folder1"), null);
    WorkspaceFolderWrapper folder1bis = new WorkspaceFolderWrapper(URI.create("file://folder1"), null);
    WorkspaceFolderWrapper folder2 = new WorkspaceFolderWrapper(URI.create("file://folder2"), null);

    assertThat(folder1)
      .isNotEqualTo("foo")

      .isEqualTo(folder1)
      .hasSameHashCodeAs(folder1)

      .isEqualTo(folder1bis)
      .hasSameHashCodeAs(folder1bis)

      .isNotEqualTo(folder2)
      .doesNotHaveSameHashCodeAs(folder2);
  }

}
