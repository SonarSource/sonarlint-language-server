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

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command
public class ServerMain implements Callable<Integer> {

  @Parameters(index = "0", description = "The port to which sonarlint should connect to.", defaultValue = "-1")
  private int jsonRpcPort;

  @Option(names = "-stdio", description = "The actual transport channel will be stdio")
  private boolean useStdio;

  @Option(names = "-analyzers", arity = "1..*", description = "A list of paths to the analyzer JARs that should be used to analyze the code.")
  private List<Path> analyzers = new ArrayList<>();

  @Spec
  private CommandSpec spec;

  public ServerMain() {
  }

  public List<Path> getAnalyzers() {
    return ImmutableList.copyOf(analyzers);
  }

  @Override
  public Integer call() throws Exception {
    SonarLintLanguageServer.bySocket(jsonRpcPort, analyzers);

    return 0;
  }

  public static void main(String... args) {
    int exitCode = new CommandLine(new ServerMain()).execute(args);
    System.exit(exitCode);
  }

}

