/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.notebooks.DelegatingCellIssue;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagnosticPublisherTests {

  private DiagnosticPublisher underTest;
  private IssuesCache issuesCache;
  private HotspotsCache hotspotsCache;
  private SonarLintExtendedLanguageClient languageClient;

  @BeforeEach
  public void init() {
    issuesCache = new IssuesCache();
    hotspotsCache = new HotspotsCache();
    languageClient = mock(SonarLintExtendedLanguageClient.class);
    underTest = new DiagnosticPublisher(languageClient, new TaintVulnerabilitiesCache(), issuesCache, hotspotsCache,
      mock(OpenNotebooksCache.class));
  }

  @Test
  void testConvertGlobalIssues() {
    var issue = mock(DelegatingFinding.class);
    var textRange = new TextRangeDto(0, 0, 0, 0);
    when(issue.getTextRange()).thenReturn(textRange);
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getMessage()).thenReturn("Do this, don't do that");
    when(issue.getStartLine()).thenReturn(null);
    Diagnostic diagnostic = underTest.issueDtoToDiagnostic(entry("id", issue));
    assertThat(diagnostic.getRange()).isEqualTo(new Range(new Position(0, 0), new Position(0, 0)));
  }

  @Test
  void testNotConvertSeverity() {
    var id = "id";
    var issue = mock(DelegatingFinding.class);
    var textRange = new TextRangeDto(1, 0, 1, 1);
    when(issue.getTextRange()).thenReturn(textRange);
    when(issue.getStartLine()).thenReturn(1);
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getMessage()).thenReturn("Do this, don't do that");
    assertThat(underTest.issueDtoToDiagnostic(entry(id, issue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn(IssueSeverity.CRITICAL);
    assertThat(underTest.issueDtoToDiagnostic(entry(id, issue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn(IssueSeverity.MAJOR);
    assertThat(underTest.issueDtoToDiagnostic(entry(id, issue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn(IssueSeverity.MINOR);
    assertThat(underTest.issueDtoToDiagnostic(entry(id, issue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn(IssueSeverity.INFO);
    assertThat(underTest.issueDtoToDiagnostic(entry(id, issue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
  }

  @Test
  void showFirstSecretDetectedNotificationOnlyOnce() {
    underTest.initialize(false);

    underTest.didDetectSecret();

    verify(languageClient, times(1)).showFirstSecretDetectionNotification();

    reset(languageClient);

    underTest.didDetectSecret();

    verify(languageClient, never()).showFirstSecretDetectionNotification();
  }

  @Test
  void dontShowFirstSecretDetectedNotificationIfAlreadyShown() {
    underTest.initialize(true);

    var uri = initWithOneSecretIssue();

    underTest.publishDiagnostics(uri, true);

    verify(languageClient, never()).showFirstSecretDetectionNotification();
  }

  @Test
  void setSeverityTest() {
    var diagnostic = new Diagnostic();
    diagnostic.setSeverity(DiagnosticSeverity.Error);
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.isOnNewCode()).thenReturn(false);
    var delegatingCellIssue = mock(DelegatingCellIssue.class);

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false);
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, true);
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Hint);
    DiagnosticPublisher.setSeverity(diagnostic, delegatingCellIssue, false);
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    DiagnosticPublisher.setSeverity(diagnostic, delegatingCellIssue, true);
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Hint);

    when(delegatingIssue.isOnNewCode()).thenReturn(true);
    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, true);
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
  }

  private URI initWithOneSecretIssue() {
    var issue = mock(RaisedFindingDto.class);
    var textRange = new TextRangeDto(1, 0, 1, 1);
    when(issue.getId()).thenReturn(UUID.randomUUID());
    when(issue.getRuleKey()).thenReturn("secrets:123");
    when(issue.getSeverityMode()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));
    when(issue.getPrimaryMessage()).thenReturn("Boo");
    when(issue.getTextRange()).thenReturn(textRange);

    var uri = URI.create("file://foo");

    issuesCache.reportIssues(Map.of(uri, List.of(issue)));
    return uri;
  }
}
