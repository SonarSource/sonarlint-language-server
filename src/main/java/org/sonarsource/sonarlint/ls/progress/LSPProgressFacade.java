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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

import static org.sonarsource.sonarlint.ls.Utils.interrupted;

public class LSPProgressFacade implements ProgressFacade {

  private final Either<String, Number> progressToken;
  private final CancelChecker cancelToken;
  private final SonarLintExtendedLanguageClient client;
  private boolean ended;

  public LSPProgressFacade(SonarLintExtendedLanguageClient client, @Nullable Either<String, Number> workDoneToken, CancelChecker cancelToken) {
    this.client = client;
    this.cancelToken = cancelToken;
    this.progressToken = workDoneToken != null ? workDoneToken : Either.forLeft("SonarLint" + ThreadLocalRandom.current().nextInt());
    if (workDoneToken == null) {
      try {
        client.createProgress(new WorkDoneProgressCreateParams(progressToken)).get();
      } catch (InterruptedException e) {
        interrupted(e);
      } catch (ExecutionException e) {
        throw new IllegalStateException(e.getCause());
      }
    }
  }

  @Override
  public void start(String title) {
    WorkDoneProgressBegin progressBegin = new WorkDoneProgressBegin();
    progressBegin.setTitle(title);
    progressBegin.setCancellable(true);
    progressBegin.setPercentage(0);
    client.notifyProgress(new ProgressParams(progressToken, progressBegin));
  }

  @Override
  public boolean ended() {
    return ended;
  }

  @Override
  public void end(@Nullable String message) {
    WorkDoneProgressEnd progressEnd = new WorkDoneProgressEnd();
    progressEnd.setMessage(message);
    client.notifyProgress(new ProgressParams(progressToken, progressEnd));
    this.ended = true;
  }

  @Override
  public ProgressMonitor createCoreMonitor() {
    return new LSPProgressMonitor(client, cancelToken, progressToken);
  }

}
