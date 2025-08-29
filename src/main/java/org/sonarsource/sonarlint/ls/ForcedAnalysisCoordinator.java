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
import java.util.List;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.notebooks.VersionedOpenNotebook;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettingsChangeListener;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

import static java.util.stream.Collectors.groupingBy;

public class ForcedAnalysisCoordinator implements WorkspaceSettingsChangeListener, WorkspaceFolderSettingsChangeListener {

  public static final String ITEM_LOCATION = "location";
  public static final String ITEM_FLOW = "flow";

  private final OpenFilesCache openFilesCache;
  private final OpenNotebooksCache openNotebooksCache;

  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ProjectBindingManager bindingManager;
  private final SonarLintExtendedLanguageClient client;
  private final BackendServiceFacade backendServiceFacade;
  private final SettingsManager settingsManager;

  public ForcedAnalysisCoordinator(WorkspaceFoldersManager workspaceFoldersManager, ProjectBindingManager bindingManager, OpenFilesCache openFilesCache,
    OpenNotebooksCache openNotebooksCache, SonarLintExtendedLanguageClient client, BackendServiceFacade backendServiceFacade, SettingsManager settingsManager) {
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.bindingManager = bindingManager;
    this.openFilesCache = openFilesCache;
    this.openNotebooksCache = openNotebooksCache;
    this.backendServiceFacade = backendServiceFacade;
    this.client = client;
    this.settingsManager = settingsManager;
  }

  public void analyzeAllOpenFilesInFolder(@Nullable WorkspaceFolderWrapper folder) {
    var openedFileUrisInFolder = openFilesCache.getAll().stream()
      .filter(f -> belongToFolder(folder, f.getUri()))
      .toList();
    analyseNotIgnoredFiles(openedFileUrisInFolder);
  }

  private void analyseNotIgnoredFiles(List<VersionedOpenFile> files) {
    if (!settingsManager.getCurrentSettings().isAutomaticAnalysis()) {
      return;
    }
    var uriStrings = files.stream().map(it -> it.getUri().toString()).toList();
    var fileUrisParams = new SonarLintExtendedLanguageClient.FileUrisParams(uriStrings);
    client.filterOutExcludedFiles(fileUrisParams)
      .thenAccept(notIgnoredFileUris -> {
        var notIgnoredFiles = files
          .stream().filter(it -> notIgnoredFileUris.getFileUris().contains(it.getUri().toString()))
          .toList();
        var filesByMaybeFolderUri = notIgnoredFiles.stream().collect(groupingBy(f -> workspaceFoldersManager.findFolderForFile(f.getUri())));
        for (var entry : filesByMaybeFolderUri.entrySet()) {
          var maybeFolderUri = entry.getKey();
          var filesToAnalyse = entry.getValue().stream().map(VersionedOpenFile::getUri).toList();
          if (maybeFolderUri.isPresent()) {
            var folderUri = maybeFolderUri.get().getUri();
            backendServiceFacade.getBackendService().analyzeFilesList(folderUri.toString(), filesToAnalyse);
          }
        }
      });
  }

  private boolean belongToFolder(WorkspaceFolderWrapper folder, URI fileUri) {
    var actualFolder = workspaceFoldersManager.findFolderForFile(fileUri);
    return (actualFolder.map(f -> f.equals(folder)).orElse(folder == null));
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    if (oldValue == null) {
      // This is when settings are loaded, not really a user change
      return;
    }
    if (!Objects.equals(oldValue.getExcludedRules(), newValue.getExcludedRules()) ||
      !Objects.equals(oldValue.getIncludedRules(), newValue.getIncludedRules()) ||
      !Objects.equals(oldValue.getRuleParameters(), newValue.getRuleParameters())) {
      analyzeAllUnboundOpenFiles();
      analyzeAllOpenNotebooks();
    }
  }

  @Override
  public void onChange(@Nullable WorkspaceFolderWrapper folder, @Nullable WorkspaceFolderSettings oldValue, WorkspaceFolderSettings newValue) {
    if (oldValue == null) {
      // This is when settings are loaded, not really a user change
      return;
    }
    if (!Objects.equals(oldValue.getPathToCompileCommands(), newValue.getPathToCompileCommands())) {
      analyzeAllOpenCOrCppFilesInFolder(folder);
    }
  }

  public void analyzeAllOpenCOrCppFilesInFolder(@Nullable WorkspaceFolderWrapper folder) {
    var openedCorCppFileUrisInFolder = openFilesCache.getAll().stream()
      .filter(VersionedOpenFile::isCOrCpp)
      .filter(f -> belongToFolder(folder, f.getUri()))
      .toList();
    analyseNotIgnoredFiles(openedCorCppFileUrisInFolder);
  }

  public void analyzeAllUnboundOpenFiles() {
    var openedUnboundFileUris = openFilesCache.getAll().stream()
      .filter(f -> bindingManager.getBinding(f.getUri()).isEmpty())
      .toList();
    analyseNotIgnoredFiles(openedUnboundFileUris);
  }

  private void analyzeAllOpenNotebooks() {
    var openNotebookUris = openNotebooksCache.getAll().stream()
      .map(VersionedOpenNotebook::asVersionedOpenFile)
      .toList();
    analyseNotIgnoredFiles(openNotebookUris);
  }

  private void analyzeAllOpenJavaFiles() {
    var openedJavaFileUris = openFilesCache.getAll().stream()
      .filter(VersionedOpenFile::isJava)
      .toList();
    analyseNotIgnoredFiles(openedJavaFileUris);
  }

  public void didClasspathUpdate() {
    analyzeAllOpenJavaFiles();
  }

  public void didServerModeChange(SonarLintExtendedLanguageServer.ServerMode serverMode) {
    if (serverMode == SonarLintExtendedLanguageServer.ServerMode.STANDARD) {
      analyzeAllOpenJavaFiles();
    }
  }
}
