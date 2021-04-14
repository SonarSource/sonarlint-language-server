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
package org.sonarsource.sonarlint.ls.progress;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;

import static org.sonarsource.sonarlint.ls.Utils.interrupted;

public class ProgressManager {

  private static final Logger LOG = Loggers.get(ProgressManager.class);

  private final LanguageClient client;
  private final Map<Either<String, Integer>, LSProgressMonitor> liveProgress = new ConcurrentHashMap<>();

  private boolean workDoneProgressSupportedByClient;

  public ProgressManager(LanguageClient client) {
    this.client = client;
  }

  public void setWorkDoneProgressSupportedByClient(boolean supported) {
    this.workDoneProgressSupportedByClient = supported;
  }

  public void doWithProgress(String progressTitle, @Nullable Either<String, Integer> workDoneToken, CancelChecker cancelToken, Consumer<ProgressFacade> runnableWithProgress) {
    if (workDoneToken == null && !workDoneProgressSupportedByClient) {
      runnableWithProgress.accept(new NoOpProgressFacade());
    } else {
      Either<String, Integer> progressToken = workDoneToken != null ? workDoneToken : Either.forLeft("SonarLint" + ThreadLocalRandom.current().nextInt());
      if (workDoneToken == null) {
        try {
          client.createProgress(new WorkDoneProgressCreateParams(progressToken)).get();
        } catch (InterruptedException e) {
          interrupted(e);
        } catch (ExecutionException e) {
          throw new IllegalStateException(e.getCause());
        }
      }
      LSProgressMonitor progress = new LSProgressMonitor(client, progressToken, cancelToken);
      liveProgress.put(progressToken, progress);
      progress.start(progressTitle);
      try {
        runnableWithProgress.accept(progress);
      } catch (CanceledException canceled) {
        endIfNotAlreadyEnded(progress, "Canceled");
      } catch (Exception e) {
        endIfNotAlreadyEnded(progress, e.getMessage());
        throw e;
      } finally {
        endIfNotAlreadyEnded(progress, null);
        liveProgress.remove(progressToken);
      }
    }
  }

  private static void endIfNotAlreadyEnded(LSProgressMonitor progress, @Nullable String msg) {
    if (!progress.ended()) {
      progress.end(msg);
    }
  }

  public void cancelProgress(WorkDoneProgressCancelParams params) {
    LSProgressMonitor progressFacade = liveProgress.get(params.getToken());
    if (progressFacade == null) {
      LOG.debug("Unable to cancel progress: " + params.getToken());
    } else {
      progressFacade.cancel();
    }
  }

}
