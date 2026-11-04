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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static java.lang.String.format;
import static org.sonarsource.sonarlint.ls.folders.SonarConfigLoader.SONAR_CONFIG_FILE_NAME;

/**
 * A loaded {@code .sonar-config.json}: knows which of its entries concern a given workspace folder. It knows nothing
 * about connections or the backend.
 */
public class SonarConfigFile {

  private final Path path;
  private final List<SonarConfigEntry> projects;
  private final LanguageClientLogger logOutput;

  SonarConfigFile(Path path, List<SonarConfigEntry> projects, LanguageClientLogger logOutput) {
    this.path = path;
    this.projects = projects;
    this.logOutput = logOutput;
  }

  Path path() {
    return path;
  }

  List<SonarConfigEntry> projects() {
    return projects;
  }

  public Optional<ResolvedSonarConfig> resolveFor(URI folderUri) {
    var folderPath = Paths.get(folderUri).toAbsolutePath().normalize();
    logOutput.info(format("[multi-project] Found '%s' at %s declaring %d project entr(y/ies)", SONAR_CONFIG_FILE_NAME, path, projects.size()));
    var resolved = classifyEntries(folderUri, folderPath);
    if (resolved.allProjects().isEmpty()) {
      logOutput.info(format("[multi-project] '%s' at %s has no entry matching folder %s, folder registered normally", SONAR_CONFIG_FILE_NAME, path, folderPath));
      return Optional.empty();
    }
    var binding = resolved.direct() != null ? ("bound to projectKey=" + resolved.direct().projectKey()) : "container without own binding";
    logOutput.info(format("[multi-project] Folder %s: %s, %d sub-folder(s)", folderPath, binding, resolved.children().size()));
    return Optional.of(resolved);
  }

  private ResolvedSonarConfig classifyEntries(URI folderUri, Path folderPath) {
    var configDir = path.getParent();
    var children = new ArrayList<SonarConfigProject>();
    SonarConfigProject direct = null;
    Path directPath = null;
    for (var entry : projects) {
      if (entry == null || !entry.isValid()) {
        logOutput.warn(format("[multi-project] Ignoring invalid entry in '%s' for folder %s: %s (need a projectKey, a path, and either serverUrl or organization)",
          SONAR_CONFIG_FILE_NAME, folderUri, entry));
        continue;
      }
      var entryPath = configDir.resolve(entry.path()).normalize();
      if (folderPath.startsWith(entryPath)) {
        if (directPath == null || entryPath.getNameCount() > directPath.getNameCount()) {
          logOutput.debug(format("[multi-project] Entry projectKey=%s path=%s contains the folder -> its own configuration scope is bound directly, no sub-folder needed",
            entry.projectKey(), entry.path()));
          direct = new SonarConfigProject(folderUri, entry.projectKey(), entry.serverUrl(), entry.organization(), entry.region(), ".");
          directPath = entryPath;
        }
      } else if (entryPath.startsWith(folderPath)) {
        if (children.stream().anyMatch(child -> child.scopeUri().equals(entryPath.toUri()))) {
          logOutput.warn(format("[multi-project] Ignoring entry projectKey=%s in '%s' for folder %s: path %s is already declared by an earlier entry",
            entry.projectKey(), SONAR_CONFIG_FILE_NAME, folderUri, entry.path()));
          continue;
        }
        var relativePath = folderPath.relativize(entryPath).toString().replace('\\', '/');
        logOutput.debug(format("[multi-project] Entry projectKey=%s path=%s is nested in the folder -> sub-folder %s", entry.projectKey(), entry.path(), entryPath.toUri()));
        children.add(new SonarConfigProject(entryPath.toUri(), entry.projectKey(), entry.serverUrl(), entry.organization(), entry.region(), relativePath));
      } else {
        logOutput.debug(format("[multi-project] Entry projectKey=%s path=%s is unrelated to folder %s, skipped", entry.projectKey(), entry.path(), folderPath));
      }
    }
    return new ResolvedSonarConfig(direct, children);
  }
}
