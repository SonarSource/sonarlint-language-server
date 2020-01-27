/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceSettingsTests {

  private static final WorkspaceSettings SETTINGS = new WorkspaceSettings(false,
    ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg")),
    asList(new RuleKey("repo1", "rule1")),
    asList(new RuleKey("repo2", "rule2")), false, false);

  @Test
  public void testHashCode() {
    assertThat(SETTINGS.hashCode()).isEqualTo(new WorkspaceSettings(false,
      ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg")),
      asList(new RuleKey("repo1", "rule1")),
      asList(new RuleKey("repo2", "rule2")), false, false).hashCode());
  }

  @Test
  public void testEquals() {
    assertThat(SETTINGS).isEqualTo(SETTINGS);
    assertThat(SETTINGS).isNotEqualTo(null);
    assertThat(SETTINGS).isNotEqualTo("foo");
    assertThat(SETTINGS).isEqualTo(new WorkspaceSettings(false,
      ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg")),
      asList(new RuleKey("repo1", "rule1")),
      asList(new RuleKey("repo2", "rule2")), false, false));

    assertThat(SETTINGS).isNotEqualTo(new WorkspaceSettings(true,
      ImmutableMap.of("serverId2", new ServerConnectionSettings("serverId2", "serverUrl", "token", "myOrg")),
      asList(new RuleKey("repo1", "rule1")),
      asList(new RuleKey("repo2", "rule2")), false, false));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceSettings(false,
      ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl2", "token", "myOrg")),
      asList(new RuleKey("repo1", "rule1")),
      asList(new RuleKey("repo2", "rule2")), false, false));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceSettings(false,
      ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token2", "myOrg")),
      asList(new RuleKey("repo1", "rule1")),
      asList(new RuleKey("repo2", "rule2")), false, false));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceSettings(false,
      ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg2")),
      asList(new RuleKey("repo1", "rule1")),
      asList(new RuleKey("repo2", "rule2")), false, false));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceSettings(false,
      ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg")),
      asList(new RuleKey("repo1", "rule12")),
      asList(new RuleKey("repo2", "rule2")), false, false));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceSettings(false,
      ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg")),
      asList(new RuleKey("repo1", "rule1")),
      asList(new RuleKey("repo2", "rule22")), false, false));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceSettings(false,
      ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg")),
      asList(new RuleKey("repo1", "rule1")),
      asList(new RuleKey("repo2", "rule2")), true, false));
    assertThat(SETTINGS).isNotEqualTo(new WorkspaceSettings(false,
      ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg")),
      asList(new RuleKey("repo1", "rule1")),
      asList(new RuleKey("repo2", "rule2")), false, true));
  }

  @Test
  public void testToString() {
    assertThat(SETTINGS.toString()).isEqualTo(
      "WorkspaceSettings[disableTelemetry=false,servers={serverId=ServerConnectionSettings[serverId=serverId,serverUrl=serverUrl,token=token,organizationKey=myOrg]},excludedRules=[repo1:rule1],includedRules=[repo2:rule2],showAnalyzerLogs=false,showVerboseLogs=false]");
  }

}
