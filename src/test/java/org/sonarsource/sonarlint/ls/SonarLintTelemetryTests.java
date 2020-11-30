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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.telemetry.TelemetryHttpClient;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.SonarLintTelemetry.getStoragePath;

class SonarLintTelemetryTests {
  private SonarLintTelemetry telemetry;
  private final TelemetryManager telemetryManager = mock(TelemetryManager.class);

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  @BeforeEach
  public void setUp() {
    this.telemetry = createTelemetry();
  }

  @AfterEach
  public void after() {
    System.clearProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY);
  }

  private SonarLintTelemetry createTelemetry() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    SonarLintTelemetry telemetry = new SonarLintTelemetry() {
      @Override
      TelemetryManager newTelemetryManager(Path path, TelemetryHttpClient client, BooleanSupplier usesConnectedMode, BooleanSupplier usesSonarCloud, Supplier<String> nodeVersion) {
        return telemetryManager;
      }
    };
    telemetry.init(Paths.get("dummy"), "product", "version", "ideVersion", () -> true, () -> true, () -> "");
    return telemetry;
  }

  @Test
  void disable_property_should_disable_telemetry() throws Exception {
    assertThat(createTelemetry().enabled()).isTrue();

    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    assertThat(createTelemetry().enabled()).isFalse();
  }

  @Test
  void stop_should_trigger_stop_telemetry() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.stop();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).stop();
  }

  @Test
  void test_scheduler() {
    assertThat((Object) telemetry.scheduledFuture).isNotNull();
    assertThat(telemetry.scheduledFuture.getDelay(TimeUnit.MINUTES)).isBetween(0L, 1L);
    telemetry.stop();
    assertThat((Object) telemetry.scheduledFuture).isNull();
  }

  @Test
  void create_telemetry_manager() {
    assertThat(telemetry.newTelemetryManager(Paths.get(""), mock(TelemetryHttpClient.class), () -> true, () -> true, () -> "")).isNotNull();
  }

  @Test
  void optOut_should_trigger_disable_telemetry() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.onChange(null, newWorkspaceSettingsWithTelemetrySetting(true));
    verify(telemetryManager).disable();
    telemetry.stop();
  }

  @Test
  void should_not_opt_out_twice() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.onChange(null, newWorkspaceSettingsWithTelemetrySetting(true));
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void optIn_should_trigger_enable_telemetry() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.onChange(null, newWorkspaceSettingsWithTelemetrySetting(false));
    verify(telemetryManager).enable();
  }

  private static WorkspaceSettings newWorkspaceSettingsWithTelemetrySetting(boolean disableTelemetry) {
    return new WorkspaceSettings(disableTelemetry, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false, false, null);
  }

  @Test
  void upload_should_trigger_upload_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.upload();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).uploadLazily();
  }

  @Test
  void upload_should_not_trigger_upload_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.upload();
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void analysisDoneOnMultipleFiles_should_trigger_analysisDoneOnMultipleFiles_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.analysisDoneOnMultipleFiles();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).analysisDoneOnMultipleFiles();
  }

  @Test
  void analysisDoneOnMultipleFiles_should_not_trigger_analysisDoneOnMultipleFiles_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.analysisDoneOnMultipleFiles();
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void analysisDoneOnSingleFile_should_trigger_analysisDoneOnSingleFile_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.analysisDoneOnSingleLanguage(Language.JAVA, 1000);
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).analysisDoneOnSingleLanguage(Language.JAVA, 1000);
  }

  @Test
  void analysisDoneOnSingleFile_should_not_trigger_analysisDoneOnSingleFile_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.analysisDoneOnSingleLanguage(Language.JAVA, 1000);
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void should_start_disabled_when_storagePath_null() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    SonarLintTelemetry telemetry = new SonarLintTelemetry() {
      @Override
      TelemetryManager newTelemetryManager(Path path, TelemetryHttpClient client, BooleanSupplier usesConnectedMode, BooleanSupplier usesSonarCloud, Supplier<String> nodeVersion) {
        return telemetryManager;
      }
    };
    telemetry.init(null, "product", "version", "ideVersion", () -> true, () -> true, () -> "");
    assertThat(telemetry.enabled()).isFalse();
  }

  @Test
  void getStoragePath_should_return_null_when_configuration_missing() {
    assertThat(getStoragePath(null, null)).isNull();
  }

  @Test
  void getStoragePath_should_return_old_path_when_product_key_missing() {
    String oldStorage = "dummy";
    assertThat(getStoragePath(null, oldStorage)).isEqualTo(Paths.get(oldStorage));
  }

  @Test
  void getStoragePath_should_return_new_path_when_product_key_present() {
    String productKey = "vim";
    assertThat(getStoragePath(productKey, "dummy")).isEqualTo(TelemetryPathManager.getPath(productKey));
  }
}
