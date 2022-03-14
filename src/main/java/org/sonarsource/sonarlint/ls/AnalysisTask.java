/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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
/*
  * SonarLint Language Server
  * Copyright (C) 2009-2022 SonarSource SA
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
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;
import org.sonarsource.sonarlint.ls.file.VersionnedOpenFile;

class AnalysisTask {

  private boolean finished;
  private boolean canceled;
  private final Set<VersionnedOpenFile> filesToAnalyze;
  private final boolean shouldFetchServerIssues;

  public AnalysisTask(Set<VersionnedOpenFile> filesToAnalyze, boolean shouldFetchServerIssues) {
    this.filesToAnalyze = filesToAnalyze;
    this.shouldFetchServerIssues = shouldFetchServerIssues;
  }

  public Set<VersionnedOpenFile> getFilesToAnalyze() {
    return filesToAnalyze;
  }

  public boolean shouldFetchServerIssues() {
    return shouldFetchServerIssues;
  }

  public void cancel() {
    this.canceled = true;
  }

  public boolean isCanceled() {
    return canceled;
  }

  public boolean isFinished() {
    return finished;
  }

  public AnalysisTask setFinished(boolean finished) {
    this.finished = finished;
    return this;
  }

  public void checkCanceled() {
    if (canceled || Thread.currentThread().isInterrupted()) {
      throw new CanceledException();
    }
  }
}
