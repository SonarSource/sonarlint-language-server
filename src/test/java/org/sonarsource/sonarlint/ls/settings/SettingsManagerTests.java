/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClientProvider;
import org.sonarsource.sonarlint.ls.util.Utils;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class SettingsManagerTests {

  private static final URI FOLDER_URI = URI.create("file://foo");

  @RegisterExtension
  public SonarLintLogTester logTester = new SonarLintLogTester();

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
    "  \"pathToCompileCommands\": \"/pathToCompileCommand\",\n" +
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
    "    \"xoo:rule4\": {\n" +
    "      \"level\": \"on\",\n" +
    "      \"parameters\": { \"param1\": \"123\" }" +
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
  private WorkspaceFoldersManager foldersManager;

  @BeforeEach
  void prepare() {
    foldersManager = mock(WorkspaceFoldersManager.class);
    underTest = new SettingsManager(mock(LanguageClient.class), foldersManager, mock(ApacheHttpClientProvider.class));
    underTest = spy(underTest);
  }

  @Test
  void shouldParseFullWellFormedJsonWorkspaceFolderSettings() {
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    underTest.didChangeConfiguration();
    var settings = underTest.getCurrentDefaultFolderSettings();

    assertThat(settings.getTestMatcher().matches(new File("./someTest").toPath())).isFalse();
    assertThat(settings.getTestMatcher().matches(new File("./someTest.ext").toPath())).isTrue();
    assertThat(settings.getAnalyzerProperties()).containsExactly(entry("sonar.polop", "palap"));
    assertThat(settings.getPathToCompileCommands()).isEqualTo("/pathToCompileCommand");
    assertThat(settings.getConnectionId()).isEqualTo("sq1");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
  }

  private void mockConfigurationRequest(@Nullable URI uri, String json) {
    doReturn(CompletableFuture.supplyAsync(() -> fromJsonString(json))).when(underTest).requestSonarLintConfigurationAsync(uri);
  }

  @Test
  void shouldParseFullDeprecatedWellFormedJsonWorkspaceFolderSettings() {
    mockConfigurationRequest(null, DEPRECATED_SAMPLE_CONFIG);
    underTest.didChangeConfiguration();
    var settings = underTest.getCurrentDefaultFolderSettings();

    assertThat(settings.getConnectionId()).isEqualTo("server1");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
  }

  @Test
  void shouldParseFullWellFormedJsonWorkspaceSettings() {
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    underTest.didChangeConfiguration();
    var settings = underTest.getCurrentSettings();
    assertThat(settings.isDisableTelemetry()).isTrue();
    assertThat(settings.showAnalyzerLogs()).isTrue();
    assertThat(settings.showVerboseLogs()).isTrue();
    assertThat(settings.getExcludedRules()).extracting(RuleKey::repository, RuleKey::rule).containsOnly(tuple("xoo", "rule1"), tuple("xoo", "rule2"), tuple("xoo", "notEvenARule"));
    assertThat(settings.getIncludedRules()).extracting(RuleKey::repository, RuleKey::rule).containsOnly(tuple("xoo", "rule3"), tuple("xoo", "rule4"));
    assertThat(settings.getRuleParameters()).hasSize(1).containsOnlyKeys(RuleKey.parse("xoo:rule4"));
    assertThat(settings.getRuleParameters().get(RuleKey.parse("xoo:rule4"))).containsOnly(entry("param1", "123"));
    assertThat(settings.hasLocalRuleConfiguration()).isTrue();
    assertThat(settings.getServerConnections()).containsKeys("sq1", "sq2", "sc1", "sc2");
    assertThat(settings.getServerConnections().values())
      .extracting(ServerConnectionSettings::getConnectionId, ServerConnectionSettings::getServerUrl, ServerConnectionSettings::getToken,
        ServerConnectionSettings::getOrganizationKey)
      .containsExactlyInAnyOrder(
        tuple("sq1", "https://mysonarqube1.mycompany.org", "ab12", null),
        tuple("sq2", "https://mysonarqube2.mycompany.org", "cd34", null),
        tuple("sc1", "https://sonarcloud.io", "ab12", "myOrga1"),
        tuple("sc2", "https://sonarcloud.io", "cd34", "myOrga2"));
  }

  @Test
  void shouldLogErrorIfIncompleteConnections() {
    mockConfigurationRequest(null, "{\n" +
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
      "}\n");
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.getServerConnections()).isEmpty();
    assertThat(logTester.logs(Level.ERROR))
      .containsExactly("Incomplete server connection configuration. Required parameters must not be blank: serverId.",
        "Incomplete server connection configuration. Required parameters must not be blank: serverUrl.",
        "Incomplete server connection configuration. Required parameters must not be blank: token.",
        "Incomplete SonarQube server connection configuration. Required parameters must not be blank: token.",
        "Incomplete SonarQube server connection configuration. Required parameters must not be blank: serverUrl.",
        "Incomplete SonarCloud connection configuration. Required parameters must not be blank: organizationKey.",
        "Incomplete SonarCloud connection configuration. Required parameters must not be blank: token.");
  }

  @Test
  void shouldLogErrorIfDuplicateConnectionId() {
    mockConfigurationRequest(null, "{\n" +
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
      "}\n");
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.getServerConnections()).containsKeys("dup");
    assertThat(logTester.logs(Level.ERROR)).containsExactly("Multiple server connections with the same identifier 'dup'. Fix your settings.");
  }

  @Test
  void shouldLogErrorIfDuplicateConnectionsWithoutId() {
    mockConfigurationRequest(null, "{\n" +
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
      "}\n");
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.getServerConnections()).containsKeys("<default>");
    assertThat(logTester.logs(Level.ERROR)).containsExactly("Please specify a unique 'connectionId' in your settings for each of the SonarQube/SonarCloud connections.");
  }

  @Test
  void shouldParseFullDeprecatedWellFormedJsonWorkspaceSettings() {
    mockConfigurationRequest(null, DEPRECATED_SAMPLE_CONFIG);
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.getServerConnections()).containsKeys("server1", "sc");
    assertThat(settings.getServerConnections().values())
      .extracting(ServerConnectionSettings::getConnectionId, ServerConnectionSettings::getServerUrl, ServerConnectionSettings::getToken,
        ServerConnectionSettings::getOrganizationKey)
      .containsExactlyInAnyOrder(tuple("server1", "https://mysonarqube.mycompany.org", "ab12", null),
        tuple("sc", "https://sonarcloud.io", "cd34", "myOrga"));
  }

  @Test
  void shouldLogErrorIfNoConnectionToDefault() {
    mockConfigurationRequest(null, "{\n" +
      "  \"connectedMode\": {\n" +
      "    \"connections\": {\n" +
      "    },\n" +
      "    \"project\": {\n" +
      "      \"projectKey\": \"myProject\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n");
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentDefaultFolderSettings();
    assertThat(settings.getConnectionId()).isNull();
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
    assertThat(logTester.logs(Level.ERROR))
      .contains("No SonarQube/SonarCloud connections defined for your binding. Please update your settings.");
  }

  @Test
  void shouldDefaultIfOnlyOneConnectionId() {
    mockConfigurationRequest(null, "{\n" +
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
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentDefaultFolderSettings();
    assertThat(settings.getConnectionId()).isEqualTo("sq");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
  }

  @Test
  void shouldDefaultIfNoConnectionId() {
    mockConfigurationRequest(null, "{\n" +
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
    underTest.didChangeConfiguration();

    assertThat(underTest.getCurrentSettings().getServerConnections().keySet()).containsExactly("<default>");

    var settings = underTest.getCurrentDefaultFolderSettings();
    assertThat(settings.getConnectionId()).isEqualTo("<default>");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
  }

  @Test
  void shouldLogAnErrorIfAmbiguousConnectionId() {
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);

    mockConfigurationRequest(FOLDER_URI, "{\n" +
      "  \"connectedMode\": {\n" +
      "    \"project\": {\n" +
      "      \"projectKey\": \"myProject\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n");
    var folderWrapper = new WorkspaceFolderWrapper(FOLDER_URI, new WorkspaceFolder());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    var settings = folderWrapper.getSettings();
    assertThat(settings.getConnectionId()).isNull();
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
    assertThat(logTester.logs(Level.ERROR))
      .containsExactly("Multiple connections defined in your settings. Please specify a 'connectionId' in your binding with one of [sc1,sq1,sc2,sq2] to disambiguate.");
  }

  @Test
  void shouldLogAnErrorIfUnknownConnectionId() {
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);

    mockConfigurationRequest(FOLDER_URI, "{\n" +
      "  \"connectedMode\": {\n" +
      "    \"project\": {\n" +
      "      \"connectionId\": \"unknown\",\n" +
      "      \"projectKey\": \"myProject\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n");
    var folderWrapper = new WorkspaceFolderWrapper(FOLDER_URI, new WorkspaceFolder());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    var settings = folderWrapper.getSettings();
    assertThat(settings.getConnectionId()).isEqualTo("unknown");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
    assertThat(logTester.logs(Level.ERROR))
      .containsExactly("No SonarQube/SonarCloud connections defined for your binding with id 'unknown'. Please update your settings.");
  }

  @Test
  void shouldHaveLocalRuleConfigurationWithDisabledRule() {
    mockConfigurationRequest(null, "{\n" +
      "  \"rules\": {\n" +
      "    \"xoo:rule1\": {\n" +
      "      \"level\": \"off\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n");
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.hasLocalRuleConfiguration()).isTrue();
  }

  @Test
  void shouldHaveLocalRuleConfigurationWithEnabledRule() {
    mockConfigurationRequest(null, "{\n" +
      "  \"rules\": {\n" +
      "    \"xoo:rule1\": {\n" +
      "      \"level\": \"on\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n");
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.hasLocalRuleConfiguration()).isTrue();
  }

  @Test
  void shouldParseScalarParameterValuesWithSomeTolerance() {
    mockConfigurationRequest(null, "{\n" +
      "  \"rules\": {\n" +
      "    \"xoo:rule1\": {\n" +
      "      \"level\": \"on\",\n" +
      "      \"parameters\": {\n" +
      "        \"intParam\": 42,\n" +
      "        \"floatParam\": 123.456,\n" +
      "        \"boolParam\": true,\n" +
      "        \"nullParam\": null,\n" +
      "        \"stringParam\": \"you get the picture\"\n" +
      "      }\n" +
      "    }\n" +
      "  }\n" +
      "}\n");
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    var key = RuleKey.parse("xoo:rule1");
    assertThat(settings.getRuleParameters()).containsOnlyKeys(key);
    assertThat(settings.getRuleParameters().get(key)).containsOnly(
      entry("intParam", "42"),
      entry("floatParam", "123.456"),
      entry("boolParam", "true"),
      entry("stringParam", "you get the picture"));
  }

  @Test
  void workspaceFolderVariableForPathToCompileCommands() {
    var config = "{\n" +
      "  \"testFilePattern\": \"**/*Test.*\",\n" +
      "  \"pathToCompileCommands\": \"${workspaceFolder}/pathToCompileCommand\",\n" +
      "  \"disableTelemetry\": true,\n" +
      "  \"output\": {\n" +
      "  \"showAnalyzerLogs\": true,\n" +
      "  \"showVerboseLogs\": true\n"
      + "}\n" +
      "}\n";
    var workspaceFolderUri = URI.create("file:///workspace/folder");
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    mockConfigurationRequest(workspaceFolderUri, config);
    var folderWrapper = new WorkspaceFolderWrapper(workspaceFolderUri, new WorkspaceFolder());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    var settings = folderWrapper.getSettings();
    assertThat(settings.getPathToCompileCommands()).isEqualTo("file:///workspace/folder/pathToCompileCommand");
  }

  @Test
  void workspaceFolderVariableShouldNotWorkForGlobalConfiguration() {
    var config = "{\n" +
      "  \"testFilePattern\": \"**/*Test.*\",\n" +
      "  \"pathToCompileCommands\": \"${workspaceFolder}/pathToCompileCommand\",\n" +
      "  \"disableTelemetry\": true,\n" +
      "  \"output\": {\n" +
      "  \"showAnalyzerLogs\": true,\n" +
      "  \"showVerboseLogs\": true\n"
      + "}\n" +
      "}\n";
    mockConfigurationRequest(null, config);

    underTest.didChangeConfiguration();
    underTest.getCurrentSettings();

    assertThat(logTester.logs(Level.WARN))
      .containsExactly("Using ${workspaceFolder} variable in sonarlint.pathToCompileCommands is only supported for files in the workspace");
  }

  @Test
  void pathToCompileCommandsWithoutWorkspaceFolderVariableForGlobalConfigShouldBeAccepted() {
    var config = "{\n" +
      "  \"testFilePattern\": \"**/*Test.*\",\n" +
      "  \"pathToCompileCommands\": \"/pathToCompileCommand\",\n" +
      "  \"disableTelemetry\": true,\n" +
      "  \"output\": {\n" +
      "  \"showAnalyzerLogs\": true,\n" +
      "  \"showVerboseLogs\": true\n"
      + "}\n" +
      "}\n";
    mockConfigurationRequest(null, config);

    underTest.didChangeConfiguration();
    var settings = underTest.getCurrentDefaultFolderSettings();

    assertThat(settings.getPathToCompileCommands()).isEqualTo("/pathToCompileCommand");
  }

  @Test
  void workspaceFolderVariableShouldBePrefixOfPropertyValue() {
    var config = "{\n" +
      "  \"testFilePattern\": \"**/*Test.*\",\n" +
      "  \"pathToCompileCommands\": \"something${workspaceFolder}/pathToCompileCommand\",\n" +
      "  \"disableTelemetry\": true,\n" +
      "  \"output\": {\n" +
      "  \"showAnalyzerLogs\": true,\n" +
      "  \"showVerboseLogs\": true\n"
      + "}\n" +
      "}\n";
    mockConfigurationRequest(null, config);

    underTest.didChangeConfiguration();
    underTest.getCurrentSettings();

    assertThat(logTester.logs(Level.ERROR))
      .containsExactly("Variable ${workspaceFolder} for sonarlint.pathToCompileCommands should be the prefix.");
  }

  @Test
  void failForNotValidWorkspaceFolderPath() {
    var config = "{\n" +
      "  \"testFilePattern\": \"**/*Test.*\",\n" +
      "  \"pathToCompileCommands\": \"${workspaceFolder}/pathToCompileCommand\",\n" +
      "  \"disableTelemetry\": true,\n" +
      "  \"output\": {\n" +
      "  \"showAnalyzerLogs\": true,\n" +
      "  \"showVerboseLogs\": true\n"
      + "}\n" +
      "}\n";
    var workspaceFolderUri = URI.create("notfile:///workspace/folder");
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    mockConfigurationRequest(workspaceFolderUri, config);
    var folderWrapper = new WorkspaceFolderWrapper(workspaceFolderUri, new WorkspaceFolder());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    folderWrapper.getSettings();
    assertThat(logTester.logs(Level.ERROR))
      .contains("Workspace folder is not in local filesystem, analysis not supported.");
  }

  private static Map<String, Object> fromJsonString(String json) {
    return Utils.parseToMap(new Gson().fromJson(json, JsonElement.class));
  }
}
