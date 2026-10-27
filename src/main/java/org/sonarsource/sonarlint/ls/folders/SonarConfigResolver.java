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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.util.URIUtils;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static org.sonarsource.sonarlint.ls.folders.SonarConfigLoader.SONAR_CONFIG_FILE_NAME;

/**
 * What {@code .sonar-config.json} files say about the workspace folders: classifies the entries of the loaded file
 * against each folder, keeps the result per folder, and answers which sub-folder scope a file falls in. A shared leaf:
 * it depends on neither the folders manager nor the binding manager, and knows nothing about connections or the backend.
 */
public class SonarConfigResolver {

  private final LanguageClientLogger logOutput;
  private final SonarConfigLoader loader;
  private final Map<URI, Optional<ResolvedSonarConfig>> configsByFolder = new ConcurrentHashMap<>();

  public SonarConfigResolver(LanguageClientLogger logOutput) {
    this(logOutput, new SonarConfigLoader(logOutput));
  }

  SonarConfigResolver(LanguageClientLogger logOutput, SonarConfigLoader loader) {
    this.logOutput = logOutput;
    this.loader = loader;
  }

  /**
   * The config as it applies to a folder, resolved on first use and then kept, empty results included.
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
      .filter(subFolderUri -> URIUtils.isAncestor(subFolderUri, fileUri))
      .max(Comparator.comparingInt(subFolderUri -> subFolderUri.getPath().length()))
      .orElse(folderUri);
  }

  public Optional<ResolvedSonarConfig> findForFile(URI folderUri, URI fileUri) {
    return get(scopeUriForFile(folderUri, fileUri));
  }

  public void forget(URI folderUri) {
    subFolderUris(folderUri).forEach(configsByFolder::remove);
    configsByFolder.remove(folderUri);
  }

  private static String directoryName(SonarConfigProject project) {
    return Optional.ofNullable(Paths.get(project.scopeUri()).getFileName()).map(Path::toString).orElse(project.projectKey());
  }

  private Optional<ResolvedSonarConfig> resolve(URI folderUri) {
    if (!Utils.uriHasFileScheme(folderUri)) {
      return Optional.empty();
    }
    var folderPath = Paths.get(folderUri).toAbsolutePath().normalize();
    return loader.load(folderPath).flatMap(file -> classify(folderUri, folderPath, file));
  }

  private Optional<ResolvedSonarConfig> classify(URI folderUri, Path folderPath, SonarConfigFile file) {
    logOutput.info(format("[multi-project] Found '%s' at %s declaring %d project entr(y/ies)", SONAR_CONFIG_FILE_NAME, file.path(), file.projects().size()));
    var resolved = classifyEntries(folderUri, folderPath, file.path().getParent(), file.projects());
    if (resolved.allProjects().isEmpty()) {
      logOutput.info(format("[multi-project] '%s' at %s has no entry matching folder %s, folder registered normally", SONAR_CONFIG_FILE_NAME, file.path(), folderPath));
      return Optional.empty();
    }
    var binding = resolved.direct() != null ? "bound to projectKey=" + resolved.direct().projectKey() : "container without own binding";
    logOutput.info(format("[multi-project] Folder %s: %s, %d sub-folder(s)", folderPath, binding, resolved.children().size()));
    return Optional.of(resolved);
  }

  private ResolvedSonarConfig classifyEntries(URI folderUri, Path folderPath, Path configDir, List<SonarConfigEntry> entries) {
    var children = new ArrayList<SonarConfigProject>();
    SonarConfigProject direct = null;
    Path directPath = null;
    for (var entry : entries) {
      if (!entry.isValid()) {
        logOutput.warn(format("[multi-project] Ignoring invalid entry in '%s' for folder %s: projectKey=%s path=%s serverUrl=%s organization=%s "
          + "(need either serverUrl or organization)", SONAR_CONFIG_FILE_NAME, folderUri, entry.projectKey(), entry.path(), entry.serverUrl(), entry.organization()));
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
