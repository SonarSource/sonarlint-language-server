/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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
import java.util.stream.Stream;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.ls.IssuesCache.VersionedIssue;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.util.stream.Collectors.toList;
import static org.sonarsource.sonarlint.ls.util.Utils.buildMessageWithPluralizedSuffix;
import static org.sonarsource.sonarlint.ls.util.Utils.severity;

public class DiagnosticPublisher {

  static final String SONARLINT_SOURCE = "sonarlint";

  public static final String ITEM_LOCATION = "location";
  public static final String ITEM_FLOW = "flow";

  private final SonarLintExtendedLanguageClient client;
  private boolean firstSecretIssueDetected;

  private final IssuesCache issuesCache;
  private SerialPortNotifier serialPortNotifier;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;

  public DiagnosticPublisher(SonarLintExtendedLanguageClient client, TaintVulnerabilitiesCache taintVulnerabilitiesCache, IssuesCache issuesCache, SerialPortNotifier serialPortNotifier) {
    this.client = client;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.issuesCache = issuesCache;
    this.serialPortNotifier = serialPortNotifier;
  }

  public void initialize(boolean firstSecretDetected) {
    this.firstSecretIssueDetected = firstSecretDetected;
  }

  public void publishDiagnostics(URI f) {
    var diagnosticsParams = createPublishDiagnosticsParams(f);
    if (diagnosticsParams.getDiagnostics().isEmpty()) {
      serialPortNotifier.send("0");
    } else {
      serialPortNotifier.send("1");
    }
    client.publishDiagnostics(diagnosticsParams);
  }

  static Diagnostic convert(Map.Entry<String, VersionedIssue> entry) {
    var issue = entry.getValue().getIssue();
    var diagnostic = new Diagnostic();
    var severity = severity(issue.getSeverity());

    diagnostic.setSeverity(severity);
    var range = Utils.convert(issue);
    diagnostic.setRange(range);
    diagnostic.setCode(issue.getRuleKey());
    diagnostic.setMessage(message(issue));
    diagnostic.setSource(SONARLINT_SOURCE);
    diagnostic.setData(entry.getKey());

    return diagnostic;
  }

  static String message(Issue issue) {
    if (issue.flows().isEmpty()) {
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

    if (!firstSecretIssueDetected && localIssues.values().stream().anyMatch(v -> v.getIssue().getRuleKey().startsWith(Language.SECRETS.getPluginKey()))) {
      client.showFirstSecretDetectionNotification();
      firstSecretIssueDetected = true;
    }

    var localDiagnostics = localIssues.entrySet()
      .stream()
      .map(DiagnosticPublisher::convert);
    var taintDiagnostics = taintVulnerabilitiesCache.getAsDiagnostics(newUri);

    p.setDiagnostics(Stream.concat(localDiagnostics, taintDiagnostics)
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
