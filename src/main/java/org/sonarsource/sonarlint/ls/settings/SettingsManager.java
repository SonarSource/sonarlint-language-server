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

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderLifecycleListener;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.util.Arrays.stream;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sonarsource.sonarlint.ls.util.Utils.interrupted;

public class SettingsManager implements WorkspaceFolderLifecycleListener {

  private static final String ORGANIZATION_KEY = "organizationKey";
  private static final String DISABLE_NOTIFICATIONS = "disableNotifications";
  private static final String PROJECT = "project";
  public static final String DEFAULT_CONNECTION_ID = "<default>";
  private static final String SERVER_URL = "serverUrl";
  private static final String SERVER_ID = "serverId";
  private static final String TOKEN = "token";
  private static final String CONNECTION_ID = "connectionId";
  public static final String SONARLINT_CONFIGURATION_NAMESPACE = "sonarlint";
  public static final String DOTNET_DEFAULT_SOLUTION_PATH = "dotnet.defaultSolution";
  public static final String OMNISHARP_USE_MODERN_NET = "omnisharp.useModernNet";
  public static final String OMNISHARP_LOAD_PROJECT_ON_DEMAND = "omnisharp.enableMsBuildLoadProjectsOnDemand";
  public static final String OMNISHARP_PROJECT_LOAD_TIMEOUT = "omnisharp.projectLoadTimeout";
  private static final String DISABLE_TELEMETRY = "disableTelemetry";
  private static final String RULES = "rules";
  private static final String TEST_FILE_PATTERN = "testFilePattern";
  static final String ANALYZER_PROPERTIES = "analyzerProperties";
  private static final String OUTPUT = "output";
  private static final String SHOW_ANALYZER_LOGS = "showAnalyzerLogs";
  private static final String SHOW_VERBOSE_LOGS = "showVerboseLogs";
  private static final String PATH_TO_NODE_EXECUTABLE = "pathToNodeExecutable";
  private static final String PATH_TO_COMPILE_COMMANDS = "pathToCompileCommands";
  private static final String FOCUS_ON_NEW_CODE = "focusOnNewCode";

  private static final String WORKSPACE_FOLDER_VARIABLE = "${workspaceFolder}";

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String SONAR_CS_FILE_SUFFIXES = "sonar.cs.file.suffixes";

  private final SonarLintExtendedLanguageClient client;
  private final WorkspaceFoldersManager foldersManager;

  private WorkspaceSettings currentSettings = null;
  private final CountDownLatch initLatch = new CountDownLatch(1);
  // Setting that are normally specific per workspace folder, but we also keep a cache of global values to analyze files outside any
  // workspace
  private WorkspaceFolderSettings currentDefaultSettings = null;

  private final ExecutorService executor;
  private final List<WorkspaceSettingsChangeListener> globalListeners = new ArrayList<>();
  private final List<WorkspaceFolderSettingsChangeListener> folderListeners = new ArrayList<>();
  private final BackendServiceFacade backendServiceFacade;

  public SettingsManager(SonarLintExtendedLanguageClient client, WorkspaceFoldersManager foldersManager,
    BackendServiceFacade backendServiceFacade) {
    this(client, foldersManager, Executors.newCachedThreadPool(Utils.threadFactory("SonarLint settings manager", false)), backendServiceFacade);
  }

  SettingsManager(SonarLintExtendedLanguageClient client, WorkspaceFoldersManager foldersManager,
    ExecutorService executor, BackendServiceFacade backendServiceFacade) {
    this.client = client;
    this.foldersManager = foldersManager;
    this.executor = executor;
    this.backendServiceFacade = backendServiceFacade;
  }

  /**
   * Get workspace level settings, waiting for them to be initialized
   */
  public WorkspaceSettings getCurrentSettings() {
    try {
      if (initLatch.await(1, TimeUnit.MINUTES)) {
        return currentSettings;
      }
    } catch (InterruptedException e) {
      interrupted(e);
    }
    throw new IllegalStateException("Unable to get settings in time");
  }

  public Map<String, StandaloneRuleConfigDto> getStandaloneRuleConfigByKey() {
    var standaloneRuleConfigByKey = new HashMap<String, StandaloneRuleConfigDto>();
    currentSettings.getRuleParameters().forEach(((ruleKey, paramValueByKey) -> {
      var active = currentSettings.getIncludedRules().contains(ruleKey);
      standaloneRuleConfigByKey.put(ruleKey.toString(), new StandaloneRuleConfigDto(active, paramValueByKey));
    }));
    return standaloneRuleConfigByKey;
  }

  /**
   * Get default workspace folder level settings, waiting for them to be initialized
   */
  public WorkspaceFolderSettings getCurrentDefaultFolderSettings() {
    try {
      if (initLatch.await(1, TimeUnit.MINUTES)) {
        return currentDefaultSettings;
      }
    } catch (InterruptedException e) {
      interrupted(e);
    }
    throw new IllegalStateException("Unable to get settings in time");
  }

  public void didChangeConfiguration() {
    executor.execute(() -> {
      try {
        var workspaceSettingsMap = requestSonarLintAndOmnisharpConfigurationAsync(null).get(1, TimeUnit.MINUTES);
        var newWorkspaceSettings = parseSettings(workspaceSettingsMap);
        var oldWorkspaceSettings = currentSettings;
        this.currentSettings = newWorkspaceSettings;
        var newDefaultFolderSettings = parseFolderSettings(workspaceSettingsMap, null);
        var oldDefaultFolderSettings = currentDefaultSettings;
        this.currentDefaultSettings = newDefaultFolderSettings;
        if (initLatch.getCount() != 0) {
          initLatch.countDown();
          backendServiceFacade.initialize(getCurrentSettings().getServerConnections());
        } else {
          backendServiceFacade.didChangeConnections(getCurrentSettings().getServerConnections());
          backendServiceFacade.updateStandaloneRulesConfiguration(getStandaloneRuleConfigByKey());
        }

        foldersManager.getAll().forEach(f -> updateWorkspaceFolderSettings(f, true));
        notifyListeners(newWorkspaceSettings, oldWorkspaceSettings, newDefaultFolderSettings, oldDefaultFolderSettings);
      } catch (InterruptedException e) {
        interrupted(e);
      } catch (Exception e) {
        LOG.error("Unable to update configuration", e);
      } finally {
        client.readyForTests();
      }
    });
  }

  private void notifyListeners(WorkspaceSettings newWorkspaceSettings, WorkspaceSettings oldWorkspaceSettings, WorkspaceFolderSettings newDefaultFolderSettings,
    WorkspaceFolderSettings oldDefaultFolderSettings) {
    if (!Objects.equals(oldWorkspaceSettings, newWorkspaceSettings)) {
      LOG.debug("Global settings updated: {}", newWorkspaceSettings);
      globalListeners.forEach(l -> l.onChange(oldWorkspaceSettings, newWorkspaceSettings));
    }
    if (!Objects.equals(oldDefaultFolderSettings, newDefaultFolderSettings)) {
      LOG.debug("Default settings updated: {}", newDefaultFolderSettings);
      folderListeners.forEach(l -> l.onChange(null, oldDefaultFolderSettings, newDefaultFolderSettings));
    }
  }

  // Visible for testing
  CompletableFuture<Map<String, Object>> requestSonarLintAndOmnisharpConfigurationAsync(@Nullable URI uri) {
    if (uri != null) {
      LOG.debug("Fetching configuration for folder '{}'", uri);
    } else {
      LOG.debug("Fetching global configuration");
    }
    var params = new ConfigurationParams();
    var sonarLintConfigurationItem = getConfigurationItem(SONARLINT_CONFIGURATION_NAMESPACE, uri);
    var defaultSolutionItem = getConfigurationItem(DOTNET_DEFAULT_SOLUTION_PATH, uri);
    var modernDotnetItem = getConfigurationItem(OMNISHARP_USE_MODERN_NET, uri);
    var loadProjectsOnDemandItem = getConfigurationItem(OMNISHARP_LOAD_PROJECT_ON_DEMAND, uri);
    var projectLoadTimeoutItem = getConfigurationItem(OMNISHARP_PROJECT_LOAD_TIMEOUT, uri);

    params.setItems(List.of(sonarLintConfigurationItem, defaultSolutionItem, modernDotnetItem, loadProjectsOnDemandItem, projectLoadTimeoutItem));
    return client.configuration(params)
      .handle((r, t) -> {
        if (t != null) {
          LOG.error("Unable to fetch configuration of folder " + uri, t);
        }
        return r;
      })
      .thenApply(response -> {
        if (response != null) {
          var settingsMap = Utils.parseToMap(response.get(0));
          if (settingsMap != null) {
            return updateAnalyzerProperties(uri, response, settingsMap);
          }
        }
        return Collections.emptyMap();
      });
  }

  static Map<String, Object> updateAnalyzerProperties(@org.jetbrains.annotations.Nullable URI workspaceUri, List<Object> response, Map<String, Object> settingsMap) {
    var analyzerProperties = (Map<String, String>) settingsMap.getOrDefault(ANALYZER_PROPERTIES, Maps.newHashMap());
    forceIgnoreRazorFiles(analyzerProperties);
    var solutionRelativePath = tryGetSetting(response, 1, "");
    if (!solutionRelativePath.isEmpty() && workspaceUri != null) {
      // uri: file:///Users/me/Documents/Sonar/roslyn
      // solutionPath: Roslyn.sln
      // we want: /Users/me/Documents/Sonar/roslyn/Roslyn.sln
      analyzerProperties.put("sonar.cs.internal.solutionPath", Path.of(workspaceUri).resolve(solutionRelativePath).toAbsolutePath().toString());
    }
    analyzerProperties.put("sonar.cs.internal.useNet6", tryGetSetting(response, 2, "true"));
    analyzerProperties.put("sonar.cs.internal.loadProjectOnDemand", tryGetSetting(response, 3, "false"));
    analyzerProperties.put("sonar.cs.internal.loadProjectsTimeout", tryGetSetting(response, 4, "60"));
    settingsMap.put(ANALYZER_PROPERTIES, analyzerProperties);

    return settingsMap;
  }

  private static void forceIgnoreRazorFiles(Map<String, String> analyzerProperties) {
    if (analyzerProperties.containsKey(SONAR_CS_FILE_SUFFIXES)) {
      var currentSetting = analyzerProperties.get(SONAR_CS_FILE_SUFFIXES);
      if (currentSetting.contains(".razor")) {
        var suffixes = currentSetting.split(",");
        var newSetting = stream(suffixes)
          .filter(suffix -> !suffix.equals(".razor"))
          .collect(Collectors.joining(","));
        analyzerProperties.put(SONAR_CS_FILE_SUFFIXES, newSetting);
      }
    } else {
      analyzerProperties.put(SONAR_CS_FILE_SUFFIXES, ".cs");
    }
  }

  private static String tryGetSetting(List<Object> response, int index, String defaultValue) {
    if (response.size() > index && response.get(index) != null) {
      try {
        var maybeSetting = new Gson().fromJson((JsonElement) response.get(index), String.class);
        return maybeSetting == null ? defaultValue : maybeSetting;
      } catch (Exception e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  private static ConfigurationItem getConfigurationItem(String section, @Nullable URI uri) {
    var configItem = new ConfigurationItem();
    configItem.setSection(section);
    if (uri != null) {
      configItem.setScopeUri(uri.toString());
    }
    return configItem;
  }

  private void updateWorkspaceFolderSettings(WorkspaceFolderWrapper f, boolean notifyOnChange) {
    try {
      var folderSettingsMap = requestSonarLintAndOmnisharpConfigurationAsync(f.getUri()).get();
      var newSettings = parseFolderSettings(folderSettingsMap, f.getUri());
      var old = f.getRawSettings();
      if (!Objects.equals(old, newSettings)) {
        f.setSettings(newSettings);
        LOG.debug("Workspace folder '{}' configuration updated: {}", f, newSettings);
        if (notifyOnChange) {
          folderListeners.forEach(l -> l.onChange(f, old, newSettings));
        }
      }
    } catch (InterruptedException e) {
      interrupted(e);
    } catch (Exception e) {
      LOG.error("Unable to update configuration of folder " + f.getUri(), e);
    }
  }

  private WorkspaceSettings parseSettings(Map<String, Object> params) {
    var disableTelemetry = (Boolean) params.getOrDefault(DISABLE_TELEMETRY, false);
    var pathToNodeExecutable = (String) params.get(PATH_TO_NODE_EXECUTABLE);
    var focusOnNewCode = (Boolean) params.getOrDefault(FOCUS_ON_NEW_CODE, false);
    var serverConnections = parseServerConnections(params);
    @SuppressWarnings("unchecked")
    var rulesConfiguration = RulesConfiguration.parse(((Map<String, Object>) params.getOrDefault(RULES, Collections.emptyMap())));
    @SuppressWarnings("unchecked")
    var consoleParams = ((Map<String, Object>) params.getOrDefault(OUTPUT, Collections.emptyMap()));
    var showAnalyzerLogs = (Boolean) consoleParams.getOrDefault(SHOW_ANALYZER_LOGS, false);
    var showVerboseLogs = (Boolean) consoleParams.getOrDefault(SHOW_VERBOSE_LOGS, false);
    return new WorkspaceSettings(disableTelemetry, serverConnections, rulesConfiguration.excludedRules(), rulesConfiguration.includedRules(), rulesConfiguration.ruleParameters(),
      showAnalyzerLogs, showVerboseLogs, pathToNodeExecutable, focusOnNewCode);
  }

  private Map<String, ServerConnectionSettings> parseServerConnections(Map<String, Object> params) {
    @SuppressWarnings("unchecked")
    var connectedModeMap = (Map<String, Object>) params.getOrDefault("connectedMode", Collections.emptyMap());
    var serverConnections = new HashMap<String, ServerConnectionSettings>();
    parseDeprecatedServerEntries(connectedModeMap, serverConnections);
    @SuppressWarnings("unchecked")
    var connectionsMap = (Map<String, Object>) connectedModeMap.getOrDefault("connections", Collections.emptyMap());
    parseSonarQubeConnections(connectionsMap, serverConnections);
    parseSonarCloudConnections(connectionsMap, serverConnections);
    return serverConnections;
  }

  private static void parseDeprecatedServerEntries(Map<String, Object> connectedModeMap, Map<String, ServerConnectionSettings> serverConnections) {
    @SuppressWarnings("unchecked")
    var deprecatedServersEntries = (List<Map<String, Object>>) connectedModeMap.getOrDefault("servers", Collections.emptyList());
    deprecatedServersEntries.forEach(m -> {
      if (checkRequiredAttribute(m, "server", SERVER_ID, SERVER_URL, TOKEN)) {
        var connectionId = (String) m.get(SERVER_ID);
        var url = (String) m.get(SERVER_URL);
        var token = (String) m.get(TOKEN);
        var organization = (String) m.get(ORGANIZATION_KEY);
        var connectionSettings = new ServerConnectionSettings(connectionId, url, token, organization, false);
        addIfUniqueConnectionId(serverConnections, connectionId, connectionSettings);
      }
    });
  }

  private void parseSonarQubeConnections(Map<String, Object> connectionsMap, Map<String, ServerConnectionSettings> serverConnections) {
    @SuppressWarnings("unchecked")
    var sonarqubeEntries = (List<Map<String, Object>>) connectionsMap.getOrDefault("sonarqube", Collections.emptyList());
    sonarqubeEntries.forEach(m -> {
      if (checkRequiredAttribute(m, "SonarQube server", SERVER_URL)) {
        var connectionId = defaultIfBlank((String) m.get(CONNECTION_ID), DEFAULT_CONNECTION_ID);
        var url = (String) m.get(SERVER_URL);
        var token = getTokenFromClient(url);
        var disableNotifications = (Boolean) m.getOrDefault(DISABLE_NOTIFICATIONS, false);
        var connectionSettings = new ServerConnectionSettings(connectionId, url, token, null, disableNotifications);
        addIfUniqueConnectionId(serverConnections, connectionId, connectionSettings);
      }
    });
  }

  private void parseSonarCloudConnections(Map<String, Object> connectionsMap, Map<String, ServerConnectionSettings> serverConnections) {
    @SuppressWarnings("unchecked")
    var sonarcloudEntries = (List<Map<String, Object>>) connectionsMap.getOrDefault("sonarcloud", Collections.emptyList());
    sonarcloudEntries.forEach(m -> {

      if (checkRequiredAttribute(m, "SonarCloud", ORGANIZATION_KEY)) {
        var connectionId = defaultIfBlank((String) m.get(CONNECTION_ID), DEFAULT_CONNECTION_ID);
        var organizationKey = (String) m.get(ORGANIZATION_KEY);
        var token = getTokenFromClient(organizationKey);
        var disableNotifs = (Boolean) m.getOrDefault(DISABLE_NOTIFICATIONS, false);
        addIfUniqueConnectionId(serverConnections, connectionId,
          new ServerConnectionSettings(connectionId, ServerConnectionSettings.SONARCLOUD_URL, token, organizationKey, disableNotifs));
      }
    });
  }

  private String getTokenFromClient(String serverUrlOrOrganization) {
    try {
      return client.getTokenForServer(serverUrlOrOrganization).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Can't get token for server " + serverUrlOrOrganization, e);
      return null;
    } catch (ExecutionException e) {
      LOG.error("Can't get token for server " + serverUrlOrOrganization, e);
      return null;
    }
  }

  private static boolean checkRequiredAttribute(Map<String, Object> map, String label, String... requiredAttributes) {
    var missing = stream(requiredAttributes).filter(att -> isBlank((String) map.get(att))).toList();
    if (!missing.isEmpty()) {
      LOG.error("Incomplete {} connection configuration. Required parameters must not be blank: {}.", label, String.join(",", missing));
      return false;
    }
    return true;
  }

  private static void addIfUniqueConnectionId(Map<String, ServerConnectionSettings> serverConnections, String connectionId, ServerConnectionSettings connectionSettings) {
    if (serverConnections.containsKey(connectionId)) {
      if (DEFAULT_CONNECTION_ID.equals(connectionId)) {
        LOG.error("Please specify a unique 'connectionId' in your settings for each of the SonarQube/SonarCloud connections.");
      } else {
        LOG.error("Multiple server connections with the same identifier '{}'. Fix your settings.", connectionId);
      }
    } else {
      serverConnections.put(connectionId, connectionSettings);
    }
  }

  // Visible for testing
  WorkspaceFolderSettings parseFolderSettings(Map<String, Object> params, @Nullable URI workspaceFolderUri) {
    var testFilePattern = (String) params.get(TEST_FILE_PATTERN);
    var pathToCompileCommands = (String) params.get(PATH_TO_COMPILE_COMMANDS);
    var analyzerProperties = (Map<String, String>) params.getOrDefault(ANALYZER_PROPERTIES, Map.of());
    String connectionId = null;
    String projectKey = null;
    @SuppressWarnings("unchecked")
    var connectedModeMap = (Map<String, Object>) params.getOrDefault("connectedMode", Collections.emptyMap());
    if (connectedModeMap.containsKey(PROJECT)) {
      @SuppressWarnings("unchecked")
      var projectBinding = (Map<String, String>) connectedModeMap.get(PROJECT);
      // params.connectedMode.project is present but empty when there is no project binding
      if (!projectBinding.isEmpty()) {
        projectKey = projectBinding.get("projectKey");
        connectionId = projectBinding.getOrDefault(SERVER_ID, projectBinding.get(CONNECTION_ID));
        if (isBlank(connectionId)) {
          if (currentSettings.getServerConnections().isEmpty()) {
            LOG.error("No SonarQube/SonarCloud connections defined for your binding. Please update your settings.");
          } else if (currentSettings.getServerConnections().size() == 1) {
            connectionId = currentSettings.getServerConnections().keySet().iterator().next();
          } else {
            LOG.error("Multiple connections defined in your settings. Please specify a 'connectionId' in your binding with one of [{}] to disambiguate.",
              String.join(",", currentSettings.getServerConnections().keySet()));
            connectionId = null;
          }
        } else if (!currentSettings.getServerConnections().containsKey(connectionId)) {
          LOG.error("No SonarQube/SonarCloud connections defined for your binding with id '{}'. Please update your settings.", connectionId);
        }
      }
    }
    pathToCompileCommands = substituteWorkspaceFolderVariable(workspaceFolderUri, pathToCompileCommands);
    return new WorkspaceFolderSettings(connectionId, projectKey, analyzerProperties, testFilePattern, pathToCompileCommands);
  }

  @CheckForNull
  private static String substituteWorkspaceFolderVariable(@Nullable URI workspaceFolderUri, @Nullable String pathToCompileCommands) {
    if (pathToCompileCommands == null) {
      return null;
    }
    if (!pathToCompileCommands.contains(WORKSPACE_FOLDER_VARIABLE)) {
      return pathToCompileCommands;
    }
    if (!pathToCompileCommands.startsWith(WORKSPACE_FOLDER_VARIABLE)) {
      LOG.error("Variable ${workspaceFolder} for sonarlint.pathToCompileCommands should be the prefix.");
      return pathToCompileCommands;
    }
    if (workspaceFolderUri == null) {
      LOG.warn("Using ${workspaceFolder} variable in sonarlint.pathToCompileCommands is only supported for files in the workspace");
      return pathToCompileCommands;
    }
    if (!Utils.uriHasFileScheme(workspaceFolderUri)) {
      LOG.error("Workspace folder is not in local filesystem, analysis not supported.");
      return null;
    }
    var workspacePath = Paths.get(workspaceFolderUri);
    var pathWithoutWorkspaceFolderPrefix = StringUtils.removeStart(pathToCompileCommands, WORKSPACE_FOLDER_VARIABLE);
    String pathWithoutLeadingSlash = removePossibleLeadingSlash(pathWithoutWorkspaceFolderPrefix);
    return workspacePath.resolve(pathWithoutLeadingSlash).toString();
  }

  private static String removePossibleLeadingSlash(String path) {
    if (path.startsWith("/")) {
      return StringUtils.removeStart(path, "/");
    } else if (path.startsWith("\\")) {
      return StringUtils.removeStart(path, "\\");
    }
    return path;
  }

  public void addListener(WorkspaceSettingsChangeListener listener) {
    globalListeners.add(listener);
  }

  public void removeListener(WorkspaceSettingsChangeListener listener) {
    globalListeners.remove(listener);
  }

  public void addListener(WorkspaceFolderSettingsChangeListener listener) {
    folderListeners.add(listener);
  }

  public void removeListener(WorkspaceFolderSettingsChangeListener listener) {
    folderListeners.remove(listener);
  }

  @Override
  public void added(WorkspaceFolderWrapper added) {
    executor.execute(() -> updateWorkspaceFolderSettings(added, false));
  }

  public void shutdown() {
    Utils.shutdownAndAwait(executor, true);
  }

  public static String connectionIdOrDefault(@Nullable String connectionId) {
    return connectionId == null ? DEFAULT_CONNECTION_ID : connectionId;
  }
}
