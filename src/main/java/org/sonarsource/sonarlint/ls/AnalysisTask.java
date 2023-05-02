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
package org.sonarsource.sonarlint.ls;

import java.util.Set;
import java.util.concurrent.Future;
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;

class AnalysisTask {

  private final Set<VersionedOpenFile> filesToAnalyze;
  private final boolean shouldFetchServerIssues;
  private final boolean shouldKeepHotspotsOnly;
  private final boolean shouldShowProgress;
  private Future<?> future;

  public AnalysisTask(Set<VersionedOpenFile> filesToAnalyze, boolean shouldFetchServerIssues, boolean shouldKeepHotspotsOnly, boolean shouldShowProgress) {
    this.filesToAnalyze = filesToAnalyze;
    this.shouldFetchServerIssues = shouldFetchServerIssues;
    this.shouldKeepHotspotsOnly = shouldKeepHotspotsOnly;
    this.shouldShowProgress = shouldShowProgress;
  }

  public Set<VersionedOpenFile> getFilesToAnalyze() {
    return filesToAnalyze;
  }

  public boolean shouldFetchServerIssues() {
    return shouldFetchServerIssues;
  }

  public boolean shouldKeepHotspotsOnly() {
    return shouldKeepHotspotsOnly;
  }

  public boolean shouldShowProgress() {
    return shouldShowProgress;
  }

  public boolean isCanceled() {
    return (future != null && future.isCancelled()) || Thread.currentThread().isInterrupted();
  }

  public void checkCanceled() {
    if (isCanceled()) {
      throw new CanceledException();
    }
  }

  public AnalysisTask setFuture(Future<?> future) {
    this.future = future;
    return this;
  }

  public Future<?> getFuture() {
    return future;
  }
}
