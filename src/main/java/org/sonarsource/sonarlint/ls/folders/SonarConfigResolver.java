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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;

/**
 * Reads the nearest {@code .sonar-config.json} at or above a workspace folder (no further than the enclosing git
 * root) and classifies its entries against that folder. Stateless: it knows nothing about connections or the backend.
 */
public class SonarConfigResolver {

  public static final String SONAR_CONFIG_FILE_NAME = ".sonar-config.json";

  private final LanguageClientLogger logOutput;
  private final Gson gson = new Gson();

  public SonarConfigResolver(LanguageClientLogger logOutput) {
    this.logOutput = logOutput;
  }

  public Optional<ResolvedSonarConfig> resolve(URI folderUri) {
    if (!Utils.uriHasFileScheme(folderUri)) {
      return Optional.empty();
    }
    var folderPath = Paths.get(folderUri).toAbsolutePath().normalize();
    var configPath = findSonarConfigFile(folderPath);
    if (configPath == null) {
      logOutput.info(format("[multi-project] No '%s' found at or above %s (up to the git root), folder registered normally", SONAR_CONFIG_FILE_NAME, folderPath));
      return Optional.empty();
    }
    var configFile = readSonarConfigFile(configPath);
    if (configFile == null || configFile.projects == null) {
      logOutput.warn(format("[multi-project] '%s' at %s was found but could not be parsed into a 'projects' array", SONAR_CONFIG_FILE_NAME, configPath));
      return Optional.empty();
    }
    logOutput.info(format("[multi-project] Found '%s' at %s declaring %d project entr(y/ies)", SONAR_CONFIG_FILE_NAME, configPath, configFile.projects.size()));
    var configDir = configPath.getParent();
    var children = new ArrayList<SonarConfigProject>();
    SonarConfigProject direct = null;
    Path directPath = null;
    for (var entry : configFile.projects) {
      if (entry.projectKey == null || entry.path == null || (entry.serverUrl == null && entry.organization == null)) {
        logOutput.warn(format("[multi-project] Ignoring invalid entry in '%s' for folder %s: projectKey=%s path=%s serverUrl=%s organization=%s (need either serverUrl or organization)",
          SONAR_CONFIG_FILE_NAME, folderUri, entry.projectKey, entry.path, entry.serverUrl, entry.organization));
        continue;
      }
      var entryPath = configDir.resolve(entry.path).normalize();
      if (folderPath.startsWith(entryPath)) {
        if (directPath == null || entryPath.getNameCount() > directPath.getNameCount()) {
          logOutput.debug(format("[multi-project] Entry projectKey=%s path=%s contains the folder -> its own configuration scope is bound directly, no sub-folder needed",
            entry.projectKey, entry.path));
          direct = new SonarConfigProject(folderUri, entry.projectKey, entry.serverUrl, entry.organization, entry.region, ".");
          directPath = entryPath;
        }
      } else if (entryPath.startsWith(folderPath)) {
        var relativePath = folderPath.relativize(entryPath).toString().replace('\\', '/');
        logOutput.debug(format("[multi-project] Entry projectKey=%s path=%s is nested in the folder -> sub-folder %s", entry.projectKey, entry.path, entryPath.toUri()));
        children.add(new SonarConfigProject(entryPath.toUri(), entry.projectKey, entry.serverUrl, entry.organization, entry.region, relativePath));
      } else {
        logOutput.debug(format("[multi-project] Entry projectKey=%s path=%s is unrelated to folder %s, skipped", entry.projectKey, entry.path, folderPath));
      }
    }
    if (children.isEmpty() && direct == null) {
      logOutput.info(format("[multi-project] '%s' at %s has no entry matching folder %s, folder registered normally", SONAR_CONFIG_FILE_NAME, configPath, folderPath));
      return Optional.empty();
    }
    logOutput.info(format("[multi-project] Folder %s: %s, %d sub-folder(s)", folderPath,
      direct != null ? "bound to projectKey=" + direct.projectKey() : "container without own binding", children.size()));
    return Optional.of(new ResolvedSonarConfig(direct, children));
  }

  @CheckForNull
  private static Path findSonarConfigFile(Path folderPath) {
    var gitRoot = findGitRoot(folderPath);
    for (var dir = folderPath; dir != null; dir = dir.getParent()) {
      var candidate = dir.resolve(SONAR_CONFIG_FILE_NAME);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      if (gitRoot == null || dir.equals(gitRoot)) {
        return null;
      }
    }
    return null;
  }

  @CheckForNull
  private static Path findGitRoot(Path folderPath) {
    for (var dir = folderPath; dir != null; dir = dir.getParent()) {
      if (Files.exists(dir.resolve(".git"))) {
        return dir;
      }
    }
    return null;
  }

  @CheckForNull
  private SonarConfigFile readSonarConfigFile(Path configPath) {
    try {
      var content = Files.readString(configPath);
      return gson.fromJson(content, SonarConfigFile.class);
    } catch (IOException | JsonSyntaxException e) {
      logOutput.warn(format("Failed to read '%s': %s", configPath, e.getMessage()));
      return null;
    }
  }

  private static class SonarConfigFile {
    @Nullable
    List<ProjectEntry> projects;
  }

  private static class ProjectEntry {
    @Nullable
    String projectKey;
    @Nullable
    String path;
    @Nullable
    String serverUrl;
    @Nullable
    String organization;
    @Nullable
    String region;
  }
}
