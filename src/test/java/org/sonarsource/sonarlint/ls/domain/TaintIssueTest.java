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
package org.sonarsource.sonarlint.ls.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;

import static org.assertj.core.api.Assertions.assertThat;


class TaintIssueTest {

  @Test
  void shouldConvertImpactsListToMap() {
    var impactsList = List.of(
      new ImpactDto(SoftwareQuality.SECURITY, ImpactSeverity.HIGH),
      new ImpactDto(SoftwareQuality.RELIABILITY, ImpactSeverity.LOW)
    );

    var result = TaintIssue.impactListToMap(impactsList);

    assertThat(result)
      .hasSize(2)
      .containsEntry(SoftwareQuality.SECURITY, ImpactSeverity.HIGH)
      .containsEntry(SoftwareQuality.RELIABILITY, ImpactSeverity.LOW);
  }

  @Test
  void shouldInitializeWithCorrectSeverityDetails() {
    var taintIssueDto = new TaintVulnerabilityDto(UUID.randomUUID(),
      "serverKey",
      false,
      "ruleKey",
      "message",
      Path.of("ideFilePath"),
      Instant.now(),
      Either.forLeft(new StandardModeDetails(IssueSeverity.MAJOR, RuleType.BUG)),
      List.of(),
      null,
      "ruleDescriptionContextKey",
      false,
      false);

    var taintIssue = new TaintIssue(taintIssueDto, "workspaceFolderUri", true);

    Assertions.assertTrue(taintIssue.getSeverityMode().isLeft());
    assertThat(taintIssue.getSeverityMode().getLeft().getSeverity()).isEqualTo(IssueSeverity.MAJOR);
    assertThat(taintIssue.getSeverityMode().getLeft().getType()).isEqualTo(RuleType.BUG);
  }

  @Test
  void shouldInitializeWithCorrectSeverityDetailsMQR() {
    var taintIssueDto = new TaintVulnerabilityDto(UUID.randomUUID(),
      "serverKey",
      false,
      "ruleKey",
      "message",
      Path.of("ideFilePath"),
      Instant.now(),
      Either.forRight(new MQRModeDetails(CleanCodeAttribute.TRUSTWORTHY, List.of(new ImpactDto(SoftwareQuality.SECURITY, ImpactSeverity.HIGH)))),
      List.of(),
      null,
      "ruleDescriptionContextKey",
      false,
      false);

    var taintIssue = new TaintIssue(taintIssueDto, "workspaceFolderUri", true);

    Assertions.assertTrue(taintIssue.getSeverityMode().isRight());
    assertThat(taintIssue.getSeverityMode().getRight().getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.TRUSTWORTHY);
    assertThat(taintIssue.getSeverityMode().getRight().getImpacts().get(0).getImpactSeverity()).isEqualTo(ImpactSeverity.HIGH);
    assertThat(taintIssue.getSeverityMode().getRight().getImpacts().get(0).getSoftwareQuality()).isEqualTo(SoftwareQuality.SECURITY);
  }

}