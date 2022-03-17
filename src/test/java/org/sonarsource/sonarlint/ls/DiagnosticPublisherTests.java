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
package org.sonarsource.sonarlint.ls;

import java.net.URI;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.ls.IssuesCache.VersionnedIssue;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.file.VersionnedOpenFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.DiagnosticPublisher.convert;

class DiagnosticPublisherTests {

  private DiagnosticPublisher underTest;
  private IssuesCache issuesCache;
  private SonarLintExtendedLanguageClient languageClient;

  @BeforeEach
  public void init() {
    issuesCache = new IssuesCache();
    languageClient = mock(SonarLintExtendedLanguageClient.class);
    underTest = new DiagnosticPublisher(null, languageClient, new TaintVulnerabilitiesCache(), issuesCache);
  }

  @Test
  void testConvertGlobalIssues() {
    var issue = mock(Issue.class);
    when(issue.getSeverity()).thenReturn("BLOCKER");
    when(issue.getMessage()).thenReturn("Do this, don't do that");
    when(issue.getStartLine()).thenReturn(null);
    var versionnedIssue = new VersionnedIssue(issue, 1);
    Diagnostic diagnostic = convert(entry("id", versionnedIssue));
    assertThat(diagnostic.getRange()).isEqualTo(new Range(new Position(0, 0), new Position(0, 0)));
  }

  @Test
  void testNotConvertSeverity() {
    var id = "id";
    var issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(1);
    when(issue.getSeverity()).thenReturn("BLOCKER");
    when(issue.getMessage()).thenReturn("Do this, don't do that");
    var versionnedIssue = new VersionnedIssue(issue, 1);
    assertThat(convert(entry(id, versionnedIssue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn("CRITICAL");
    assertThat(convert(entry(id, versionnedIssue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn("MAJOR");
    assertThat(convert(entry(id, versionnedIssue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn("MINOR");
    assertThat(convert(entry(id, versionnedIssue)).getSeverity()).isEqualTo(DiagnosticSeverity.Information);
    when(issue.getSeverity()).thenReturn("INFO");
    assertThat(convert(entry(id, versionnedIssue)).getSeverity()).isEqualTo(DiagnosticSeverity.Hint);
  }

  @Test
  void showFirstSecretDetectedNotificationOnlyOnce() {
    underTest.initialize(false);

    var uri = initWithOneSecretIssue();

    underTest.publishDiagnostics(uri);

    verify(languageClient, times(1)).showFirstSecretDetectionNotification();

    reset(languageClient);

    underTest.publishDiagnostics(uri);

    verify(languageClient, never()).showFirstSecretDetectionNotification();
  }

  @Test
  void dontShowFirstSecretDetectedNotificationIfAlreadyShown() {
    underTest.initialize(true);

    var uri = initWithOneSecretIssue();

    underTest.publishDiagnostics(uri);

    verify(languageClient, never()).showFirstSecretDetectionNotification();
  }

  private URI initWithOneSecretIssue() {
    var issue = mock(Issue.class);
    when(issue.getRuleKey()).thenReturn("secrets:123");
    when(issue.getSeverity()).thenReturn("MAJOR");
    when(issue.getMessage()).thenReturn("Boo");

    var uri = URI.create("file://foo");

    VersionnedOpenFile versionnedOpenFile = new VersionnedOpenFile(uri, null, 1, null);
    issuesCache.analysisStarted(versionnedOpenFile);
    issuesCache.reportIssue(versionnedOpenFile, issue);
    issuesCache.analysisSucceeded(versionnedOpenFile);
    return uri;
  }
}
