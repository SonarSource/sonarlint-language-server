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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangeClientNodeJsPathParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.backend.BackendInitParams;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.ANALYSIS_EXCLUDES;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.ANALYZER_PROPERTIES;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.DOTNET_DEFAULT_SOLUTION_PATH;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.OMNISHARP_LOAD_PROJECT_ON_DEMAND;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.OMNISHARP_PROJECT_LOAD_TIMEOUT;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.OMNISHARP_USE_MODERN_NET;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.SONARLINT_CONFIGURATION_NAMESPACE;
import static org.sonarsource.sonarlint.ls.settings.SettingsManager.VSCODE_FILE_EXCLUDES;

class SettingsManagerTests {

  private static final URI FOLDER_URI = URI.create("file://foo");

  @RegisterExtension
  public SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String DEPRECATED_SAMPLE_CONFIG = """
    {
      "connectedMode": {
        "servers": [
          { "serverId": "server1", "serverUrl": "https://mysonarqube.mycompany.org", "token": "ab12" },      { "serverId": "sc", "serverUrl": "https://sonarcloud.io", "token": "cd34", "organizationKey": "myOrga" }    ],
        "project": {
          "serverId": "server1",
          "projectKey": "myProject"
        }
      }
    }
    """;

  private static final String FULL_SAMPLE_CONFIG = """
    {
      "testFilePattern": "**/*Test.*",
      "analyzerProperties": {
        "sonar.polop": "palap"
      },
      "pathToCompileCommands": "/pathToCompileCommand",
      "disableTelemetry": true,
    "output": {
      "showAnalyzerLogs": true,
      "showVerboseLogs": true
    },
      "rules": {
        "xoo:rule1": {
          "level": "off"
        },
        "xoo:rule2": {
          "level": "warn"
        },
        "xoo:rule3": {
          "level": "on"
        },
        "xoo:rule4": {
          "level": "on",
          "parameters": { "param1": "123" }    },
        "xoo:notEvenARule": "definitely not a rule",
        "somethingNotParsedByRuleKey": {
          "level": "off"
        }
      },
      "connectedMode": {
        "connections": {
          "sonarqube": [
            { "connectionId": "sq1", "serverUrl": "https://mysonarqube1.mycompany.org", "token": "ab12" },        { "connectionId": "sq2", "serverUrl": "https://mysonarqube2.mycompany.org", "token": "cd34" }      ],
          "sonarcloud": [
            { "connectionId": "sc1", "token": "ab12", "organizationKey": "myOrga1" },        { "connectionId": "sc2", "token": "cd34", "organizationKey": "myOrga2" }      ]
        },
        "project": {
          "connectionId": "sq1",
          "projectKey": "myProject"
        }
      }
    }
    """;

  private SettingsManager underTest;
  private WorkspaceFoldersManager foldersManager;
  private SonarLintExtendedLanguageClient client;
  private BackendService backendService;

  @BeforeEach
  void prepare() {
    foldersManager = mock(WorkspaceFoldersManager.class);
    client = mock(SonarLintExtendedLanguageClient.class);
    when(client.getTokenForServer(any())).thenReturn(CompletableFuture.supplyAsync(() -> "token-from-storage"));
    var backendFacade = mock(BackendServiceFacade.class);
    backendService = mock(BackendService.class);
    when(backendFacade.getInitParams()).thenReturn(new BackendInitParams());
    when(backendFacade.getBackendService()).thenReturn(backendService);
    var backendInitLatch = new CountDownLatch(1);
    when(backendFacade.getInitLatch()).thenReturn(backendInitLatch);
    underTest = new SettingsManager(client, foldersManager, new ImmediateExecutorService(), backendFacade, logTester.getLogger());
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
    mockConfigurationRequest(null, """
      {
        "connectedMode": {
          "servers": [
            { "serverUrl": "https://mysonarqube.mycompany.org", "token": "ab12" },      { "serverId": "server1", "token": "ab12" }    ],
          "connections": {
            "sonarqube": [
              { "token": "cd34" }      ],
            "sonarcloud": [
              { "token": "ab12" }      ]
          }
        }
      }
      """);
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.getServerConnections()).isEmpty();
    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("Incomplete server connection configuration. Required parameters must not be blank: serverId."))
      .anyMatch(log -> log.contains("Incomplete server connection configuration. Required parameters must not be blank: serverUrl."))
      .anyMatch(log -> log.contains("Incomplete SonarQube server connection configuration. Required parameters must not be blank: serverUrl."))
      .anyMatch(log -> log.contains("Incomplete SonarCloud connection configuration. Required parameters must not be blank: organizationKey."));
  }


  @Test
  void shouldLogErrorIfDuplicateConnectionId() {
    mockConfigurationRequest(null, """
      {
        "connectedMode": {
          "connections": {
            "sonarqube": [
              { "connectionId": "dup", "serverUrl": "https://mysonarqube1.mycompany.org", "token": "ab12" }      ],
            "sonarcloud": [
              { "connectionId": "dup", "token": "ab12", "organizationKey": "myOrga1" }      ]
          }
        }
      }
      """);
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.getServerConnections()).containsKeys("dup");
    assertThat(logTester.logs(MessageType.Log)).anyMatch(log -> log.contains("Multiple server connections with the same identifier 'dup'. Fix your settings."));
  }

  @Test
  void shouldParseUSSonarCloudConnection() {
    mockConfigurationRequest(null, """
      {
        "connectedMode": {
          "connections": {
            "sonarcloud": [
              { "connectionId": "usConn", "token": "ab12", "organizationKey": "myOrga1", "region": "US" }      ]
          }
        }
      }
      """);
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.getServerConnections()).containsKeys("usConn");
    assertThat(settings.getServerConnections().get("usConn").getRegion()).isEqualTo(SonarCloudRegion.US);
    assertThat(settings.getServerConnections().get("usConn").getServerUrl()).isEqualTo(ServerConnectionSettings.getSonarCloudUSUrl());
  }


  @Test
  void shouldLogErrorIfDuplicateConnectionsWithoutId() {
    mockConfigurationRequest(null, """
      {
        "connectedMode": {
          "connections": {
            "sonarqube": [
              { "serverUrl": "https://mysonarqube1.mycompany.org", "token": "ab12" }      ],
            "sonarcloud": [
              { "token": "ab12", "organizationKey": "myOrga1" }      ]
          }
        }
      }
      """);
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.getServerConnections()).containsKeys("<default>");
    assertThat(logTester.logs(MessageType.Log)).anyMatch(log -> log.contains("Please specify a unique 'connectionId' in your settings for each of the SonarQube (Server, Cloud) connections."));
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
    mockConfigurationRequest(null, """
      {
        "connectedMode": {
          "connections": {
          },
          "project": {
            "projectKey": "myProject"
          }
        }
      }
      """);
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentDefaultFolderSettings();
    assertThat(settings.getConnectionId()).isNull();
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("No SonarQube (Server, Cloud) connections defined for your binding. Please update your settings."));
  }


  @Test
  void shouldDefaultIfOnlyOneConnectionId() {
    mockConfigurationRequest(null, """
      {
        "connectedMode": {
          "connections": {
            "sonarqube": [
              { "connectionId": "sq", "serverUrl": "https://mysonarqube2.mycompany.org", "token": "cd34" }      ]
          },
          "project": {
            "projectKey": "myProject"
          }
        }
      }
      """);
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentDefaultFolderSettings();
    assertThat(settings.getConnectionId()).isEqualTo("sq");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
  }


  @Test
  void shouldDefaultIfNoConnectionId() {
    mockConfigurationRequest(null, """
      {
        "connectedMode": {
          "connections": {
            "sonarqube": [
              { "serverUrl": "https://mysonarqube2.mycompany.org", "token": "cd34" }      ]
          },
          "project": {
            "projectKey": "myProject"
          }
        }
      }
      """);
    underTest.didChangeConfiguration();

    assertThat(underTest.getCurrentSettings().getServerConnections().keySet()).containsExactly("<default>");

    var settings = underTest.getCurrentDefaultFolderSettings();
    assertThat(settings.getConnectionId()).isEqualTo("<default>");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
  }


  @Test
  void shouldLogAnErrorIfAmbiguousConnectionId() {
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);

    mockConfigurationRequest(FOLDER_URI, """
      {
        "connectedMode": {
          "project": {
            "projectKey": "myProject"
          }
        }
      }
      """);
    var folderWrapper = new WorkspaceFolderWrapper(FOLDER_URI, new WorkspaceFolder(), logTester.getLogger());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    var settings = folderWrapper.getSettings();
    assertThat(settings.getConnectionId()).isNull();
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("Multiple connections defined in your settings. Please specify a 'connectionId' in your binding with one of [sc1,sq1,sc2,sq2] to disambiguate."));
  }


  @Test
  void shouldLogAnErrorIfUnknownConnectionId() {
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);

    mockConfigurationRequest(FOLDER_URI, """
      {
        "connectedMode": {
          "project": {
            "connectionId": "unknown",
            "projectKey": "myProject"
          }
        }
      }
      """);
    var folderWrapper = new WorkspaceFolderWrapper(FOLDER_URI, new WorkspaceFolder(), logTester.getLogger());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    var settings = folderWrapper.getSettings();
    assertThat(settings.getConnectionId()).isEqualTo("unknown");
    assertThat(settings.getProjectKey()).isEqualTo("myProject");
    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("No SonarQube (Server, Cloud) connections defined for your binding with id 'unknown'. Please update your settings."));
  }


  @Test
  void shouldHaveLocalRuleConfigurationWithDisabledRule() {
    mockConfigurationRequest(null, """
      {
        "rules": {
          "xoo:rule1": {
            "level": "off"
          }
        }
      }
      """);
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.hasLocalRuleConfiguration()).isTrue();
  }


  @Test
  void shouldHaveLocalRuleConfigurationWithEnabledRule() {
    mockConfigurationRequest(null, """
      {
        "rules": {
          "xoo:rule1": {
            "level": "on"
          }
        }
      }
      """);
    underTest.didChangeConfiguration();

    var settings = underTest.getCurrentSettings();
    assertThat(settings.hasLocalRuleConfiguration()).isTrue();
  }


  @Test
  void shouldParseScalarParameterValuesWithSomeTolerance() {
    mockConfigurationRequest(null, """
      {
        "rules": {
          "xoo:rule1": {
            "level": "on",
            "parameters": {
              "intParam": 42,
              "floatParam": 123.456,
              "boolParam": true,
              "nullParam": null,
              "stringParam": "you get the picture"
            }
          }
        }
      }
      """);
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
    var config = """
      {
        "testFilePattern": "**/*Test.*",
        "pathToCompileCommands": "${workspaceFolder}/pathToCompileCommand",
        "disableTelemetry": true,
        "output": {
        "showAnalyzerLogs": true,
        "showVerboseLogs": true
      }
      }
      """;
    var workspaceFolderUri = workspaceFolder.toUri();
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    mockConfigurationRequest(workspaceFolderUri, config);
    var folderWrapper = new WorkspaceFolderWrapper(workspaceFolderUri, new WorkspaceFolder(), logTester.getLogger());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    var settings = folderWrapper.getSettings();
    assertThat(settings.getPathToCompileCommands()).isEqualTo(workspaceFolder.resolve("pathToCompileCommand").toString());
  }


  @Test
  void workspaceFolderVariableForPathToCompileCommandsShouldWorkWithoutFileSeparator(@TempDir Path workspaceFolder) {
    var config = """
      {
        "testFilePattern": "**/*Test.*",
        "pathToCompileCommands": "${workspaceFolder}pathToCompileCommand",
        "disableTelemetry": true,
        "output": {
        "showAnalyzerLogs": true,
        "showVerboseLogs": true
      }
      }
      """;
    var workspaceFolderUri = workspaceFolder.toUri();
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    mockConfigurationRequest(workspaceFolderUri, config);
    var folderWrapper = new WorkspaceFolderWrapper(workspaceFolderUri, new WorkspaceFolder(), logTester.getLogger());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    var settings = folderWrapper.getSettings();
    assertThat(settings.getPathToCompileCommands()).isEqualTo(workspaceFolder.resolve("pathToCompileCommand").toString());
  }


  @Test
  void workspaceFolderVariableForPathToCompileCommandsShouldWorkWithWindowsFileSeparator(@TempDir Path workspaceFolder) {
    var config = """
      {
        "testFilePattern": "**/*Test.*",
        "pathToCompileCommands": "${workspaceFolder}\\\\pathToCompileCommand",
        "disableTelemetry": true,
        "output": {
        "showAnalyzerLogs": true,
        "showVerboseLogs": true
      }
      }
      """;
    var workspaceFolderUri = workspaceFolder.toUri();
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    mockConfigurationRequest(workspaceFolderUri, config);
    var folderWrapper = new WorkspaceFolderWrapper(workspaceFolderUri, new WorkspaceFolder(), logTester.getLogger());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    var settings = folderWrapper.getSettings();
    assertThat(settings.getPathToCompileCommands()).isEqualTo(workspaceFolder.resolve("pathToCompileCommand").toString());
  }


  @Test
  void workspaceFolderVariableShouldNotWorkForGlobalConfiguration() {
    var config = """
      {
        "testFilePattern": "**/*Test.*",
        "pathToCompileCommands": "${workspaceFolder}/pathToCompileCommand",
        "disableTelemetry": true,
        "output": {
        "showAnalyzerLogs": true,
        "showVerboseLogs": true
      }
      }
      """;
    mockConfigurationRequest(null, config);

    underTest.didChangeConfiguration();
    underTest.getCurrentSettings();

    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("Using ${workspaceFolder} variable in sonarlint.pathToCompileCommands is only supported for files in the workspace"));
  }


  @Test
  void pathToCompileCommandsWithoutWorkspaceFolderVariableForGlobalConfigShouldBeAccepted() {
    var config = """
      {
        "testFilePattern": "**/*Test.*",
        "pathToCompileCommands": "/pathToCompileCommand",
        "disableTelemetry": true,
        "output": {
        "showAnalyzerLogs": true,
        "showVerboseLogs": true
      }
      }
      """;
    mockConfigurationRequest(null, config);

    underTest.didChangeConfiguration();
    var settings = underTest.getCurrentDefaultFolderSettings();

    assertThat(settings.getPathToCompileCommands()).isEqualTo("/pathToCompileCommand");
  }


  @Test
  void workspaceFolderVariableShouldBePrefixOfPropertyValue() {
    var config = """
      {
        "testFilePattern": "**/*Test.*",
        "pathToCompileCommands": "something${workspaceFolder}/pathToCompileCommand",
        "disableTelemetry": true,
        "output": {
        "showAnalyzerLogs": true,
        "showVerboseLogs": true
      }
      }
      """;
    mockConfigurationRequest(null, config);

    underTest.didChangeConfiguration();
    underTest.getCurrentSettings();

    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("Variable ${workspaceFolder} for sonarlint.pathToCompileCommands should be the prefix."));
  }


  @Test
  void failForNotValidWorkspaceFolderPath() {
    var config = """
      {
        "testFilePattern": "**/*Test.*",
        "pathToCompileCommands": "${workspaceFolder}/pathToCompileCommand",
        "disableTelemetry": true,
        "output": {
        "showAnalyzerLogs": true,
        "showVerboseLogs": true
      }
      }
      """;
    var workspaceFolderUri = URI.create("notfile:///workspace/folder");
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    mockConfigurationRequest(workspaceFolderUri, config);
    var folderWrapper = new WorkspaceFolderWrapper(workspaceFolderUri, new WorkspaceFolder(), logTester.getLogger());
    when(foldersManager.getAll()).thenReturn(List.of(folderWrapper));

    underTest.didChangeConfiguration();

    folderWrapper.getSettings();
    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("Workspace folder is not in local filesystem, analysis not supported."));
  }


  @Test
  void ifCanNotGetTokenFromClientDueToInterruptedExceptionShouldLogError() {
    when(client.getTokenForServer(any())).thenReturn(CompletableFuture.failedFuture(new InterruptedException()));
    mockConfigurationRequest(null, """
      {
        "connectedMode": {
          "connections": {
            "sonarqube": [
              { "connectionId": "sq1", "serverUrl": "https://mysonarqube1.mycompany.org", "token": "ab12" },      ]
          },
          "project": {
            "connectionId": "sq1",
            "projectKey": "myProject"
          }
        }
      }
      """);

    underTest.didChangeConfiguration();

    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("Can't get token for server https://mysonarqube1.mycompany.org"));
  }


  @Test
  void ifCanNotGetTokenFromClientDueToExcecutionExceptionShouldLogError() {
    when(client.getTokenForServer(any())).thenReturn(CompletableFuture.failedFuture(new ExecutionException(new IllegalStateException())));
    mockConfigurationRequest(null, """
      {
        "connectedMode": {
          "connections": {
            "sonarqube": [
              { "connectionId": "sq1", "serverUrl": "https://mysonarqube1.mycompany.org", "token": "ab12" },      ]
          },
          "project": {
            "connectionId": "sq1",
            "projectKey": "myProject"
          }
        }
      }
      """);

    underTest.didChangeConfiguration();

    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("Can't get token for server https://mysonarqube1.mycompany.org"));
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
    var sonarLintSettings = new JsonObject();
    sonarLintSettings.add("disableTelemetry", new JsonPrimitive(false));
    sonarLintSettings.add("focusOnNewCode", new JsonPrimitive(true));
    Map<String, Object> settingsMap = new HashMap<>(Map.of(SONARLINT_CONFIGURATION_NAMESPACE, sonarLintSettings,
      DOTNET_DEFAULT_SOLUTION_PATH, new JsonPrimitive("Roslyn.sln"),
      OMNISHARP_USE_MODERN_NET, new JsonPrimitive("true"),
      OMNISHARP_LOAD_PROJECT_ON_DEMAND, new JsonPrimitive("false"),
      OMNISHARP_PROJECT_LOAD_TIMEOUT, new JsonPrimitive("600")));

    var result = SettingsManager.updateProperties(workspaceUri, settingsMap);

    assertThat(result).containsKey(ANALYZER_PROPERTIES);
    var analyzerProperties = (Map<String, String>) result.get(ANALYZER_PROPERTIES);
    assertThat(analyzerProperties).contains(entry("sonar.cs.internal.useNet6", "true"),
      entry("sonar.cs.internal.loadProjectOnDemand", "false"),
      entry("sonar.cs.internal.loadProjectsTimeout", "600"));
    assertThat(analyzerProperties.get("sonar.cs.internal.solutionPath")).endsWith("Roslyn.sln");
  }

  @Test
  void shouldAddVSCodeExcludesInFileExclusions() {
    var workspaceUri = URI.create("file:///User/user/documents/project");

    var vscodeExclusions = new JsonObject();
    var extraCondition = new JsonObject();
    extraCondition.add("when", new JsonPrimitive("$(basename).ext"));
    // "{\"**/.git\":true,\"**/.DS_Store\":true,\"**/Thumbs.db\":{\"when\":\"$(basename).ext\"}}")
    vscodeExclusions.add("**/.git", new JsonPrimitive(true));
    vscodeExclusions.add("**/.DS_Store", new JsonPrimitive(true));
    vscodeExclusions.add("**/Thumbs.db", extraCondition);

    var sonarLintSettings = new JsonObject();
    sonarLintSettings.add("disableTelemetry", new JsonPrimitive(false));
    sonarLintSettings.add("focusOnNewCode", new JsonPrimitive(true));
    Map<String, Object> settingsMap = new HashMap<>(Map.of(SONARLINT_CONFIGURATION_NAMESPACE, sonarLintSettings,
      DOTNET_DEFAULT_SOLUTION_PATH, new JsonPrimitive("Roslyn.sln"),
      OMNISHARP_USE_MODERN_NET, new JsonPrimitive("true"),
      OMNISHARP_LOAD_PROJECT_ON_DEMAND, new JsonPrimitive("false"),
      OMNISHARP_PROJECT_LOAD_TIMEOUT, new JsonPrimitive("600"),
      VSCODE_FILE_EXCLUDES, vscodeExclusions));

    var result = SettingsManager.updateProperties(workspaceUri, settingsMap);

    assertThat(result).containsKey(ANALYSIS_EXCLUDES);
    var exclusions = (String) result.get(ANALYSIS_EXCLUDES);
    assertThat(exclusions).isEqualTo("**/.git,**/.DS_Store");
  }

  @Test
  void shouldIgnoreRazorFiles() {
    var workspaceUri = URI.create("file:///User/user/documents/project");
    var sonarLintSettings = new JsonObject();
    JsonObject initalAnalyzerProperties = new JsonObject();
    initalAnalyzerProperties.add("sonar.cs.file.suffixes", new JsonPrimitive(".cs,.razor"));
    sonarLintSettings.add("analyzerProperties", initalAnalyzerProperties);
    Map<String, Object> settingsMap = new HashMap<>(Map.of(SONARLINT_CONFIGURATION_NAMESPACE, sonarLintSettings));

    var result = SettingsManager.updateProperties(workspaceUri, settingsMap);

    assertThat(result).containsKey(ANALYZER_PROPERTIES);
    var analyzerProperties = (Map<String, String>) result.get(ANALYZER_PROPERTIES);
    assertThat(analyzerProperties).contains(entry("sonar.cs.file.suffixes", ".cs"));
  }

  @Test
  void shouldIgnoreRazorCsFiles() {
    var workspaceUri = URI.create("file:///User/user/documents/project");
    var sonarLintSettings = new JsonObject();
    JsonObject initalAnalyzerProperties = new JsonObject();
    initalAnalyzerProperties.add("sonar.cs.file.suffixes", new JsonPrimitive(".cs,.razor,.razor.cs"));
    sonarLintSettings.add("analyzerProperties", initalAnalyzerProperties);
    Map<String, Object> settingsMap = new HashMap<>(Map.of(SONARLINT_CONFIGURATION_NAMESPACE, sonarLintSettings));

    var result = SettingsManager.updateProperties(workspaceUri, settingsMap);

    assertThat(result).containsKey(ANALYZER_PROPERTIES);
    var analyzerProperties = (Map<String, String>) result.get(ANALYZER_PROPERTIES);
    assertThat(analyzerProperties).contains(entry("sonar.cs.file.suffixes", ".cs"));
  }

  @Test
  void shouldNotifyAboutNodeJsChange() {
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    underTest.didChangeConfiguration();

    var newNodePath = "path/to/node";
    mockConfigurationRequest(null, "{\n" +
      "\"pathToNodeExecutable\" : \"" + newNodePath + "\"" +
      "}\n");
    underTest.didChangeConfiguration();

    var argumentCaptor = ArgumentCaptor.forClass(DidChangeClientNodeJsPathParams.class);
    verify(backendService).didChangeClientNodeJsPath(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getClientNodeJsPath()).isEqualTo(Path.of(newNodePath));
  }

  @Test
  void shouldNotNotifyAboutNodeJsChange() {
    mockConfigurationRequest(null, FULL_SAMPLE_CONFIG);
    underTest.didChangeConfiguration();

    mockConfigurationRequest(null, """
      {
      "disableTelemetry" : "true"}
      """);
    underTest.didChangeConfiguration();

    verify(backendService, never()).didChangeClientNodeJsPath(any());
  }

  @Test
  void shouldUpdatePropertiesWithDefaultValuesWhenNullSettings() {
    var workspaceUri = URI.create("file:///User/user/documents/project");
    var sonarLintSettings = new JsonObject();
    sonarLintSettings.add("disableTelemetry", new JsonPrimitive(false));
    sonarLintSettings.add("focusOnNewCode", new JsonPrimitive(true));
    Map<String, Object> settingsMap = new HashMap<>(Map.of(SONARLINT_CONFIGURATION_NAMESPACE, sonarLintSettings,
      DOTNET_DEFAULT_SOLUTION_PATH, JsonNull.INSTANCE,
      OMNISHARP_USE_MODERN_NET, JsonNull.INSTANCE,
      OMNISHARP_LOAD_PROJECT_ON_DEMAND, JsonNull.INSTANCE,
      OMNISHARP_PROJECT_LOAD_TIMEOUT, JsonNull.INSTANCE));

    var result = SettingsManager.updateProperties(workspaceUri, settingsMap);

    var analyzerProperties = (Map<String, String>) result.get(ANALYZER_PROPERTIES);
    assertThat(analyzerProperties).contains(entry("sonar.cs.internal.useNet6", "true"),
      entry("sonar.cs.internal.loadProjectOnDemand", "false"),
      entry("sonar.cs.internal.loadProjectsTimeout", "60"));
    assertThat(analyzerProperties.get("sonar.cs.internal.solutionPath")).isNull();
  }

  @Test
  void shouldUpdatePropertiesWithDefaultValuesWhenEmptySettings() {
    var workspaceUri = URI.create("file:///User/user/documents/project");
    var sonarLintSettings = new JsonObject();
    sonarLintSettings.add("disableTelemetry", new JsonPrimitive(false));
    sonarLintSettings.add("focusOnNewCode", new JsonPrimitive(true));
    Map<String, Object> settingsMap = new HashMap<>(Map.of(SONARLINT_CONFIGURATION_NAMESPACE, sonarLintSettings,
      DOTNET_DEFAULT_SOLUTION_PATH, new JsonPrimitive(""),
      OMNISHARP_USE_MODERN_NET, new JsonPrimitive(""),
      OMNISHARP_LOAD_PROJECT_ON_DEMAND, new JsonPrimitive(""),
      OMNISHARP_PROJECT_LOAD_TIMEOUT, new JsonPrimitive("")));

    var result = SettingsManager.updateProperties(workspaceUri, settingsMap);

    var analyzerProperties = (Map<String, String>) result.get(ANALYZER_PROPERTIES);
    assertThat(analyzerProperties).contains(entry("sonar.cs.internal.useNet6", "true"),
      entry("sonar.cs.internal.loadProjectOnDemand", "false"),
      entry("sonar.cs.internal.loadProjectsTimeout", "60"));
    assertThat(analyzerProperties.get("sonar.cs.internal.solutionPath")).isNull();
  }

  @Test
  void shouldUpdatePropertiesWithDefaultValuesWhenParsingFails() {
    var workspaceUri = URI.create("file:///User/user/documents/project");
    var sonarLintSettings = new JsonObject();
    sonarLintSettings.add("disableTelemetry", new JsonPrimitive(false));
    sonarLintSettings.add("focusOnNewCode", new JsonPrimitive(true));
    Map<String, Object> settingsMap = new HashMap<>(Map.of(SONARLINT_CONFIGURATION_NAMESPACE, sonarLintSettings,
      DOTNET_DEFAULT_SOLUTION_PATH, new JsonObject(),
      OMNISHARP_USE_MODERN_NET, new JsonObject(),
      OMNISHARP_LOAD_PROJECT_ON_DEMAND, new JsonObject(),
      OMNISHARP_PROJECT_LOAD_TIMEOUT, new JsonObject()));

    var result = SettingsManager.updateProperties(workspaceUri, settingsMap);

    var analyzerProperties = (Map<String, String>) result.get(ANALYZER_PROPERTIES);
    assertThat(analyzerProperties).contains(entry("sonar.cs.internal.useNet6", "true"),
      entry("sonar.cs.internal.loadProjectOnDemand", "false"),
      entry("sonar.cs.internal.loadProjectsTimeout", "60"));
    assertThat(analyzerProperties.get("sonar.cs.internal.solutionPath")).isNull();
  }

  @Test
  void shouldParseRegion() {
    var validRegionEU = "EU";
    var validRegionUS = "US";
    var invalidRegion = "invalid";

    assertThat(underTest.parseRegion(validRegionEU)).isEqualTo(SonarCloudRegion.EU);
    assertThat(underTest.parseRegion(validRegionUS)).isEqualTo(SonarCloudRegion.US);
    assertThat(underTest.parseRegion(invalidRegion)).isEqualTo(SonarCloudRegion.EU);
  }

  private static Map<String, Object> fromJsonString(String json) {
    return Utils.parseToMap(new Gson().fromJson(json, JsonElement.class));
  }
}
