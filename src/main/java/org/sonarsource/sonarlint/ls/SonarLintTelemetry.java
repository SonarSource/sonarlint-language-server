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
package org.sonarsource.sonarlint.ls;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.telemetry.TelemetryHttpClient;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClient;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;

public class SonarLintTelemetry implements WorkspaceSettingsChangeListener {
  public static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";
  private static final Logger LOG = Loggers.get(SonarLintTelemetry.class);

  private final Supplier<ScheduledExecutorService> executorFactory;
  private final ApacheHttpClient httpClient;
  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private final NodeJsRuntime nodeJsRuntime;
  private final StandaloneEngineManager standaloneEngineManager;

  private TelemetryManager telemetry;

  @VisibleForTesting
  ScheduledFuture<?> scheduledFuture;
  private ScheduledExecutorService scheduler;
  private Map<String, Object> additionalAttributes;

  public SonarLintTelemetry(ApacheHttpClient httpClient, SettingsManager settingsManager, ProjectBindingManager bindingManager, NodeJsRuntime nodeJsRuntime,
    StandaloneEngineManager standaloneEngineManager) {
    this(() -> Executors.newScheduledThreadPool(1, Utils.threadFactory("SonarLint Telemetry", false)), httpClient, settingsManager, bindingManager, nodeJsRuntime,
      standaloneEngineManager);
  }

  public SonarLintTelemetry(Supplier<ScheduledExecutorService> executorFactory, ApacheHttpClient httpClient, SettingsManager settingsManager, ProjectBindingManager bindingManager,
    NodeJsRuntime nodeJsRuntime, StandaloneEngineManager standaloneEngineManager) {
    this.executorFactory = executorFactory;
    this.httpClient = httpClient;
    this.settingsManager = settingsManager;
    this.bindingManager = bindingManager;
    this.nodeJsRuntime = nodeJsRuntime;
    this.standaloneEngineManager = standaloneEngineManager;
  }

  private void optOut(boolean optOut) {
    if (telemetry != null) {
      if (optOut) {
        if (telemetry.isEnabled()) {
          LOG.debug("Disabling telemetry");
          telemetry.disable();
        }
      } else {
        if (!telemetry.isEnabled()) {
          LOG.debug("Enabling telemetry");
          telemetry.enable();
        }
      }
    }
  }

  public boolean enabled() {
    return telemetry != null && telemetry.isEnabled();
  }

  public void initialize(@Nullable String productKey, @Nullable String telemetryStorage, String productName, String productVersion, String ideVersion,
    Map<String, Object> additionalAttributes) {
    Path storagePath = getStoragePath(productKey, telemetryStorage);
    init(storagePath, productName, productVersion, ideVersion, additionalAttributes);
  }

  // Visible for testing
  void init(@Nullable Path storagePath, String productName, String productVersion, String ideVersion, Map<String, Object> additionalAttributes) {
    this.additionalAttributes = additionalAttributes;
    if (storagePath == null) {
      LOG.info("Telemetry disabled because storage path is null");
      return;
    }
    if ("true".equals(System.getProperty(DISABLE_PROPERTY_KEY))) {
      LOG.info("Telemetry disabled by system property");
      return;
    }
    TelemetryHttpClient client = new TelemetryHttpClient(productName, productVersion, ideVersion, httpClient);
    this.telemetry = newTelemetryManager(storagePath, client);
    try {
      this.scheduler = executorFactory.get();
      this.scheduledFuture = scheduler.scheduleWithFixedDelay(this::upload,
        1, TimeUnit.HOURS.toMinutes(6), TimeUnit.MINUTES);
    } catch (Exception e) {
      if (SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Failed scheduling period telemetry job", e);
      }
    }
  }

  @VisibleForTesting
  static Path getStoragePath(@Nullable String productKey, @Nullable String telemetryStorage) {
    if (productKey != null) {
      if (telemetryStorage != null) {
        TelemetryPathManager.migrate(productKey, Paths.get(telemetryStorage));
      }
      return TelemetryPathManager.getPath(productKey);
    }
    return telemetryStorage != null ? Paths.get(telemetryStorage) : null;
  }

  TelemetryManager newTelemetryManager(Path path, TelemetryHttpClient client) {
    return new TelemetryManager(path, client,
      new TelemetryClientAttributesProviderImpl(settingsManager, bindingManager, nodeJsRuntime, standaloneEngineManager, additionalAttributes));
  }

  @VisibleForTesting
  void upload() {
    if (enabled()) {
      telemetry.uploadLazily();
    }
  }

  public void analysisDoneOnMultipleFiles() {
    if (enabled()) {
      telemetry.analysisDoneOnMultipleFiles();
    }
  }

  public void analysisDoneOnSingleLanguage(Language language, int analysisTimeMs) {
    if (enabled()) {
      telemetry.analysisDoneOnSingleLanguage(language, analysisTimeMs);
    }
  }

  public void addReportedRules(Set<String> ruleKeys) {
    if (enabled()) {
      telemetry.addReportedRules(ruleKeys);
    }
  }

  public void devNotificationsReceived(String category) {
    if (enabled()) {
      telemetry.devNotificationsReceived(category);
    }
  }

  public void devNotificationsClicked(String eventType) {
    if (enabled()) {
      telemetry.devNotificationsClicked(eventType);
    }
  }

  public void showHotspotRequestReceived() {
    if (enabled()) {
      telemetry.showHotspotRequestReceived();
    }
  }

  public void taintVulnerabilitiesInvestigatedLocally() {
    if (enabled()) {
      telemetry.taintVulnerabilitiesInvestigatedLocally();
    }
  }

  public void taintVulnerabilitiesInvestigatedRemotely() {
    if (enabled()) {
      telemetry.taintVulnerabilitiesInvestigatedRemotely();
    }
  }

  public void addQuickFixAppliedForRule(String ruleKey) {
    if (enabled()) {
      telemetry.addQuickFixAppliedForRule(ruleKey);
    }
  }

  public void stop() {
    if (enabled()) {
      telemetry.stop();
    }

    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
      scheduledFuture = null;
    }
    if (scheduler != null) {
      scheduler.shutdown();
    }
  }

  @Override
  public void onChange(WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    optOut(newValue.isDisableTelemetry());
  }
}
