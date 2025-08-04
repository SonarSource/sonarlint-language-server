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
package org.sonarsource.sonarlint.ls;

import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;
import org.sonarsource.sonarlint.ls.connected.DelegatingHotspot;
import org.sonarsource.sonarlint.ls.connected.DependencyRisksCache;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

import static org.sonarsource.sonarlint.ls.util.TextRangeUtils.convert;
import static org.sonarsource.sonarlint.ls.util.Utils.buildMessageWithPluralizedSuffix;

public class DiagnosticPublisher {

  static final String SONARLINT_SOURCE = "sonarqube";
  static final String REMOTE_SOURCE = "remote-hotspot";
  static final String LOCAL_HOTSPOT_SOURCE = "local-hotspot";

  public static final String ITEM_LOCATION = "location";
  public static final String ITEM_FLOW = "flow";

  private static final String SEVERITY_ERROR = "Error";
  private static final String SEVERITY_WARNING = "Warning";
  private static final String LEVEL_ALL = "All";
  private static final String LEVEL_MEDIUM_AND_ABOVE = "Medium severity and above";

  private final SonarLintExtendedLanguageClient client;
  private boolean firstSecretIssueDetected;

  private final IssuesCache issuesCache;
  private final HotspotsCache hotspotsCache;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final DependencyRisksCache dependencyRisksCache;
  private final OpenNotebooksCache openNotebooksCache;
  private final SettingsManager settingsManager;

  private boolean focusOnNewCode;

  public DiagnosticPublisher(SonarLintExtendedLanguageClient client, TaintVulnerabilitiesCache taintVulnerabilitiesCache,
    IssuesCache issuesCache, HotspotsCache hotspotsCache, OpenNotebooksCache openNotebooksCache, DependencyRisksCache dependencyRisksCache, SettingsManager settingsManager) {
    this.client = client;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.issuesCache = issuesCache;
    this.hotspotsCache = hotspotsCache;
    this.openNotebooksCache = openNotebooksCache;
    this.dependencyRisksCache = dependencyRisksCache;
    this.settingsManager = settingsManager;
    this.focusOnNewCode = false;
  }

  public void initialize(boolean firstSecretDetected) {
    this.firstSecretIssueDetected = firstSecretDetected;
  }

  public void publishDiagnostics(URI f, boolean onlyIssues) {
    if (openNotebooksCache.isNotebook(f)) {
      return;
    }
    if (!onlyIssues) {
      client.publishSecurityHotspots(createPublishSecurityHotspotsParams(f));
    }
    client.publishDiagnostics(createPublishDiagnosticsParams(f));
  }

  public void publishTaints(URI f) {
    client.publishTaintVulnerabilities(createPublishTaintsParams(f));
  }

  public void publishDependencyRisks(URI f) {
    client.publishDependencyRisks(createPublishDependencyRisksParams(f));
  }

  public void publishTaints() {
    taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().forEach((uri, taints) -> client.publishTaintVulnerabilities(createPublishTaintsParams(uri)));
  }

  public void publishHotspots(URI f) {
    client.publishSecurityHotspots(createPublishSecurityHotspotsParams(f));
  }

  Diagnostic issueDtoToDiagnostic(Map.Entry<String, DelegatingFinding> entry) {
    var issue = entry.getValue();
    return prepareDiagnostic(
      issue,
      entry.getKey(),
      false,
      focusOnNewCode,
      settingsManager.getCurrentSettings().getReportIssuesAsErrorLevel(),
      settingsManager.getCurrentSettings().getReportIssuesAsErrorOverrides());
  }

  public void setFocusOnNewCode(boolean focusOnNewCode) {
    this.focusOnNewCode = focusOnNewCode;
  }

  public static Diagnostic prepareDiagnostic(DelegatingFinding issue, String entryKey, boolean ignoreSecondaryLocations, boolean focusOnNewCode, String reportIssuesAsErrorLevel,
    Map<String, String> reportIssuesAsErrorOverrides) {
    var diagnostic = new Diagnostic();

    if (issue.getFinding() instanceof RaisedHotspotDto hotspotDto) {
      setVulnerabilityProbability(diagnostic, hotspotDto, reportIssuesAsErrorLevel, reportIssuesAsErrorOverrides);
    } else {
      setSeverity(diagnostic, issue, focusOnNewCode, reportIssuesAsErrorLevel, reportIssuesAsErrorOverrides);
    }
    var range = convert(issue);
    diagnostic.setRange(range);
    diagnostic.setCode(issue.getRuleKey());
    diagnostic.setMessage(message(issue, ignoreSecondaryLocations));
    setSource(diagnostic, issue);
    setData(diagnostic, issue, entryKey);

    return diagnostic;
  }

  static void setVulnerabilityProbability(Diagnostic diagnostic, RaisedHotspotDto hotspot, String reportIssuesAsErrorLevel, Map<String, String> reportIssuesAsErrorOverrides) {
    if (applySeverityOverrideOrLevelLogic(diagnostic, hotspot.getRuleKey(), reportIssuesAsErrorLevel, reportIssuesAsErrorOverrides)) {
      return;
    }

    if (LEVEL_MEDIUM_AND_ABOVE.equals(reportIssuesAsErrorLevel) && isMediumOrHighVulnerabilityProbability(hotspot)) {
      diagnostic.setSeverity(DiagnosticSeverity.Error);
      return;
    }

    switch (hotspot.getVulnerabilityProbability()) {
      case MEDIUM -> diagnostic.setSeverity(DiagnosticSeverity.Warning);
      case HIGH -> diagnostic.setSeverity(DiagnosticSeverity.Error);
      default -> diagnostic.setSeverity(DiagnosticSeverity.Information);
    }
  }

  static void setSeverity(Diagnostic diagnostic, DelegatingFinding issue, boolean focusOnNewCode, String reportIssuesAsErrorLevel,
    Map<String, String> reportIssuesAsErrorOverrides) {
    if (applySeverityOverrideOrLevelLogic(diagnostic, issue.getRuleKey(), reportIssuesAsErrorLevel, reportIssuesAsErrorOverrides)) {
      return;
    }

    if (LEVEL_MEDIUM_AND_ABOVE.equals(reportIssuesAsErrorLevel) && isMediumOrHighSeverityIssue(issue)) {
      diagnostic.setSeverity(DiagnosticSeverity.Error);
      return;
    }

    if (focusOnNewCode) {
      var newCodeSeverity = issue.isOnNewCode() ? DiagnosticSeverity.Warning : DiagnosticSeverity.Hint;
      diagnostic.setSeverity(newCodeSeverity);
    } else {
      diagnostic.setSeverity(DiagnosticSeverity.Warning);
    }
  }

  private static boolean applySeverityOverrideOrLevelLogic(Diagnostic diagnostic, String ruleKey,
    String reportIssuesAsErrorLevel, Map<String, String> reportIssuesAsErrorOverrides) {
    if (reportIssuesAsErrorOverrides.containsKey(ruleKey)) {
      var overriddenSeverity = reportIssuesAsErrorOverrides.get(ruleKey);
      if (SEVERITY_ERROR.equals(overriddenSeverity)) {
        diagnostic.setSeverity(DiagnosticSeverity.Error);
      } else if (SEVERITY_WARNING.equals(overriddenSeverity)) {
        diagnostic.setSeverity(DiagnosticSeverity.Warning);
      }
      return true;
    }

    if (LEVEL_ALL.equals(reportIssuesAsErrorLevel)) {
      diagnostic.setSeverity(DiagnosticSeverity.Error);
      return true;
    }

    return false;
  }

  private static boolean isMediumOrHighSeverityIssue(DelegatingFinding issue) {
    if (issue.getSeverityDetails().isLeft()) {
      var severity = issue.getSeverityDetails().getLeft().getSeverity();
      return severity == IssueSeverity.BLOCKER || severity == IssueSeverity.CRITICAL || severity == IssueSeverity.MAJOR;
    } else {
      var impacts = issue.getSeverityDetails().getRight().getImpacts();
      return impacts.stream().anyMatch(impact ->
        impact.getImpactSeverity() == ImpactSeverity.HIGH || impact.getImpactSeverity() == ImpactSeverity.MEDIUM);
    }
  }

  private static boolean isMediumOrHighVulnerabilityProbability(RaisedHotspotDto hotspot) {
    return hotspot.getVulnerabilityProbability() == VulnerabilityProbability.HIGH ||
      hotspot.getVulnerabilityProbability() == VulnerabilityProbability.MEDIUM;
  }

  public static class DiagnosticData {

    String entryKey;

    @Nullable
    String serverIssueKey;
    @Nullable
    HotspotStatus status;
    boolean isAiCodeFixable;
    boolean isOnNewCode;
    boolean hasQuickFix;
    Integer impactSeverity;

    public void setEntryKey(String entryKey) {
      this.entryKey = entryKey;
    }

    public void setServerIssueKey(@Nullable String serverIssueKey) {
      this.serverIssueKey = serverIssueKey;
    }

    public void setStatus(@Nullable HotspotStatus status) {
      this.status = status;
    }

    public String getEntryKey() {
      return entryKey;
    }

    @Nullable
    public String getServerIssueKey() {
      return serverIssueKey;
    }

    public void setAiCodeFixable(boolean aiCodeFixable) {
      isAiCodeFixable = aiCodeFixable;
    }

    public void setOnNewCode(boolean onNewCode) {
      isOnNewCode = onNewCode;
    }

    public boolean isOnNewCode() {
      return isOnNewCode;
    }

    public boolean hasQuickFix() {
      return hasQuickFix;
    }

    public void setHasQuickFix(boolean hasQuickFix) {
      this.hasQuickFix = hasQuickFix;
    }

    public Integer getImpactSeverity() {
      return impactSeverity;
    }

    public void setImpactSeverity(Integer impactSeverity) {
      this.impactSeverity = impactSeverity;
    }
  }

  public static void setSource(Diagnostic diagnostic, DelegatingFinding issue) {
    if (issue instanceof DelegatingHotspot hotspotDto) {
      var isKnown = hotspotDto.getServerIssueKey() != null;
      var isHotspot = hotspotDto.getType() == RuleType.SECURITY_HOTSPOT;
      if (isHotspot) {
        diagnostic.setSource(isKnown ? REMOTE_SOURCE : LOCAL_HOTSPOT_SOURCE);
      }
    } else {
      diagnostic.setSource(SONARLINT_SOURCE);
    }
  }

  private static void setData(Diagnostic diagnostic, DelegatingFinding issue, String entryKey) {
    var data = new DiagnosticData();
    if (issue.getServerIssueKey() != null) {
      data.setServerIssueKey(issue.getServerIssueKey());
    }
    if (issue instanceof DelegatingHotspot raisedHotspotDto) {
      data.setStatus(raisedHotspotDto.getReviewStatus());
    }
    var isAiCodeFixable = issue.getFinding() instanceof RaisedIssueDto raisedIssueDto && raisedIssueDto.isAiCodeFixable();
    data.setEntryKey(entryKey);
    data.setOnNewCode(issue.isOnNewCode());
    data.setAiCodeFixable(isAiCodeFixable);
    data.setHasQuickFix(!issue.quickFixes().isEmpty());
    data.setImpactSeverity(getImpactSeverity(issue.getSeverityDetails()));
    diagnostic.setData(data);
  }

  public static Integer getImpactSeverity(Either<StandardModeDetails, MQRModeDetails> severityDetails) {
    if (severityDetails.isLeft()) {
      return severityDetails.getLeft().getSeverity().ordinal();
    }
    var highestQualityImpact = Collections.max(severityDetails.getRight().getImpacts(), Comparator.comparing(ImpactDto::getImpactSeverity));
    return highestQualityImpact.getImpactSeverity().ordinal();
  }

  public static String message(DelegatingFinding issue, boolean ignoreSecondaryLocations) {
    if (issue.flows().isEmpty() || ignoreSecondaryLocations) {
      return issue.getMessage();
    } else if (issue.flows().size() == 1) {
      return buildMessageWithPluralizedSuffix(issue.getMessage(), issue.flows().get(0).getLocations().size(), ITEM_LOCATION);
    } else if (issue.flows().stream().allMatch(f -> f.getLocations().size() == 1)) {
      int nbLocations = issue.flows().size();
      return buildMessageWithPluralizedSuffix(issue.getMessage(), nbLocations, ITEM_LOCATION);
    } else {
      int nbFlows = issue.flows().size();
      return buildMessageWithPluralizedSuffix(issue.getMessage(), nbFlows, ITEM_FLOW);
    }
  }

  public void didDetectSecret() {
    if (!firstSecretIssueDetected) {
      client.showFirstSecretDetectionNotification();
      firstSecretIssueDetected = true;
    }
  }

  private PublishDiagnosticsParams createPublishDiagnosticsParams(URI newUri) {
    var p = new PublishDiagnosticsParams();

    Map<String, DelegatingFinding> localIssues = issuesCache.get(newUri);

    var localDiagnostics = localIssues.entrySet()
      .stream()
      .filter(e -> !e.getValue().isResolved())
      .map(this::issueDtoToDiagnostic);

    var diagnosticList = localDiagnostics
      .sorted(DiagnosticPublisher.byLineNumber())
      .toList();
    p.setDiagnostics(diagnosticList);
    p.setUri(newUri.toString());

    return p;
  }

  private PublishDiagnosticsParams createPublishTaintsParams(URI newUri) {
    var p = new PublishDiagnosticsParams();

    var taintDiagnostics = taintVulnerabilitiesCache.getAsDiagnostics(newUri);

    var diagnosticList = taintDiagnostics
      .sorted(DiagnosticPublisher.byLineNumber())
      .toList();
    p.setDiagnostics(diagnosticList);
    p.setUri(newUri.toString());

    return p;
  }

  private PublishDiagnosticsParams createPublishSecurityHotspotsParams(URI newUri) {
    var p = new PublishDiagnosticsParams();

    p.setDiagnostics(hotspotsCache.get(newUri).entrySet()
      .stream()
      .filter(e -> !e.getValue().isResolved())
      .map(e -> prepareDiagnostic(e.getValue(), e.getKey(), false, focusOnNewCode, settingsManager.getCurrentSettings().getReportIssuesAsErrorLevel(),
        settingsManager.getCurrentSettings().getReportIssuesAsErrorOverrides()))
      .sorted(DiagnosticPublisher.byLineNumber())
      .toList());
    p.setUri(newUri.toString());

    return p;
  }

  private PublishDiagnosticsParams createPublishDependencyRisksParams(URI folderUri) {
    var p = new PublishDiagnosticsParams();

    p.setDiagnostics(dependencyRisksCache.getAsDiagnostics(folderUri).toList());
    p.setUri(folderUri.toString());

    return p;
  }

  private static Comparator<? super Diagnostic> byLineNumber() {
    return Comparator.comparing((Diagnostic d) -> d.getRange().getStart().getLine())
      .thenComparing(Diagnostic::getMessage);
  }
}
