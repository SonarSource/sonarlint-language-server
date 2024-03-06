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
package org.sonarsource.sonarlint.ls.connected;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProjectBindingTests {

  @Test
  void test_getters() {
    var engine = mock(SonarLintAnalysisEngine.class);
    var issueTrackerWrapper = mock(ServerIssueTrackerWrapper.class);
    var underTest = new ProjectBinding("serverId", "projectKey", engine, issueTrackerWrapper);

    assertThat(underTest.getConnectionId()).isEqualTo("serverId");
    assertThat(underTest.getProjectKey()).isEqualTo("projectKey");
    assertThat(underTest.getEngine()).isEqualTo(engine);
    assertThat(underTest.getServerIssueTracker()).isEqualTo(issueTrackerWrapper);
  }

}
