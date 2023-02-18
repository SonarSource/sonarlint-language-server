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
import java.util.Optional;
import java.util.concurrent.Callable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command
public class ServerMain implements Callable<Integer> {

  @Parameters(index = "0", description = "The port to which sonarlint should connect to.", defaultValue = "-1")
  private int deprecatedJsonRpcPort;

  @Option(names = "-port", description = "The port to which sonarlint should connect to.")
  private Optional<Integer> jsonRpcPort;

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
    validate();

    SonarLintLanguageServer server;
    if (useStdio) {
      server = SonarLintLanguageServer.byStdio(analyzers);
    } else {
      int actualJsonRpcPort = jsonRpcPort.orElse(deprecatedJsonRpcPort);
      server = SonarLintLanguageServer.bySocket(actualJsonRpcPort, analyzers);
    }

    server.waitForShutDown();

    return 0;
  }

  private void validate() {
    if (deprecatedJsonRpcPort > 0 && jsonRpcPort.isPresent()) {
      throw new ParameterException(spec.commandLine(), "Cannot use positional port argument and option at the same time.");
    }

    int possibleJsonRpcPort = jsonRpcPort.orElse(deprecatedJsonRpcPort);
    if (possibleJsonRpcPort > 0 && useStdio) {
      throw new ParameterException(spec.commandLine(), "Cannot use stdio and socket port at the same time.");
    }
    if (possibleJsonRpcPort <= 0 && !useStdio) {
      throw new ParameterException(spec.commandLine(), "Use -stdio or -jsonRpcPort.");
    }

    if (!useStdio && deprecatedJsonRpcPort > 0) {
      SonarLintLogger.get().warn("Warning: using deprecated positional parameter jsonRpcPort. Please, use -jsonRpcPort instead.");
    }
  }

  public static void main(String... args) {
    int exitCode = new CommandLine(new ServerMain()).execute(args);
    System.exit(exitCode);
  }

}
