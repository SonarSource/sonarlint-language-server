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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static java.lang.String.format;

/**
 * Finds the nearest {@code .sonar-config.json} at or above a folder (no further than the enclosing git root) and parses
 * it. Stateless, and unaware of what the entries mean: every call reads the file system again.
 */
class SonarConfigLoader {

  static final String SONAR_CONFIG_FILE_NAME = ".sonar-config.json";

  private final LanguageClientLogger logOutput;
  private final Gson gson = new Gson();

  SonarConfigLoader(LanguageClientLogger logOutput) {
    this.logOutput = logOutput;
  }

  Optional<SonarConfigFile> load(Path folderPath) {
    var configPath = findSonarConfigFile(folderPath);
    if (configPath == null) {
      logOutput.info(format("[multi-project] No '%s' found at or above %s (up to the git root), folder registered normally", SONAR_CONFIG_FILE_NAME, folderPath));
      return Optional.empty();
    }
    var parsed = parse(configPath);
    if (parsed == null || parsed.projects == null) {
      logOutput.warn(format("[multi-project] '%s' at %s was found but could not be parsed into a 'projects' array", SONAR_CONFIG_FILE_NAME, configPath));
      return Optional.empty();
    }
    return Optional.of(new SonarConfigFile(configPath, parsed.projects));
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
  private ParsedFile parse(Path configPath) {
    try {
      return gson.fromJson(Files.readString(configPath), ParsedFile.class);
    } catch (IOException | JsonSyntaxException e) {
      logOutput.warn(format("Failed to read '%s': %s", configPath, e.getMessage()));
      return null;
    }
  }

  private static class ParsedFile {
    @Nullable
    List<SonarConfigEntry> projects;
  }
}
