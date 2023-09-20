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
package org.sonarsource.sonarlint.ls;

import java.net.URI;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.ls.IssuesCache.VersionedIssue;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;

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
  private IssuesCache hotspotsCache;
  private SonarLintExtendedLanguageClient languageClient;

  @BeforeEach
  public void init() {
    issuesCache = new IssuesCache();
    hotspotsCache = new IssuesCache();
    languageClient = mock(SonarLintExtendedLanguageClient.class);
    underTest = new DiagnosticPublisher(languageClient, new TaintVulnerabilitiesCache(), issuesCache, hotspotsCache, mock(OpenNotebooksCache.class));
  }

  @Test
  void testConvertGlobalIssues() {
    var issue = mock(Issue.class);
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getMessage()).thenReturn("Do this, don't do that");
    when(issue.getStartLine()).thenReturn(null);
    var versionedIssue = new VersionedIssue(issue, 1);
    Diagnostic diagnostic = convert(entry("id", versionedIssue));
    assertThat(diagnostic.getRange()).isEqualTo(new Range(new Position(0, 0), new Position(0, 0)));
  }

  @Test
  void testNotConvertSeverity() {
    var id = "id";
    var issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(1);
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getMessage()).thenReturn("Do this, don't do that");
    var versionedIssue = new VersionedIssue(issue, 1);
    assertThat(convert(entry(id, versionedIssue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn(IssueSeverity.CRITICAL);
    assertThat(convert(entry(id, versionedIssue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn(IssueSeverity.MAJOR);
    assertThat(convert(entry(id, versionedIssue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn(IssueSeverity.MINOR);
    assertThat(convert(entry(id, versionedIssue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn(IssueSeverity.INFO);
    assertThat(convert(entry(id, versionedIssue)).getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
  }

  @Test
  void showFirstSecretDetectedNotificationOnlyOnce() {
    underTest.initialize(false);

    var uri = initWithOneSecretIssue();

    underTest.publishDiagnostics(uri, false);

    verify(languageClient, times(1)).showFirstSecretDetectionNotification();

    reset(languageClient);

    underTest.publishDiagnostics(uri, false);

    verify(languageClient, never()).showFirstSecretDetectionNotification();
  }

  @Test
  void dontShowFirstSecretDetectedNotificationIfAlreadyShown() {
    underTest.initialize(true);

    var uri = initWithOneSecretIssue();

    underTest.publishDiagnostics(uri, false);

    verify(languageClient, never()).showFirstSecretDetectionNotification();
  }

  private URI initWithOneSecretIssue() {
    var issue = mock(Issue.class);
    when(issue.getRuleKey()).thenReturn("secrets:123");
    when(issue.getSeverity()).thenReturn(IssueSeverity.MAJOR);
    when(issue.getMessage()).thenReturn("Boo");

    var uri = URI.create("file://foo");

    VersionedOpenFile versionedOpenFile = new VersionedOpenFile(uri, null, 1, null);
    issuesCache.analysisStarted(versionedOpenFile);
    issuesCache.reportIssue(versionedOpenFile, issue);
    issuesCache.analysisSucceeded(versionedOpenFile);
    return uri;
  }

  private URI initWithOneCobolIssue() {
    var issue = mock(Issue.class);
    when(issue.getRuleKey()).thenReturn("cobol:S3643");
    when(issue.getSeverity()).thenReturn(IssueSeverity.MAJOR);
    when(issue.getMessage()).thenReturn("Boo");

    var uri = URI.create("file://foo");

    VersionedOpenFile versionedOpenFile = new VersionedOpenFile(uri, null, 1, null);
    issuesCache.analysisStarted(versionedOpenFile);
    issuesCache.reportIssue(versionedOpenFile, issue);
    issuesCache.analysisSucceeded(versionedOpenFile);
    return uri;
  }
}
