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

import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;

import static org.sonarsource.sonarlint.ls.domain.TaintIssue.SONARQUBE_SERVER_SOURCE;

public class DependencyRisk extends DependencyRiskDto {
  private final String workspaceFolderUri;
  private final String source;

  public DependencyRisk(DependencyRiskDto dependencyRiskDto, String workspaceFolderUri) {
    super(dependencyRiskDto.getId(), dependencyRiskDto.getType(), dependencyRiskDto.getSeverity(), dependencyRiskDto.getQuality(),
      dependencyRiskDto.getStatus(), dependencyRiskDto.getPackageName(), dependencyRiskDto.getPackageVersion(),
      dependencyRiskDto.getTransitions());
    this.workspaceFolderUri = workspaceFolderUri;
    this.source = SONARQUBE_SERVER_SOURCE;
  }

  public String getWorkspaceFolderUri() {
    return workspaceFolderUri;
  }

  public String getSource() {
    return source;
  }
}
