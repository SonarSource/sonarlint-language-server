/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.SonarLintLanguageServer.convert;
import static org.sonarsource.sonarlint.ls.SonarLintLanguageServer.getHtmlDescription;

public class SonarLintLanguageServerTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNotConvertGlobalIssues() {
    Issue issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(null);
    assertThat(convert(issue)).isEmpty();
  }

  @Test
  public void testNotConvertSeverity() {
    Issue issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(1);
    when(issue.getSeverity()).thenReturn("BLOCKER");
    when(issue.getMessage()).thenReturn("Do this, don't do that");
    assertThat(convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Error);
    when(issue.getSeverity()).thenReturn("CRITICAL");
    assertThat(convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Error);
    when(issue.getSeverity()).thenReturn("MAJOR");
    assertThat(convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn("MINOR");
    assertThat(convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Information);
    when(issue.getSeverity()).thenReturn("INFO");
    assertThat(convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Hint);
  }

  @Test
  public void getHtmlDescription_appends_extended_description_when_non_empty() {
    String htmlDescription = "foo";
    String extendedDescription = "bar";

    RuleDetails ruleDetails = mock(RuleDetails.class);
    when(ruleDetails.getHtmlDescription()).thenReturn(htmlDescription);
    when(ruleDetails.getExtendedDescription()).thenReturn("");

    assertThat(getHtmlDescription(ruleDetails)).isEqualTo(htmlDescription);

    when(ruleDetails.getExtendedDescription()).thenReturn(extendedDescription);
    assertThat(getHtmlDescription(ruleDetails)).isEqualTo(htmlDescription + "<div>" + extendedDescription + "</div>");
  }

}
