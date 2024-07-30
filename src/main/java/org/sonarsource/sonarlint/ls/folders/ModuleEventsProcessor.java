/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.ls.AnalysisClientInputFile;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.util.Utils;

import static org.sonarsource.sonarlint.ls.backend.BackendServiceFacade.ROOT_CONFIGURATION_SCOPE;

public class ModuleEventsProcessor {

  private final FileTypeClassifier fileTypeClassifier;
  private final JavaConfigCache javaConfigCache;
  private final BackendServiceFacade backendServiceFacade;

  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ExecutorService asyncExecutor;

  private final SettingsManager settingsManager;

  public ModuleEventsProcessor(WorkspaceFoldersManager workspaceFoldersManager,
    FileTypeClassifier fileTypeClassifier, JavaConfigCache javaConfigCache, BackendServiceFacade backendServiceFacade, SettingsManager settingsManager) {
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.fileTypeClassifier = fileTypeClassifier;
    this.javaConfigCache = javaConfigCache;
    this.backendServiceFacade = backendServiceFacade;
    this.settingsManager = settingsManager;
    this.asyncExecutor = Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint Language Server Module Events Processor", false));
  }

  public void didChangeWatchedFiles(List<FileEvent> changes) {
    notifyBackend(changes);
  }

  private void notifyBackend(List<FileEvent> changes) {
    List<URI> deletedFileUris = new ArrayList<>();
    List<ClientFileDto> addedOrChangedFiles = new ArrayList<>();
    changes.forEach(event -> {
      var fileUri = URI.create(event.getUri());
      if (event.getType() == FileChangeType.Deleted) {
        deletedFileUris.add(fileUri);
      } else {
        workspaceFoldersManager.findFolderForFile(fileUri)
          .ifPresent(folder -> {
            var settings = folder.getSettings();
            var baseDir = folder.getRootPath();
            var fsPath = Paths.get(fileUri);
            var relativePath = baseDir.relativize(fsPath);
            var folderUri = folder.getUri().toString();
            var isTest = isTestFile(fileUri, settings);
            addedOrChangedFiles.add(new ClientFileDto(fileUri, relativePath, folderUri, isTest, StandardCharsets.UTF_8.name(), fsPath, null, null, true));
          });
      }
    });
    backendServiceFacade.getBackendService().updateFileSystem(deletedFileUris, addedOrChangedFiles);
  }

  public void notifyBackendWithFileLanguageAndContent(VersionedOpenFile file) {
    List<ClientFileDto> filesToNotify = new ArrayList<>();
    var fileUri = file.getUri();
    var fsPath = Paths.get(fileUri);
    SonarLanguage sqLanguage = AnalysisClientInputFile.toSqLanguage(file.getLanguageId().toLowerCase(Locale.ROOT));
    workspaceFoldersManager.findFolderForFile(fileUri)
      .ifPresentOrElse(folder -> {
        var settings = folder.getSettings();
        var baseDir = folder.getRootPath();
        var relativePath = baseDir.relativize(fsPath);
        var folderUri = folder.getUri().toString();
        var isTest = isTestFile(file, settings);
        filesToNotify.add(new ClientFileDto(fileUri, relativePath, folderUri, isTest, StandardCharsets.UTF_8.name(),
          fsPath, file.getContent(), sqLanguage != null ? Language.valueOf(sqLanguage.name()) : null, true));
      }, () -> {
        var isTest = isTestFile(file, settingsManager.getCurrentDefaultFolderSettings());
        filesToNotify.add(new ClientFileDto(fileUri, fsPath, ROOT_CONFIGURATION_SCOPE, isTest, StandardCharsets.UTF_8.name(),
          fsPath, file.getContent(), sqLanguage != null ? Language.valueOf(sqLanguage.name()) : null, true));
      });
    backendServiceFacade.getBackendService().updateFileSystem(List.of(), filesToNotify);
  }

  private boolean isTestFile(URI fileUri, WorkspaceFolderSettings settings) {
    return fileTypeClassifier.isTest(settings, fileUri, false, () -> javaConfigCache.getOrFetch(fileUri));
  }

  private boolean isTestFile(VersionedOpenFile file, WorkspaceFolderSettings settings) {
    return fileTypeClassifier.isTest(settings, file.getUri(), file.isJava(), () -> javaConfigCache.getOrFetch(file.getUri()));
  }

  public void shutdown() {
    Utils.shutdownAndAwait(asyncExecutor, true);
  }

}
