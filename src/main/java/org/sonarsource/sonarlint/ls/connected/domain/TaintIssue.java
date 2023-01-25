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
package org.sonarsource.sonarlint.ls.connected.domain;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;

public class TaintIssue extends ServerTaintIssue {
  private String source;
  public TaintIssue(String key, boolean resolved, String ruleKey, String message, String filePath,
    Instant creationDate, IssueSeverity severity, RuleType type,
    @Nullable TextRangeWithHash textRange, List<Flow> flows, String source) {
    super(key, resolved, ruleKey, message, filePath, creationDate, severity, type, textRange, null);
    this.setFlows(flows);
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
      serverTaintIssue.getType(), serverTaintIssue.getTextRange(), serverTaintIssue.getFlows(), source);
  }

  public static List<TaintIssue> from(List<ServerTaintIssue> serverTaintIssues, boolean isSonarCloudAlias) {
    var source = isSonarCloudAlias ? AnalysisScheduler.SONARCLOUD_TAINT_SOURCE : AnalysisScheduler.SONARQUBE_TAINT_SOURCE;
    return serverTaintIssues
      .stream()
      .map(serverTaintIssue -> TaintIssue.from(serverTaintIssue, source))
      .collect(Collectors.toList());
  }
}
