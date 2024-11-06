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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.Issue;

public class DelegatingFinding implements Issue {

  protected UUID issueId;
  protected boolean resolved;
  protected IssueSeverity severity;
  protected String serverIssueKey;
  protected final RuleType type;
  protected final RaisedFindingDto finding;
  protected boolean isOnNewCode;
  protected URI fileUri;
  protected Either<StandardModeDetails, MQRModeDetails> severityDetails;

  private DelegatingFinding(RaisedFindingDto finding) {
    this.issueId = finding.getId();
    this.severity = finding.getSeverityMode().isLeft() ? finding.getSeverityMode().getLeft().getSeverity() : null;
    this.type = finding.getSeverityMode().isLeft() ? finding.getSeverityMode().getLeft().getType() : null;
    this.isOnNewCode = finding.isOnNewCode();
    this.finding = finding;
    this.serverIssueKey = finding.getServerKey();
    this.resolved = finding.isResolved();
    this.severityDetails = finding.getSeverityMode();
  }

  public DelegatingFinding(RaisedFindingDto rawFinding, URI fileUri) {
    this(rawFinding);
    this.fileUri = fileUri;
  }

  @CheckForNull
  public IssueSeverity getSeverity() {
    return severityDetails.isLeft() ? severityDetails.getLeft().getSeverity() : null;
  }

  @CheckForNull
  public RuleType getType() {
    return severityDetails.isLeft() ? severityDetails.getLeft().getType() : null;
  }

  @CheckForNull
  public String getMessage() {
    return finding.getPrimaryMessage();
  }

  public String getRuleKey() {
    return finding.getRuleKey();
  }

  @Override
  @CheckForNull
  public Integer getStartLine() {
    var textRange = finding.getTextRange();
    return textRange != null ? textRange.getStartLine() : null;
  }

  @Override
  @CheckForNull
  public Integer getStartLineOffset() {
    var textRange = finding.getTextRange();
    return textRange != null ? textRange.getStartLineOffset() : null;
  }

  @Override
  @CheckForNull
  public Integer getEndLine() {
    var textRange = finding.getTextRange();
    return textRange != null ? textRange.getEndLine() : null;
  }

  @Override
  @CheckForNull
  public Integer getEndLineOffset() {
    var textRange = finding.getTextRange();
    return textRange != null ? textRange.getEndLineOffset() : null;
  }

  public List<IssueFlowDto> flows() {
    return finding.getFlows();
  }

  @CheckForNull
  public TextRangeDto getTextRange() {
    return finding.getTextRange();
  }

  @Override
  public URI getFileUri() {
    return this.fileUri;
  }

  public List<QuickFixDto> quickFixes() {
    return finding.getQuickFixes();
  }

  @CheckForNull
  public String getRuleDescriptionContextKey() {
    return finding.getRuleDescriptionContextKey();
  }

  public String getServerIssueKey() {
    return serverIssueKey;
  }

  public UUID getIssueId() {
    return issueId;
  }

  @Override
  public Either<StandardModeDetails, MQRModeDetails> getSeverityDetails() {
    return severityDetails;
  }

  public boolean isResolved() {
    return resolved;
  }

  public boolean isOnNewCode() {
    return isOnNewCode;
  }

  public RaisedFindingDto getFinding() {
    return finding;
  }
}
