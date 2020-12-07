/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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
package org.sonarsource.sonarlint.ls.progress;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

public class ProgressManager {

  private final SonarLintExtendedLanguageClient client;

  private boolean workDoneProgressSupportedByClient;

  public ProgressManager(SonarLintExtendedLanguageClient client) {
    this.client = client;
  }

  public void setWorkDoneProgressSupportedByClient(boolean supported) {
    this.workDoneProgressSupportedByClient = supported;
  }

  public void doWithProgress(String progressTitle, @Nullable Either<String, Number> workDoneToken, CancelChecker cancelToken, Consumer<ProgressFacade> runnableWithProgress) {
    ProgressFacade progress;
    if (workDoneToken == null && !workDoneProgressSupportedByClient) {
      progress = new NoOpProgressFacade();
    } else {
      progress = new LSPProgressFacade(client, workDoneToken, cancelToken);
    }
    progress.start(progressTitle);
    try {
      runnableWithProgress.accept(progress);
    } finally {
      if (!progress.ended()) {
        progress.end(null);
      }
    }
  }

}
