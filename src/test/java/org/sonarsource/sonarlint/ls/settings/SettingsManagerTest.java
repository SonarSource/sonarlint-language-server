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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.File;
import java.util.Map;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.ls.Utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

class SettingsManagerTest {

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private static final String DEPRECATED_SAMPLE_CONFIG = "{\n" +
    "  \"connectedMode\": {\n" +
    "    \"servers\": [\n" +
    "      { \"serverId\": \"server1\", \"serverUrl\": \"https://mysonarqube.mycompany.org\", \"token\": \"ab12\" }," +
    "      { \"serverId\": \"sc\", \"serverUrl\": \"https://sonarcloud.io\", \"token\": \"cd34\", \"organizationKey\": \"myOrga\" }" +
    "    ],\n" +
    "    \"project\": {\n" +
    "      \"serverId\": \"server1\",\n" +
    "      \"projectKey\": \"myProject\"\n" +
    "    }\n" +
    "  }\n" +
    "}\n";

  private static final String FULL_SAMPLE_CONFIG = "{\n" +
    "  \"testFilePattern\": \"**/*Test.*\",\n" +
    "  \"analyzerProperties\": {\n" +
    "    \"sonar.polop\": \"palap\"\n" +
    "  },\n" +
    "  \"disableTelemetry\": true,\n"
    + "\"output\": {\n" +
    "  \"showAnalyzerLogs\": true,\n" +
    "  \"showVerboseLogs\": true\n"
    + "},\n" +
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
    "    \"connections\": {\n" +
    "      \"sonarqube\": [\n" +
    "        { \"connectionId\": \"sq1\", \"serverUrl\": \"https://mysonarqube1.mycompany.org\", \"token\": \"ab12\" }," +
    "        { \"connectionId\": \"sq2\", \"serverUrl\": \"https://mysonarqube2.mycompany.org\", \"token\": \"cd34\" }" +
    "      ],\n" +
    "      \"sonarcloud\": [\n" +
    "        { \"connectionId\": \"sc1\", \"token\": \"ab12\", \"organizationKey\": \"myOrga1\" }," +
    "        { \"connectionId\": \"sc2\", \"token\": \"cd34\", \"organizationKey\": \"myOrga2\" }" +
    "      ]\n" +
    "    },\n" +
    "    \"project\": {\n" +
    "      \"connectionId\": \"sq1\",\n" +
    "      \"projectKey\": \"myProject\"\n" +
    "    }\n" +
    "  }\n" +
    "}\n";

  private SettingsManager underTest;

  @BeforeEach
  public void prepare() {
    underTest = new SettingsManager(mock(LanguageClient.class));
  }

  @Test
  public void shouldParseFullWellFormedJsonWorkspaceFolderSettings() {
    underTest.update(fromJsonString(FULL_SAMPLE_CONFIG));
    WorkspaceFolderSettings settings = underTest.parseFolderSettings(fromJsonString(FULL_SAMPLE_CONFIG));

    assertThat(settings.getTestMatcher().matches(new File("./someTest").toPath())).isFalse();
    assertThat(settings.getTestMatcher().matches(new File("./someTest.ext").toPath())).isTrue();
    assertThat(settings.getAnalyzerProperties()).containsExactly(entry("sonar.polop", "palap"));
    assertThat(settings.getConnectionId()).isEqualTo("sq1");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
  }

  @Test
  public void shouldParseFullDeprecatedWellFormedJsonWorkspaceFolderSettings() {
    underTest.update(fromJsonString(DEPRECATED_SAMPLE_CONFIG));
    WorkspaceFolderSettings settings = underTest.parseFolderSettings(fromJsonString(DEPRECATED_SAMPLE_CONFIG));

    assertThat(settings.getConnectionId()).isEqualTo("server1");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
  }

  @Test
  public void shouldParseFullWellFormedJsonWorkspaceSettings() {
    underTest.update(fromJsonString(FULL_SAMPLE_CONFIG));

    WorkspaceSettings settings = underTest.getCurrentSettings();
    assertThat(settings.isDisableTelemetry()).isTrue();
    assertThat(settings.showAnalyzerLogs()).isTrue();
    assertThat(settings.showVerboseLogs()).isTrue();
    assertThat(settings.getExcludedRules()).extracting(RuleKey::repository, RuleKey::rule).containsExactly(tuple("xoo", "rule1"));
    assertThat(settings.getExcludedRules()).extracting(RuleKey::repository, RuleKey::rule).containsExactly(tuple("xoo", "rule1"));
    assertThat(settings.getIncludedRules()).extracting(RuleKey::repository, RuleKey::rule).containsExactly(tuple("xoo", "rule3"));
    assertThat(settings.hasLocalRuleConfiguration()).isTrue();
    assertThat(settings.getServers()).containsKeys("sq1", "sq2", "sc1", "sc2");
    assertThat(settings.getServers().values())
      .extracting(ServerConnectionSettings::getServerId, ServerConnectionSettings::getServerUrl, ServerConnectionSettings::getToken, ServerConnectionSettings::getOrganizationKey)
      .containsExactlyInAnyOrder(
        tuple("sq1", "https://mysonarqube1.mycompany.org", "ab12", null),
        tuple("sq2", "https://mysonarqube2.mycompany.org", "cd34", null),
        tuple("sc1", "https://sonarcloud.io", "ab12", "myOrga1"),
        tuple("sc2", "https://sonarcloud.io", "cd34", "myOrga2"));
  }

  @Test
  public void shouldLogErrorIfIncompleteConnections() {
    underTest.update(fromJsonString("{\n" +
      "  \"connectedMode\": {\n" +
      "    \"servers\": [\n" +
      "      { \"serverUrl\": \"https://mysonarqube.mycompany.org\", \"token\": \"ab12\" }," +
      "      { \"serverId\": \"server1\", \"token\": \"ab12\" }," +
      "      { \"serverId\": \"server1\", \"serverUrl\": \"https://mysonarqube.mycompany.org\" }" +
      "    ],\n" +
      "    \"connections\": {\n" +
      "      \"sonarqube\": [\n" +
      "        { \"serverUrl\": \"https://mysonarqube1.mycompany.org\" }," +
      "        { \"token\": \"cd34\" }" +
      "      ],\n" +
      "      \"sonarcloud\": [\n" +
      "        { \"token\": \"ab12\" }," +
      "        { \"organizationKey\": \"myOrga2\" }" +
      "      ]\n" +
      "    }\n" +
      "  }\n" +
      "}\n"));

    WorkspaceSettings settings = underTest.getCurrentSettings();
    assertThat(settings.getServers()).isEmpty();
    assertThat(logTester.logs(LoggerLevel.ERROR))
      .containsExactly("Incomplete server connection configuration. Required parameters must not be blank: serverId.",
        "Incomplete server connection configuration. Required parameters must not be blank: serverUrl.",
        "Incomplete server connection configuration. Required parameters must not be blank: token.",
        "Incomplete SonarQube server connection configuration. Required parameters must not be blank: token.",
        "Incomplete SonarQube server connection configuration. Required parameters must not be blank: serverUrl.",
        "Incomplete SonarCloud connection configuration. Required parameters must not be blank: organizationKey.",
        "Incomplete SonarCloud connection configuration. Required parameters must not be blank: token.");
  }

  @Test
  public void shouldLogErrorIfDuplicateConnectionId() {
    underTest.update(fromJsonString("{\n" +
      "  \"connectedMode\": {\n" +
      "    \"connections\": {\n" +
      "      \"sonarqube\": [\n" +
      "        { \"connectionId\": \"dup\", \"serverUrl\": \"https://mysonarqube1.mycompany.org\", \"token\": \"ab12\" }" +
      "      ],\n" +
      "      \"sonarcloud\": [\n" +
      "        { \"connectionId\": \"dup\", \"token\": \"ab12\", \"organizationKey\": \"myOrga1\" }" +
      "      ]\n" +
      "    }\n" +
      "  }\n" +
      "}\n"));

    WorkspaceSettings settings = underTest.getCurrentSettings();
    assertThat(settings.getServers()).containsKeys("dup");
    assertThat(logTester.logs(LoggerLevel.ERROR)).containsExactly("Multiple server connections with the same identifier 'dup'. Fix your settings.");
  }

  @Test
  public void shouldLogErrorIfDuplicateConnectionsWithoutId() {
    underTest.update(fromJsonString("{\n" +
      "  \"connectedMode\": {\n" +
      "    \"connections\": {\n" +
      "      \"sonarqube\": [\n" +
      "        { \"serverUrl\": \"https://mysonarqube1.mycompany.org\", \"token\": \"ab12\" }" +
      "      ],\n" +
      "      \"sonarcloud\": [\n" +
      "        { \"token\": \"ab12\", \"organizationKey\": \"myOrga1\" }" +
      "      ]\n" +
      "    }\n" +
      "  }\n" +
      "}\n"));

    WorkspaceSettings settings = underTest.getCurrentSettings();
    assertThat(settings.getServers()).containsKeys("<default>");
    assertThat(logTester.logs(LoggerLevel.ERROR)).containsExactly("Please specify a unique 'connectionId' in your settings for each of the SonarQube/SonarCloud connections.");
  }

  @Test
  public void shouldParseFullDeprecatedWellFormedJsonWorkspaceSettings() {
    underTest.update(fromJsonString(DEPRECATED_SAMPLE_CONFIG));

    WorkspaceSettings settings = underTest.getCurrentSettings();
    assertThat(settings.getServers()).containsKeys("server1", "sc");
    assertThat(settings.getServers().values())
      .extracting(ServerConnectionSettings::getServerId, ServerConnectionSettings::getServerUrl, ServerConnectionSettings::getToken, ServerConnectionSettings::getOrganizationKey)
      .containsExactlyInAnyOrder(tuple("server1", "https://mysonarqube.mycompany.org", "ab12", null),
        tuple("sc", "https://sonarcloud.io", "cd34", "myOrga"));
  }

  @Test
  public void shouldLogErrorIfNoConnectionToDefault() {
    Map<String, Object> defaultConnectionId = fromJsonString("{\n" +
      "  \"connectedMode\": {\n" +
      "    \"connections\": {\n" +
      "    },\n" +
      "    \"project\": {\n" +
      "      \"projectKey\": \"myProject\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n");
    underTest.update(defaultConnectionId);

    WorkspaceFolderSettings settings = underTest.parseFolderSettings(defaultConnectionId);
    assertThat(settings.getConnectionId()).isNull();
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
    assertThat(logTester.logs(LoggerLevel.ERROR))
      .contains("No SonarQube/SonarCloud connections defined for your binding. Please update your settings.");
  }

  @Test
  public void shouldDefaultIfOnlyOneConnectionId() {
    Map<String, Object> defaultConnectionId = fromJsonString("{\n" +
      "  \"connectedMode\": {\n" +
      "    \"connections\": {\n" +
      "      \"sonarqube\": [\n" +
      "        { \"connectionId\": \"sq\", \"serverUrl\": \"https://mysonarqube2.mycompany.org\", \"token\": \"cd34\" }" +
      "      ]\n" +
      "    },\n" +
      "    \"project\": {\n" +
      "      \"projectKey\": \"myProject\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n");
    underTest.update(defaultConnectionId);

    WorkspaceFolderSettings settings = underTest.parseFolderSettings(defaultConnectionId);
    assertThat(settings.getConnectionId()).isEqualTo("sq");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
  }

  @Test
  public void shouldDefaultIfNoConnectionId() {
    Map<String, Object> noConnectionsId = fromJsonString("{\n" +
      "  \"connectedMode\": {\n" +
      "    \"connections\": {\n" +
      "      \"sonarqube\": [\n" +
      "        { \"serverUrl\": \"https://mysonarqube2.mycompany.org\", \"token\": \"cd34\" }" +
      "      ]\n" +
      "    },\n" +
      "    \"project\": {\n" +
      "      \"projectKey\": \"myProject\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n");
    underTest.update(noConnectionsId);

    assertThat(underTest.getCurrentSettings().getServers().keySet()).containsExactly("<default>");

    WorkspaceFolderSettings settings = underTest.parseFolderSettings(noConnectionsId);
    assertThat(settings.getConnectionId()).isEqualTo("<default>");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
  }

  @Test
  public void shouldLogAnErrorIfAmbiguousConnectionId() {
    underTest.update(fromJsonString(FULL_SAMPLE_CONFIG));

    WorkspaceFolderSettings settings = underTest.parseFolderSettings(fromJsonString("{\n" +
      "  \"connectedMode\": {\n" +
      "    \"project\": {\n" +
      "      \"projectKey\": \"myProject\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n"));
    assertThat(settings.getConnectionId()).isNull();
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
    assertThat(logTester.logs(LoggerLevel.ERROR))
      .containsExactly("Multiple connections defined in your settings. Please specify a 'connectionId' in your binding with one of [sc1,sq1,sc2,sq2] to disambiguate.");
  }

  @Test
  public void shouldLogAnErrorIfUnknownConnectionId() {
    underTest.update(fromJsonString(FULL_SAMPLE_CONFIG));

    WorkspaceFolderSettings settings = underTest.parseFolderSettings(fromJsonString("{\n" +
      "  \"connectedMode\": {\n" +
      "    \"project\": {\n" +
      "      \"connectionId\": \"unknown\",\n" +
      "      \"projectKey\": \"myProject\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n"));
    assertThat(settings.getConnectionId()).isEqualTo("unknown");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
    assertThat(logTester.logs(LoggerLevel.ERROR))
      .containsExactly("No SonarQube/SonarCloud connections defined for your binding with id 'unknown'. Please update your settings.");
  }

  @Test
  public void shouldHaveLocalRuleConfigurationWithDisabledRule() {
    underTest.update(fromJsonString("{\n" +
      "  \"rules\": {\n" +
      "    \"xoo:rule1\": {\n" +
      "      \"level\": \"off\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n"));

    WorkspaceSettings settings = underTest.getCurrentSettings();
    assertThat(settings.hasLocalRuleConfiguration()).isTrue();
  }

  @Test
  public void shouldHaveLocalRuleConfigurationWithEnabledRule() {
    underTest.update(fromJsonString("{\n" +
      "  \"rules\": {\n" +
      "    \"xoo:rule1\": {\n" +
      "      \"level\": \"on\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n"));

    WorkspaceSettings settings = underTest.getCurrentSettings();
    assertThat(settings.hasLocalRuleConfiguration()).isTrue();
  }

  private static Map<String, Object> fromJsonString(String json) {
    return Utils.parseToMap(new Gson().fromJson(json, JsonElement.class));
  }
}
