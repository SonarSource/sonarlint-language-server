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
package org.sonarsource.sonarlint.ls.progress;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

public class SubProgressMonitor implements ClientProgressMonitor, ProgressFacade {

  private final LSProgressMonitor parent;
  private final String title;
  private final float subFraction;
  private final float startPercentage;
  boolean ended;

  public SubProgressMonitor(LSProgressMonitor parent, String title, float subFraction) {
    this.parent = parent;
    this.title = title;
    this.subFraction = subFraction;
    this.startPercentage = parent.getLastPercentage();
    parent.setMessage(title);
  }

  @Override
  public void end(@Nullable String message) {
    this.ended = true;
    sendReport(message, 100.0f);
  }

  @Override
  public ClientProgressMonitor asCoreMonitor() {
    return this;
  }

  @Override
  public void doInSubProgress(String subTitle, float subFraction, Consumer<ProgressFacade> subRunnable) {
    parent.checkCanceled();

    float subSubFraction = this.subFraction * subFraction;
    var subProgressMonitor = new SubProgressMonitor(parent, title + " - " + subTitle, subSubFraction);
    subRunnable.accept(subProgressMonitor);

    if (!subProgressMonitor.ended) {
      // Automatic end
      subProgressMonitor.sendReport("Completed", 100.0f);
    }
  }

  @Override
  public void executeNonCancelableSection(Runnable nonCancelable) {
    parent.executeNonCancelableSection(nonCancelable);
  }

  @Override
  public void setMessage(String msg) {
    sendReport(msg, null);
  }

  @Override
  public boolean isCanceled() {
    return parent.isCanceled();
  }

  @Override
  public void setFraction(float fraction) {
    sendReport(null, 100.0f * fraction);
  }

  @Override
  public void setIndeterminate(boolean b) {
    // Unsupported
  }

  void sendReport(@Nullable String message, @Nullable Float percentage) {
    parent.sendReport(message == null ? null : (title + " - " + message), percentage == null ? null : (startPercentage + percentage * subFraction));
  }

  @Override
  public void checkCanceled() {
    parent.checkCanceled();
  }

}
