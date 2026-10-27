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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;

/**
 * Everything about {@code .sonar-config.json} files as they apply to folders: reads the nearest file at or above a
 * folder (no further than the enclosing git root), classifies its entries against that folder, caches the result per
 * folder, and answers which sub-folder scope a file falls in. Knows nothing about connections or the backend.
 */
public class SonarConfigResolver {

  public static final String SONAR_CONFIG_FILE_NAME = ".sonar-config.json";

  private final LanguageClientLogger logOutput;
  private final Gson gson = new Gson();
  private final Map<URI, Optional<ResolvedSonarConfig>> configsByFolder = new ConcurrentHashMap<>();
  private final Set<URI> registeredFolders = ConcurrentHashMap.newKeySet();

  public SonarConfigResolver(LanguageClientLogger logOutput) {
    this.logOutput = logOutput;
  }

  /**
   * The config as it applies to a folder, resolved on first use and then cached, empty results included.
   */
  public Optional<ResolvedSonarConfig> get(URI folderUri) {
    return configsByFolder.computeIfAbsent(folderUri, this::resolve);
  }

  public List<URI> subFolderUris(URI folderUri) {
    return get(folderUri)
      .map(config -> config.children().stream().map(SonarConfigProject::scopeUri).toList())
      .orElse(List.of());
  }

  public List<WorkspaceFolder> subFolders(URI folderUri) {
    return get(folderUri)
      .map(config -> config.children().stream().map(project -> new WorkspaceFolder(project.scopeUri().toString(), directoryName(project))).toList())
      .orElse(List.of());
  }

  /**
   * The scope a file belongs to: the deepest sub-folder of the workspace folder that contains it, otherwise the
   * workspace folder itself.
   */
  public URI scopeUriForFile(URI folderUri, URI fileUri) {
    return subFolderUris(folderUri).stream()
      .filter(subFolderUri -> WorkspaceFoldersManager.isAncestor(subFolderUri, fileUri))
      .max(Comparator.comparingInt(subFolderUri -> subFolderUri.getPath().length()))
      .orElse(folderUri);
  }

  public Optional<ResolvedSonarConfig> findForFile(URI folderUri, URI fileUri) {
    return get(scopeUriForFile(folderUri, fileUri));
  }

  public void markRegistered(URI folderUri) {
    registeredFolders.add(folderUri);
  }

  /**
   * Configs of the workspace folders whose scopes already exist in the backend.
   */
  public Map<URI, ResolvedSonarConfig> registeredConfigs() {
    var registered = new HashMap<URI, ResolvedSonarConfig>();
    registeredFolders.forEach(folderUri -> get(folderUri).ifPresent(config -> registered.put(folderUri, config)));
    return registered;
  }

  public void forget(URI folderUri) {
    subFolderUris(folderUri).forEach(configsByFolder::remove);
    configsByFolder.remove(folderUri);
    registeredFolders.remove(folderUri);
  }

  private static String directoryName(SonarConfigProject project) {
    return Optional.ofNullable(Paths.get(project.scopeUri()).getFileName()).map(Path::toString).orElse(project.projectKey());
  }

  private Optional<ResolvedSonarConfig> resolve(URI folderUri) {
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
