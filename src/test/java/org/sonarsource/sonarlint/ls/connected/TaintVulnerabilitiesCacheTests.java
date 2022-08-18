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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.util.List;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache.convert;

class TaintVulnerabilitiesCacheTests {

  private static final String SAMPLE_SECURITY_RULE_KEY = "javasecurity:S12345";
  private final TaintVulnerabilitiesCache underTest = new TaintVulnerabilitiesCache();

  @Test
  void testIssueConversion() {
    var issue = mock(ServerTaintIssue.class);
    var flow = mock(ServerTaintIssue.Flow.class);
    var loc1 = mock(ServerTaintIssue.ServerIssueLocation.class);
    var loc2 = mock(ServerTaintIssue.ServerIssueLocation.class);
    when(flow.locations()).thenReturn(List.of(loc1, loc2));
    when(issue.getTextRange()).thenReturn(new TextRangeWithHash(1,0,0,0, ""));
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getRuleKey()).thenReturn("ruleKey");
    when(issue.getMessage()).thenReturn("message");
    when(issue.getKey()).thenReturn("issueKey");
    when(issue.getFlows()).thenReturn(List.of(flow));

    var diagnostic = convert(issue).get();

    assertThat(diagnostic.getMessage()).isEqualTo("message [+2 locations]");
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    assertThat(diagnostic.getSource()).isEqualTo("SonarQube Taint Analyzer");
    assertThat(diagnostic.getCode().getLeft()).isEqualTo("ruleKey");
    assertThat(diagnostic.getData()).isEqualTo("issueKey");
  }

  @Test
  void testGetServerIssueForDiagnosticBasedOnLocation() throws Exception {
    var uri = new URI("/");
    var issue = mock(ServerTaintIssue.class);
    when(issue.getTextRange()).thenReturn(new TextRangeWithHash(228, 14, 322, 14, ""));
    when(issue.getRuleKey()).thenReturn(SAMPLE_SECURITY_RULE_KEY);
    when(issue.isResolved()).thenReturn(false);

    var diagnostic = mock(Diagnostic.class);
    when(diagnostic.getCode()).thenReturn(Either.forLeft(SAMPLE_SECURITY_RULE_KEY));
    var range = new Range(new Position(227, 14), new Position(321, 14));
    when(diagnostic.getRange()).thenReturn(range);

    underTest.reload(uri, List.of(issue));

    assertThat(underTest.getTaintVulnerabilityForDiagnostic(uri, diagnostic)).hasValue(issue);
  }

  @Test
  void testGetServerIssueForDiagnosticBasedOnKey() throws Exception {
    var uri = new URI("/");
    var issue = mock(ServerTaintIssue.class);
    when(issue.getKey()).thenReturn("issueKey");
    when(issue.getRuleKey()).thenReturn(SAMPLE_SECURITY_RULE_KEY);
    when(issue.isResolved()).thenReturn(false);

    var diagnostic = mock(Diagnostic.class);
    when(diagnostic.getData()).thenReturn("issueKey");
    underTest.reload(uri, List.of(issue));

    assertThat(underTest.getTaintVulnerabilityForDiagnostic(uri, diagnostic)).hasValue(issue);
  }

  @Test
  void testGetServerIssueForDiagnosticNotFound() throws Exception {
    var uri = new URI("/");
    var issue = mock(ServerTaintIssue.class);
    when(issue.getRuleKey()).thenReturn("someRuleKey");
    when(issue.getKey()).thenReturn("issueKey");

    var diagnostic = mock(Diagnostic.class);
    when(diagnostic.getData()).thenReturn("anotherKey");
    when(diagnostic.getCode()).thenReturn(Either.forLeft("anotherRuleKey"));
    underTest.reload(uri, List.of(issue));

    assertThat(underTest.getTaintVulnerabilityForDiagnostic(uri, diagnostic)).isEmpty();
  }

  @Test
  void testGetServerIssueByKey() throws Exception {
    var uri = new URI("/");
    var issue = mock(ServerTaintIssue.class);
    var issueKey = "key";
    when(issue.getKey()).thenReturn(issueKey);
    when(issue.getRuleKey()).thenReturn(SAMPLE_SECURITY_RULE_KEY);
    when(issue.isResolved()).thenReturn(false);

    underTest.reload(uri, List.of(issue));

    assertThat(underTest.getTaintVulnerabilityByKey(issueKey)).hasValue(issue);
    assertThat(underTest.getTaintVulnerabilityByKey("otherKey")).isEmpty();
  }

}
