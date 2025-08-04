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
import java.util.Collections;
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
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

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
  private SettingsManager settingsManager;

  @BeforeEach
  void init() {
    issuesCache = new IssuesCache();
    hotspotsCache = new HotspotsCache();
    languageClient = mock(SonarLintExtendedLanguageClient.class);
    dependencyRisksCache = new DependencyRisksCache();
    settingsManager = mock(SettingsManager.class);
    underTest = new DiagnosticPublisher(languageClient, new TaintVulnerabilitiesCache(), issuesCache, hotspotsCache,
      mock(OpenNotebooksCache.class), dependencyRisksCache, settingsManager);
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
    mockWorkspaceSettings();
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
    mockWorkspaceSettings();
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

    var diagnostic = DiagnosticPublisher.prepareDiagnostic(finding, "entryKey", false, false, "None", Collections.emptyMap());

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
          List.of(new ImpactDto(SoftwareQuality.RELIABILITY, ImpactSeverity.HIGH), new ImpactDto(SoftwareQuality.SECURITY, ImpactSeverity.BLOCKER)))),
        ImpactSeverity.BLOCKER.ordinal()),
      Arguments.of(Either.forRight(new MQRModeDetails(CleanCodeAttribute.TRUSTWORTHY,
        List.of(new ImpactDto(SoftwareQuality.RELIABILITY, ImpactSeverity.MEDIUM)))), ImpactSeverity.MEDIUM.ordinal()),
      Arguments.of(Either.forRight(new MQRModeDetails(CleanCodeAttribute.TRUSTWORTHY,
          List.of(new ImpactDto(SoftwareQuality.RELIABILITY, ImpactSeverity.LOW), new ImpactDto(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW)))),
        ImpactSeverity.LOW.ordinal()),
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

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "None", Collections.emptyMap());

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
    mockWorkspaceSettings();
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

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "None", Collections.emptyMap());
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, true, "None", Collections.emptyMap());
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Hint);
    DiagnosticPublisher.setSeverity(diagnostic, delegatingCellIssue, false, "None", Collections.emptyMap());
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    DiagnosticPublisher.setSeverity(diagnostic, delegatingCellIssue, true, "None", Collections.emptyMap());
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Hint);

    when(delegatingIssue.isOnNewCode()).thenReturn(true);
    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, true, "None", Collections.emptyMap());
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
  }

  @Test
  void setSeverity_AllLevel_ShouldReportAsError() {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.isOnNewCode()).thenReturn(false);

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "All", Collections.emptyMap());
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  @Test
  void setSeverity_MediumSeverityAndAbove_WithHighSeverityIssue_ShouldReportAsError() {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.isOnNewCode()).thenReturn(false);
    when(delegatingIssue.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "Medium severity and above", Collections.emptyMap());
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  @Test
  void setSeverity_MediumSeverityAndAbove_WithLowSeverityIssue_ShouldReportAsWarning() {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.isOnNewCode()).thenReturn(false);
    when(delegatingIssue.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.MINOR, RuleType.BUG)));

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "Medium severity and above", Collections.emptyMap());
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
  }

  @Test
  void setSeverity_WithRuleOverride_ShouldReportAsError() {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.isOnNewCode()).thenReturn(false);
    when(delegatingIssue.getRuleKey()).thenReturn("test:rule");
    when(delegatingIssue.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.MINOR, RuleType.BUG)));

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "None", Map.of("test:rule", "Error"));
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  @Test
  void setVulnerabilityProbability_AllLevel_ShouldReportAsError() {
    var diagnostic = new Diagnostic();
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getVulnerabilityProbability()).thenReturn(VulnerabilityProbability.LOW);

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "All", Collections.emptyMap());
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  @Test
  void setVulnerabilityProbability_MediumSeverityAndAbove_WithHighProbability_ShouldReportAsError() {
    var diagnostic = new Diagnostic();
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getVulnerabilityProbability()).thenReturn(VulnerabilityProbability.HIGH);

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "Medium severity and above", Collections.emptyMap());
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  @Test
  void setVulnerabilityProbability_MediumSeverityAndAbove_WithLowProbability_ShouldReportAsInformation() {
    var diagnostic = new Diagnostic();
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getVulnerabilityProbability()).thenReturn(VulnerabilityProbability.LOW);

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "Medium severity and above", Collections.emptyMap());
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Information);
  }

  @Test
  void setVulnerabilityProbability_WithRuleOverride_ShouldReportAsError() {
    var diagnostic = new Diagnostic();
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getVulnerabilityProbability()).thenReturn(VulnerabilityProbability.LOW);
    when(hotspotDto.getRuleKey()).thenReturn("security:hotspot");

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "None", Map.of("security:hotspot", "Error"));
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  @Test
  void setSeverity_WithRuleOverride_ShouldReportAsWarning() {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.isOnNewCode()).thenReturn(false);
    when(delegatingIssue.getRuleKey()).thenReturn("test:rule");
    when(delegatingIssue.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "None", Map.of("test:rule", "Warning"));
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
  }

  @Test
  void setVulnerabilityProbability_WithRuleOverride_ShouldReportAsWarning() {
    var diagnostic = new Diagnostic();
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getVulnerabilityProbability()).thenReturn(VulnerabilityProbability.HIGH);
    when(hotspotDto.getRuleKey()).thenReturn("security:hotspot");

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "All", Map.of("security:hotspot", "Warning"));
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

  @Test
  void setSeverity_WithRuleOverrideError_ShouldReportAsError() {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.getRuleKey()).thenReturn("test:rule");

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "None", Map.of("test:rule", "Error"));

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  @Test
  void setSeverity_WithRuleOverrideWarning_ShouldReportAsWarning() {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.getRuleKey()).thenReturn("test:rule");

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "None", Map.of("test:rule", "Warning"));

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
  }

  @Test
  void setSeverity_WithLevelAll_ShouldReportAsError() {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.getRuleKey()).thenReturn("test:rule");
    when(delegatingIssue.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.INFO, RuleType.BUG)));

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "All", Collections.emptyMap());

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  @ParameterizedTest
  @MethodSource("provideMediumAndHighSeverityIssues")
  void setSeverity_WithLevelMediumAndAbove_HighSeverityIssues_ShouldReportAsError(Either<StandardModeDetails, MQRModeDetails> severityDetails) {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.getRuleKey()).thenReturn("test:rule");
    when(delegatingIssue.getSeverityDetails()).thenReturn(severityDetails);

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "Medium severity and above", Collections.emptyMap());

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  private static Stream<Arguments> provideMediumAndHighSeverityIssues() {
    return Stream.of(
      // Standard mode - high severity issues
      Arguments.of(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG))),
      Arguments.of(Either.forLeft(new StandardModeDetails(IssueSeverity.CRITICAL, RuleType.BUG))),
      Arguments.of(Either.forLeft(new StandardModeDetails(IssueSeverity.MAJOR, RuleType.BUG))),

      // MQR mode - high impact issues
      Arguments.of(Either.forRight(new MQRModeDetails(CleanCodeAttribute.CONVENTIONAL, List.of(
        new ImpactDto(org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality.MAINTAINABILITY, ImpactSeverity.HIGH))))),
      Arguments.of(Either.forRight(new MQRModeDetails(CleanCodeAttribute.CONVENTIONAL, List.of(
        new ImpactDto(org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality.RELIABILITY, ImpactSeverity.MEDIUM)))))
    );
  }

  @ParameterizedTest
  @MethodSource("provideLowSeverityIssues")
  void setSeverity_WithLevelMediumAndAbove_LowSeverityIssues_ShouldReportAsWarning(Either<StandardModeDetails, MQRModeDetails> severityDetails) {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.getRuleKey()).thenReturn("test:rule");
    when(delegatingIssue.getSeverityDetails()).thenReturn(severityDetails);
    when(delegatingIssue.isOnNewCode()).thenReturn(false);

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "Medium severity and above", Collections.emptyMap());

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
  }

  private static Stream<Arguments> provideLowSeverityIssues() {
    return Stream.of(
      // Standard mode - low severity issues
      Arguments.of(Either.forLeft(new StandardModeDetails(IssueSeverity.MINOR, RuleType.BUG))),
      Arguments.of(Either.forLeft(new StandardModeDetails(IssueSeverity.INFO, RuleType.BUG))),

      // MQR mode - low impact issues
      Arguments.of(Either.forRight(new MQRModeDetails(CleanCodeAttribute.CONVENTIONAL, List.of(
        new ImpactDto(org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW)))))
    );
  }

  @Test
  void setSeverity_WithFocusOnNewCode_NewCodeIssue_ShouldReportAsWarning() {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.getRuleKey()).thenReturn("test:rule");
    when(delegatingIssue.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.MINOR, RuleType.BUG)));
    when(delegatingIssue.isOnNewCode()).thenReturn(true);

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, true, "None", Collections.emptyMap());

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
  }

  @Test
  void setSeverity_WithFocusOnNewCode_OldCodeIssue_ShouldReportAsHint() {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.getRuleKey()).thenReturn("test:rule");
    when(delegatingIssue.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.MINOR, RuleType.BUG)));
    when(delegatingIssue.isOnNewCode()).thenReturn(false);

    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, true, "None", Collections.emptyMap());

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Hint);
  }

  @Test
  void setVulnerabilityProbability_WithRuleOverrideError_ShouldReportAsError() {
    var diagnostic = new Diagnostic();
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getRuleKey()).thenReturn("security:hotspot");

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "None", Map.of("security:hotspot", "Error"));

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  @Test
  void setVulnerabilityProbability_WithLevelAll_ShouldReportAsError() {
    var diagnostic = new Diagnostic();
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getRuleKey()).thenReturn("security:hotspot");
    when(hotspotDto.getVulnerabilityProbability()).thenReturn(VulnerabilityProbability.LOW);

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "All", Collections.emptyMap());

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  @ParameterizedTest
  @MethodSource("provideMediumAndHighVulnerabilityProbabilities")
  void setVulnerabilityProbability_WithLevelMediumAndAbove_HighVulnerability_ShouldReportAsError(VulnerabilityProbability probability) {
    var diagnostic = new Diagnostic();
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getRuleKey()).thenReturn("security:hotspot");
    when(hotspotDto.getVulnerabilityProbability()).thenReturn(probability);

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "Medium severity and above", Collections.emptyMap());

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
  }

  private static Stream<Arguments> provideMediumAndHighVulnerabilityProbabilities() {
    return Stream.of(
      Arguments.of(VulnerabilityProbability.HIGH),
      Arguments.of(VulnerabilityProbability.MEDIUM)
    );
  }

  @Test
  void setVulnerabilityProbability_WithLevelMediumAndAbove_LowVulnerability_ShouldReportAsInformation() {
    var diagnostic = new Diagnostic();
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getRuleKey()).thenReturn("security:hotspot");
    when(hotspotDto.getVulnerabilityProbability()).thenReturn(VulnerabilityProbability.LOW);

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "Medium severity and above", Collections.emptyMap());

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Information);
  }

  @ParameterizedTest
  @MethodSource("provideVulnerabilityProbabilitiesWithExpectedSeverities")
  void setVulnerabilityProbability_DefaultBehavior_ShouldMapCorrectly(VulnerabilityProbability probability, DiagnosticSeverity expectedSeverity) {
    var diagnostic = new Diagnostic();
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getRuleKey()).thenReturn("security:hotspot");
    when(hotspotDto.getVulnerabilityProbability()).thenReturn(probability);

    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "None", Collections.emptyMap());

    assertThat(diagnostic.getSeverity()).isEqualTo(expectedSeverity);
  }

  private static Stream<Arguments> provideVulnerabilityProbabilitiesWithExpectedSeverities() {
    return Stream.of(
      Arguments.of(VulnerabilityProbability.HIGH, DiagnosticSeverity.Error),
      Arguments.of(VulnerabilityProbability.MEDIUM, DiagnosticSeverity.Warning),
      Arguments.of(VulnerabilityProbability.LOW, DiagnosticSeverity.Information)
    );
  }

  @Test
  void setSeverity_RuleOverrideTakesPrecedenceOverLevel() {
    var diagnostic = new Diagnostic();
    var delegatingIssue = mock(DelegatingFinding.class);
    when(delegatingIssue.getRuleKey()).thenReturn("test:rule");
    when(delegatingIssue.getSeverityDetails()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.INFO, RuleType.BUG)));

    // Even with "All" level, rule override should take precedence
    DiagnosticPublisher.setSeverity(diagnostic, delegatingIssue, false, "All", Map.of("test:rule", "Warning"));

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
  }

  @Test
  void setVulnerabilityProbability_RuleOverrideTakesPrecedenceOverLevel() {
    var diagnostic = new Diagnostic();
    var hotspotDto = mock(RaisedHotspotDto.class);
    when(hotspotDto.getRuleKey()).thenReturn("security:hotspot");
    when(hotspotDto.getVulnerabilityProbability()).thenReturn(VulnerabilityProbability.LOW);

    // Even with "All" level, rule override should take precedence
    DiagnosticPublisher.setVulnerabilityProbability(diagnostic, hotspotDto, "All", Map.of("security:hotspot", "Warning"));

    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
  }

  private void mockWorkspaceSettings() {
    var workspaceSettings = mock(WorkspaceSettings.class);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    when(workspaceSettings.getReportIssuesAsErrorLevel()).thenReturn("None");
    when(workspaceSettings.getReportIssuesAsErrorOverrides()).thenReturn(Collections.emptyMap());
  }
}
