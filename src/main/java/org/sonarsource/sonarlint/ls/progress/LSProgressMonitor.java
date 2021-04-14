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

import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;

public class LSProgressMonitor extends ProgressMonitor implements ProgressFacade {

  private final Either<String, Integer> progressToken;
  private final CancelChecker cancelToken;
  private final LanguageClient client;
  private boolean cancelled;
  private boolean ended;
  private String lastMessage = null;
  private float lastPercentage = 0.0f;

  public LSProgressMonitor(LanguageClient client, Either<String, Integer> progressToken, CancelChecker cancelToken) {
    this.client = client;
    this.cancelToken = cancelToken;
    this.progressToken = progressToken;
  }

  void start(String title) {
    WorkDoneProgressBegin progressBegin = new WorkDoneProgressBegin();
    progressBegin.setTitle(title);
    progressBegin.setCancellable(true);
    progressBegin.setPercentage(0);
    client.notifyProgress(new ProgressParams(progressToken, Either.forLeft(progressBegin)));
  }

  boolean ended() {
    return ended;
  }

  @Override
  public void end(@Nullable String message) {
    WorkDoneProgressEnd progressEnd = new WorkDoneProgressEnd();
    progressEnd.setMessage(message);
    client.notifyProgress(new ProgressParams(progressToken, Either.forLeft(progressEnd)));
    this.ended = true;
  }

  @Override
  public ProgressMonitor asCoreMonitor() {
    return this;
  }

  @Override
  public void executeNonCancelableSection(Runnable nonCancelable) {
    disableCancelation();
    try {
      nonCancelable.run();
    } finally {
      enableCancelation();
    }
  }

  void enableCancelation() {
    WorkDoneProgressReport progressReport = prepareProgressReport();
    progressReport.setCancellable(true);
    client.notifyProgress(new ProgressParams(progressToken, Either.forLeft(progressReport)));
  }

  void disableCancelation() {
    WorkDoneProgressReport progressReport = prepareProgressReport();
    progressReport.setCancellable(false);
    client.notifyProgress(new ProgressParams(progressToken, Either.forLeft(progressReport)));
  }

  private WorkDoneProgressReport prepareProgressReport() {
    WorkDoneProgressReport progressReport = new WorkDoneProgressReport();
    // Repeat the last message and percentage in every notification, because contrary to what is documented, VSCode doesn't preserve
    // the previous one
    // if you send a progress without message/percentage
    if (lastMessage != null) {
      progressReport.setMessage(lastMessage);
    }
    progressReport.setPercentage((int) lastPercentage);
    return progressReport;
  }

  void cancel() {
    this.cancelled = true;
  }

  @Override
  public boolean isCanceled() {
    return cancelled || cancelToken.isCanceled();
  }

  @Override
  public void setMessage(String msg) {
    sendReport(msg, null);
  }

  @Override
  public void setFraction(float fraction) {
    sendReport(null, 100.0f * fraction);
  }

  void sendReport(@Nullable String message, @Nullable Float percentage) {
    if (percentage != null) {
      this.lastPercentage = percentage;
    }
    if (message != null) {
      this.lastMessage = message;
    }
    WorkDoneProgressReport progressReport = prepareProgressReport();
    client.notifyProgress(new ProgressParams(progressToken, Either.forLeft(progressReport)));
  }

  @Override
  public void checkCanceled() {
    if (isCanceled()) {
      throw new CanceledException();
    }
  }

  @Override
  public void doInSubProgress(String title, float fraction, Consumer<ProgressFacade> subRunnable) {
    this.checkCanceled();

    SubProgressMonitor subProgressMonitor = new SubProgressMonitor(this, title, fraction);
    subRunnable.accept(subProgressMonitor);

    if (!subProgressMonitor.ended) {
      // Automatic end
      subProgressMonitor.sendReport("Completed", 100.0f);
    }
  }

  float getLastPercentage() {
    return lastPercentage;
  }

}
