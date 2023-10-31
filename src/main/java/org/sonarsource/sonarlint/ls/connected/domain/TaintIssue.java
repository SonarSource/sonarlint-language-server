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
package org.sonarsource.sonarlint.ls.connected.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;

public class TaintIssue extends ServerTaintIssue {
  private String source;
  public TaintIssue(String key, boolean resolved, String ruleKey, String message, String filePath,
    Instant creationDate, IssueSeverity severity, RuleType type, @Nullable TextRangeWithHash textRange,
     List<Flow> flows, @Nullable String ruleDescriptionContextKey, String source, @Nullable CleanCodeAttribute cleanCodeAttribute,
    Map<SoftwareQuality, ImpactSeverity> impacts, boolean isOnNewCode) {
    super(key, resolved, ruleKey, message, filePath, creationDate, severity, type, textRange, ruleDescriptionContextKey, cleanCodeAttribute, impacts);
    this.setFlows(flows);
    this.setIsOnNewCode(isOnNewCode);
    this.source = source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getSource() {
    return this.source;
  }

  public static TaintIssue from(ServerTaintIssue serverTaintIssue, String source) {
    return new TaintIssue(serverTaintIssue.getKey(), serverTaintIssue.isResolved(), serverTaintIssue.getRuleKey(),
      serverTaintIssue.getMessage(), serverTaintIssue.getFilePath(), serverTaintIssue.getCreationDate(), serverTaintIssue.getSeverity(),
      serverTaintIssue.getType(), serverTaintIssue.getTextRange(), serverTaintIssue.getFlows(),
      serverTaintIssue.getRuleDescriptionContextKey(), source, serverTaintIssue.getCleanCodeAttribute().orElse(null),
      serverTaintIssue.getImpacts(), serverTaintIssue.isOnNewCode());
  }

  public static List<TaintIssue> from(List<ServerTaintIssue> serverTaintIssues, boolean isSonarCloudAlias) {
    var source = isSonarCloudAlias ? AnalysisScheduler.SONARCLOUD_TAINT_SOURCE : AnalysisScheduler.SONARQUBE_TAINT_SOURCE;
    return serverTaintIssues
      .stream()
      .map(serverTaintIssue -> TaintIssue.from(serverTaintIssue, source))
      .toList();
  }
}
