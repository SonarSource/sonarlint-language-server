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

public class LSProgressMonitor implements ProgressFacade {

  private final Either<String, Number> progressToken;
  private final CancelChecker cancelToken;
  private final LanguageClient client;
  private boolean cancelled;
  private boolean ended;
  private String lastMessage = null;
  private Float lastFraction = 0f;

  public LSProgressMonitor(LanguageClient client, Either<String, Number> progressToken, CancelChecker cancelToken) {
    this.client = client;
    this.cancelToken = cancelToken;
    this.progressToken = progressToken;
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
    return new CoreProgressMonitorAdapter(this);
  }

  void enableCancellation() {
    WorkDoneProgressReport progressReport = prepareProgressReport();
    progressReport.setCancellable(true);
    client.notifyProgress(new ProgressParams(progressToken, progressReport));
  }

  void disableCancellation() {
    WorkDoneProgressReport progressReport = prepareProgressReport();
    progressReport.setCancellable(false);
    client.notifyProgress(new ProgressParams(progressToken, progressReport));
  }

  private WorkDoneProgressReport prepareProgressReport() {
    WorkDoneProgressReport progressReport = new WorkDoneProgressReport();
    // Repeat the last message and percentage in every notification, because contrary to what is documented, VSCode doesn't preserve
    // the previous one
    // if you send a progress without message/percentage
    if (lastMessage != null) {
      progressReport.setMessage(lastMessage);
    }
    if (lastFraction != null) {
      progressReport.setPercentage((int) (100 * lastFraction));
    }
    return progressReport;
  }

  @Override
  public void cancel() {
    disableCancellation();
    this.cancelled = true;
  }

  public boolean isCancelled() {
    return cancelled || cancelToken.isCanceled();
  }

  void setMessage(String msg) {
    this.lastMessage = msg;
    WorkDoneProgressReport progressReport = prepareProgressReport();
    client.notifyProgress(new ProgressParams(progressToken, progressReport));
  }

  void setFraction(float fraction) {
    this.lastFraction = fraction;
    WorkDoneProgressReport progressReport = prepareProgressReport();
    client.notifyProgress(new ProgressParams(progressToken, progressReport));
  }

  @Override
  public void checkCanceled() {
    if (isCancelled()) {
      throw new CanceledException();
    }
  }

}
