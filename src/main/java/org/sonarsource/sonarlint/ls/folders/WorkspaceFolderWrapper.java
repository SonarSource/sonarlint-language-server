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

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;

public class WorkspaceFolderWrapper {

  private static final Logger LOG = Loggers.get(WorkspaceFolderWrapper.class);

  private final URI uri;
  private final WorkspaceFolder lspFolder;
  private WorkspaceFolderSettings settings;
  private final CountDownLatch initLatch = new CountDownLatch(1);

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
   * Get non null settings, waiting for them to be initialized
   */
  public WorkspaceFolderSettings getSettings() {
    try {
      if (initLatch.await(1, TimeUnit.MINUTES)) {
        return settings;
      }
    } catch (InterruptedException e) {
      LOG.debug("Interrupted!", e);
      Thread.currentThread().interrupt();
    }
    throw new IllegalStateException("Unable to get settings in time");
  }

  @CheckForNull
  public WorkspaceFolderSettings getRawSettings() {
    return settings;
  }

  public void setSettings(WorkspaceFolderSettings settings) {
    this.settings = settings;
    initLatch.countDown();
  }

}
