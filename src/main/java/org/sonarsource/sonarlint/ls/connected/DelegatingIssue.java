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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
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
  private final RawIssue issue;
  private final RuleType type;
  private HotspotStatus reviewStatus;
  private boolean isOnNewCode;

  private DelegatingIssue(RawIssue issue) {
    this.issueId = UUID.randomUUID();
    this.issue = issue;
    this.severity = issue.getSeverity();
    this.type = issue.getType();
    this.isOnNewCode = true;
  }

  public DelegatingIssue(RawIssue rawIssue, UUID issueId, boolean resolved, boolean isOnNewCode) {
    this(rawIssue);
    this.issueId = issueId;
    this.resolved = resolved;
    this.isOnNewCode = isOnNewCode;
  }

  DelegatingIssue(RawIssue rawIssue, UUID issueId, boolean resolved, IssueSeverity overriddenSeverity,
    String serverIssueKey, boolean isOnNewCode, @Nullable HotspotStatus reviewStatus) {
    this(rawIssue);
    this.issueId = issueId;
    this.resolved = resolved;
    this.severity = overriddenSeverity;
    this.serverIssueKey = serverIssueKey;
    this.isOnNewCode = isOnNewCode;
    this.reviewStatus = reviewStatus;
  }

  private DelegatingIssue(RawIssue rawIssue, RuleType type, String serverIssueKey, IssueSeverity severity, HotspotStatus reviewStatus) {
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

  public Optional<CleanCodeAttribute> getCleanCodeAttribute() {
    return issue.getCleanCodeAttribute();
  }

  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return issue.getImpacts();
  }

  @CheckForNull
  public String getMessage() {
    return issue.getMessage();
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

  public List<Flow> flows() {
    return issue.getFlows();
  }

  @CheckForNull
  public ClientInputFile getInputFile() {
    return issue.getInputFile();
  }

  @CheckForNull
  public TextRangeDto getTextRange() {
    return issue.getTextRange();
  }

  @Override
  public RawIssue getRawIssue() {
    return issue;
  }

  public List<QuickFix> quickFixes() {
    return issue.quickFixes();
  }

  public Optional<String> getRuleDescriptionContextKey() {
    return issue.getRuleDescriptionContextKey();
  }

  public String getServerIssueKey() {
    return serverIssueKey;
  }

  public Optional<VulnerabilityProbability> getVulnerabilityProbability() {
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
