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

import java.util.List;
import java.util.Map;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;

public class TaintIssue extends TaintVulnerabilityDto {
  public static final String SONARQUBE_TAINT_SOURCE = "Latest SonarQube Server Analysis";
  public static final String SONARCLOUD_TAINT_SOURCE = "Latest SonarQube Cloud Analysis";
  String workspaceFolderUri;
  String source;

  public TaintIssue(TaintVulnerabilityDto taintDto, String workspaceFolderUri, boolean isSonarCloud) {
    super(taintDto.getId(), taintDto.getSonarServerKey(), taintDto.isResolved(), taintDto.getRuleKey(), taintDto.getMessage(),
      taintDto.getIdeFilePath(), taintDto.getIntroductionDate(), taintDto.getSeverityMode(), taintDto.getFlows(), taintDto.getTextRange(),
      taintDto.getRuleDescriptionContextKey(), taintDto.isOnNewCode());
    this.workspaceFolderUri = workspaceFolderUri;
    this.source = isSonarCloud ? SONARCLOUD_TAINT_SOURCE : SONARQUBE_TAINT_SOURCE;
  }

  public static Map<SoftwareQuality, ImpactSeverity> impactListToMap(List<ImpactDto> impacts) {
    return impacts.stream().collect(
      java.util.stream.Collectors.toMap(ImpactDto::getSoftwareQuality, ImpactDto::getImpactSeverity));
  }

  public String getWorkspaceFolderUri() {
    return workspaceFolderUri;
  }

  public String getSource() {
    return source;
  }
}
