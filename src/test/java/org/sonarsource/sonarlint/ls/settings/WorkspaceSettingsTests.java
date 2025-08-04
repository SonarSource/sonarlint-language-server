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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceSettingsTests {

  public static final RuleKey RULE_KEY_1 = RuleKey.of("repo1", "rule1");
  public static final RuleKey RULE_KEY_2 = RuleKey.of("repo2", "rule2");
  public static final Map<String, String> REPORT_ISSUES_AS_OVERRIDES = Map.of("repo2:rule2", "Error");
  private static final WorkspaceSettings SETTINGS = new WorkspaceSettings(false,
    Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
    List.of(RULE_KEY_1),
    List.of(RULE_KEY_2),
    Map.of(RULE_KEY_2, Map.of("param1", "value1")),
    false, false, "path/to/node", false, "", "None", REPORT_ISSUES_AS_OVERRIDES);

  @Test
  void testHashCode() {
    assertThat(new WorkspaceSettings(false,
      Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
      List.of(RULE_KEY_1),
      List.of(RULE_KEY_2),
      Map.of(RULE_KEY_2, Map.of("param1", "value1")),
      false, false, "path/to/node", false, "", "None", Collections.emptyMap())).hasSameHashCodeAs(SETTINGS);
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
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, false, "path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(true,
        Map.of("serverId2", new ServerConnectionSettings("serverId2", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, false, "path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl2", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, false, "path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token2", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, false, "path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg2", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, false, "path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule12")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, false, "path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule22")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, false, "path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule22")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, false, "path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RULE_KEY_1),
        List.of(RULE_KEY_2),
        Map.of(RULE_KEY_2, Map.of("param1", "value2")), false, false, "path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), true, false, "path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, true, "path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RULE_KEY_1),
        List.of(RULE_KEY_2),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")),
        false, false, "other/path/to/node", false, "", "None", Collections.emptyMap()))
      .isNotEqualTo(new WorkspaceSettings(false,
        Map.of("serverId", new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", true, SonarCloudRegion.EU)),
        List.of(RuleKey.of("repo1", "rule1")),
        List.of(RuleKey.of("repo2", "rule2")),
        Map.of(RULE_KEY_2, Map.of("param1", "value1")), false, false, "path/to/node", true, "", "Off", Collections.emptyMap()));
  }

  @Test
  void testToString() {
    assertThat(SETTINGS).hasToString(
      "WorkspaceSettings[analysisExcludes=,connections={serverId=ServerConnectionSettings[connectionId=serverId,disableNotifications=true,organizationKey=myOrg,region=EU," +
        "serverUrl=serverUrl]},disableTelemetry=false,excludedRules=[repo1:rule1],focusOnNewCode=false,includedRules=[repo2:rule2],pathToNodeExecutable=path/to/node," +
        "reportIssuesAsErrorLevel=None,reportIssuesAsErrorOverrides={repo2:rule2=Error}," +
        "ruleParameters={repo2:rule2={param1=value1}},showAnalyzerLogs=false,showVerboseLogs=false]");
  }

}
