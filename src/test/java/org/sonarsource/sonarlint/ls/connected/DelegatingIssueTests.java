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

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DelegatingIssueTests {

  private final TextRangeDto textRange = mock(TextRangeDto.class);
  private final RaisedFindingDto issue = mock(RaisedFindingDto.class);
  private DelegatingFinding delegatingFinding;

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
    when(issue.getFlows()).thenReturn(List.of(mock(IssueFlowDto.class)));
    delegatingFinding = new DelegatingFinding(issue, URI.create("file:///myFile.py"));
  }

  @Test
  void testGetSeverity() {
    assertThat(delegatingFinding.getSeverity()).isEqualTo(issue.getSeverity());
  }

  @Test
  void testGetUserSeverity() {
    when(issue.getSeverity()).thenReturn(IssueSeverity.INFO);
    var delegatingIssueWithUserSeverity = new DelegatingFinding(issue, URI.create("file:///myFile.py"));

    assertThat(delegatingIssueWithUserSeverity.getSeverity()).isEqualTo(IssueSeverity.INFO);
  }

  @Test
  void testGetType() {
    assertThat(delegatingFinding.getType()).isEqualTo(issue.getType());
  }

  @Test
  void testGetMessage() {
    assertThat(delegatingFinding.getMessage()).isNotEmpty().isEqualTo(issue.getPrimaryMessage());
  }

  @Test
  void testGetRuleKey() {
    assertThat(delegatingFinding.getRuleKey()).isNotEmpty().isEqualTo(issue.getRuleKey());
  }

  @Test
  void testGetStartLine() {
    assertThat(delegatingFinding.getStartLine()).isPositive().isEqualTo(issue.getTextRange().getStartLine());
  }

  @Test
  void testGetStartLineOffset() {
    assertThat(delegatingFinding.getStartLineOffset()).isPositive().isEqualTo(issue.getTextRange().getStartLineOffset());
  }

  @Test
  void testGetEndLine() {
    assertThat(delegatingFinding.getEndLine()).isPositive().isEqualTo(issue.getTextRange().getEndLine());
  }

  @Test
  void testGetEndLineOffset() {
    assertThat(delegatingFinding.getEndLineOffset()).isPositive().isEqualTo(issue.getTextRange().getEndLineOffset());
  }

  @Test
  void testFlows() {
    assertThat(delegatingFinding.flows()).isNotEmpty().isEqualTo(issue.getFlows());
  }

  @Test
  void testGetTextRange() {
    assertThat(delegatingFinding.getTextRange()).isNotNull().isEqualTo(issue.getTextRange());
  }
}
