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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.backend.BackendInitParams;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.util.Utils;
import testutils.ImmediateExecutorService;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.ANALYZER_PROPERTIES;

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
  private ProjectBindingManager bindingManager;
  private SonarLintExtendedLanguageClient client;

  @BeforeEach
  void prepare() {
    foldersManager = mock(WorkspaceFoldersManager.class);
    bindingManager = mock(ProjectBindingManager.class);
    client = mock(SonarLintExtendedLanguageClient.class);
    when(client.getTokenForServer(any())).thenReturn(CompletableFuture.supplyAsync(() -> "token-from-storage"));
    var backendFacade = mock(BackendServiceFacade.class);
    var backend = mock(BackendService.class);
    when(backendFacade.getInitParams()).thenReturn(new BackendInitParams());
    when(backendFacade.getBackendService()).thenReturn(backend);
    underTest = new SettingsManager(client, foldersManager, new ImmediateExecutorService(), backendFacade);
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
    doReturn(CompletableFuture.supplyAsync(() -> fromJsonString(json))).when(underTest).requestSonarLintAndOmnisharpConfigurationAsync(uri);
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
        tuple("sq1", "https://mysonarqube1.mycompany.org", "token-from-storage", null),
        tuple("sq2", "https://mysonarqube2.mycompany.org", "token-from-storage", null),
        tuple("sc1", "https://sonarcloud.io", "token-from-storage", "myOrga1"),
        tuple("sc2", "https://sonarcloud.io", "token-from-storage", "myOrga2"));
  }

  @Test
  void shouldLogErrorIfIncompleteConnections() {
    mockConfigurationRequest(null, "{\n" +
      "  \"connectedMode\": {\n" +
      "    \"servers\": [\n" +
      "      { \"serverUrl\": \"https://mysonarqube.mycompany.org\", \"token\": \"ab12\" }," +
      "      { \"serverId\": \"server1\", \"token\": \"ab12\" }" +
      "    ],\n" +
      "    \"connections\": {\n" +
      "      \"sonarqube\": [\n" +
      "        { \"token\": \"cd34\" }" +
      "      ],\n" +
      "      \"sonarcloud\": [\n" +
      "        { \"token\": \"ab12\" }" +
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
        "Incomplete SonarQube server connection configuration. Required parameters must not be blank: serverUrl.",
        "Incomplete SonarCloud connection configuration. Required parameters must not be blank: organizationKey.");
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
  void workspaceFolderVariableForPathToCompileCommands(@TempDir Path workspaceFolder) {
    var config = "{\n" +
      "  \"testFilePattern\": \"**/*Test.*\",\n" +
      "  \"pathToCompileCommands\": \"${workspaceFolder}/pathToCompileCommand\",\n" +
      "  \"disableTelemetry\": true,\n" +
      "  \"output\": {\n" +
      "  \"showAnalyzerLogs\": true,\n" +
      "  \"showVerboseLogs\": true\n"
      + "}\n" +
      "}\n";
    var workspaceFolderUri = workspaceFolder.toUri();
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    mockConfigurationRequest(workspaceFolderUri, config);
    var folderWrapper = new WorkspaceFolderWrapper(workspaceFolderUri, new WorkspaceFolder());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    var settings = folderWrapper.getSettings();
    assertThat(settings.getPathToCompileCommands()).isEqualTo(workspaceFolder.resolve("pathToCompileCommand").toString());
  }

  @Test
  void workspaceFolderVariableForPathToCompileCommandsShouldWorkWithoutFileSeparator(@TempDir Path workspaceFolder) {
    var config = "{\n" +
      "  \"testFilePattern\": \"**/*Test.*\",\n" +
      "  \"pathToCompileCommands\": \"${workspaceFolder}pathToCompileCommand\",\n" +
      "  \"disableTelemetry\": true,\n" +
      "  \"output\": {\n" +
      "  \"showAnalyzerLogs\": true,\n" +
      "  \"showVerboseLogs\": true\n"
      + "}\n" +
      "}\n";
    var workspaceFolderUri = workspaceFolder.toUri();
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    mockConfigurationRequest(workspaceFolderUri, config);
    var folderWrapper = new WorkspaceFolderWrapper(workspaceFolderUri, new WorkspaceFolder());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    var settings = folderWrapper.getSettings();
    assertThat(settings.getPathToCompileCommands()).isEqualTo(workspaceFolder.resolve("pathToCompileCommand").toString());
  }

  @Test
  void workspaceFolderVariableForPathToCompileCommandsShouldWorkWithWindowsFileSeparator(@TempDir Path workspaceFolder) {
    var config = "{\n" +
      "  \"testFilePattern\": \"**/*Test.*\",\n" +
      "  \"pathToCompileCommands\": \"${workspaceFolder}\\\\pathToCompileCommand\",\n" +
      "  \"disableTelemetry\": true,\n" +
      "  \"output\": {\n" +
      "  \"showAnalyzerLogs\": true,\n" +
      "  \"showVerboseLogs\": true\n"
      + "}\n" +
      "}\n";
    var workspaceFolderUri = workspaceFolder.toUri();
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    mockConfigurationRequest(workspaceFolderUri, config);
    var folderWrapper = new WorkspaceFolderWrapper(workspaceFolderUri, new WorkspaceFolder());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    var settings = folderWrapper.getSettings();
    assertThat(settings.getPathToCompileCommands()).isEqualTo(workspaceFolder.resolve("pathToCompileCommand").toString());
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

  @Test
  void ifCanNotGetTokenFromClientDueToInterruptedExceptionShouldLogError() {
    when(client.getTokenForServer(any())).thenReturn(CompletableFuture.failedFuture(new InterruptedException()));
    mockConfigurationRequest(null, "{\n" +
      "  \"connectedMode\": {\n" +
      "    \"connections\": {\n" +
      "      \"sonarqube\": [\n" +
      "        { \"connectionId\": \"sq1\", \"serverUrl\": \"https://mysonarqube1.mycompany.org\", \"token\": \"ab12\" }," +
      "      ]\n" +
      "    },\n" +
      "    \"project\": {\n" +
      "      \"connectionId\": \"sq1\",\n" +
      "      \"projectKey\": \"myProject\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n");

    underTest.didChangeConfiguration();

    assertThat(logTester.logs(Level.ERROR))
      .contains("Can't get token for server https://mysonarqube1.mycompany.org");
  }

  @Test
  void ifCanNotGetTokenFromClientDueToExcecutionExceptionShouldLogError() {
    when(client.getTokenForServer(any())).thenReturn(CompletableFuture.failedFuture(new ExecutionException(new IllegalStateException())));
    mockConfigurationRequest(null, "{\n" +
      "  \"connectedMode\": {\n" +
      "    \"connections\": {\n" +
      "      \"sonarqube\": [\n" +
      "        { \"connectionId\": \"sq1\", \"serverUrl\": \"https://mysonarqube1.mycompany.org\", \"token\": \"ab12\" }," +
      "      ]\n" +
      "    },\n" +
      "    \"project\": {\n" +
      "      \"connectionId\": \"sq1\",\n" +
      "      \"projectKey\": \"myProject\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n");

    underTest.didChangeConfiguration();

    assertThat(logTester.logs(Level.ERROR))
      .contains("Can't get token for server https://mysonarqube1.mycompany.org");
  }

  @Test
  void shouldReturnUntouchedNonNullConnectionId() {
    var connectionId = "connectionId";
    assertThat(SettingsManager.connectionIdOrDefault(connectionId)).isEqualTo(connectionId);
  }

  @Test
  void shouldReturnDefaultConnectionIdIfNull() {
    assertThat(SettingsManager.connectionIdOrDefault(null)).isEqualTo(SettingsManager.DEFAULT_CONNECTION_ID);
  }

  @Test
  void shouldUpdateAnalyzerProperties() {
    var workspaceUri = URI.create("file:///User/user/documents/project");
    List<Object> response = List.of("{\"disableTelemetry\": false,\"focusOnNewCode\": true}",
      new JsonPrimitive("Roslyn.sln"),
      new JsonPrimitive("true"),
      new JsonPrimitive("false"),
      new JsonPrimitive("600"));
    Map<String, Object> settingsMap = new HashMap<>(Map.of("disableTelemetry", false, "focusOnNewCode", true));

    var result = SettingsManager.updateAnalyzerProperties(workspaceUri, response, settingsMap);

    assertThat(result).containsKey(ANALYZER_PROPERTIES);
    var analyzerProperties = (Map<String, String>) result.get(ANALYZER_PROPERTIES);
    assertThat(analyzerProperties).contains(entry("sonar.cs.internal.useNet6", "true"),
      entry("sonar.cs.internal.loadProjectOnDemand", "false"),
      entry("sonar.cs.internal.loadProjectsTimeout", "600"));
    assertThat(analyzerProperties.get("sonar.cs.internal.solutionPath")).endsWith("Roslyn.sln");
  }

  @Test
  void shouldIgnoreRazorFiles() {
    var workspaceUri = URI.create("file:///User/user/documents/project");
    List<Object> response = List.of("{\"disableTelemetry\": false,\"focusOnNewCode\": true, \"analyzerProperties\":{\"sonar.cs.file.suffixes\":\".cs\",\".razor\"}");
    Map<String, Object> settingsMap = new HashMap<>(Map.of("disableTelemetry", false, "focusOnNewCode", true, "analyzerProperties", new HashMap<>(Map.of("sonar.cs.file.suffixes", ".cs,.razor"))));

    var result = SettingsManager.updateAnalyzerProperties(workspaceUri, response, settingsMap);

    assertThat(result).containsKey(ANALYZER_PROPERTIES);
    var analyzerProperties = (Map<String, String>) result.get(ANALYZER_PROPERTIES);
    assertThat(analyzerProperties).contains(entry("sonar.cs.file.suffixes", ".cs"));
  }

  private static Map<String, Object> fromJsonString(String json) {
    return Utils.parseToMap(new Gson().fromJson(json, JsonElement.class));
  }
}
