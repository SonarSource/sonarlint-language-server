/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisTasksCacheTest {
  AnalysisTasksCache underTest;

  @BeforeEach
  void setUp() {
    underTest = new AnalysisTasksCache();
  }

  @Test
  void shouldGetAnalysisTask() {
    var analysisId1 = UUID.randomUUID();
    var analysisTask = mock(AnalysisTask.class);
    when(analysisTask.getAnalysisId()).thenReturn(analysisId1);

    underTest.analyze(analysisId1, analysisTask);

    var result = underTest.getAnalysisTask(analysisId1);

    assertThat(result.getAnalysisId()).isEqualTo(analysisId1);
  }

  @Test
  void shouldNotFailIfIssueRaisedButNoTask() {
    Assertions.assertDoesNotThrow(() -> underTest.didRaiseIssue(UUID.randomUUID(), mock(RawIssueDto.class)));
  }

  @Test
  void shouldHandleRaisedIssueProperly() {
    var analysisId = UUID.randomUUID();
    var testVariable = new ArrayList<Integer>();
    Consumer<RawIssueDto> testListener = ri -> testVariable.add(1);
    var analysisTask = mock(AnalysisTask.class);
    when(analysisTask.getIssueRaisedListener()).thenReturn(testListener);
    when(analysisTask.getAnalysisId()).thenReturn(analysisId);

    // start analysis
    underTest.analyze(analysisId, analysisTask);

    // issue raised
    underTest.didRaiseIssue(analysisId, mock(RawIssueDto.class));

    assertThat(testVariable).hasSize(1);
    assertThat(testVariable.get(0)).isEqualTo(1);

    // analysis finished
    underTest.didFinishAnalysis(analysisId);

    assertThat(underTest.getAnalysisTask(analysisId)).isNull();
  }
}