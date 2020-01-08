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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.ls.Utils;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderLifecycleListener;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;

import static org.apache.commons.lang.StringUtils.isBlank;

public class SettingsManager implements WorkspaceFolderLifecycleListener {

  private static final String SONARLINT_CONFIGURATION_NAMESPACE = "sonarlint";
  private static final String DISABLE_TELEMETRY = "disableTelemetry";
  private static final String RULES = "rules";
  private static final String TEST_FILE_PATTERN = "testFilePattern";
  private static final String ANALYZER_PROPERTIES = "analyzerProperties";

  private static final Logger LOG = Loggers.get(SettingsManager.class);

  private final LanguageClient client;

  private WorkspaceSettings currentSettings = null;
  private final CountDownLatch initLatch = new CountDownLatch(1);
  // Setting that are normally specific per workspace folder, but we also keep a cache of global values to analyze files outside any
  // workspace
  private WorkspaceFolderSettings currentDefaultSettings = null;

  private final ExecutorService executor;
  private final List<WorkspaceSettingsChangeListener> globalListeners = new ArrayList<>();
  private final List<WorkspaceFolderSettingsChangeListener> folderListeners = new ArrayList<>();

  public SettingsManager(LanguageClient client) {
    this.client = client;
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
    updateWorkspaceSettingsAsync();
  }

  public void updateWorkspaceSettingsAsync() {
    requestSonarLintConfigurationAsync(null, response -> update(Utils.parseToMap(response.get(0))));
  }

  private void requestSonarLintConfigurationAsync(@Nullable URI uri, Consumer<List<Object>> handler) {
    executor.execute(() -> {
      ConfigurationParams params = new ConfigurationParams();
      ConfigurationItem configurationItem = new ConfigurationItem();
      configurationItem.setSection(SONARLINT_CONFIGURATION_NAMESPACE);
      if (uri != null) {
        configurationItem.setScopeUri(uri.toString());
      }
      params.setItems(Arrays.asList(configurationItem));
      List<Object> response;
      try {
        response = client.configuration(params).get(1, TimeUnit.MINUTES);
      } catch (InterruptedException e) {
        interrupted(e);
        return;
      } catch (ExecutionException | TimeoutException e) {
        LOG.error("Unable to fetch configuration", e);
        return;
      }
      handler.accept(response);
    });
  }

  private static void interrupted(InterruptedException e) {
    LOG.debug("Interrupted!", e);
    Thread.currentThread().interrupt();
  }

  private void update(@Nullable Map<String, Object> params) {
    WorkspaceSettings newSettings = parseSettings(params != null ? params : Collections.emptyMap());
    WorkspaceSettings old = currentSettings;
    this.currentSettings = newSettings;
    WorkspaceFolderSettings newDefaultSettings = parseFolderSettings(params != null ? params : Collections.emptyMap());
    WorkspaceFolderSettings oldDefault = currentDefaultSettings;
    this.currentDefaultSettings = newDefaultSettings;
    initLatch.countDown();
    if (!Objects.equals(old, newSettings)) {
      LOG.debug("Global settings updated: {}", newSettings);
      globalListeners.forEach(l -> l.onChange(old, newSettings));
    }
    if (!Objects.equals(oldDefault, newDefaultSettings)) {
      LOG.debug("Default settings updated: {}", newDefaultSettings);
      folderListeners.forEach(l -> l.onChange(null, oldDefault, newDefaultSettings));
    }
  }

  public void updateWorkspaceFolderSettingsAsync(WorkspaceFolderWrapper f) {
    requestSonarLintConfigurationAsync(f.getUri(), response -> update(f, Utils.parseToMap(response.get(0))));

  }

  private void update(WorkspaceFolderWrapper f, @Nullable Map<String, Object> params) {
    WorkspaceFolderSettings newSettings = parseFolderSettings(params != null ? params : Collections.emptyMap());
    WorkspaceFolderSettings old = f.getRawSettings();
    if (!Objects.equals(old, newSettings)) {
      f.setSettings(newSettings);
      LOG.debug("Workspace workspaceFolderPath '{}' configuration updated: {}", f, newSettings);
      folderListeners.forEach(l -> l.onChange(f, old, newSettings));
    }
  }

  static WorkspaceSettings parseSettings(Map<String, Object> params) {
    boolean disableTelemetry = (Boolean) params.getOrDefault(DISABLE_TELEMETRY, false);
    Map<String, ServerConnectionSettings> serverConnections = parseServerConnections(params);
    Set<RuleKey> excludedRules = parseRuleKeysMatching(params, SettingsManager.hasLevelSetTo("off"));
    Set<RuleKey> includedRules = parseRuleKeysMatching(params, SettingsManager.hasLevelSetTo("on"));
    return new WorkspaceSettings(disableTelemetry, serverConnections, excludedRules, includedRules);
  }

  private static Map<String, ServerConnectionSettings> parseServerConnections(Map<String, Object> params) {
    @SuppressWarnings("unchecked")
    Map<String, Object> connectedModeMap = (Map<String, Object>) params.getOrDefault("connectedMode", Collections.emptyMap());
    @SuppressWarnings("unchecked")
    List<Map<String, String>> serversEntries = (List<Map<String, String>>) connectedModeMap.getOrDefault("servers", Collections.emptyList());
    Map<String, ServerConnectionSettings> serverConnections = new HashMap<>();
    serversEntries.forEach(m -> {
      String serverId = m.get("serverId");
      String url = m.get("serverUrl");
      String token = m.get("token");
      String organization = m.get("organizationKey");

      if (!isBlank(serverId) && !isBlank(url) && !isBlank(token)) {
        serverConnections.put(serverId, new ServerConnectionSettings(serverId, url, token, organization));
      } else {
        LOG.error("Incomplete server configuration. Required parameters must not be blank: serverId, serverUrl, token.");
      }
    });
    return serverConnections;
  }

  // Visible for testing
  static WorkspaceFolderSettings parseFolderSettings(Map<String, Object> params) {
    String testFilePattern = (String) params.get(TEST_FILE_PATTERN);
    Map<String, String> analyzerProperties = getAnalyzerProperties(params);
    @SuppressWarnings("unchecked")
    Map<String, Object> connectedModeMap = (Map<String, Object>) params.getOrDefault("connectedMode", Collections.emptyMap());
    @SuppressWarnings("unchecked")
    Map<String, String> projectBinding = (Map<String, String>) connectedModeMap.getOrDefault("project", Collections.emptyMap());
    return new WorkspaceFolderSettings(projectBinding.get("serverId"), projectBinding.get("projectKey"), analyzerProperties, testFilePattern);
  }

  @SuppressWarnings("unchecked")
  private static Set<RuleKey> parseRuleKeysMatching(Map<String, Object> params, Predicate<Map.Entry<String, Object>> filter) {
    return ((Map<String, Object>) params.getOrDefault(RULES, Collections.emptyMap()))
      .entrySet()
      .stream()
      .filter(filter)
      .map(SettingsManager::safeParseRuleKey)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  @SuppressWarnings("unchecked")
  private static Predicate<Map.Entry<String, Object>> hasLevelSetTo(String expectedLevel) {
    return e -> e.getValue() instanceof Map &&
      expectedLevel.equals(((Map<String, String>) e.getValue()).get("level"));
  }

  @CheckForNull
  private static RuleKey safeParseRuleKey(Map.Entry<String, Object> e) {
    try {
      return RuleKey.parse(e.getKey());
    } catch (Exception any) {
      return null;
    }
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
    updateWorkspaceFolderSettingsAsync(added);
  }

  @Override
  public void removed(WorkspaceFolderWrapper removed) {
    // Nothing to do
  }

  public void shutdown() {
    executor.shutdown();
  }

}
