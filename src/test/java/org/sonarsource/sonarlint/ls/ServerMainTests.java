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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ServerMainTests {

  private static final String INVALID_PATH = "\0invalid?;path\n";
  private ServerMain underTest;

  private CommandLine cmd;
  private StringWriter cmdOutput;

  @BeforeEach
  public void prepare() {
    underTest = new ServerMain();
    cmd = new CommandLine(underTest);
    cmd.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF));

    cmdOutput = new StringWriter();
    cmd.setOut(new PrintWriter(cmdOutput));
    cmd.setErr(new PrintWriter(cmdOutput));
  }

  @Test
  void testRequiredArguments() {
    cmd.execute();

    assertThat(cmdOutput.toString()).startsWith("Use -stdio or -jsonRpcPort");
  }

  @Test
  void testInvalidPortArgument() {
    cmd.execute("not_a_number");

    assertThat(cmdOutput.toString()).contains("'not_a_number' is not an int");
  }

  @Test
  void testConflictingPortArgument() {
    cmd.execute("42042", "-port", "42042");

    assertThat(cmdOutput.toString()).contains("Cannot use positional port argument and option at the same time.");
  }

  @Test
  void testConflictingIoArguments() {
    cmd.execute("-port", "42042", "-stdio");

    assertThat(cmdOutput.toString()).contains("Cannot use stdio and socket port at the same time.");
  }

  @Test
  void testInvalidPluginPath() {
    cmd.execute("-analyzers", INVALID_PATH);

    assertThat(cmdOutput.toString()).contains("java.nio.file.InvalidPathException");
  }

  @Test
  void testExtractingAnalyzersPositive() {
    cmd.execute("-analyzers", "folder/analyzer1.jar", "folder/analyzer2.jar");
    var paths = underTest.getAnalyzers();
    assertThat(paths).containsExactly(Paths.get("folder/analyzer1.jar"), Paths.get("folder/analyzer2.jar"));
  }
}

