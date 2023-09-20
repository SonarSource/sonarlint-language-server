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
package org.sonarsource.sonarlint.ls.connected;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;

public class DelegatingIssue implements Issue {

  private UUID issueId;
  private boolean resolved;
  private IssueSeverity severity;
  private String serverIssueKey;
  private final Issue issue;
  private final RuleType type;
  private final HotspotReviewStatus reviewStatus;
  private boolean isOnNewCode;

  DelegatingIssue(Trackable<Issue> trackable) {
    var userSeverity = trackable.getSeverity();
    this.issue = trackable.getClientObject();
    this.severity = userSeverity != null ? userSeverity : issue.getSeverity();
    this.type = trackable.getType();
    this.serverIssueKey = trackable.getServerIssueKey();
    this.reviewStatus = trackable.getReviewStatus();
    this.isOnNewCode = true;
  }

  DelegatingIssue(Trackable<Issue> trackable, UUID issueId, boolean resolved, boolean isOnNewCode) {
    this(trackable);
    this.issueId = issueId;
    this.resolved = resolved;
    this.isOnNewCode = isOnNewCode;
  }

  DelegatingIssue(Trackable<Issue> trackable, UUID issueId, boolean resolved, IssueSeverity overriddenSeverity, String serverIssueKey, boolean isOnNewCode) {
    this(trackable);
    this.issueId = issueId;
    this.resolved = resolved;
    this.severity = overriddenSeverity;
    this.serverIssueKey = serverIssueKey;
    this.isOnNewCode = isOnNewCode;
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
    return issue.getStartLine();
  }

  @CheckForNull
  @Override
  public Integer getStartLineOffset() {
    return issue.getStartLineOffset();
  }

  @CheckForNull
  @Override
  public Integer getEndLine() {
    return issue.getEndLine();
  }

  @CheckForNull
  @Override
  public Integer getEndLineOffset() {
    return issue.getEndLineOffset();
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
    return issue.getTextRange();
  }

  @Override
  public List<QuickFix> quickFixes() {
    return issue.quickFixes();
  }

  @Override
  public Optional<String> getRuleDescriptionContextKey() {
    return issue.getRuleDescriptionContextKey();
  }

  public String getServerIssueKey() {
    return serverIssueKey;
  }

  @Override
  public Optional<VulnerabilityProbability> getVulnerabilityProbability() {
    return issue.getVulnerabilityProbability();
  }

  public HotspotReviewStatus getReviewStatus() {
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

  private DelegatingIssue(Issue issue, RuleType type, String serverIssueKey, IssueSeverity severity, HotspotReviewStatus reviewStatus) {
    this.issue = issue;
    this.type = type;
    this.serverIssueKey = serverIssueKey;
    this.severity = severity;
    this.reviewStatus = reviewStatus;
  }

  public DelegatingIssue cloneWithNewStatus(HotspotReviewStatus newStatus) {
    return new DelegatingIssue(this.issue, this.type, this.serverIssueKey, this.severity, newStatus);
  }
}
