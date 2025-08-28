/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;

class WorkspaceSettingsTests {

  public static final RuleKey RULE_KEY_1 = RuleKey.of("repo1", "rule1");
  public static final RuleKey RULE_KEY_2 = RuleKey.of("repo2", "rule2");
  private static final WorkspaceSettings SETTINGS = new WorkspaceSettings(false,
    Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
    List.of(RULE_KEY_1),
    List.of(RULE_KEY_2),
    Map.of(RULE_KEY_2, Map.of("param1", "value1")),
    false, "path/to/node", false, true, "");

  @Test
  void testHashCode() {
    assertThat(new WorkspaceSettings(false,
      Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
      List.of(RULE_KEY_1),
      List.of(RULE_KEY_2),
      Map.of(RULE_KEY_2, Map.of("param1", "value1")),
      false, "path/to/node", false, true, "")).hasSameHashCodeAs(SETTINGS);
  }

  @Test
  void testEquals() {
    assertThat(SETTINGS)
      .isEqualTo(SETTINGS)
      .isNotEqualTo(null)
      .isNotEqualTo("foo")
      .isEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, "path/to/node", false, true, ""))
      .isNotEqualTo(new WorkspaceSettings(true,
        Map.of("serverId2", new ServerConnectionSettings("serverId2", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, "path/to/node", false, true, ""))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl2", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, "path/to/node", false, true, ""))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token2", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, "path/to/node", false, true, ""))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg2", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, "path/to/node", false, true, ""))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule12")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, "path/to/node", false, true, ""))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule22")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, "path/to/node", false, true, ""))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule22")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, "path/to/node", false, true, ""))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RULE_KEY_1),
        List.of(RULE_KEY_2),
        Map.of(RULE_KEY_2, Map.of("param1", "value2")), false, "path/to/node", false, true, ""))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), true, "path/to/node", false, true, ""))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RULE_KEY_1),
        List.of(RULE_KEY_2),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")),
        false, "other/path/to/node", false, true, ""))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, "path/to/node", true, true, ""))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, "path/to/node", true, false, ""));
  }

  @Test
  void testToString() {
    assertThat(SETTINGS).hasToString(
      "WorkspaceSettings[analysisExcludes=,automaticAnalysis=true,connections={serverId=ServerConnectionSettings[connectionId=serverId,disableNotifications=true,organizationKey=myOrg,region=EU,serverUrl=serverUrl]},disableTelemetry=false,excludedRules=[repo1:rule1],focusOnNewCode=false,includedRules=[repo2:rule2],pathToNodeExecutable=path/to/node,ruleParameters={repo2:rule2={param1=value1}},showVerboseLogs=false]");
  }

}
