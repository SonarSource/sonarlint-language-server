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

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;
import org.sonarsource.sonarlint.ls.connected.domain.TaintIssue;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.util.Collections.emptyList;
import static org.sonarsource.sonarlint.ls.util.Utils.buildMessageWithPluralizedSuffix;

public class TaintVulnerabilitiesCache {

  private final Map<URI, List<TaintIssue>> taintVulnerabilitiesPerFile = new ConcurrentHashMap<>();

  public void didClose(URI fileUri) {
    clear(fileUri);
  }

  public void clear(URI fileUri) {
    taintVulnerabilitiesPerFile.remove(fileUri);
  }

  public Optional<TaintIssue> getTaintVulnerabilityForDiagnostic(URI fileUri, Diagnostic d) {
    return taintVulnerabilitiesPerFile.getOrDefault(fileUri, Collections.emptyList())
      .stream()
      .filter(i -> hasSameKey(d, i) || hasSameRuleKeyAndLocation(d, i))
      .findFirst();
  }

  private static boolean hasSameKey(Diagnostic d, TaintIssue i) {
    return d.getData() != null && d.getData().equals(i.getKey());
  }

  private static boolean hasSameRuleKeyAndLocation(Diagnostic d, TaintIssue i) {
    return i.getRuleKey().equals(d.getCode().getLeft()) && Utils.locationMatches(i, d);
  }

  public Optional<TaintIssue> getTaintVulnerabilityByKey(String issueId) {
    return taintVulnerabilitiesPerFile.values().stream()
      .flatMap(List::stream)
      .filter(i -> issueId.equals(i.getKey()))
      .findFirst();
  }

  public Stream<Diagnostic> getAsDiagnostics(URI fileUri, boolean focusOnNewCode) {
    return taintVulnerabilitiesPerFile.getOrDefault(fileUri, emptyList())
      .stream()
      .flatMap(i -> TaintVulnerabilitiesCache.convert(i, focusOnNewCode).stream());
  }

  static Optional<Diagnostic> convert(TaintIssue issue, boolean focusOnNewCode) {
    if (issue.getTextRange() != null) {
      var range = Utils.convert(issue);
      var diagnostic = new Diagnostic();
      var severity = focusOnNewCode && !issue.isOnNewCode() ? DiagnosticSeverity.Hint : DiagnosticSeverity.Warning;

      diagnostic.setSeverity(severity);
      diagnostic.setRange(range);
      diagnostic.setCode(issue.getRuleKey());
      diagnostic.setMessage(message(issue));
      diagnostic.setSource(issue.getSource());
      diagnostic.setData(issue.getKey());

      return Optional.of(diagnostic);
    }
    return Optional.empty();
  }

  static String message(TaintIssue issue) {
    if (issue.getFlows().isEmpty()) {
      return issue.getMessage();
    } else if (issue.getFlows().size() == 1) {
      return buildMessageWithPluralizedSuffix(issue.getMessage(), issue.getFlows().get(0).locations().size(), AnalysisScheduler.ITEM_LOCATION);
    } else {
      return buildMessageWithPluralizedSuffix(issue.getMessage(), issue.getFlows().size(), AnalysisScheduler.ITEM_FLOW);
    }
  }

  public void reload(URI fileUri, List<TaintIssue> taintIssues) {
    taintVulnerabilitiesPerFile.put(fileUri, taintIssues);
  }

  public void removeTaintIssue(String fileUriStr, String key) {
    var fileUri = URI.create(fileUriStr);
    var issues = taintVulnerabilitiesPerFile.get(fileUri);
    if (issues != null) {
      var issueToRemove = issues.stream().filter(taintIssue -> taintIssue.getKey().equals(key)).findFirst();
      issueToRemove.ifPresent(issues::remove);
    }
  }

  public Set<URI> getAllFilesWithTaintIssues(){
    return taintVulnerabilitiesPerFile.keySet();
  }

  public Map<URI, List<TaintIssue>> getTaintVulnerabilitiesPerFile() {
    return taintVulnerabilitiesPerFile;
  }
}
