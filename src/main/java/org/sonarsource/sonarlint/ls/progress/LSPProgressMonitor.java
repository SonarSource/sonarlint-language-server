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
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;

public class LSPProgressMonitor extends ProgressMonitor {

  private String lastMessage = null;
  private Float lastFraction = 0f;
  private final Either<String, Number> progressToken;
  private final CancelChecker cancelToken;
  private final LanguageClient client;

  public LSPProgressMonitor(LanguageClient client, @Nullable CancelChecker cancelToken, Either<String, Number> progressToken) {
    this.client = client;
    this.cancelToken = cancelToken;
    this.progressToken = progressToken;
  }

  @Override
  public boolean isCanceled() {
    // TODO handle cancellation from progress
    return cancelToken != null && cancelToken.isCanceled();
  }

  @Override
  public void executeNonCancelableSection(Runnable nonCancelable) {
    disableCancellation();
    try {
      nonCancelable.run();
    } finally {
      enableCancellation();
    }
  }

  private void enableCancellation() {
    WorkDoneProgressReport progressReport = prepareProgressReport();
    progressReport.setCancellable(true);
    client.notifyProgress(new ProgressParams(progressToken, progressReport));
  }

  private void disableCancellation() {
    WorkDoneProgressReport progressReport = prepareProgressReport();
    progressReport.setCancellable(false);
    client.notifyProgress(new ProgressParams(progressToken, progressReport));
  }

  @Override
  public void setMessage(String msg) {
    this.lastMessage = msg;
    WorkDoneProgressReport progressReport = prepareProgressReport();
    client.notifyProgress(new ProgressParams(progressToken, progressReport));
  }

  @Override
  public void setFraction(float fraction) {
    this.lastFraction = fraction;
    WorkDoneProgressReport progressReport = prepareProgressReport();
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

}
