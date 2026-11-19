/*
 * SonarLint Language Server
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.ls.telemetry;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.BindingSource;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ObservedBindingSourceDto;
import org.sonarsource.sonarlint.ls.connected.BindingSourceObserver;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.util.Utils;

/**
 * Keeps the latest binding reported by the binding sources for each workspace folder, until drained.
 */
public class BindingSourceObservations implements BindingSourceObserver {

  private final Map<URI, Observation> byFolder = new ConcurrentHashMap<>();
  private volatile boolean drained;

  @Override
  public void boundBySettings(WorkspaceFolderWrapper folder) {
    var folderPath = Utils.uriHasFileScheme(folder.getUri()) ? Paths.get(folder.getUri()) : null;
    record(folder, new Observation(BindingSource.IDE_SETTINGS, folderPath, 1));
  }

  @Override
  public void boundBySonarConfig(WorkspaceFolderWrapper folder, Path sonarConfigFile, int declaredBindingCount) {
    record(folder, new Observation(BindingSource.SONAR_CONFIG, sonarConfigFile.getParent(), declaredBindingCount));
  }

  @Override
  public void unbound(WorkspaceFolderWrapper folder) {
    if (!drained) {
      byFolder.remove(folder.getUri());
    }
  }

  /**
   * The sources of the given bound folders, each with the directory its declaration applies to. Stops recording.
   */
  public List<ObservedBindingSourceDto> drain(Collection<WorkspaceFolderWrapper> folders) {
    drained = true;
    var sources = folders.stream()
      .map(folder -> byFolder.get(folder.getUri()))
      .filter(Objects::nonNull)
      .map(observation -> new ObservedBindingSourceDto(observation.declarationRoot(), observation.source(), observation.declaredBindingCount()))
      .toList();
    byFolder.clear();
    return sources;
  }

  private void record(WorkspaceFolderWrapper folder, Observation observation) {
    if (!drained) {
      byFolder.put(folder.getUri(), observation);
    }
  }

  private record Observation(BindingSource source, @Nullable Path declarationRoot, int declaredBindingCount) {
  }
}
