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
package org.sonarsource.sonarlint.ls;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class SonarLintLanguageServerTests {

  @Test
  void should_fail_if_analyzer_is_not_provided() {
    var inputStream = mock(InputStream.class);
    var outputStream = mock(OutputStream.class);
    var analyzers = Collections.<Path>emptyList();
    Assertions.assertThatThrownBy(() -> new SonarLintLanguageServer(inputStream, outputStream, analyzers))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Embedded plugin not found: HTML");
  }

}
