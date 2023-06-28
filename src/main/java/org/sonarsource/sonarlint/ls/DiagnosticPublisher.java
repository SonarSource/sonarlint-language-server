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
package org.sonarsource.sonarlint.ls;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.classic.HttpClient;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.ls.IssuesCache.VersionedIssue;
import org.sonarsource.sonarlint.ls.connected.DelegatingIssue;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.util.Utils;
import org.sonarsource.sonarlint.ls.watcher.IssueParams;
import org.sonarsource.sonarlint.ls.watcher.WebsocketClientEndpoint;

import static java.util.stream.Collectors.toList;
import static org.sonarsource.sonarlint.ls.util.Utils.buildMessageWithPluralizedSuffix;
import static org.sonarsource.sonarlint.ls.util.Utils.hotspotSeverity;
import static org.sonarsource.sonarlint.ls.util.Utils.severity;

public class DiagnosticPublisher {

  private Gson gson = new Gson();
  static final String SONARLINT_SOURCE = "sonarlint";
  static final String REMOTE_SOURCE = "remote";

  public static final String ITEM_LOCATION = "location";
  public static final String ITEM_FLOW = "flow";

  private final SonarLintExtendedLanguageClient client;
  private boolean firstSecretIssueDetected;
  private boolean firstCobolIssueDetected;

  private final IssuesCache issuesCache;
  private final IssuesCache hotspotsCache;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final OpenNotebooksCache openNotebooksCache;

  public DiagnosticPublisher(SonarLintExtendedLanguageClient client, TaintVulnerabilitiesCache taintVulnerabilitiesCache, IssuesCache issuesCache, IssuesCache hotspotsCache,
    OpenNotebooksCache openNotebooksCache) {
    this.client = client;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.issuesCache = issuesCache;
    this.hotspotsCache = hotspotsCache;
    this.openNotebooksCache = openNotebooksCache;
  }

  public void initialize(boolean firstSecretDetected, boolean firstCobolIssueDetected) {
    this.firstSecretIssueDetected = firstSecretDetected;
    this.firstCobolIssueDetected = firstCobolIssueDetected;
  }

  public void publishDiagnostics(URI f, boolean onlyHotspots) {
    if (openNotebooksCache.isNotebook(f)) {
      return;
    }
    if (!onlyHotspots) {
      var diagnostics = createPublishDiagnosticsParams(f);
      client.publishDiagnostics(diagnostics);

      if (!diagnostics.getDiagnostics().isEmpty()) {
        try {
          processIssue(new IssueParams(diagnostics.getDiagnostics().get(0).getMessage()));
        } catch (URISyntaxException e) {
          System.out.println("bugbug");
        } catch (InterruptedException e) {
          System.out.println("bugbug");
        }
      }
    }
    client.publishSecurityHotspots(createPublishSecurityHotspotsParams(f));
  }

  public void processIssue(IssueParams message) throws URISyntaxException, InterruptedException {
    var client = java.net.http.HttpClient.newHttpClient();

    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:8080/issues"))
      .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(message)))
      .build();

    try {
      client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      System.out.println("grosbug");
    }
  }

  static Diagnostic convert(Map.Entry<String, VersionedIssue> entry) {
    var issue = entry.getValue().getIssue();
    var severity =
      issue.getType() == RuleType.SECURITY_HOTSPOT ?
        hotspotSeverity(issue.getVulnerabilityProbability().orElse(VulnerabilityProbability.MEDIUM)) : severity(issue.getSeverity());

    return prepareDiagnostic(severity, issue, entry.getKey(), false);
  }

  public static Diagnostic prepareDiagnostic(DiagnosticSeverity severity, Issue issue, String entryKey, boolean ignoreSecondaryLocations) {
    var diagnostic = new Diagnostic();

    diagnostic.setSeverity(severity);
    var range = Utils.convert(issue);
    diagnostic.setRange(range);
    diagnostic.setCode(issue.getRuleKey());
    diagnostic.setMessage(message(issue, ignoreSecondaryLocations));
    setSource(issue, diagnostic);
    diagnostic.setData(getData(issue, entryKey));

    return diagnostic;
  }

  public static class DiagnosticData {
    String entryKey;
    @Nullable
    String serverIssueKey;
    @Nullable
    HotspotReviewStatus status;

    public void setEntryKey(String entryKey) {
      this.entryKey = entryKey;
    }

    public void setServerIssueKey(@Nullable String serverIssueKey) {
      this.serverIssueKey = serverIssueKey;
    }

    public void setStatus(@Nullable HotspotReviewStatus status) {
      this.status = status;
    }

    public String getEntryKey() {
      return entryKey;
    }
  }

  private static DiagnosticData getData(Issue issue, String entryKey) {
    var data = new DiagnosticData();
    if (issue instanceof DelegatingIssue && issue.getType() == RuleType.SECURITY_HOTSPOT) {
      var delegatedIssue = (DelegatingIssue) issue;
      data.setStatus(delegatedIssue.getReviewStatus());
      data.setServerIssueKey(delegatedIssue.getServerIssueKey());
    }
    data.setEntryKey(entryKey);
    return data;
  }

  public static void setSource(Issue issue, Diagnostic diagnostic) {
    if (issue instanceof DelegatingIssue) {
      var delegatedIssue = (DelegatingIssue) issue;
      var isKnown = delegatedIssue.getServerIssueKey() != null;
      var isHotspot = delegatedIssue.getType() == RuleType.SECURITY_HOTSPOT;
      diagnostic.setSource(isKnown && isHotspot ? REMOTE_SOURCE : SONARLINT_SOURCE);
    } else {
      diagnostic.setSource(SONARLINT_SOURCE);
    }
  }

  public static String message(Issue issue, boolean ignoreSecondaryLocations) {
    if (issue.flows().isEmpty() || ignoreSecondaryLocations) {
      return issue.getMessage();
    } else if (issue.flows().size() == 1) {
      return buildMessageWithPluralizedSuffix(issue.getMessage(), issue.flows().get(0).locations().size(), ITEM_LOCATION);
    } else if (issue.flows().stream().allMatch(f -> f.locations().size() == 1)) {
      int nbLocations = issue.flows().size();
      return buildMessageWithPluralizedSuffix(issue.getMessage(), nbLocations, ITEM_LOCATION);
    } else {
      int nbFlows = issue.flows().size();
      return buildMessageWithPluralizedSuffix(issue.getMessage(), nbFlows, ITEM_FLOW);
    }
  }

  private PublishDiagnosticsParams createPublishDiagnosticsParams(URI newUri) {
    var p = new PublishDiagnosticsParams();

    Map<String, VersionedIssue> localIssues = issuesCache.get(newUri);

    if (!firstSecretIssueDetected && localIssues.values().stream().anyMatch(v -> v.getIssue().getRuleKey().startsWith(Language.SECRETS.getLanguageKey()))) {
      client.showFirstSecretDetectionNotification();
      firstSecretIssueDetected = true;
    }

    if (!firstCobolIssueDetected && localIssues.values().stream().anyMatch(v -> v.getIssue().getRuleKey().startsWith(Language.COBOL.getLanguageKey()))) {
      client.showFirstCobolIssueDetectedNotification();
      firstCobolIssueDetected = true;
    }

    var localDiagnostics = localIssues.entrySet()
      .stream()
      .map(DiagnosticPublisher::convert);
    var taintDiagnostics = taintVulnerabilitiesCache.getAsDiagnostics(newUri);

    var diagnosticList = Stream.concat(localDiagnostics, taintDiagnostics)
      .sorted(DiagnosticPublisher.byLineNumber())
      .collect(toList());
    p.setDiagnostics(diagnosticList);
    p.setUri(newUri.toString());

    return p;
  }

  private PublishDiagnosticsParams createPublishSecurityHotspotsParams(URI newUri) {
    var p = new PublishDiagnosticsParams();

    p.setDiagnostics(hotspotsCache.get(newUri).entrySet()
      .stream()
      .map(DiagnosticPublisher::convert)
      .sorted(DiagnosticPublisher.byLineNumber())
      .collect(toList()));
    p.setUri(newUri.toString());

    return p;
  }

  private static Comparator<? super Diagnostic> byLineNumber() {
    return Comparator.comparing((Diagnostic d) -> d.getRange().getStart().getLine())
      .thenComparing(Diagnostic::getMessage);
  }
}
