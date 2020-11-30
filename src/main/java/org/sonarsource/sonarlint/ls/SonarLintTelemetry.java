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
package org.sonarsource.sonarlint.ls;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.client.api.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.telemetry.TelemetryClientAttributesProvider;
import org.sonarsource.sonarlint.core.telemetry.TelemetryHttpClient;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

public class SonarLintTelemetry implements WorkspaceSettingsChangeListener {
  public static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";
  private static final Logger LOG = Loggers.get(SonarLintTelemetry.class);

  private final Supplier<ScheduledExecutorService> executorFactory;
  private TelemetryManager telemetry;

  @VisibleForTesting
  ScheduledFuture<?> scheduledFuture;
  private ScheduledExecutorService scheduler;

  public SonarLintTelemetry() {
    this(() -> Executors.newScheduledThreadPool(1, Utils.threadFactory("SonarLint Telemetry", false)));
  }

  public SonarLintTelemetry(Supplier<ScheduledExecutorService> executorFactory) {
    this.executorFactory = executorFactory;
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

  private static TelemetryClientConfig getTelemetryClientConfig() {
    return new TelemetryClientConfig.Builder()
      .userAgent("SonarLint")
      .build();
  }

  public boolean enabled() {
    return telemetry != null && telemetry.isEnabled();
  }

  public void init(@Nullable String productKey, @Nullable String telemetryStorage, String productName, String productVersion, String ideVersion,
    BooleanSupplier usesConnectedMode,
    BooleanSupplier usesSonarCloud,
    Supplier<String> nodeVersion) {
    Path storagePath = getStoragePath(productKey, telemetryStorage);
    init(storagePath, productName, productVersion, ideVersion, usesConnectedMode, usesSonarCloud, nodeVersion);
  }

  // Visible for testing
  void init(@Nullable Path storagePath, String productName, String productVersion, String ideVersion, BooleanSupplier usesConnectedMode,
    BooleanSupplier usesSonarCloud, Supplier<String> nodeVersion) {
    if (storagePath == null) {
      LOG.info("Telemetry disabled because storage path is null");
      return;
    }
    if ("true".equals(System.getProperty(DISABLE_PROPERTY_KEY))) {
      LOG.info("Telemetry disabled by system property");
      return;
    }
    TelemetryClientConfig clientConfig = getTelemetryClientConfig();
    TelemetryHttpClient client = new TelemetryHttpClient(clientConfig, productName, productVersion, ideVersion);
    this.telemetry = newTelemetryManager(storagePath, client, usesConnectedMode, usesSonarCloud, nodeVersion);
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

  TelemetryManager newTelemetryManager(Path path, TelemetryHttpClient client, BooleanSupplier usesConnectedMode, BooleanSupplier usesSonarCloud, Supplier<String> nodeVersion) {
    return new TelemetryManager(path, client, new TelemetryClientAttributesProvider() {
      @Override
      public boolean usesConnectedMode() {
        return usesConnectedMode.getAsBoolean();
      }

      @Override
      public boolean useSonarCloud() {
        return usesSonarCloud.getAsBoolean();
      }

      @Override
      public Optional<String> nodeVersion() {
        return Optional.ofNullable(nodeVersion.get());
      }

      @Override
      public boolean devNotificationsDisabled() {
        return false;
      }
    });
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
