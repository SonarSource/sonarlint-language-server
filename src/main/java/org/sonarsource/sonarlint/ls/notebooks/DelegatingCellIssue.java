/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.sonarlint.ls.notebooks;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.Issue;

import static org.sonarsource.sonarlint.ls.util.TextRangeUtils.textRangeDtoFromTextRange;

public class DelegatingCellIssue implements Issue {
  private final RawIssue issue;
  private final RuleType type;
  private final IssueSeverity severity;
  private final TextRange textRange;
  private final List<QuickFix> quickFixes;

  DelegatingCellIssue(RawIssue issue, @Nullable TextRange textRange, List<QuickFix> quickFixes) {
    var userSeverity = issue.getSeverity();
    this.issue = issue;
    this.severity = userSeverity != null ? userSeverity : this.issue.getSeverity();
    this.type = issue.getType();
    this.textRange = textRange;
    this.quickFixes = quickFixes;
  }

  @Override
  public UUID getIssueId() {
    return UUID.randomUUID();
  }

  @Override
  public IssueSeverity getSeverity() {
    return severity;
  }

  @CheckForNull
  @Override
  public RuleType getType() {
    return type;
  }

  @Override
  public Optional<CleanCodeAttribute> getCleanCodeAttribute() {
    return issue.getCleanCodeAttribute();
  }

  @Override
  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return issue.getImpacts();
  }

  @CheckForNull
  @Override
  public String getMessage() {
    return issue.getMessage();
  }

  @CheckForNull
  @Override
  public TextRangeDto getTextRange() {
    return textRangeDtoFromTextRange(textRange);
  }

  @Override
  public RawIssue getRawIssue() {
    return issue;
  }

  @Override
  public String getRuleKey() {
    return issue.getRuleKey();
  }

  @CheckForNull
  @Override
  public Integer getStartLine() {
    return textRange != null ? textRange.getStartLine() : null;
  }

  @CheckForNull
  @Override
  public Integer getStartLineOffset() {
    return textRange != null ? textRange.getStartLineOffset() : null;
  }

  @CheckForNull
  @Override
  public Integer getEndLine() {
    return textRange != null ? textRange.getEndLine() : null;
  }

  @CheckForNull
  @Override
  public Integer getEndLineOffset() {
    return textRange != null ? textRange.getEndLineOffset() : null;
  }

  @Override
  public List<Flow> flows() {
    return issue.getFlows();
  }

  @CheckForNull
  public ClientInputFile getInputFile() {
    return issue.getInputFile();
  }

  // TODO non interface method, ideally find a way to use the one from the interface
  @CheckForNull
  public TextRange getCellIssueTextRange() {
    return textRange;
  }

  @Override
  public List<QuickFix> quickFixes() {
    return quickFixes;
  }

  @Override
  public Optional<String> getRuleDescriptionContextKey() {
    return issue.getRuleDescriptionContextKey();
  }

  @Override
  public Optional<VulnerabilityProbability> getVulnerabilityProbability() {
    return issue.getVulnerabilityProbability();
  }
}
