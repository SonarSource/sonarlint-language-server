/*
 * SonarLint Language Server
 * Copyright (C) 2009-2021 SonarSource SA
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

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.AnalysisManager.convert;

class AnalysisManagerTests {

  AnalysisManager underTest;
  Map<URI, List<ServerIssue>> taintVulnerabilitiesPerFile;

  @BeforeEach
  void prepare() {
    taintVulnerabilitiesPerFile = new ConcurrentHashMap<>();
    underTest = new AnalysisManager(mock(LanguageClientLogOutput.class), mock(EnginesFactory.class), mock(SonarLintExtendedLanguageClient.class), mock(SonarLintTelemetry.class),
      mock(WorkspaceFoldersManager.class), mock(SettingsManager.class), mock(ProjectBindingManager.class), taintVulnerabilitiesPerFile);

  }

  @Test
  void testNotConvertGlobalIssues() {
    Issue issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(null);
    assertThat(convert(issue)).isEmpty();
  }

  @Test
  void testNotConvertSeverity() {
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
  void testIssueConversion() {
    ServerIssue issue = mock(ServerIssue.class);
    ServerIssue.Flow flow = mock(ServerIssue.Flow.class);
    when(issue.getStartLine()).thenReturn(1);
    when(issue.severity()).thenReturn("BLOCKER");
    when(issue.ruleKey()).thenReturn("ruleKey");
    when(issue.getMessage()).thenReturn("message");
    when(issue.getFlows()).thenReturn(Collections.singletonList(flow));

    Diagnostic diagnostic = convert(issue).get();

    assertThat(diagnostic.getMessage()).isEqualTo("message [+1 flow]");
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
    assertThat(diagnostic.getSource()).isEqualTo("SonarQube Taint Analyzer");
    assertThat(diagnostic.getCode().getLeft()).isEqualTo("ruleKey");
  }

  @Test
  void testGetServerIssueForDiagnostic() throws Exception {
    URI uri = new URI("/");
    ServerIssue issue = mock(ServerIssue.class);
    when(issue.getStartLine()).thenReturn(228);
    when(issue.getStartLineOffset()).thenReturn(14);
    when(issue.getEndLine()).thenReturn(322);
    when(issue.getEndLineOffset()).thenReturn(14);

    Diagnostic diagnostic = mock(Diagnostic.class);
    when(diagnostic.getCode()).thenReturn(Either.forLeft("ruleKey"));
    Range range = new Range(new Position(227, 14), new Position(321, 14));
    when(diagnostic.getRange()).thenReturn(range);
    when(issue.ruleKey()).thenReturn("ruleKey");
    taintVulnerabilitiesPerFile.put(uri, Collections.singletonList(issue));

    assertThat(underTest.getTaintVulnerabilityForDiagnostic(uri, diagnostic)).hasValue(issue);
  }

  @Test
  void testGetServerIssueByKey() throws Exception {
    URI uri = new URI("/");
    ServerIssue issue = mock(ServerIssue.class);
    String issueKey = "key";
    when(issue.key()).thenReturn(issueKey);

    taintVulnerabilitiesPerFile.put(uri, Collections.singletonList(issue));

    assertThat(underTest.getTaintVulnerabilityByKey(issueKey)).hasValue(issue);
    assertThat(underTest.getTaintVulnerabilityByKey("otherKey")).isEmpty();
  }

}
