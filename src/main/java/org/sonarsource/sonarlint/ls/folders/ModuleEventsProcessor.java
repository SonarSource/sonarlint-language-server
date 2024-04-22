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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.FolderFileSystem;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.ls.util.Utils;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent.Type;

public class ModuleEventsProcessor implements WorkspaceFolderLifecycleListener {

  private final FileTypeClassifier fileTypeClassifier;
  private final JavaConfigCache javaConfigCache;
  private final BackendServiceFacade backendServiceFacade;

  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ProjectBindingManager bindingManager;
  private final StandaloneEngineManager standaloneEngineManager;
  private final ExecutorService asyncExecutor;

  public ModuleEventsProcessor(StandaloneEngineManager standaloneEngineManager, WorkspaceFoldersManager workspaceFoldersManager, ProjectBindingManager bindingManager,
    FileTypeClassifier fileTypeClassifier, JavaConfigCache javaConfigCache, BackendServiceFacade backendServiceFacade) {
    this.standaloneEngineManager = standaloneEngineManager;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.bindingManager = bindingManager;
    this.fileTypeClassifier = fileTypeClassifier;
    this.javaConfigCache = javaConfigCache;
    this.backendServiceFacade = backendServiceFacade;
    this.asyncExecutor = Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint Language Server Module Events Processor", false));
  }

  public void didChangeWatchedFiles(List<FileEvent> changes) {
    changes.forEach(fileEvent -> {
      var fileUri = URI.create(fileEvent.getUri());
      var eventType = translate(fileEvent.getType());
      asyncExecutor.execute(() -> processFileEvent(fileUri, eventType));
    });
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
            addedOrChangedFiles.add(new ClientFileDto(fileUri, relativePath, folderUri, isTest, StandardCharsets.UTF_8.name(), fsPath, null, null));
          });
      }
    });
    backendServiceFacade.getBackendService().updateFileSystem(deletedFileUris, addedOrChangedFiles);
  }

  private void processFileEvent(URI fileUri, Type eventType) {
    workspaceFoldersManager.findFolderForFile(fileUri)
      .ifPresent(folder -> {
        var settings = folder.getSettings();
        var baseDir = folder.getRootPath();
        var binding = bindingManager.getBinding(fileUri);
        var engineForFile = binding.isPresent() ? binding.get().getEngine() : standaloneEngineManager.getOrCreateAnalysisEngine();
        var inputFile = new InFolderClientInputFile(fileUri, baseDir.relativize(Paths.get(fileUri)).toString(), isTestFile(fileUri, settings));
        engineForFile.fireModuleFileEvent(WorkspaceFoldersProvider.key(folder), ClientModuleFileEvent.of(inputFile, eventType));
      });
  }

  private boolean isTestFile(URI fileUri, WorkspaceFolderSettings settings) {
    return fileTypeClassifier.isTest(settings, fileUri, false, () -> javaConfigCache.getOrFetch(fileUri));
  }

  private static ModuleFileEvent.Type translate(FileChangeType type) {
    return switch (type) {
      case Created -> Type.CREATED;
      case Changed -> Type.MODIFIED;
      case Deleted -> Type.DELETED;
    };
  }

  private SonarLintAnalysisEngine findEngineFor(WorkspaceFolderWrapper folder) {
    return bindingManager.getBinding(folder)
      .map(ProjectBinding::getEngine)
      .map(SonarLintAnalysisEngine.class::cast)
      .orElseGet(standaloneEngineManager::getOrCreateAnalysisEngine);
  }

  @Override
  public void added(WorkspaceFolderWrapper addedFolder) {
    asyncExecutor.execute(() -> {
      var folderFileSystem = new FolderFileSystem(addedFolder, javaConfigCache, fileTypeClassifier);
      findEngineFor(addedFolder).declareModule(new ClientModuleInfo(WorkspaceFoldersProvider.key(addedFolder), folderFileSystem));
    });
  }

  @Override
  public void removed(WorkspaceFolderWrapper removedFolder) {
    asyncExecutor.execute(() -> findEngineFor(removedFolder).stopModule(WorkspaceFoldersProvider.key(removedFolder)));
  }

  public void shutdown() {
    Utils.shutdownAndAwait(asyncExecutor, true);
  }

}
