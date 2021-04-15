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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.ls.Utils;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderLifecycleListener;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClient;

import static java.util.Arrays.stream;
import static org.apache.commons.lang.StringUtils.defaultIfBlank;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.sonarsource.sonarlint.ls.Utils.interrupted;

public class SettingsManager implements WorkspaceFolderLifecycleListener {

  private static final String ORGANIZATION_KEY = "organizationKey";
  private static final String DISABLE_NOTIFICATIONS = "disableNotifications";
  private static final String PROJECT = "project";
  private static final String DEFAULT_CONNECTION_ID = "<default>";
  private static final String SERVER_URL = "serverUrl";
  private static final String SERVER_ID = "serverId";
  private static final String TOKEN = "token";
  private static final String CONNECTION_ID = "connectionId";
  private static final String SONARLINT_CONFIGURATION_NAMESPACE = "sonarlint";
  private static final String DISABLE_TELEMETRY = "disableTelemetry";
  private static final String RULES = "rules";
  private static final String TEST_FILE_PATTERN = "testFilePattern";
  private static final String ANALYZER_PROPERTIES = "analyzerProperties";
  private static final String OUTPUT = "output";
  private static final String SHOW_ANALYZER_LOGS = "showAnalyzerLogs";
  private static final String SHOW_VERBOSE_LOGS = "showVerboseLogs";
  private static final String PATH_TO_NODE_EXECUTABLE = "pathToNodeExecutable";

  private static final Logger LOG = Loggers.get(SettingsManager.class);

  private final LanguageClient client;
  private final WorkspaceFoldersManager foldersManager;
  private final ApacheHttpClient httpClient;

  private WorkspaceSettings currentSettings = null;
  private final CountDownLatch initLatch = new CountDownLatch(1);
  // Setting that are normally specific per workspace folder, but we also keep a cache of global values to analyze files outside any
  // workspace
  private WorkspaceFolderSettings currentDefaultSettings = null;

  private final ExecutorService executor;
  private final List<WorkspaceSettingsChangeListener> globalListeners = new ArrayList<>();
  private final List<WorkspaceFolderSettingsChangeListener> folderListeners = new ArrayList<>();

  public SettingsManager(LanguageClient client, WorkspaceFoldersManager foldersManager, ApacheHttpClient httpClient) {
    this.client = client;
    this.foldersManager = foldersManager;
    this.httpClient = httpClient;
    this.executor = Executors.newCachedThreadPool(Utils.threadFactory("SonarLint settings manager", false));
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
        Map<String, Object> workspaceSettingsMap = requestSonarLintConfigurationAsync(null).get(1, TimeUnit.MINUTES);
        WorkspaceSettings newWorkspaceSettings = parseSettings(workspaceSettingsMap, httpClient);
        WorkspaceSettings oldWorkspaceSettings = currentSettings;
        this.currentSettings = newWorkspaceSettings;
        WorkspaceFolderSettings newDefaultFolderSettings = parseFolderSettings(workspaceSettingsMap);
        WorkspaceFolderSettings oldDefaultFolderSettings = currentDefaultSettings;
        this.currentDefaultSettings = newDefaultFolderSettings;
        initLatch.countDown();

        foldersManager.getAll().forEach(f -> updateWorkspaceFolderSettings(f, true));

        notifyListeners(newWorkspaceSettings, oldWorkspaceSettings, newDefaultFolderSettings, oldDefaultFolderSettings);

      } catch (InterruptedException e) {
        interrupted(e);
      } catch (Exception e) {
        LOG.error("Unable to update configuration", e);
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
  CompletableFuture<Map<String, Object>> requestSonarLintConfigurationAsync(@Nullable URI uri) {
    LOG.debug("Fetching configuration for folder '{}'", uri);
    ConfigurationParams params = new ConfigurationParams();
    ConfigurationItem configurationItem = new ConfigurationItem();
    configurationItem.setSection(SONARLINT_CONFIGURATION_NAMESPACE);
    if (uri != null) {
      configurationItem.setScopeUri(uri.toString());
    }
    params.setItems(Collections.singletonList(configurationItem));
    return client.configuration(params)
      .handle((r, t) -> {
        if (t != null) {
          LOG.error("Unable to fetch configuration of folder " + uri, t);
        }
        return r;
      })
      .thenApply(response -> response != null ? Utils.parseToMap(response.get(0)) : Collections.emptyMap());
  }

  private void updateWorkspaceFolderSettings(WorkspaceFolderWrapper f, boolean notifyOnChange) {
    try {
      Map<String, Object> folderSettingsMap = requestSonarLintConfigurationAsync(f.getUri()).get();
      WorkspaceFolderSettings newSettings = parseFolderSettings(folderSettingsMap);
      WorkspaceFolderSettings old = f.getRawSettings();
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

  private static WorkspaceSettings parseSettings(Map<String, Object> params, ApacheHttpClient httpClient) {
    boolean disableTelemetry = (Boolean) params.getOrDefault(DISABLE_TELEMETRY, false);
    String pathToNodeExecutable = (String) params.get(PATH_TO_NODE_EXECUTABLE);
    Map<String, ServerConnectionSettings> serverConnections = parseServerConnections(params, httpClient);
    @SuppressWarnings("unchecked")
    RulesConfiguration rulesConfiguration = RulesConfiguration.parse(((Map<String, Object>) params.getOrDefault(RULES, Collections.emptyMap())));
    @SuppressWarnings("unchecked")
    Map<String, Object> consoleParams = ((Map<String, Object>) params.getOrDefault(OUTPUT, Collections.emptyMap()));
    boolean showAnalyzerLogs = (Boolean) consoleParams.getOrDefault(SHOW_ANALYZER_LOGS, false);
    boolean showVerboseLogs = (Boolean) consoleParams.getOrDefault(SHOW_VERBOSE_LOGS, false);
    return new WorkspaceSettings(disableTelemetry, serverConnections, rulesConfiguration.excludedRules(), rulesConfiguration.includedRules(), rulesConfiguration.ruleParameters(),
      showAnalyzerLogs, showVerboseLogs, pathToNodeExecutable);
  }

  private static Map<String, ServerConnectionSettings> parseServerConnections(Map<String, Object> params, ApacheHttpClient httpClient) {
    @SuppressWarnings("unchecked")
    Map<String, Object> connectedModeMap = (Map<String, Object>) params.getOrDefault("connectedMode", Collections.emptyMap());
    Map<String, ServerConnectionSettings> serverConnections = new HashMap<>();
    parseDeprecatedServerEntries(connectedModeMap, serverConnections, httpClient);
    @SuppressWarnings("unchecked")
    Map<String, Object> connectionsMap = (Map<String, Object>) connectedModeMap.getOrDefault("connections", Collections.emptyMap());
    parseSonarQubeConnections(connectionsMap, serverConnections, httpClient);
    parseSonarCloudConnections(connectionsMap, serverConnections, httpClient);
    return serverConnections;
  }

  private static void parseDeprecatedServerEntries(Map<String, Object> connectedModeMap, Map<String, ServerConnectionSettings> serverConnections, ApacheHttpClient httpClient) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> deprecatedServersEntries = (List<Map<String, Object>>) connectedModeMap.getOrDefault("servers", Collections.emptyList());
    deprecatedServersEntries.forEach(m -> {
      if (checkRequiredAttribute(m, "server", SERVER_ID, SERVER_URL, TOKEN)) {
        String connectionId = (String) m.get(SERVER_ID);
        String url = (String) m.get(SERVER_URL);
        String token = (String) m.get(TOKEN);
        String organization = (String) m.get(ORGANIZATION_KEY);
        ServerConnectionSettings connectionSettings = new ServerConnectionSettings(connectionId, url, token, organization, false, httpClient);
        addIfUniqueConnectionId(serverConnections, connectionId, connectionSettings);
      }
    });
  }

  private static void parseSonarQubeConnections(Map<String, Object> connectionsMap, Map<String, ServerConnectionSettings> serverConnections, ApacheHttpClient httpClient) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> sonarqubeEntries = (List<Map<String, Object>>) connectionsMap.getOrDefault("sonarqube", Collections.emptyList());
    sonarqubeEntries.forEach(m -> {
      if (checkRequiredAttribute(m, "SonarQube server", SERVER_URL, TOKEN)) {
        String connectionId = defaultIfBlank((String) m.get(CONNECTION_ID), DEFAULT_CONNECTION_ID);
        String url = (String) m.get(SERVER_URL);
        String token = (String) m.get(TOKEN);
        boolean disableNotifications = (Boolean) m.getOrDefault(DISABLE_NOTIFICATIONS, false);
        ServerConnectionSettings connectionSettings = new ServerConnectionSettings(connectionId, url, token, null, disableNotifications, httpClient);
        addIfUniqueConnectionId(serverConnections, connectionId, connectionSettings);
      }
    });
  }

  private static void parseSonarCloudConnections(Map<String, Object> connectionsMap, Map<String, ServerConnectionSettings> serverConnections, ApacheHttpClient httpClient) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> sonarcloudEntries = (List<Map<String, Object>>) connectionsMap.getOrDefault("sonarcloud", Collections.emptyList());
    sonarcloudEntries.forEach(m -> {

      if (checkRequiredAttribute(m, "SonarCloud", ORGANIZATION_KEY, TOKEN)) {
        String connectionId = defaultIfBlank((String) m.get(CONNECTION_ID), DEFAULT_CONNECTION_ID);
        String organizationKey = (String) m.get(ORGANIZATION_KEY);
        String token = (String) m.get(TOKEN);
        boolean disableNotifs = (Boolean) m.getOrDefault(DISABLE_NOTIFICATIONS, false);
        addIfUniqueConnectionId(serverConnections, connectionId,
          new ServerConnectionSettings(connectionId, ServerConnectionSettings.SONARCLOUD_URL, token, organizationKey, disableNotifs, httpClient));
      }
    });
  }

  private static boolean checkRequiredAttribute(Map<String, Object> map, String label, String... requiredAttributes) {
    List<String> missing = stream(requiredAttributes).filter(att -> isBlank((String) map.get(att))).collect(Collectors.toList());
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
  WorkspaceFolderSettings parseFolderSettings(Map<String, Object> params) {
    String testFilePattern = (String) params.get(TEST_FILE_PATTERN);
    Map<String, String> analyzerProperties = getAnalyzerProperties(params);
    String connectionId = null;
    String projectKey = null;
    @SuppressWarnings("unchecked")
    Map<String, Object> connectedModeMap = (Map<String, Object>) params.getOrDefault("connectedMode", Collections.emptyMap());
    if (connectedModeMap.containsKey(PROJECT)) {
      @SuppressWarnings("unchecked")
      Map<String, String> projectBinding = (Map<String, String>) connectedModeMap.get(PROJECT);
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
    return new WorkspaceFolderSettings(connectionId, projectKey, analyzerProperties, testFilePattern);
  }

  private static Map<String, String> getAnalyzerProperties(Map<String, Object> params) {
    @SuppressWarnings("unchecked")
    Map<String, String> map = (Map<String, String>) params.get(ANALYZER_PROPERTIES);
    if (map == null) {
      return Collections.emptyMap();
    }
    return map;
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

  @Override
  public void removed(WorkspaceFolderWrapper removed) {
    // Nothing to do
  }

  public void shutdown() {
    executor.shutdown();
  }

}
