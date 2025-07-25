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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.domain.TaintIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache.convert;
import static org.sonarsource.sonarlint.ls.domain.TaintIssue.SONARQUBE_CLOUD_SOURCE;
import static org.sonarsource.sonarlint.ls.domain.TaintIssue.SONARQUBE_SERVER_SOURCE;

class TaintVulnerabilitiesCacheTests {


  private static final String SAMPLE_SECURITY_RULE_KEY = "javasecurity:S12345";
  private final TaintVulnerabilitiesCache underTest = new TaintVulnerabilitiesCache();

  @ParameterizedTest
  @MethodSource("testIssueConversionParameters")
  void testIssueConversion(String taintSource, boolean isOnNewCode, DiagnosticSeverity expectedSeverity) {
    var issue = mock(TaintIssue.class);
    var flow = mock(TaintVulnerabilityDto.FlowDto.class);
    var loc1 = mock(TaintVulnerabilityDto.FlowDto.LocationDto.class);
    var loc2 = mock(TaintVulnerabilityDto.FlowDto.LocationDto.class);
    when(flow.getLocations()).thenReturn(List.of(loc1, loc2));
    var issueId = UUID.randomUUID();
    when(issue.getId()).thenReturn(issueId);
    when(issue.getTextRange()).thenReturn(new TextRangeWithHashDto(1, 0, 0, 0, ""));
    when(issue.getSeverityMode()).thenReturn(org.sonarsource.sonarlint.core.rpc.protocol.common.Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));
    when(issue.getRuleKey()).thenReturn("ruleKey");
    when(issue.getMessage()).thenReturn("message");
    when(issue.getSonarServerKey()).thenReturn("issueKey");
    when(issue.getFlows()).thenReturn(List.of(flow));
    when(issue.getSource()).thenReturn(taintSource);
    when(issue.isOnNewCode()).thenReturn(isOnNewCode);

    var diagnostic = convert(issue).get();

    assertThat(diagnostic.getMessage()).isEqualTo("message [+2 locations]");
    assertThat(diagnostic.getSeverity()).isEqualTo(expectedSeverity);
    assertThat(diagnostic.getSource()).isEqualTo(taintSource);
    assertThat(diagnostic.getCode().getLeft()).isEqualTo("ruleKey");
    assertThat(diagnostic.getData().getClass()).isEqualTo(DiagnosticPublisher.DiagnosticData.class);
    var diagnosticData = (DiagnosticPublisher.DiagnosticData) diagnostic.getData();
    assertThat(diagnosticData.getEntryKey()).isEqualTo(issueId.toString());
    assertThat(diagnosticData.getServerIssueKey()).isEqualTo("issueKey");
    assertThat(diagnosticData.hasQuickFix()).isFalse();
    assertThat(diagnosticData.getImpactSeverity()).isEqualTo(ImpactSeverity.BLOCKER.ordinal());
  }

  @Test
  void testIssueConversionNoTextRange() {
    var issue = mock(TaintIssue.class);

    var diagnosticOptional = convert(issue);

    assertThat(diagnosticOptional).isEmpty();
  }

  @Test
  void testCacheOnlyUnresolvedTaintVulnerabilities() throws Exception {
    var uri = new URI("/");
    var taint = mock(TaintIssue.class);
    when(taint.getId()).thenReturn(UUID.randomUUID());
    when(taint.getRuleKey()).thenReturn("key1");
    when(taint.getRuleKey()).thenReturn(SAMPLE_SECURITY_RULE_KEY);
    when(taint.isResolved()).thenReturn(false);
    when(taint.getSeverityMode()).thenReturn(org.sonarsource.sonarlint.core.rpc.protocol.common.Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.VULNERABILITY)));
    when(taint.getTextRange()).thenReturn(new TextRangeWithHashDto(1, 1, 1, 1, ""));
    when(taint.getMessage()).thenReturn("Boo");

    var resolvedTaint = mock(TaintIssue.class);
    when(resolvedTaint.getId()).thenReturn(UUID.randomUUID());
    when(resolvedTaint.getRuleKey()).thenReturn(SAMPLE_SECURITY_RULE_KEY);
    when(resolvedTaint.isResolved()).thenReturn(true);
    when(resolvedTaint.getSeverityMode()).thenReturn(org.sonarsource.sonarlint.core.rpc.protocol.common.Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.VULNERABILITY)));
    when(resolvedTaint.getTextRange()).thenReturn(new TextRangeWithHashDto(1, 1, 1, 1, ""));
    when(resolvedTaint.getMessage()).thenReturn("Foo");


    underTest.reload(uri, List.of(taint, resolvedTaint));

    assertThat(underTest.getAsDiagnostics(uri)).hasSize(2);
  }

  @Test
  void testGetServerIssueByKey() throws Exception {
    var uri = new URI("/");
    var issue = mock(TaintIssue.class);
    var issueId = UUID.randomUUID();
    when(issue.getId()).thenReturn(issueId);
    when(issue.getRuleKey()).thenReturn(SAMPLE_SECURITY_RULE_KEY);
    when(issue.isResolved()).thenReturn(false);
    when(issue.getSonarServerKey()).thenReturn("serverIssueKey");

    underTest.reload(uri, List.of(issue));

    assertThat(underTest.getTaintVulnerabilityByKey("serverIssueKey")).hasValue(issue);
    assertThat(underTest.getTaintVulnerabilityByKey("otherKey")).isEmpty();
  }

  @Test
  void testRemoveTaintIssue() throws Exception {
    var uri = new URI("/");
    var issue = mock(TaintIssue.class);
    var issueId = UUID.randomUUID();
    when(issue.getId()).thenReturn(issueId);
    when(issue.getRuleKey()).thenReturn(SAMPLE_SECURITY_RULE_KEY);
    when(issue.isResolved()).thenReturn(false);
    when(issue.getSonarServerKey()).thenReturn("serverIssueKey");

    underTest.getTaintVulnerabilitiesPerFile().put(uri, new ArrayList<>(Arrays.asList(issue)));
    assertThat(underTest.getTaintVulnerabilityByKey("serverIssueKey")).hasValue(issue);

    underTest.removeTaintIssue(uri.toString(), issueId.toString());
    assertThat(underTest.getTaintVulnerabilityByKey("serverIssueKey")).isEmpty();


  }

  private static Stream<Arguments> testIssueConversionParameters() {
    return Stream.of(
      Arguments.of(SONARQUBE_CLOUD_SOURCE, true, DiagnosticSeverity.Error),
      Arguments.of(SONARQUBE_CLOUD_SOURCE, true, DiagnosticSeverity.Error),
      Arguments.of(SONARQUBE_CLOUD_SOURCE, false, DiagnosticSeverity.Error),
      Arguments.of(SONARQUBE_CLOUD_SOURCE, false, DiagnosticSeverity.Error),

      Arguments.of(SONARQUBE_SERVER_SOURCE, true, DiagnosticSeverity.Error),
      Arguments.of(SONARQUBE_SERVER_SOURCE, true, DiagnosticSeverity.Error),
      Arguments.of(SONARQUBE_SERVER_SOURCE, false, DiagnosticSeverity.Error),
      Arguments.of(SONARQUBE_SERVER_SOURCE, false, DiagnosticSeverity.Error)
    );
  }
}
