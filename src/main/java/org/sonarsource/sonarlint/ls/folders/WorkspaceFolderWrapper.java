/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;

import static java.util.Objects.requireNonNull;

public class WorkspaceFolderWrapper {

  private final URI uri;
  private final WorkspaceFolder lspFolder;
  private WorkspaceFolderSettings settings;

  public WorkspaceFolderWrapper(URI uri, WorkspaceFolder lspFolder) {
    this.uri = uri;
    this.lspFolder = lspFolder;
  }

  public Path getRootPath() {
    // We only support file:// protocol for now
    return Paths.get(uri);
  }

  public URI getUri() {
    return uri;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(lspFolder, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  /**
   * Get non null globalSettings, assuming they have been initialized 
   */
  public WorkspaceFolderSettings getSettings() {
    return requireNonNull(settings, "Settings are not yet initialized");
  }

  @CheckForNull
  public WorkspaceFolderSettings getRawSettings() {
    return settings;
  }

  public void setSettings(WorkspaceFolderSettings settings) {
    this.settings = settings;
  }

}
