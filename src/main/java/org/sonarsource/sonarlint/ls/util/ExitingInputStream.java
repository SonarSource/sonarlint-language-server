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

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.ls.SonarLintLanguageServer;

public class ExitingInputStream extends InputStream {
  private final InputStream delegate;
  private final SonarLintLanguageServer sonarLintLanguageServer;

  public ExitingInputStream(InputStream delegate, SonarLintLanguageServer sonarLintLanguageServer) {
    this.delegate = delegate;
    this.sonarLintLanguageServer = sonarLintLanguageServer;
  }

  @Override
  public int read() throws IOException {
    return exitIfNegative(delegate::read);
  }

  @Override
  public int read(byte[] b) throws IOException {
    return exitIfNegative(() -> delegate.read(b));
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return exitIfNegative(() -> delegate.read(b, off, len));
  }

  private int exitIfNegative(SupplierWithIOException<Integer> call) throws IOException {
    int result = call.get();

    if (result < 0) {
      System.err.println("Input stream has closed, exiting");

      try {
        sonarLintLanguageServer.shutdown().get(5, TimeUnit.SECONDS);
      } catch (Exception e) {
        e.printStackTrace(System.err);
      }

      System.exit(0);
    }

    return result;
  }

}
