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
package org.sonarsource.sonarlint.ls.util;

import javax.annotation.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.ls.Issue;

import static java.util.Objects.requireNonNull;

public class TextRangeUtils {

  private TextRangeUtils() {
    // util class
  }

  public static Range convert(Issue issue) {
    if (issue.getStartLine() == null) {
      return new Range(new Position(0, 0), new Position(0, 0));
    }
    requireNonNull(issue.getStartLineOffset());
    requireNonNull(issue.getEndLine());
    requireNonNull(issue.getEndLineOffset());
    return new Range(
      new Position(
        issue.getStartLine() - 1,
        issue.getStartLineOffset()),
      new Position(
        issue.getEndLine() - 1,
        issue.getEndLineOffset()));
  }

  public static Range convert(TaintVulnerabilityDto issue) {
    return convert(issue.getTextRange());
  }

  public static Range convert(ServerTaintIssue.ServerIssueLocation issue) {
    return convert(issue.getTextRange());
  }

  public static Range convert(@Nullable TextRangeWithHash textRange) {
    if (textRange == null) {
      return new Range(new Position(0, 0), new Position(0, 0));
    }
    return new Range(
      new Position(
        textRange.getStartLine() - 1,
        textRange.getStartLineOffset()),
      new Position(
        textRange.getEndLine() - 1,
        textRange.getEndLineOffset()));
  }

  public static Range convert(@Nullable TextRangeWithHashDto textRange) {
    if (textRange == null) {
      return new Range(new Position(0, 0), new Position(0, 0));
    }
    return new Range(
      new Position(
        textRange.getStartLine() - 1,
        textRange.getStartLineOffset()),
      new Position(
        textRange.getEndLine() - 1,
        textRange.getEndLineOffset()));
  }

  public static TextRangeDto textRangeWithHashDtoToTextRangeDto(@Nullable TextRangeWithHashDto textRange) {
    if (textRange == null) {
      return new TextRangeDto(0, 0, 0, 0);
    }
    return new TextRangeDto(
      textRange.getStartLine() - 1,
      textRange.getStartLineOffset(),
      textRange.getEndLine() - 1,
      textRange.getEndLineOffset());
  }

  public static boolean locationMatches(TaintVulnerabilityDto i, Diagnostic d) {
    return convert(i).equals(d.getRange());
  }


  public static TextRangeDto textRangeDtoFromTextRange(@Nullable TextRange textRange) {
    return textRange != null ? new TextRangeDto(textRange.getStartLine(),
      textRange.getStartLineOffset(),
      textRange.getEndLine(),
      textRange.getEndLineOffset()) : null;
  }
}
