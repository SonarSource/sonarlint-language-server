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
package org.sonarsource.sonarlint.ls.notebooks;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

public class DelegatingCellIssue implements Issue {
  private final Issue issue;
  private final RuleType type;
  private final IssueSeverity severity;
  private final TextRange textRange;
  private final List<QuickFix> quickFixes;

  DelegatingCellIssue(Issue issue, TextRange textRange, List<QuickFix> quickFixes) {
    var userSeverity = issue.getSeverity();
    this.issue = issue;
    this.severity = userSeverity != null ? userSeverity : this.issue.getSeverity();
    this.type = issue.getType();
    this.textRange = textRange;
    this.quickFixes = quickFixes;
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
    return issue.flows();
  }

  @CheckForNull
  @Override
  public ClientInputFile getInputFile() {
    return issue.getInputFile();
  }

  @CheckForNull
  @Override
  public TextRange getTextRange() {
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
