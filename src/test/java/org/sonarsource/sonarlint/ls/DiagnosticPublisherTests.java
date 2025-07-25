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
import java.util.stream.Stream;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;
import org.sonarsource.sonarlint.ls.connected.DependencyRisksCache;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.domain.DependencyRisk;
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
import static org.sonarsource.sonarlint.ls.domain.TaintIssue.SONARQUBE_SERVER_SOURCE;

class DiagnosticPublisherTests {

  private DiagnosticPublisher underTest;
  private IssuesCache issuesCache;
  private HotspotsCache hotspotsCache;
  private DependencyRisksCache dependencyRisksCache;
  private SonarLintExtendedLanguageClient languageClient;

  @BeforeEach
  public void init() {
    issuesCache = new IssuesCache();
    hotspotsCache = new HotspotsCache();
    languageClient = mock(SonarLintExtendedLanguageClient.class);
    dependencyRisksCache = new DependencyRisksCache();
    underTest = new DiagnosticPublisher(languageClient, new TaintVulnerabilitiesCache(), issuesCache, hotspotsCache,
      mock(OpenNotebooksCache.class), dependencyRisksCache);
  }

  @Test
  void testConvertGlobalIssues() {
    var issue = mock(DelegatingFinding.class);
    var textRange = new TextRangeDto(0, 0, 0, 0);
    when(issue.getTextRange()).thenReturn(textRange);
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));
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
    when(issue.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));
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
  void shouldPrepareDiagnostic() {
    var finding = mock(DelegatingFinding.class);
    var textRange = new TextRangeDto(1, 0, 1, 1);
    when(finding.getTextRange()).thenReturn(textRange);
    when(finding.getStartLine()).thenReturn(1);
    when(finding.getMessage()).thenReturn("Do this, don't do that");
    when(finding.getRuleKey()).thenReturn("rule-key");
    when(finding.isOnNewCode()).thenReturn(false);
    when(finding.quickFixes()).thenReturn(List.of(mock(QuickFixDto.class)));
    when(finding.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));

    var diagnostic = DiagnosticPublisher.prepareDiagnostic(finding, "entryKey", false, false);

    assertThat(diagnostic.getRange().getStart().getLine()).isZero();
    assertThat(diagnostic.getRange().getStart().getCharacter()).isZero();
    assertThat(diagnostic.getRange().getEnd().getLine()).isZero();
    assertThat(diagnostic.getRange().getEnd().getCharacter()).isEqualTo(1);
    assertThat(diagnostic.getMessage()).isEqualTo("Do this, don't do that");
    assertThat(diagnostic.getCode().getLeft()).isEqualTo("rule-key");
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    assertThat(diagnostic.getData()).isNotNull();
    assertThat(((DiagnosticPublisher.DiagnosticData) diagnostic.getData()).getEntryKey()).isEqualTo("entryKey");
    assertThat(((DiagnosticPublisher.DiagnosticData) diagnostic.getData()).isOnNewCode()).isFalse();
    assertThat(((DiagnosticPublisher.DiagnosticData) diagnostic.getData()).hasQuickFix()).isTrue();
    assertThat(((DiagnosticPublisher.DiagnosticData) diagnostic.getData()).getImpactSeverity()).isEqualTo(ImpactSeverity.BLOCKER.ordinal());
  }

  @ParameterizedTest
  @MethodSource("severityProvider")
  void shouldAssessIfFindingIsHighSeverity(Either<StandardModeDetails, MQRModeDetails> severityDetails, Integer impactSeverity) {
    assertThat(DiagnosticPublisher.getImpactSeverity(severityDetails)).isEqualTo(impactSeverity);
  }

  private static Stream<Arguments> severityProvider() {
    return Stream.of(
      Arguments.of(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)), ImpactSeverity.BLOCKER.ordinal()),
      Arguments.of(Either.forLeft(new StandardModeDetails(IssueSeverity.CRITICAL, RuleType.BUG)), ImpactSeverity.HIGH.ordinal()),
      Arguments.of(Either.forLeft(new StandardModeDetails(IssueSeverity.INFO, RuleType.BUG)), ImpactSeverity.INFO.ordinal()),
      Arguments.of(Either.forLeft(new StandardModeDetails(IssueSeverity.MINOR, RuleType.BUG)), ImpactSeverity.LOW.ordinal()),
      Arguments.of(Either.forLeft(new StandardModeDetails(IssueSeverity.MAJOR, RuleType.BUG)), ImpactSeverity.MEDIUM.ordinal()),
      Arguments.of(Either.forRight(new MQRModeDetails(CleanCodeAttribute.TRUSTWORTHY,
        List.of(new ImpactDto(SoftwareQuality.RELIABILITY, ImpactSeverity.HIGH), new ImpactDto(SoftwareQuality.SECURITY, ImpactSeverity.BLOCKER)))), ImpactSeverity.BLOCKER.ordinal()),
      Arguments.of(Either.forRight(new MQRModeDetails(CleanCodeAttribute.TRUSTWORTHY,
        List.of(new ImpactDto(SoftwareQuality.RELIABILITY, ImpactSeverity.MEDIUM)))), ImpactSeverity.MEDIUM.ordinal()),
      Arguments.of(Either.forRight(new MQRModeDetails(CleanCodeAttribute.TRUSTWORTHY,
        List.of(new ImpactDto(SoftwareQuality.RELIABILITY, ImpactSeverity.LOW), new ImpactDto(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW)))), ImpactSeverity.LOW.ordinal()),
      Arguments.of(Either.forRight(new MQRModeDetails(CleanCodeAttribute.TRUSTWORTHY,
        List.of(new ImpactDto(SoftwareQuality.RELIABILITY, ImpactSeverity.BLOCKER)))), ImpactSeverity.BLOCKER.ordinal()),
      Arguments.of(Either.forRight(new MQRModeDetails(CleanCodeAttribute.TRUSTWORTHY,
        List.of(new ImpactDto(SoftwareQuality.RELIABILITY, ImpactSeverity.INFO)))), ImpactSeverity.INFO.ordinal())
    );
  }

  @ParameterizedTest
  @MethodSource("vulnerabilityProbabilityProvider")
  void testConvertVulnerabilityProbability(VulnerabilityProbability vulnerabilityProbability, DiagnosticSeverity expectedSeverity) {
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getVulnerabilityProbability()).thenReturn(vulnerabilityProbability);
    var diagnostic = new Diagnostic();

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto);

    assertThat(diagnostic.getSeverity()).isEqualTo(expectedSeverity);
  }

  private static Stream<Arguments> vulnerabilityProbabilityProvider() {
    return Stream.of(
      Arguments.of(VulnerabilityProbability.HIGH, DiagnosticSeverity.Error),
      Arguments.of(VulnerabilityProbability.MEDIUM, DiagnosticSeverity.Warning),
      Arguments.of(VulnerabilityProbability.LOW, DiagnosticSeverity.Information)
    );
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
  void shouldPublishDependencyRisksForFolder() {
    var folderUri = URI.create("file:///workspace");
    var dependencyRisk = mock(DependencyRisk.class);
    when(dependencyRisk.getId()).thenReturn(UUID.randomUUID());
    when(dependencyRisk.getPackageName()).thenReturn("vulnerable-package");
    when(dependencyRisk.getPackageVersion()).thenReturn("1.0.0");
    when(dependencyRisk.getStatus()).thenReturn(DependencyRiskDto.Status.OPEN);
    when(dependencyRisk.getType()).thenReturn(DependencyRiskDto.Type.VULNERABILITY);
    when(dependencyRisk.getSeverity()).thenReturn(DependencyRiskDto.Severity.BLOCKER);
    when(dependencyRisk.getSource()).thenReturn(SONARQUBE_SERVER_SOURCE);
    dependencyRisksCache.putAll(folderUri, List.of(dependencyRisk));

    underTest.publishDependencyRisks(folderUri);

    var diagnostics = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
    verify(languageClient, times(1)).publishDependencyRisks(diagnostics.capture());
    assertThat(diagnostics.getValue().getUri()).isEqualTo(folderUri.toString());
    assertThat(diagnostics.getValue().getDiagnostics()).hasSize(1);
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
