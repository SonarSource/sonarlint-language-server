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
import java.util.Comparator;
import java.util.Map;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;
import org.sonarsource.sonarlint.ls.connected.DelegatingHotspot;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;

import static org.sonarsource.sonarlint.ls.util.TextRangeUtils.convert;
import static org.sonarsource.sonarlint.ls.util.Utils.buildMessageWithPluralizedSuffix;

public class DiagnosticPublisher {

  static final String SONARLINT_SOURCE = "sonarqube";
  static final String REMOTE_SOURCE = "remote";

  public static final String ITEM_LOCATION = "location";
  public static final String ITEM_FLOW = "flow";

  private final SonarLintExtendedLanguageClient client;
  private boolean firstSecretIssueDetected;

  private final IssuesCache issuesCache;
  private final HotspotsCache hotspotsCache;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final OpenNotebooksCache openNotebooksCache;

  private boolean focusOnNewCode;

  public DiagnosticPublisher(SonarLintExtendedLanguageClient client, TaintVulnerabilitiesCache taintVulnerabilitiesCache,
    IssuesCache issuesCache, HotspotsCache hotspotsCache, OpenNotebooksCache openNotebooksCache) {
    this.client = client;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.issuesCache = issuesCache;
    this.hotspotsCache = hotspotsCache;
    this.openNotebooksCache = openNotebooksCache;
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

  public void publishTaints() {
    taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().forEach((uri, taints) -> client.publishTaintVulnerabilities(createPublishTaintsParams(uri)));
  }

  public void publishHotspots(URI f) {
    client.publishSecurityHotspots(createPublishSecurityHotspotsParams(f));
  }

  Diagnostic issueDtoToDiagnostic(Map.Entry<String, DelegatingFinding> entry) {
    var issue = entry.getValue();
    return prepareDiagnostic(issue, entry.getKey(), false, focusOnNewCode);
  }

  public void setFocusOnNewCode(boolean focusOnNewCode) {
    this.focusOnNewCode = focusOnNewCode;
  }

  public static Diagnostic prepareDiagnostic(DelegatingFinding issue, String entryKey, boolean ignoreSecondaryLocations, boolean focusOnNewCode) {
    var diagnostic = new Diagnostic();

    setSeverity(diagnostic, issue, focusOnNewCode);
    var range = convert(issue);
    diagnostic.setRange(range);
    diagnostic.setCode(issue.getRuleKey());
    diagnostic.setMessage(message(issue, ignoreSecondaryLocations));
    setSource(diagnostic, issue);
    setData(diagnostic, issue, entryKey);

    return diagnostic;
  }

  static void setSeverity(Diagnostic diagnostic, DelegatingFinding issue, boolean focusOnNewCode) {
    if (focusOnNewCode) {
      var newCodeSeverity = issue.isOnNewCode() ? DiagnosticSeverity.Warning : DiagnosticSeverity.Hint;
      diagnostic.setSeverity(newCodeSeverity);
    } else {
      diagnostic.setSeverity(DiagnosticSeverity.Warning);
    }
  }

  public static class DiagnosticData {

    String entryKey;

    @Nullable
    String serverIssueKey;
    @Nullable
    HotspotStatus status;
    boolean isAiCodeFixable;

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
  }

  public static void setSource(Diagnostic diagnostic, DelegatingFinding issue) {
    if (issue instanceof DelegatingHotspot hotspotDto) {
      var isKnown = hotspotDto.getServerIssueKey() != null;
      var isHotspot = hotspotDto.getType() == RuleType.SECURITY_HOTSPOT;
      diagnostic.setSource(isKnown && isHotspot ? REMOTE_SOURCE : SONARLINT_SOURCE);
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
    data.setEntryKey(entryKey);
    diagnostic.setData(data);
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

    var taintDiagnostics = taintVulnerabilitiesCache.getAsDiagnostics(newUri, focusOnNewCode);

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
      .map(e -> prepareDiagnostic(e.getValue(), e.getKey(), false, focusOnNewCode))
      .sorted(DiagnosticPublisher.byLineNumber())
      .toList());
    p.setUri(newUri.toString());

    return p;
  }

  private static Comparator<? super Diagnostic> byLineNumber() {
    return Comparator.comparing((Diagnostic d) -> d.getRange().getStart().getLine())
      .thenComparing(Diagnostic::getMessage);
  }
}
