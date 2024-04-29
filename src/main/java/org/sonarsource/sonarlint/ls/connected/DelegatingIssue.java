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
import java.util.Map;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.Issue;

public class DelegatingIssue implements Issue {

  private UUID issueId;
  private boolean resolved;
  private IssueSeverity severity;
  private String serverIssueKey;
  private final RawIssueDto issue;
  private final RuleType type;
  private HotspotStatus reviewStatus;
  private boolean isOnNewCode;

  private DelegatingIssue(RawIssueDto issue) {
    this.issueId = UUID.randomUUID();
    this.issue = issue;
    this.severity = issue.getSeverity();
    this.type = issue.getType();
    this.isOnNewCode = true;
  }

  public DelegatingIssue(RawIssueDto rawIssue, UUID issueId, boolean resolved, boolean isOnNewCode) {
    this(rawIssue);
    this.issueId = issueId;
    this.resolved = resolved;
    this.isOnNewCode = isOnNewCode;
  }

  DelegatingIssue(RawIssueDto rawIssue, UUID issueId, boolean resolved, IssueSeverity overriddenSeverity,
    String serverIssueKey, boolean isOnNewCode, @Nullable HotspotStatus reviewStatus) {
    this(rawIssue);
    this.issueId = issueId;
    this.resolved = resolved;
    this.severity = overriddenSeverity;
    this.serverIssueKey = serverIssueKey;
    this.isOnNewCode = isOnNewCode;
    this.reviewStatus = reviewStatus;
  }

  private DelegatingIssue(RawIssueDto rawIssue, RuleType type, String serverIssueKey, IssueSeverity severity, HotspotStatus reviewStatus) {
    this.issueId = UUID.randomUUID();
    this.issue = rawIssue;
    this.type = type;
    this.serverIssueKey = serverIssueKey;
    this.severity = severity;
    this.reviewStatus = reviewStatus;
  }

  public DelegatingIssue cloneWithNewStatus(HotspotStatus newStatus) {
    return new DelegatingIssue(this.issue, this.type, this.serverIssueKey, this.severity, newStatus);
  }

  public IssueSeverity getSeverity() {
    return severity;
  }

  @CheckForNull
  public RuleType getType() {
    return type;
  }

  public CleanCodeAttribute getCleanCodeAttribute() {
    return issue.getCleanCodeAttribute();
  }

  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return issue.getImpacts();
  }

  @CheckForNull
  public String getMessage() {
    return issue.getPrimaryMessage();
  }

  public String getRuleKey() {
    return issue.getRuleKey();
  }

  @Override
  @CheckForNull
  public Integer getStartLine() {
    var textRange = issue.getTextRange();
    return textRange != null ? textRange.getStartLine() : null;
  }

  @Override
  @CheckForNull
  public Integer getStartLineOffset() {
    var textRange = issue.getTextRange();
    return textRange != null ? textRange.getStartLineOffset() : null;
  }

  @Override
  @CheckForNull
  public Integer getEndLine() {
    var textRange = issue.getTextRange();
    return textRange != null ? textRange.getEndLine() : null;
  }

  @Override
  @CheckForNull
  public Integer getEndLineOffset() {
    var textRange = issue.getTextRange();
    return textRange != null ? textRange.getEndLineOffset() : null;
  }

  public List<RawIssueFlowDto> flows() {
    return issue.getFlows();
  }

  @CheckForNull
  public TextRangeDto getTextRange() {
    return issue.getTextRange();
  }

  @Override
  public URI getFileUri() {
    return issue.getFileUri();
  }

  @Override
  public RawIssueDto getRawIssue() {
    return issue;
  }

  public List<QuickFixDto> quickFixes() {
    return issue.getQuickFixes();
  }

  @CheckForNull
  public String getRuleDescriptionContextKey() {
    return issue.getRuleDescriptionContextKey();
  }

  public String getServerIssueKey() {
    return serverIssueKey;
  }

  public VulnerabilityProbability getVulnerabilityProbability() {
    return issue.getVulnerabilityProbability();
  }

  @CheckForNull
  public HotspotStatus getReviewStatus() {
    return reviewStatus;
  }

  public UUID getIssueId() {
    return issueId;
  }

  public boolean isResolved() {
    return resolved;
  }

  public boolean isOnNewCode() {
    return isOnNewCode;
  }
}
