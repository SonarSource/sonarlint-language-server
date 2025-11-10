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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.domain.LSLanguage;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.util.Utils;

import static org.sonarsource.sonarlint.ls.backend.BackendService.ROOT_CONFIGURATION_SCOPE;

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
    List<ClientFileDto> addedFiles = new ArrayList<>();
    List<ClientFileDto> changedFiles = new ArrayList<>();
    changes.forEach(event -> {
      var fileUri = URI.create(event.getUri());
      if (event.getType() == FileChangeType.Deleted) {
        deletedFileUris.add(fileUri);
      } else {
        var clientFileDto = getClientFileDto(new VersionedOpenFile(fileUri, null, 0, null));
        if (event.getType() == FileChangeType.Created) {
          addedFiles.add(clientFileDto);
        } else {
          changedFiles.add(clientFileDto);
        }
      }
    });
    backendServiceFacade.getBackendService().updateFileSystem(addedFiles, changedFiles, deletedFileUris);
  }

  public void notifyBackendWithFileLanguageAndContent(VersionedOpenFile file) {
    var openedFileDto = getClientFileDto(file);
    // We are simply enriching already added files with language and content information; The files were not actually modified
    // i.e. didOpen
    backendServiceFacade.getBackendService().updateFileSystem(List.of(openedFileDto), List.of(), List.of());
  }

  public void notifyBackendWithUpdatedContent(VersionedOpenFile file) {
    var changedFileDto = getClientFileDto(file);
    backendServiceFacade.getBackendService().updateFileSystem(List.of(), List.of(changedFileDto), List.of());
  }

  @NotNull
  ClientFileDto getClientFileDto(VersionedOpenFile file) {
    AtomicReference<ClientFileDto> clientFileDto = new AtomicReference<>();
    var fileUri = file.getUri();
    var fsPath = Paths.get(fileUri);
    var language = file.getLanguageId() != null ? toLanguage(file.getLanguageId().toLowerCase(Locale.ROOT)) : null;
    workspaceFoldersManager.findFolderForFile(fileUri)
      .ifPresentOrElse(folder -> {
        var settings = folder.getSettings();
        var baseDir = folder.getRootPath();
        var relativePath = baseDir.relativize(fsPath);
        var folderUri = folder.getUri().toString();
        var isTest = isTestFile(file, settings);
        clientFileDto.set(new ClientFileDto(fileUri, relativePath, folderUri, isTest, StandardCharsets.UTF_8.name(), fsPath, file.getContent(), language, true));
      }, () -> {
        var isTest = isTestFile(file, settingsManager.getCurrentDefaultFolderSettings());
        clientFileDto.set(new ClientFileDto(fileUri, fsPath, ROOT_CONFIGURATION_SCOPE, isTest, StandardCharsets.UTF_8.name(), fsPath, file.getContent(), language, true));
      });
    return clientFileDto.get();
  }

  private boolean isTestFile(VersionedOpenFile file, WorkspaceFolderSettings settings) {
    return fileTypeClassifier.isTest(settings, file.getUri(), file.isJava(), () -> javaConfigCache.getOrFetch(file.getUri()));
  }

  @CheckForNull
  static Language toLanguage(@Nullable String clientLanguageId) {
    if (clientLanguageId == null) {
      return null;
    }
    // See https://microsoft.github.io/language-server-protocol/specification#textDocumentItem
    return switch (clientLanguageId) {
      case "javascript", "javascriptreact", "vue", "vue component", "babel es6 javascript" -> Language.JS;
      case "python" -> Language.PYTHON;
      case "typescript", "typescriptreact" -> Language.TS;
      case "html" -> Language.HTML;
      case "oraclesql" -> Language.PLSQL;
      case "apex", "apex-anon" ->
        // See https://github.com/forcedotcom/salesforcedx-vscode/blob/5e4b7715d1cb3d1ee2780780ed63f70f58e93b20/packages/salesforcedx-vscode-apex/package.json#L273
        Language.APEX;
      default ->
        // Other supported languages map to the same key as the one used in SonarQube/SonarCloud
        LSLanguage.forKey(clientLanguageId).map(l -> Language.valueOf(l.name())).orElse(null);
    };
  }

  public void shutdown() {
    Utils.shutdownAndAwait(asyncExecutor, true);
  }

}
