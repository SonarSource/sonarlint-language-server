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

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DelegatingIssueTests {

  private final TextRangeDto textRange = mock(TextRangeDto.class);
  private final RawIssueDto issue = mock(RawIssueDto.class);
  private DelegatingIssue delegatingIssue;

  @BeforeEach
  public void prepare() {
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getPrimaryMessage()).thenReturn("don't do this");
    when(issue.getRuleKey()).thenReturn("squid:123");
    when(issue.getTextRange()).thenReturn(textRange);
    when(textRange.getStartLine()).thenReturn(2);
    when(textRange.getStartLineOffset()).thenReturn(3);
    when(textRange.getEndLine()).thenReturn(4);
    when(textRange.getEndLineOffset()).thenReturn(5);
    when(issue.getFlows()).thenReturn(List.of(mock(RawIssueFlowDto.class)));
    delegatingIssue = new DelegatingIssue(issue, UUID.randomUUID(), false, false);
  }

  @Test
  void testGetSeverity() {
    assertThat(delegatingIssue.getSeverity()).isEqualTo(issue.getSeverity());
  }

  @Test
  void testGetUserSeverity() {
    when(issue.getSeverity()).thenReturn(IssueSeverity.INFO);
    var delegatingIssueWithUserSeverity = new DelegatingIssue(issue, UUID.randomUUID(), false, false);

    assertThat(delegatingIssueWithUserSeverity.getSeverity()).isEqualTo(IssueSeverity.INFO);
  }

  @Test
  void testGetType() {
    assertThat(delegatingIssue.getType()).isEqualTo(issue.getType());
  }

  @Test
  void testGetMessage() {
    assertThat(delegatingIssue.getMessage()).isNotEmpty().isEqualTo(issue.getPrimaryMessage());
  }

  @Test
  void testGetRuleKey() {
    assertThat(delegatingIssue.getRuleKey()).isNotEmpty().isEqualTo(issue.getRuleKey());
  }

  @Test
  void testGetStartLine() {
    assertThat(delegatingIssue.getStartLine()).isPositive().isEqualTo(issue.getTextRange().getStartLine());
  }

  @Test
  void testGetStartLineOffset() {
    assertThat(delegatingIssue.getStartLineOffset()).isPositive().isEqualTo(issue.getTextRange().getStartLineOffset());
  }

  @Test
  void testGetEndLine() {
    assertThat(delegatingIssue.getEndLine()).isPositive().isEqualTo(issue.getTextRange().getEndLine());
  }

  @Test
  void testGetEndLineOffset() {
    assertThat(delegatingIssue.getEndLineOffset()).isPositive().isEqualTo(issue.getTextRange().getEndLineOffset());
  }

  @Test
  void testFlows() {
    assertThat(delegatingIssue.flows()).isNotEmpty().isEqualTo(issue.getFlows());
  }

  @Test
  void testGetTextRange() {
    assertThat(delegatingIssue.getTextRange()).isNotNull().isEqualTo(issue.getTextRange());
  }
}
