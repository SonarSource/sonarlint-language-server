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
package org.sonarsource.sonarlint.ls.settings;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceFolderSettingsTests {

  private static final WorkspaceFolderSettings SETTINGS = new WorkspaceFolderSettings("serverId", "projectKey", Map.of("sonar.foo", "bar"), "filePattern", "compilationDatabase");

  @Test
  void testHashCode() {
    assertThat(new WorkspaceFolderSettings("serverId", "projectKey", Map.of("sonar.foo", "bar"), "filePattern", "compilationDatabase")).hasSameHashCodeAs(SETTINGS);
  }

  @Test
  void testEquals() {
    assertThat(SETTINGS).isEqualTo(SETTINGS)
      .isNotEqualTo(null)
      .isNotEqualTo("foo")
      .isEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", Map.of("sonar.foo", "bar"), "filePattern", "compilationDatabase"))
      .isNotEqualTo(new WorkspaceFolderSettings("serverId2", "projectKey", Map.of("sonar.foo", "bar"), "filePattern", "compilationDatabase"))
      .isNotEqualTo(new WorkspaceFolderSettings("serverId", "projectKey2", Map.of("sonar.foo", "bar"), "filePattern", "compilationDatabase"))
      .isNotEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", Map.of("sonar.foo2", "bar"), "filePattern", "compilationDatabase"))
      .isNotEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", Map.of("sonar.foo", "bar2"), "filePattern", "compilationDatabase"))
      .isNotEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", Map.of("sonar.foo", "bar", "sonar.foo2", "bar2"), "filePattern", "compilationDatabase"))
      .isNotEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", Map.of("sonar.foo", "bar"), "filePattern2", "compilationDatabase"))
      .isNotEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", Map.of("sonar.foo", "bar"), "filePattern", "compilationDatabase2"));
  }

  @Test
  void testToString() {
    assertThat(SETTINGS).hasToString(
      "WorkspaceFolderSettings[analyzerProperties={sonar.foo=bar},connectionId=serverId,pathToCompileCommands=compilationDatabase,projectKey=projectKey,testFilePattern=filePattern]");
  }

}
