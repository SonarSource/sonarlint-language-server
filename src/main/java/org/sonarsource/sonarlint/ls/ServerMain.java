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
package org.sonarsource.sonarlint.ls;

import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ServerMain {

  private static final String ANALYZERS_KEY = "-analyzers";
  private static final String EXTRA_ANALYZERS_KEY = "-extraAnalyzers";
  private static final String USAGE = "Usage: java -jar sonarlint-server.jar <jsonRpcPort> " +
          "[-analyzers file:///path/to/analyzer1.jar [file:///path/to/analyzer2.jar] ...] " +
          "[-extraAnalyzers file:///path/to/analyzer3.jar [file:///path/to/analyzer4.jar] ...]";

  private PrintStream out;
  private PrintStream err;

  public ServerMain(PrintStream out, PrintStream err) {
    this.out = out;
    this.err = err;
  }

  public static void main(String... args) {
    new ServerMain(System.out, System.err).startLanguageServer(args);
  }

  static int getIndexOfNextParam(int start, String[] args) {
    for (int i = start + 1; i < args.length; i++) {
      if (args[i].startsWith("-")) {
        return i;
      }
    }
    return -1;
  }

  public void startLanguageServer(String... args) {
    if (args.length < 1) {
      err.println(USAGE);
      exitWithError();
    }
    int jsonRpcPort = parsePortArgument(args);

    Collection<URL> analyzers = extractAnalyzers(args);
    Collection<URL> extraAnalyzers = extractExtraAnalyzers(args);

    out.println("Binding to " + jsonRpcPort);
    try {
      SonarLintLanguageServer.bySocket(jsonRpcPort, analyzers, extraAnalyzers);
    } catch (IOException e) {
      err.println("Unable to connect to the client");
      e.printStackTrace(err);
      exitWithError();
    }
  }

  Collection<URL> extractAnalyzers(String[] args) {
    int indexOfAnalyzersParam = Arrays.asList(args).indexOf(ANALYZERS_KEY);
    if (indexOfAnalyzersParam == -1) {
      err.println(USAGE);
      exitWithError();
    }
    int nextParam = getIndexOfNextParam(indexOfAnalyzersParam, args);

    return extractAnalyzersUrlsToList(args, indexOfAnalyzersParam, nextParam);
  }

  Collection<URL> extractExtraAnalyzers(String[] args) {
    int indexOfExtraAnalyzersParam = Arrays.asList(args).indexOf(EXTRA_ANALYZERS_KEY);
    if (indexOfExtraAnalyzersParam == -1) {
      return Collections.emptyList();
    }
    return extractAnalyzersUrlsToList(args, indexOfExtraAnalyzersParam, args.length);
  }

  List<URL> extractAnalyzersUrlsToList(String[] args, int from, int to) {
    if (to == -1) {
      to = args.length;
    }
    List<URL> analyzers = new ArrayList<>();
    for (int i = from + 1; i < to; i++) {
      try {
        analyzers.add(new URL(args[i]));
      } catch (MalformedURLException e) {
        err.println("Invalid argument at position " + (i + 1) + ". Expected an URL.");
        e.printStackTrace(err);
        exitWithError();
      }
    }
    return analyzers;
  }

  private int parsePortArgument(String... args) {
    try {
      return Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      err.println("Invalid port provided as first parameter");
      e.printStackTrace(err);
      exitWithError();
    }
    return 0;
  }

  void exitWithError() {
    System.exit(1);
  }

}
