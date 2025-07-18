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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.domain.DependencyRisk;

import static java.util.Collections.emptyList;

public class DependencyRisksCache {

  private final Map<URI, List<DependencyRisk>> dependencyRisksPerConfigScope = new ConcurrentHashMap<>();

  public void clear(URI fileUri) {
    dependencyRisksPerConfigScope.remove(fileUri);
  }

  public Optional<DependencyRisk> getDependencyRiskById(String issueId) {
    return dependencyRisksPerConfigScope.values().stream()
      .flatMap(List::stream)
      .filter(i -> issueId.equals(i.getId().toString()))
      .findFirst();
  }

  public Stream<Diagnostic> getAsDiagnostics(URI folderUri) {
    return dependencyRisksPerConfigScope.getOrDefault(folderUri, emptyList())
      .stream()
      .filter(i -> i.getStatus().equals(DependencyRiskDto.Status.OPEN))
      .flatMap(i -> DependencyRisksCache.convert(i).stream());
  }

  static Optional<Diagnostic> convert(DependencyRisk issue) {
    var diagnostic = new Diagnostic();
    var onNewCode = true;

    diagnostic.setSeverity(DiagnosticSeverity.Warning);
    diagnostic.setCode(issue.getType().toString());
    diagnostic.setMessage(message(issue));
    diagnostic.setSource(issue.getSource());

    var diagnosticData = new DiagnosticPublisher.DiagnosticData();
    diagnosticData.setEntryKey(issue.getId().toString());
    diagnosticData.setServerIssueKey(issue.getId().toString());
    diagnosticData.setImpactSeverity(issue.getSeverity().ordinal());
    diagnosticData.setOnNewCode(onNewCode);
    diagnosticData.setHasQuickFix(false);
    diagnostic.setData(diagnosticData);

    return Optional.of(diagnostic);
  }

  static String message(DependencyRiskDto issue) {
    return issue.getPackageName().concat(" ").concat(issue.getPackageVersion());
  }

  public void reload(URI folderUri, List<DependencyRisk> dependencyRisks) {
    dependencyRisksPerConfigScope.put(folderUri, dependencyRisks);
  }

  public void add(URI folderUri, DependencyRisk dependencyRisk) {
    dependencyRisksPerConfigScope.get(folderUri).add(dependencyRisk);
  }

  public void removeDependencyRisks(String folderUriStr, String key) {
    var folderUri = URI.create(folderUriStr);
    var issues = dependencyRisksPerConfigScope.get(folderUri);
    if (issues != null) {
      var issueToRemove = issues.stream().filter(dependencyRisk -> dependencyRisk.getId().toString().equals(key)).findFirst();
      issueToRemove.ifPresent(issues::remove);
    }
  }

  public Map<URI, List<DependencyRisk>> getDependencyRisksPerConfigScope() {
    return dependencyRisksPerConfigScope;
  }
}
