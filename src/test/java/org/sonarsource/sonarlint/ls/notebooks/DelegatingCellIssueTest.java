/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.ls.notebooks;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class DelegatingCellIssueTest {
  @Test
  void shouldGetIssue() {
    var id = UUID.randomUUID();
    var finding = new RaisedIssueDto(
      id,
      null,
      "ruleKey",
      "message",
      Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)),
      Instant.now(),
      true,
      false,
      null,
      List.of(),
      List.of(),
      null,
      false,
      null);
    var cellIssue = new DelegatingCellIssue(finding, URI.create("/my/workspace"), null, List.of());

    var issue = cellIssue.getIssue();

    assertThat(issue.getId()).isEqualTo(id);
    assertThat(issue.getRuleKey()).isEqualTo("ruleKey");
    assertThat(issue.getPrimaryMessage()).isEqualTo("message");
  }
}
