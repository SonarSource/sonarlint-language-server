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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.ForcedAnalysisCoordinator;
import org.sonarsource.sonarlint.ls.domain.TaintIssue;
import org.sonarsource.sonarlint.ls.util.TextRangeUtils;

import static java.util.Collections.emptyList;
import static org.sonarsource.sonarlint.ls.util.Utils.buildMessageWithPluralizedSuffix;

public class TaintVulnerabilitiesCache {

  private final Map<URI, List<TaintIssue>> taintVulnerabilitiesPerFile = new ConcurrentHashMap<>();

  public void clear(URI fileUri) {
    taintVulnerabilitiesPerFile.remove(fileUri);
  }

  public Optional<TaintIssue> getTaintVulnerabilityByKey(String issueId) {
    return taintVulnerabilitiesPerFile.values().stream()
      .flatMap(List::stream)
      .filter(i -> issueId.equals(i.getSonarServerKey()))
      .findFirst();
  }

  public Stream<Diagnostic> getAsDiagnostics(URI fileUri) {
    return taintVulnerabilitiesPerFile.getOrDefault(fileUri, emptyList())
      .stream()
      .flatMap(i -> TaintVulnerabilitiesCache.convert(i).stream());
  }

  static Optional<Diagnostic> convert(TaintIssue issue) {
    if (issue.getTextRange() != null) {
      var range = TextRangeUtils.convert(issue.getTextRange());
      var diagnostic = new Diagnostic();
      boolean onNewCode = issue.isOnNewCode();

      diagnostic.setSeverity(DiagnosticSeverity.Error);
      diagnostic.setRange(range);
      diagnostic.setCode(issue.getRuleKey());
      diagnostic.setMessage(message(issue));
      diagnostic.setSource(issue.getSource());

      var diagnosticData = new DiagnosticPublisher.DiagnosticData();
      diagnosticData.setEntryKey(issue.getId().toString());
      diagnosticData.setServerIssueKey(issue.getSonarServerKey());
      diagnosticData.setAiCodeFixable(issue.isAiCodeFixable());
      diagnosticData.setImpactSeverity(DiagnosticPublisher.getImpactSeverity(issue.getSeverityMode()));
      diagnosticData.setOnNewCode(onNewCode);
      diagnosticData.setHasQuickFix(false);
      diagnostic.setData(diagnosticData);

      return Optional.of(diagnostic);
    }
    return Optional.empty();
  }

  static String message(TaintVulnerabilityDto issue) {
    if (issue.getFlows().isEmpty()) {
      return issue.getMessage();
    } else if (issue.getFlows().size() == 1) {
      return buildMessageWithPluralizedSuffix(issue.getMessage(), issue.getFlows().get(0).getLocations().size(), ForcedAnalysisCoordinator.ITEM_LOCATION);
    } else {
      return buildMessageWithPluralizedSuffix(issue.getMessage(), issue.getFlows().size(), ForcedAnalysisCoordinator.ITEM_FLOW);
    }
  }

  public void reload(URI fileUri, List<TaintIssue> taintIssues) {
    taintVulnerabilitiesPerFile.put(fileUri, taintIssues);
  }

  public void add(URI fileUri, TaintIssue taintIssue) {
    taintVulnerabilitiesPerFile.get(fileUri).add(taintIssue);
  }

  public void removeTaintIssue(String fileUriStr, String key) {
    var fileUri = URI.create(fileUriStr);
    var issues = taintVulnerabilitiesPerFile.get(fileUri);
    if (issues != null) {
      var issueToRemove = issues.stream().filter(taintIssue -> taintIssue.getSonarServerKey().equals(key) || taintIssue.getId().toString().equals(key)).findFirst();
      issueToRemove.ifPresent(issues::remove);
    }
  }

  public Map<URI, List<TaintIssue>> getTaintVulnerabilitiesPerFile() {
    return taintVulnerabilitiesPerFile;
  }
}
