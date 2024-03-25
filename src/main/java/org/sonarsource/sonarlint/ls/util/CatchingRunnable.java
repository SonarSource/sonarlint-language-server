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

import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class CatchingRunnable implements Runnable {
  private final Runnable delegate;
  private final SonarLintLogger clientLogger = SonarLintLogger.get();

  public CatchingRunnable(Runnable delegate) {
    this.delegate = delegate;
  }

  @Override
  public void run() {
    try {
      this.delegate.run();
    } catch (Exception e) {
      clientLogger.error("Task failed, {}", e);
    }
  }
}
