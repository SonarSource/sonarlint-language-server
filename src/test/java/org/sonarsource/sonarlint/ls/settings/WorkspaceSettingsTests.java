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
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClient;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkspaceSettingsTests {

  private static final ApacheHttpClient httpClient = mock(ApacheHttpClient.class);
  public static final RuleKey RULE_KEY_1 = new RuleKey("repo1", "rule1");
  public static final RuleKey RULE_KEY_2 = new RuleKey("repo2", "rule2");
  private static final WorkspaceSettings SETTINGS = new WorkspaceSettings(false,
    ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, httpClient)),
    singletonList(RULE_KEY_1),
    singletonList(RULE_KEY_2),
    ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")),
    false, false, "path/to/node");

  @Test
  void testHashCode() {
    assertThat(new WorkspaceSettings(false,
      ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, httpClient)),
      singletonList(RULE_KEY_1),
      singletonList(RULE_KEY_2),
      ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")),
      false, false, "path/to/node")).hasSameHashCodeAs(SETTINGS);
  }

  @Test
  void testEquals() {
    assertThat(SETTINGS)
      .isEqualTo(SETTINGS)
      .isNotEqualTo(null)
      .isNotEqualTo("foo")
      .isEqualTo(new WorkspaceSettings(false,
        ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, httpClient)),
        singletonList(new RuleKey("repo1", "rule1")),
        singletonList(new RuleKey("repo2", "rule2")),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")), false, false, "path/to/node"))
      .isNotEqualTo(new WorkspaceSettings(true,
        ImmutableMap.of("serverId2", new ServerConnectionSettings("serverId2", "serverUrl", "token", "myOrg", true, httpClient)),
        singletonList(new RuleKey("repo1", "rule1")),
        singletonList(new RuleKey("repo2", "rule2")),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")), false, false, "path/to/node"))
      .isNotEqualTo(new WorkspaceSettings(false,
        ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl2", "token", "myOrg", true, httpClient)),
        singletonList(new RuleKey("repo1", "rule1")),
        singletonList(new RuleKey("repo2", "rule2")),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")), false, false, "path/to/node"))
      .isNotEqualTo(new WorkspaceSettings(false,
        ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token2", "myOrg", true, httpClient)),
        singletonList(new RuleKey("repo1", "rule1")),
        singletonList(new RuleKey("repo2", "rule2")),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")), false, false, "path/to/node"))
      .isNotEqualTo(new WorkspaceSettings(false,
        ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg2", true, httpClient)),
        singletonList(new RuleKey("repo1", "rule1")),
        singletonList(new RuleKey("repo2", "rule2")),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")), false, false, "path/to/node"))
      .isNotEqualTo(new WorkspaceSettings(false,
        ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, httpClient)),
        singletonList(new RuleKey("repo1", "rule12")),
        singletonList(new RuleKey("repo2", "rule2")),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")), false, false, "path/to/node"))
      .isNotEqualTo(new WorkspaceSettings(false,
        ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, httpClient)),
        singletonList(new RuleKey("repo1", "rule1")),
        singletonList(new RuleKey("repo2", "rule22")),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")), false, false, "path/to/node"))
      .isNotEqualTo(new WorkspaceSettings(false,
        ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, httpClient)),
        singletonList(new RuleKey("repo1", "rule1")),
        singletonList(new RuleKey("repo2", "rule22")),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")), false, false, "path/to/node"))
      .isNotEqualTo(new WorkspaceSettings(false,
        ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, httpClient)),
        singletonList(RULE_KEY_1),
        singletonList(RULE_KEY_2),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value2")), false, false, "path/to/node"))
      .isNotEqualTo(new WorkspaceSettings(false,
        ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, httpClient)),
        singletonList(new RuleKey("repo1", "rule1")),
        singletonList(new RuleKey("repo2", "rule2")),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")), true, false, "path/to/node"))
      .isNotEqualTo(new WorkspaceSettings(false,
        ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, httpClient)),
        singletonList(new RuleKey("repo1", "rule1")),
        singletonList(new RuleKey("repo2", "rule2")),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")), false, true, "path/to/node"))
      .isNotEqualTo(new WorkspaceSettings(false,
        ImmutableMap.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, httpClient)),
        singletonList(RULE_KEY_1),
        singletonList(RULE_KEY_2),
        ImmutableMap.of(RULE_KEY_2, ImmutableMap.of("param1", "value1")),
        false, false, "other/path/to/node"));
  }

  @Test
  void testToString() {
    assertThat(SETTINGS).hasToString(
      "WorkspaceSettings[disableTelemetry=false,connections={serverId=ServerConnectionSettings[connectionId=serverId,serverUrl=serverUrl,token=token,disableNotifications=true,organizationKey=myOrg]},excludedRules=[repo1:rule1],includedRules=[repo2:rule2],ruleParameters={repo2:rule2={param1=value1}},showAnalyzerLogs=false,showVerboseLogs=false,pathToNodeExecutable=path/to/node]");
  }

}
