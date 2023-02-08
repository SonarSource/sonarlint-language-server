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

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.shaded.org.apache.commons.io.output.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class ServerMainTests {

  private static final String INVALID_PATH = "\0invalid?;path\n";
  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();
  private ServerMain underTest = new ServerMain(new PrintStream(out), new PrintStream(err));

  @BeforeEach
  public void prepare() {
    underTest = spy(underTest);
    doThrow(new RuntimeException("exit called")).when(underTest).exitWithError();
  }

  @Test
  void testRequiredArguments() {

    var thrown = assertThrows(RuntimeException.class, () -> {
      underTest.startLanguageServer();
    });

    assertThat(thrown).hasMessage("exit called");
    assertThat(err.toString(StandardCharsets.UTF_8))
      .isEqualTo("Usage: java -jar sonarlint-server.jar <jsonRpcPort> " +
        "[-analyzers path/to/analyzer1.jar [path/to/analyzer2.jar] ...]" + System.lineSeparator());
  }

  @Test
  void testInvalidPortArgument() {

    var thrown = assertThrows(RuntimeException.class, () -> {
      underTest.startLanguageServer("not_a_number");
    });

    assertThat(thrown).hasMessage("exit called");
    assertThat(err.toString(StandardCharsets.UTF_8))
      .contains("Invalid port provided as first parameter");
  }

  @Test
  void testInvalidPluginPath() {

    var thrown = assertThrows(RuntimeException.class, () -> {
      underTest.startLanguageServer("1", "-analyzers", INVALID_PATH);
    });

    assertThat(thrown).hasMessage("exit called");
    assertThat(err.toString(StandardCharsets.UTF_8))
      .contains("Invalid argument at position 3. Expected a path.");
  }

  @Test
  void testExtractingAnalyzersPositive() {
    var args = new String[] {"-analyzers", "folder/analyzer1.jar", "folder/analyzer2.jar"};
    var paths = underTest.extractAnalyzers(args);
    assertThat(paths).containsExactly(Paths.get("folder/analyzer1.jar"), Paths.get("folder/analyzer2.jar"));
  }

  @Test
  void testExtractingAnalyzersExitsIfNoKey() {
    var args = new String[] {"folder/analyzer1.jar", "folder/analyzer2.jar"};

    assertThrows(RuntimeException.class, () -> underTest.extractAnalyzers(args));
  }

  @Test
  void testExtractingAnalyzersExitsOnMalformedPath() {
    var args = new String[] {"-analyzers", INVALID_PATH};

    assertThrows(RuntimeException.class, () -> underTest.extractAnalyzers(args));
  }

}
