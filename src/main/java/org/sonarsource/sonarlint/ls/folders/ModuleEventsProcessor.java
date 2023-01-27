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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.FolderFileSystem;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.ls.util.Utils;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent.Type;

public class ModuleEventsProcessor implements WorkspaceFolderLifecycleListener {

  private final FileTypeClassifier fileTypeClassifier;
  private final JavaConfigCache javaConfigCache;

  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final ProjectBindingManager bindingManager;
  private final StandaloneEngineManager standaloneEngineManager;
  private final ExecutorService asyncExecutor;

  public ModuleEventsProcessor(StandaloneEngineManager standaloneEngineManager, WorkspaceFoldersManager workspaceFoldersManager, ProjectBindingManager bindingManager,
    FileTypeClassifier fileTypeClassifier, JavaConfigCache javaConfigCache) {
    this.standaloneEngineManager = standaloneEngineManager;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.bindingManager = bindingManager;
    this.fileTypeClassifier = fileTypeClassifier;
    this.javaConfigCache = javaConfigCache;
    this.asyncExecutor = Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint Language Server Module Events Processor", false));
  }

  public void didChangeWatchedFiles(List<FileEvent> changes) {
    changes.forEach(fileEvent -> {
      var fileUri = URI.create(fileEvent.getUri());
      var eventType = translate(fileEvent.getType());
      asyncExecutor.execute(() -> processFileEvent(fileUri, eventType));
    });
  }

  private void processFileEvent(URI fileUri, Type eventType) {
    workspaceFoldersManager.findFolderForFile(fileUri)
      .ifPresent(folder -> {
        var settings = folder.getSettings();
        var baseDir = folder.getRootPath();

        var binding = bindingManager.getBinding(fileUri);

        var engineForFile = binding.isPresent() ? binding.get().getEngine() : standaloneEngineManager.getOrCreateStandaloneEngine();

        var inputFile = new InFolderClientInputFile(fileUri, baseDir.relativize(Paths.get(fileUri)).toString(),
          fileTypeClassifier.isTest(settings, fileUri, false, () -> javaConfigCache.getOrFetch(fileUri)));

        engineForFile.fireModuleFileEvent(WorkspaceFoldersProvider.key(folder), ClientModuleFileEvent.of(inputFile, eventType));
      });
  }

  private static ModuleFileEvent.Type translate(FileChangeType type) {
    switch (type) {
      case Created:
        return ModuleFileEvent.Type.CREATED;
      case Changed:
        return ModuleFileEvent.Type.MODIFIED;
      case Deleted:
        return ModuleFileEvent.Type.DELETED;
    }
    throw new IllegalArgumentException("Unknown event type: " + type);
  }

  private SonarLintEngine findEngineFor(WorkspaceFolderWrapper folder) {
    return bindingManager.getBinding(folder)
      .map(ProjectBindingWrapper::getEngine)
      .map(SonarLintEngine.class::cast)
      .orElseGet(standaloneEngineManager::getOrCreateStandaloneEngine);
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
