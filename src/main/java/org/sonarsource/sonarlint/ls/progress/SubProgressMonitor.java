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
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;

public class SubProgressMonitor extends ProgressMonitor implements ProgressFacade {

  private final LSProgressMonitor parent;
  private final String title;
  private final float subFraction;
  private final float startPercentage;

  public SubProgressMonitor(LSProgressMonitor parent, String title, float subFraction) {
    this.parent = parent;
    this.title = title;
    this.subFraction = subFraction;
    this.startPercentage = parent.getLastPercentage();
    parent.setMessage(title);
  }

  @Override
  public void end(@Nullable String message) {
    setFraction(1.0f);
    if (message != null) {
      setMessage(message);
    }
  }

  @Override
  public ProgressMonitor asCoreMonitor() {
    return this;
  }

  @Override
  public void doInSubProgress(String subTitle, float subFraction, Consumer<ProgressFacade> subRunnable) {
    parent.checkCanceled();

    float subSubFraction = this.subFraction * subFraction;
    subRunnable.accept(new SubProgressMonitor(parent, title + " - " + subTitle, subSubFraction));

    float endSubPercentage = startPercentage + 100.0f * subSubFraction;
    if (parent.getLastPercentage() < endSubPercentage) {
      parent.setPercentage((int) endSubPercentage);
    }
  }

  @Override
  public void executeNonCancelableSection(Runnable nonCancelable) {
    parent.executeNonCancelableSection(nonCancelable);
  }

  @Override
  public void setMessage(String msg) {
    parent.setMessage(title + " - " + msg);
  }

  @Override
  public boolean isCanceled() {
    return parent.isCanceled();
  }

  @Override
  public void setFraction(float fraction) {
    parent.setPercentage(startPercentage + 100.0f * fraction * subFraction);
  }

  @Override
  public void checkCanceled() {
    parent.checkCanceled();
  }

}
