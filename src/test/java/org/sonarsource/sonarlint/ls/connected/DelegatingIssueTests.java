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
package org.sonarsource.sonarlint.ls.connected;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DelegatingIssueTests {

  private final Trackable<Issue> trackable = mock(Trackable.class);
  private final TextRangeWithHash textRange = mock(TextRangeWithHash.class);
  private final Issue issue = mock(Issue.class);
  private DelegatingIssue delegatingIssue;

  @BeforeEach
  public void prepare() {
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(trackable.getType()).thenReturn(RuleType.BUG);
    when(issue.getMessage()).thenReturn("don't do this");
    when(issue.getRuleKey()).thenReturn("squid:123");
    when(issue.getTextRange()).thenReturn(textRange);
    when(issue.getStartLine()).thenReturn(2);
    when(issue.getStartLineOffset()).thenReturn(3);
    when(issue.getEndLine()).thenReturn(4);
    when(issue.getEndLineOffset()).thenReturn(5);
    when(trackable.getClientObject()).thenReturn(issue);
    when(issue.flows()).thenReturn(List.of(mock(Flow.class)));
    when(issue.getInputFile()).thenReturn(mock(ClientInputFile.class));
    when(trackable.getTextRange()).thenReturn(mock(TextRangeWithHash.class));
    delegatingIssue = new DelegatingIssue(trackable);
  }

  @Test
  void testGetSeverity() {
    assertThat(delegatingIssue.getSeverity()).isEqualTo(issue.getSeverity());
  }

  @Test
  void testGetUserSeverity() {
    when(trackable.getSeverity()).thenReturn(IssueSeverity.INFO);
    var delegatingIssueWithUserSeverity = new DelegatingIssue(trackable);

    assertThat(delegatingIssueWithUserSeverity.getSeverity()).isEqualTo(IssueSeverity.INFO);
  }

  @Test
  void testGetType() {
    assertThat(delegatingIssue.getType()).isEqualTo(trackable.getType());
  }

  @Test
  void testGetMessage() {
    assertThat(delegatingIssue.getMessage()).isNotEmpty().isEqualTo(issue.getMessage());
  }

  @Test
  void testGetRuleKey() {
    assertThat(delegatingIssue.getRuleKey()).isNotEmpty().isEqualTo(issue.getRuleKey());
  }

  @Test
  void testGetStartLine() {
    assertThat(delegatingIssue.getStartLine()).isPositive().isEqualTo(issue.getStartLine());
  }

  @Test
  void testGetStartLineOffset() {
    assertThat(delegatingIssue.getStartLineOffset()).isPositive().isEqualTo(issue.getStartLineOffset());
  }

  @Test
  void testGetEndLine() {
    assertThat(delegatingIssue.getEndLine()).isPositive().isEqualTo(issue.getEndLine());
  }

  @Test
  void testGetEndLineOffset() {
    assertThat(delegatingIssue.getEndLineOffset()).isPositive().isEqualTo(issue.getEndLineOffset());
  }

  @Test
  void testFlows() {
    assertThat(delegatingIssue.flows()).isNotEmpty().isEqualTo(issue.flows());
  }

  @Test
  void testGetInputFile() {
    assertThat(delegatingIssue.getInputFile()).isNotNull().isEqualTo(issue.getInputFile());
  }

  @Test
  void testGetTextRange() {
    assertThat(delegatingIssue.getTextRange()).isNotNull().isEqualTo(issue.getTextRange());
  }
}
