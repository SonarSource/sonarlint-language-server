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
package org.sonarsource.sonarlint.ls.notebooks;

import java.net.URI;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;

public class DelegatingCellIssue extends DelegatingFinding {
  private final RaisedFindingDto issue;
  private final TextRangeDto cellTextRange;
  private final List<QuickFixDto> cellQuickFixes;

  DelegatingCellIssue(RaisedFindingDto issue, URI fileUri, @Nullable TextRangeDto textRange, List<QuickFixDto> quickFixes) {
    super(issue, fileUri);
    this.issue = issue;
    this.cellTextRange = textRange;
    this.cellQuickFixes = quickFixes;
  }

  @Override
  public TextRangeDto getTextRange() {
    return cellTextRange;
  }

  @Override
  @CheckForNull
  public Integer getStartLine() {
    return cellTextRange != null ? cellTextRange.getStartLine() : null;
  }

  @Override
  @CheckForNull
  public Integer getStartLineOffset() {
    return cellTextRange != null ? cellTextRange.getStartLineOffset() : null;
  }

  @Override
  @CheckForNull
  public Integer getEndLine() {
    return cellTextRange != null ? cellTextRange.getEndLine() : null;
  }

  @Override
  @CheckForNull
  public Integer getEndLineOffset() {
    return cellTextRange != null ? cellTextRange.getEndLineOffset() : null;
  }

  public RaisedIssueDto getIssue() {
    return new RaisedIssueDto(issue.getId(), issue.getServerKey(), issue.getRuleKey(), issue.getPrimaryMessage(),
      issue.getSeverityMode(), issue.getIntroductionDate(),
      issue.isOnNewCode(), issue.isResolved(), cellTextRange, issue.getFlows(), cellQuickFixes, issue.getRuleDescriptionContextKey(), false);
  }

  @Override
  public List<QuickFixDto> quickFixes() {
    return cellQuickFixes;
  }
}
