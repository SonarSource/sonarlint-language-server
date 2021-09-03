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
package org.sonarsource.sonarlint.ls.settings;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceFolderSettingsTests {

  private static final WorkspaceFolderSettings SETTINGS = new WorkspaceFolderSettings("serverId", "projectKey", ImmutableMap.of("sonar.foo", "bar"), "filePattern");

  @Test
  public void testHashCode() {
    assertThat(SETTINGS.hashCode()).isEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", ImmutableMap.of("sonar.foo", "bar"), "filePattern").hashCode());
  }

  @Test
  public void testEquals() {
    assertThat(SETTINGS).isEqualTo(SETTINGS);
    assertThat(SETTINGS).isNotEqualTo(null);
    assertThat(SETTINGS).isNotEqualTo("foo");
    assertThat(SETTINGS).isEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", ImmutableMap.of("sonar.foo", "bar"), "filePattern"));

    assertThat(SETTINGS).isNotEqualTo(new WorkspaceFolderSettings("serverId2", "projectKey", ImmutableMap.of("sonar.foo", "bar"), "filePattern"));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceFolderSettings("serverId", "projectKey2", ImmutableMap.of("sonar.foo", "bar"), "filePattern"));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", ImmutableMap.of("sonar.foo2", "bar"), "filePattern"));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", ImmutableMap.of("sonar.foo", "bar2"), "filePattern"));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", ImmutableMap.of("sonar.foo", "bar", "sonar.foo2", "bar2"), "filePattern"));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceFolderSettings("serverId", "projectKey", ImmutableMap.of("sonar.foo", "bar"), "filePattern2"));
  }

  @Test
  public void testToString() {
    assertThat(SETTINGS.toString())
      .isEqualTo("WorkspaceFolderSettings[analyzerProperties={sonar.foo=bar},connectionId=serverId,projectKey=projectKey,testFilePattern=filePattern]");
  }

}
