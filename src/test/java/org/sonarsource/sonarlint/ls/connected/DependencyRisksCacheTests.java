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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.domain.DependencyRisk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.connected.DependencyRisksCache.convert;
import static org.sonarsource.sonarlint.ls.domain.TaintIssue.SONARQUBE_SERVER_SOURCE;

class DependencyRisksCacheTests {

  private final DependencyRisksCache underTest = new DependencyRisksCache();

  @Test
  void should_convert_dependency_risk_to_diagnostic() {
    var risk = mock(DependencyRisk.class);
    var riskId = UUID.randomUUID();
    when(risk.getId()).thenReturn(riskId);
    when(risk.getType()).thenReturn(DependencyRiskDto.Type.VULNERABILITY);
    when(risk.getSeverity()).thenReturn(DependencyRiskDto.Severity.BLOCKER);
    when(risk.getPackageName()).thenReturn("vulnerable-package");
    when(risk.getPackageVersion()).thenReturn("1.0.0");
    when(risk.getSource()).thenReturn(SONARQUBE_SERVER_SOURCE);

    var diagnostic = convert(risk).get();

    assertThat(diagnostic.getMessage()).isEqualTo("vulnerable-package 1.0.0");
    assertThat(diagnostic.getSource()).isEqualTo(SONARQUBE_SERVER_SOURCE);
    assertThat(diagnostic.getCode().getLeft()).isEqualTo("VULNERABILITY");
    assertThat(diagnostic.getData().getClass()).isEqualTo(DiagnosticPublisher.DiagnosticData.class);
    var diagnosticData = (DiagnosticPublisher.DiagnosticData) diagnostic.getData();
    assertThat(diagnosticData.getEntryKey()).isEqualTo(riskId.toString());
    assertThat(diagnosticData.getServerIssueKey()).isEqualTo(riskId.toString());
    assertThat(diagnosticData.hasQuickFix()).isFalse();
    assertThat(diagnosticData.getImpactSeverity()).isEqualTo(DependencyRiskDto.Severity.BLOCKER.ordinal());
    assertThat(diagnosticData.isOnNewCode()).isTrue();
  }

  @Test
  void should_return_only_open_dependency_risks_as_diagnostics() throws Exception {
    var uri = new URI("/");
    var openRisk = mock(DependencyRisk.class);
    when(openRisk.getId()).thenReturn(UUID.randomUUID());
    when(openRisk.getType()).thenReturn(DependencyRiskDto.Type.VULNERABILITY);
    when(openRisk.getSeverity()).thenReturn(DependencyRiskDto.Severity.BLOCKER);
    when(openRisk.getStatus()).thenReturn(DependencyRiskDto.Status.OPEN);
    when(openRisk.getPackageName()).thenReturn("vulnerable-package");
    when(openRisk.getPackageVersion()).thenReturn("1.0.0");
    when(openRisk.getSource()).thenReturn(SONARQUBE_SERVER_SOURCE);

    var closedRisk = mock(DependencyRisk.class);
    when(closedRisk.getId()).thenReturn(UUID.randomUUID());
    when(closedRisk.getType()).thenReturn(DependencyRiskDto.Type.VULNERABILITY);
    when(closedRisk.getSeverity()).thenReturn(DependencyRiskDto.Severity.BLOCKER);
    when(closedRisk.getStatus()).thenReturn(DependencyRiskDto.Status.CONFIRM);
    when(closedRisk.getPackageName()).thenReturn("safe-package");
    when(closedRisk.getPackageVersion()).thenReturn("2.0.0");
    when(closedRisk.getSource()).thenReturn(SONARQUBE_SERVER_SOURCE);

    underTest.reload(uri, List.of(openRisk, closedRisk));

    assertThat(underTest.getAsDiagnostics(uri)).hasSize(1);
  }

  @Test
  void should_get_dependency_risk_by_id() throws Exception {
    var uri = new URI("/");
    var issue = mock(DependencyRisk.class);
    var issueId = UUID.randomUUID();
    when(issue.getId()).thenReturn(issueId);
    when(issue.getType()).thenReturn(DependencyRiskDto.Type.VULNERABILITY);
    when(issue.getSeverity()).thenReturn(DependencyRiskDto.Severity.BLOCKER);
    when(issue.getStatus()).thenReturn(DependencyRiskDto.Status.OPEN);
    when(issue.getPackageName()).thenReturn("vulnerable-package");
    when(issue.getPackageVersion()).thenReturn("1.0.0");
    when(issue.getSource()).thenReturn(SONARQUBE_SERVER_SOURCE);

    underTest.reload(uri, List.of(issue));

    assertThat(underTest.getDependencyRiskById(issueId.toString())).hasValue(issue);
    assertThat(underTest.getDependencyRiskById("otherId")).isEmpty();
  }

  @Test
  void should_remove_dependency_risk() throws Exception {
    var uri = new URI("/");
    var issue = mock(DependencyRisk.class);
    var issueId = UUID.randomUUID();
    when(issue.getId()).thenReturn(issueId);
    when(issue.getType()).thenReturn(DependencyRiskDto.Type.VULNERABILITY);
    when(issue.getSeverity()).thenReturn(DependencyRiskDto.Severity.BLOCKER);
    when(issue.getStatus()).thenReturn(DependencyRiskDto.Status.OPEN);
    when(issue.getPackageName()).thenReturn("vulnerable-package");
    when(issue.getPackageVersion()).thenReturn("1.0.0");
    when(issue.getSource()).thenReturn(SONARQUBE_SERVER_SOURCE);

    underTest.getDependencyRisksPerConfigScope().put(uri, new ArrayList<>(List.of(issue)));
    assertThat(underTest.getDependencyRiskById(issueId.toString())).hasValue(issue);

    underTest.removeDependencyRisks(uri.toString(), issueId.toString());
    assertThat(underTest.getDependencyRiskById(issueId.toString())).isEmpty();
  }

  @Test
  void should_clear_cache() throws Exception {
    var uri = new URI("/");
    var issue = mock(DependencyRisk.class);
    when(issue.getId()).thenReturn(UUID.randomUUID());
    when(issue.getType()).thenReturn(DependencyRiskDto.Type.VULNERABILITY);
    when(issue.getSeverity()).thenReturn(DependencyRiskDto.Severity.BLOCKER);
    when(issue.getStatus()).thenReturn(DependencyRiskDto.Status.OPEN);
    when(issue.getPackageName()).thenReturn("vulnerable-package");
    when(issue.getPackageVersion()).thenReturn("1.0.0");
    when(issue.getSource()).thenReturn(SONARQUBE_SERVER_SOURCE);

    underTest.reload(uri, List.of(issue));
    assertThat(underTest.getAsDiagnostics(uri)).hasSize(1);

    underTest.clear(uri);
    assertThat(underTest.getAsDiagnostics(uri)).isEmpty();
  }

  @Test
  void should_add_to_cache() throws Exception {
    var uri = new URI("/");
    var initialIssue = mock(DependencyRisk.class);
    when(initialIssue.getId()).thenReturn(UUID.randomUUID());
    when(initialIssue.getType()).thenReturn(DependencyRiskDto.Type.VULNERABILITY);
    when(initialIssue.getSeverity()).thenReturn(DependencyRiskDto.Severity.BLOCKER);
    when(initialIssue.getStatus()).thenReturn(DependencyRiskDto.Status.OPEN);
    when(initialIssue.getPackageName()).thenReturn("vulnerable-package");
    when(initialIssue.getPackageVersion()).thenReturn("1.0.0");
    when(initialIssue.getSource()).thenReturn(SONARQUBE_SERVER_SOURCE);

    underTest.reload(uri, new ArrayList<>(List.of(initialIssue)));
    assertThat(underTest.getAsDiagnostics(uri)).hasSize(1);

    var newIssue = mock(DependencyRisk.class);
    when(newIssue.getId()).thenReturn(UUID.randomUUID());
    when(newIssue.getType()).thenReturn(DependencyRiskDto.Type.VULNERABILITY);
    when(newIssue.getSeverity()).thenReturn(DependencyRiskDto.Severity.MEDIUM);
    when(newIssue.getStatus()).thenReturn(DependencyRiskDto.Status.OPEN);
    when(newIssue.getPackageName()).thenReturn("another-vulnerable-package");
    when(newIssue.getPackageVersion()).thenReturn("2.0.0");
    when(newIssue.getSource()).thenReturn(SONARQUBE_SERVER_SOURCE);

    underTest.add(uri, newIssue);
    assertThat(underTest.getAsDiagnostics(uri)).hasSize(2);
  }

  @Test
  void should_get_dependency_risks_per_config_scope() throws Exception {
    var uri = new URI("/");
    var issue = mock(DependencyRisk.class);
    when(issue.getId()).thenReturn(UUID.randomUUID());
    when(issue.getType()).thenReturn(DependencyRiskDto.Type.VULNERABILITY);
    when(issue.getSeverity()).thenReturn(DependencyRiskDto.Severity.BLOCKER);
    when(issue.getStatus()).thenReturn(DependencyRiskDto.Status.OPEN);
    when(issue.getPackageName()).thenReturn("vulnerable-package");
    when(issue.getPackageVersion()).thenReturn("1.0.0");
    when(issue.getSource()).thenReturn(SONARQUBE_SERVER_SOURCE);

    underTest.reload(uri, List.of(issue));

    var result = underTest.getDependencyRisksPerConfigScope();
    assertThat(result).containsKey(uri);
    assertThat(result.get(uri)).hasSize(1);
    assertThat(result.get(uri).get(0)).isEqualTo(issue);
  }

}
