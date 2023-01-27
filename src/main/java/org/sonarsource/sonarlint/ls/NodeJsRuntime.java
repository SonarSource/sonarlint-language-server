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
package org.sonarsource.sonarlint.ls;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

public class NodeJsRuntime {

  private final SettingsManager settingsManager;
  private final Supplier<NodeJsHelper> nodeJsHelperFactory;
  private boolean init = false;
  private Path nodeJsPath = null;
  private Version nodeJsVersion = null;

  NodeJsRuntime(SettingsManager settingsManager) {
    this(settingsManager, NodeJsHelper::new);
  }

  NodeJsRuntime(SettingsManager settingsManager, Supplier<NodeJsHelper> nodeJsHelperFactory) {
    this.settingsManager = settingsManager;
    this.nodeJsHelperFactory = nodeJsHelperFactory;
  }

  private void init() {
    var currentSettings = settingsManager.getCurrentSettings();
    var helper = nodeJsHelperFactory.get();
    helper.detect(Optional.ofNullable(currentSettings.pathToNodeExecutable())
      .filter(StringUtils::isNotEmpty)
      .map(Paths::get)
      .orElse(null));
    this.nodeJsPath = helper.getNodeJsPath();
    this.nodeJsVersion = helper.getNodeJsVersion();
    this.init = true;
  }

  @Nullable
  public String nodeVersion() {
    return Optional.ofNullable(getNodeJsVersion())
      .map(Version::toString)
      .orElse(null);
  }

  public Path getNodeJsPath() {
    if (!init) {
      init();
    }
    return nodeJsPath;
  }

  public Version getNodeJsVersion() {
    if (!init) {
      init();
    }
    return nodeJsVersion;
  }
}
