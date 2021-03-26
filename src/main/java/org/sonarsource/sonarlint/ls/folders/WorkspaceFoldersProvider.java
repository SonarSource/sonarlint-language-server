/*
 * SonarLint Language Server
 * Copyright (C) 2009-2021 SonarSource SA
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

import java.util.List;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.ModulesProvider;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.FolderFileSystem;
import org.sonarsource.sonarlint.ls.java.JavaConfigProvider;

public class WorkspaceFoldersProvider implements ModulesProvider<String> {

  public static String key(WorkspaceFolderWrapper folder) {
    return folder.getLspFolder().getName();
  }

  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final FileTypeClassifier fileTypeClassifier;
  private final JavaConfigProvider javaConfigProvider;

  public WorkspaceFoldersProvider(WorkspaceFoldersManager workspaceFoldersManager, FileTypeClassifier fileTypeClassifier, JavaConfigProvider javaConfigProvider) {
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.fileTypeClassifier = fileTypeClassifier;
    this.javaConfigProvider = javaConfigProvider;
  }

  @Override
  public List<ModuleInfo<String>> getModules() {
    return workspaceFoldersManager.getAll().stream()
      .map(this::createModuleInfo)
      .collect(Collectors.toList());
  }

  private ModuleInfo<String> createModuleInfo(WorkspaceFolderWrapper folder) {
    FolderFileSystem clientFileWalker = new FolderFileSystem(folder, javaConfigProvider, fileTypeClassifier);
    return new ModuleInfo<>(key(folder), clientFileWalker);
  }
}
