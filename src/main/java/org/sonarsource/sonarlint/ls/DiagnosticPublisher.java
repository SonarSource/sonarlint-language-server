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
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.ls.IssuesCache.VersionnedIssue;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static java.util.stream.Collectors.toList;
import static org.sonarsource.sonarlint.ls.Utils.buildMessageWithPluralizedSuffix;
import static org.sonarsource.sonarlint.ls.Utils.severity;

public class DiagnosticPublisher {

  public static final String TYPESCRIPT_PATH_PROP = "sonar.typescript.internal.typescriptLocation";
  static final String SONARLINT_SOURCE = "sonarlint";
  public static final String SONARQUBE_TAINT_SOURCE = "SonarQube Taint Analyzer";

  public static final String ITEM_LOCATION = "location";
  public static final String ITEM_FLOW = "flow";

  private final SonarLintExtendedLanguageClient client;
  private boolean firstSecretIssueDetected;

  private final IssuesCache issuesCache;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final LanguageClientLogger lsLogOutput;

  public DiagnosticPublisher(LanguageClientLogger lsLogOutput, SonarLintExtendedLanguageClient client, TaintVulnerabilitiesCache taintVulnerabilitiesCache,
    IssuesCache issuesCache) {
    this.lsLogOutput = lsLogOutput;
    this.client = client;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.issuesCache = issuesCache;
  }

  public void initialize(boolean firstSecretDetected) {
    this.firstSecretIssueDetected = firstSecretDetected;
  }

  public void didClose(URI fileUri) {
    lsLogOutput.debug("File '" + fileUri + "' closed. Cleaning diagnostics.");
    issuesCache.clear(fileUri);
    publishDiagnostics(fileUri);
  }

  public void publishDiagnostics(URI f) {
    client.publishDiagnostics(createPublishDiagnosticsParams(f));
  }

  static Optional<Diagnostic> convert(Map.Entry<String, VersionnedIssue> entry) {
    var issue = entry.getValue().getIssue();
    if (issue.getStartLine() != null) {
      var range = Utils.convert(issue);
      var diagnostic = new Diagnostic();
      var severity = severity(issue.getSeverity());

      diagnostic.setSeverity(severity);
      diagnostic.setRange(range);
      diagnostic.setCode(issue.getRuleKey());
      diagnostic.setMessage(message(issue));
      diagnostic.setSource(SONARLINT_SOURCE);
      diagnostic.setData(entry.getKey());

      return Optional.of(diagnostic);
    }
    return Optional.empty();
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

    Map<String, VersionnedIssue> localIssues = issuesCache.get(newUri);

    if (!firstSecretIssueDetected && localIssues.values().stream().anyMatch(v -> v.getIssue().getRuleKey().startsWith(Language.SECRETS.getPluginKey()))) {
      client.showFirstSecretDetectionNotification();
      firstSecretIssueDetected = true;
    }

    var localDiagnostics = localIssues.entrySet()
      .stream()
      .flatMap(i -> DiagnosticPublisher.convert(i).stream());
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
