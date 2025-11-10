/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.GetJavaConfigResponse;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.notebooks.NotebookDiagnosticPublisher;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;

import static java.lang.String.format;
import static java.util.function.Function.identity;
import static org.sonarsource.sonarlint.ls.backend.BackendService.ROOT_CONFIGURATION_SCOPE;

public class AnalysisHelper {
  private final SonarLintExtendedLanguageClient client;
  private final LanguageClientLogger clientLogger;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final JavaConfigCache javaConfigCache;
  private final SettingsManager settingsManager;
  private final IssuesCache issuesCache;
  private final HotspotsCache securityHotspotsCache;
  private final DiagnosticPublisher diagnosticPublisher;
  private final OpenNotebooksCache openNotebooksCache;
  private final NotebookDiagnosticPublisher notebookDiagnosticPublisher;
  private final OpenFilesCache openFilesCache;

  public AnalysisHelper(SonarLintExtendedLanguageClient client, LanguageClientLogger clientLogger,
    WorkspaceFoldersManager workspaceFoldersManager, JavaConfigCache javaConfigCache, SettingsManager settingsManager,
    IssuesCache issuesCache, HotspotsCache securityHotspotsCache, DiagnosticPublisher diagnosticPublisher,
    OpenNotebooksCache openNotebooksCache, NotebookDiagnosticPublisher notebookDiagnosticPublisher,
    OpenFilesCache openFilesCache) {
    this.client = client;
    this.clientLogger = clientLogger;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.javaConfigCache = javaConfigCache;
    this.settingsManager = settingsManager;
    this.issuesCache = issuesCache;
    this.securityHotspotsCache = securityHotspotsCache;
    this.diagnosticPublisher = diagnosticPublisher;
    this.openNotebooksCache = openNotebooksCache;
    this.notebookDiagnosticPublisher = notebookDiagnosticPublisher;
    this.openFilesCache = openFilesCache;
  }

  private void clearIssueCacheAndPublishEmptyDiagnostics(URI f) {
    issuesCache.clear(f);
    securityHotspotsCache.clear(f);
    diagnosticPublisher.publishDiagnostics(f, false);
  }

  private Map<URI, GetJavaConfigResponse> collectJavaFilesWithConfig(Map<URI, VersionedOpenFile> javaFiles) {
    Map<URI, GetJavaConfigResponse> javaFilesWithConfig = new HashMap<>();
    javaFiles.forEach((uri, openFile) -> {
      var javaConfigOpt = javaConfigCache.getOrFetch(uri);
      if (javaConfigOpt.isEmpty()) {
        clientLogger.debug(format("Analysis of Java file \"%s\" may not show all issues because SonarLint" +
          " was unable to query project configuration (classpath, source level, ...)", uri));
        clearIssueCacheAndPublishEmptyDiagnostics(uri);
      } else {
        javaFilesWithConfig.put(uri, javaConfigOpt.get());
      }
    });
    return javaFilesWithConfig;
  }

  public void handleIssues(Map<URI, List<RaisedFindingDto>> issuesByFileUri) {
    issuesCache.reportIssues(issuesByFileUri);
    issuesByFileUri.forEach((uri, issues) -> {
      diagnosticPublisher.publishDiagnostics(uri, true);
      openNotebooksCache.getFile(uri).ifPresent(notebook -> {
        // clean up old diagnostics
        notebookDiagnosticPublisher.cleanupCellsList(uri);
        notebookDiagnosticPublisher.cleanupDiagnosticsForCellsWithoutIssues(uri);
        notebookDiagnosticPublisher.publishNotebookDiagnostics(uri, notebook);
      });
    });
  }

  public void handleHotspots(Map<URI, List<RaisedHotspotDto>> hotspotsByFileUri) {
    securityHotspotsCache.reportHotspots(hotspotsByFileUri);
    hotspotsByFileUri.forEach((uri, issues) -> {
      diagnosticPublisher.publishHotspots(uri);
      notebookDiagnosticPublisher.cleanupDiagnosticsForCellsWithoutIssues(uri);
      openNotebooksCache.getFile(uri).ifPresent(notebook -> notebookDiagnosticPublisher.publishNotebookDiagnostics(uri, notebook));
    });
  }

  public Map<String, String> getInferredAnalysisProperties(String configurationScopeId, List<URI> filesToAnalyzeUris) {
    // Need to analyze files outside any workspace folder as well
    var workspaceFolder = configurationScopeId.equals(ROOT_CONFIGURATION_SCOPE) ?
      Optional.empty() : workspaceFoldersManager.getFolder(URI.create(configurationScopeId));
    var settings = workspaceFolder.map(f -> ((WorkspaceFolderWrapper) f).getSettings())
      .orElseGet(() -> CompletableFutures.computeAsync(c -> settingsManager.getCurrentDefaultFolderSettings()).join());

    var extraProperties = new HashMap<>(settings.getAnalyzerProperties());

    populateJavaProperties(filesToAnalyzeUris, extraProperties);

    populateCfamilyProperties(filesToAnalyzeUris, extraProperties, settings);

    return extraProperties;
  }

  private void populateJavaProperties(List<URI> filesToAnalyzeUris, Map<String, String> extraProperties) {
    var javaFiles = filesToAnalyzeUris.stream().map(openFilesCache::getFile).filter(Optional::isPresent).map(Optional::get)
      .filter(VersionedOpenFile::isJava).collect(Collectors.toMap(VersionedOpenFile::getUri, identity()));

    var javaConfigs = collectJavaFilesWithConfig(javaFiles);

    extraProperties.putAll(javaConfigCache.configureJavaProperties(filesToAnalyzeUris, javaConfigs));
  }

  private void populateCfamilyProperties(List<URI> filesToAnalyzeUris, Map<String,
    String> extraProperties,
    WorkspaceFolderSettings settings) {
    var cOrCppFiles = filesToAnalyzeUris.stream().map(openFilesCache::getFile).filter(Optional::isPresent).map(Optional::get)
      .filter(VersionedOpenFile::isCOrCpp).collect(Collectors.toMap(VersionedOpenFile::getUri, identity()));

    var pathToCompileCommands = settings.getPathToCompileCommands();
    if (!cOrCppFiles.isEmpty() && (pathToCompileCommands == null || !Files.isRegularFile(Paths.get(pathToCompileCommands)))) {
      client.needCompilationDatabase();
    }
    if (pathToCompileCommands != null) {
      extraProperties.put("sonar.cfamily.compile-commands", pathToCompileCommands);
    }
  }
}
