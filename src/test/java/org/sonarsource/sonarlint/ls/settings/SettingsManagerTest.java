/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.ls.Utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

class SettingsManagerTest {

  private static final String FULL_SAMPLE_CONFIG = "{\n" +
    "  \"testFilePattern\": \"**/*Test.*\",\n" +
    "  \"analyzerProperties\": {\n" +
    "    \"sonar.polop\": \"palap\"\n" +
    "  },\n" +
    "  \"disableTelemetry\": true,\n" +
    "  \"rules\": {\n" +
    "    \"xoo:rule1\": {\n" +
    "      \"level\": \"off\"\n" +
    "    },\n" +
    "    \"xoo:rule2\": {\n" +
    "      \"level\": \"warn\"\n" +
    "    },\n" +
    "    \"xoo:rule3\": {\n" +
    "      \"level\": \"on\"\n" +
    "    },\n" +
    "    \"xoo:notEvenARule\": \"definitely not a rule\",\n" +
    "    \"somethingNotParsedByRuleKey\": {\n" +
    "      \"level\": \"off\"\n" +
    "    }\n" +
    "  },\n" +
    "  \"connectedMode\": {\n" +
    "    \"servers\": [\n" +
    "      { \"serverId\": \"server1\", \"serverUrl\": \"https://mysonarqube.mycompany.org\", \"token\": \"ab12\" }," +
    "      { \"serverId\": \"sc\", \"serverUrl\": \"https://sonarcloud.io\", \"token\": \"cd34\", \"organizationKey\": \"myOrga\" }" +
    "    ]\n" +
    "  }\n" +
    "}\n";

  @Test
  public void shouldParseFullWellFormedJsonWorkspaceFolderSettings() {
    WorkspaceFolderSettings settings = SettingsManager.parseFolderSettings(fromJsonString(FULL_SAMPLE_CONFIG));

    assertThat(settings.getTestMatcher().matches(new File("./someTest").toPath())).isFalse();
    assertThat(settings.getTestMatcher().matches(new File("./someTest.ext").toPath())).isTrue();
    assertThat(settings.getAnalyzerProperties()).containsExactly(entry("sonar.polop", "palap"));
  }

  @Test
  public void shouldParseFullWellFormedJsonWorkspaceSettings() {
    WorkspaceSettings settings = SettingsManager.parseSettings(fromJsonString(FULL_SAMPLE_CONFIG));

    assertThat(settings.isDisableTelemetry()).isTrue();
    assertThat(settings.getExcludedRules()).extracting(RuleKey::repository, RuleKey::rule).containsExactly(tuple("xoo", "rule1"));
    assertThat(settings.getExcludedRules()).extracting(RuleKey::repository, RuleKey::rule).containsExactly(tuple("xoo", "rule1"));
    assertThat(settings.getIncludedRules()).extracting(RuleKey::repository, RuleKey::rule).containsExactly(tuple("xoo", "rule3"));
    assertThat(settings.hasLocalRuleConfiguration()).isTrue();
    assertThat(settings.getServers()).containsKeys("server1", "sc");
    assertThat(settings.getServers().values())
      .extracting(ServerConnectionSettings::getServerId, ServerConnectionSettings::getServerUrl, ServerConnectionSettings::getToken, ServerConnectionSettings::getOrganizationKey)
      .containsExactlyInAnyOrder(tuple("server1", "https://mysonarqube.mycompany.org", "ab12", null),
        tuple("sc", "https://sonarcloud.io", "cd34", "myOrga"));
  }

  @Test
  public void shouldHaveLocalRuleConfigurationWithDisabledRule() {
    assertThat(SettingsManager.parseSettings(fromJsonString("{\n" +
      "  \"rules\": {\n" +
      "    \"xoo:rule1\": {\n" +
      "      \"level\": \"off\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n")).hasLocalRuleConfiguration()).isTrue();
  }

  @Test
  public void shouldHaveLocalRuleConfigurationWithEnabledRule() {
    assertThat(SettingsManager.parseSettings(fromJsonString("{\n" +
      "  \"rules\": {\n" +
      "    \"xoo:rule1\": {\n" +
      "      \"level\": \"on\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n")).hasLocalRuleConfiguration()).isTrue();
  }

  private static Map<String, Object> fromJsonString(String json) {
    return Utils.parseToMap(new Gson().fromJson(json, JsonElement.class));
  }
}
