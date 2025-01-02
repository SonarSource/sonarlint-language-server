/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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

import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.commons.api.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;

public class LSProgressMonitor implements ClientProgressMonitor {

  private final LanguageClient client;

  public LSProgressMonitor(LanguageClient client) {
    this.client = client;
  }

  public void createAndStartProgress(StartProgressParams startProgressParams) {
    client.createProgress(new WorkDoneProgressCreateParams(Either.forLeft(startProgressParams.getTaskId())));
    start(startProgressParams.getTitle(), startProgressParams.getTaskId(), startProgressParams.isCancellable());
  }

  public void reportProgress(ReportProgressParams reportProgressParams) {
    var progressReport = new WorkDoneProgressReport();
    progressReport.setMessage(reportProgressParams.getNotification().getLeft().getMessage());
    progressReport.setCancellable(false);
    progressReport.setPercentage(reportProgressParams.getNotification().getLeft().getPercentage());
    client.notifyProgress(new ProgressParams(Either.forLeft(reportProgressParams.getTaskId()), Either.forLeft(progressReport)));
  }

  public void start(String title, String taskId, boolean isCancellable) {
    var progressBegin = new WorkDoneProgressBegin();
    progressBegin.setTitle(title);
    progressBegin.setCancellable(isCancellable);
    progressBegin.setPercentage(0);
    client.notifyProgress(new ProgressParams(Either.forLeft(taskId), Either.forLeft(progressBegin)));
  }

  public void end(ReportProgressParams reportProgressParams) {
    var progressEnd = new WorkDoneProgressEnd();
    client.notifyProgress(new ProgressParams(Either.forLeft(reportProgressParams.getTaskId()),
      Either.forLeft(progressEnd)));
  }

}
