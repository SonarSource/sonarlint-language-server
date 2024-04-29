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

import java.net.URI;
import java.util.List;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.Issue;

public class DelegatingCellIssue implements Issue {
  private final RawIssueDto issue;
  private final RuleType type;
  private final IssueSeverity severity;
  private final TextRangeDto textRange;
  private final List<QuickFixDto> quickFixes;

  DelegatingCellIssue(RawIssueDto issue, @Nullable TextRangeDto textRange, List<QuickFixDto> quickFixes) {
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

  @CheckForNull
  @Override
  public String getMessage() {
    return issue.getPrimaryMessage();
  }

  @CheckForNull
  @Override
  public TextRangeDto getTextRange() {
    return textRange;
  }

  @Override
  public URI getFileUri() {
    return issue.getFileUri();
  }

  @Override
  public RawIssueDto getRawIssue() {
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
  public List<RawIssueFlowDto> flows() {
    return issue.getFlows();
  }

  // TODO non interface method, ideally find a way to use the one from the interface
  @CheckForNull
  public TextRangeDto getCellIssueTextRange() {
    return textRange;
  }

  @Override
  public List<QuickFixDto> quickFixes() {
    return quickFixes;
  }

  @Override
  @CheckForNull
  public String getRuleDescriptionContextKey() {
    return issue.getRuleDescriptionContextKey();
  }

}
