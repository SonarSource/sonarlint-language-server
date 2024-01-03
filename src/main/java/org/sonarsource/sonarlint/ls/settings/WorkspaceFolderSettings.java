/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.sonarlint.ls.settings;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Settings specific to a workspace workspaceFolderPath (but they may be inherited from higher level)
 * For a file not part of any workspace workspaceFolderPath, this will be same as global globalSettings.
 */
@Immutable
public class WorkspaceFolderSettings {

  private final Map<String, String> analyzerProperties;
  // Keep the string pattern for equals comparison
  private final String testFilePattern;
  private final PathMatcher testMatcher;
  private final String connectionId;
  private final String projectKey;
  private final String pathToCompileCommands;

  public WorkspaceFolderSettings(@Nullable String connectionId, @Nullable String projectKey, Map<String, String> analyzerProperties, @Nullable String testFilePattern,
    @Nullable String pathToCompileCommands) {
    this.connectionId = connectionId;
    this.projectKey = projectKey;
    this.analyzerProperties = analyzerProperties;
    this.testFilePattern = testFilePattern;
    this.pathToCompileCommands = pathToCompileCommands;
    this.testMatcher = testFilePattern != null ? FileSystems.getDefault().getPathMatcher("glob:" + testFilePattern) : (p -> false);
  }

  public Map<String, String> getAnalyzerProperties() {
    return Collections.unmodifiableMap(analyzerProperties);
  }

  public PathMatcher getTestMatcher() {
    return testMatcher;
  }

  @CheckForNull
  public String getPathToCompileCommands() {
    return pathToCompileCommands;
  }

  @CheckForNull
  public String getConnectionId() {
    return connectionId;
  }

  @CheckForNull
  public String getProjectKey() {
    return projectKey;
  }

  public boolean hasBinding() {
    return StringUtils.isNotBlank(connectionId) && StringUtils.isNotBlank(projectKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectionId, projectKey, analyzerProperties, testFilePattern, pathToCompileCommands);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    var other = (WorkspaceFolderSettings) obj;
    return Objects.equals(connectionId, other.connectionId) && Objects.equals(projectKey, other.projectKey) && Objects.equals(analyzerProperties, other.analyzerProperties)
      && Objects.equals(testFilePattern, other.testFilePattern) && Objects.equals(pathToCompileCommands, other.pathToCompileCommands);
  }

  @Override
  public String toString() {
    return new ReflectionToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).setExcludeFieldNames("testMatcher").toString();
  }

}
