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
import java.util.List;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.api.ClientModulesProvider;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.file.FolderFileSystem;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;

public class WorkspaceFoldersProvider implements ClientModulesProvider {

  public static URI key(WorkspaceFolderWrapper folder) {
    return folder.getUri();
  }

  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final FileTypeClassifier fileTypeClassifier;
  private final JavaConfigCache javaConfigCache;

  public WorkspaceFoldersProvider(WorkspaceFoldersManager workspaceFoldersManager, FileTypeClassifier fileTypeClassifier, JavaConfigCache javaConfigCache) {
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.fileTypeClassifier = fileTypeClassifier;
    this.javaConfigCache = javaConfigCache;
  }

  @Override
  public List<ClientModuleInfo> getModules() {
    return workspaceFoldersManager.getAll().stream()
      .map(this::createModuleInfo)
      .collect(Collectors.toList());
  }

  private ClientModuleInfo createModuleInfo(WorkspaceFolderWrapper folder) {
    var clientFileWalker = new FolderFileSystem(folder, javaConfigCache, fileTypeClassifier);
    return new ClientModuleInfo(key(folder), clientFileWalker);
  }
}
