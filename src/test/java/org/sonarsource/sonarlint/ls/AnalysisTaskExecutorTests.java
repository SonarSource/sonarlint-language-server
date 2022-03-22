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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisTaskExecutorTests {

  private AnalysisTaskExecutor underTest;
  private LanguageClientLogger lsLogOutput;
  private ExecutorService executor;

  @BeforeEach
  public void init() {
    lsLogOutput = mock(LanguageClientLogger.class);
    underTest = new AnalysisTaskExecutor(null, lsLogOutput, null, null, null, null, null, null, null, null, null, null, null);
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterEach
  public void cleanup() {
    executor.shutdown();
  }

  @Test
  void testCancellation() {
    Future<Void> future = mock(Future.class);
    when(future.isCancelled()).thenReturn(true);
    AnalysisTask cancelledTask = new AnalysisTask(Set.of(), false).setFuture(future);

    underTest.run(cancelledTask);

    verify(lsLogOutput).debug("Analysis canceled");
  }

  @Test
  void taskCompletedOnError() {
    AnalysisTask errorTask = spy(new AnalysisTask(Set.of(), false));
    when(errorTask.getFilesToAnalyze()).thenThrow(new IllegalStateException());

    var future = executor.submit(() -> underTest.run(errorTask));

    errorTask.setFuture(future);

    verify(lsLogOutput, timeout(1000)).error(eq("Analysis failed"), any());
    assertThat(errorTask.getFuture().isDone()).isTrue();
  }

}
