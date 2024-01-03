/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.sonarlint.ls.telemetry;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.telemetry.TelemetryHttpClient;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;
import org.sonarsource.sonarlint.ls.NodeJsRuntime;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry.getStoragePath;

class SonarLintTelemetryTests {
  private SonarLintTelemetry telemetry;
  private final TelemetryManager telemetryManager = mock(TelemetryManager.class);
  private static final BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  private final BackendService backendService = mock(BackendService.class);

  @RegisterExtension
  public SonarLintLogTester logTester = new SonarLintLogTester();

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
    when(backendServiceFacade.getBackendService()).thenReturn(backendService);
    var telemetry = new SonarLintTelemetry(mock(SettingsManager.class), mock(ProjectBindingManager.class), mock(NodeJsRuntime.class),
      backendServiceFacade, logTester.getLogger()) {
      @Override
      TelemetryManager newTelemetryManager(Path path, TelemetryHttpClient client) {
        return telemetryManager;
      }
    };
    telemetry.init(Paths.get("dummy"), "product", "version", "ideVersion", "platform", "architecture", new HashMap<>());
    return telemetry;
  }

  @Test
  void disable_property_should_disable_telemetry() {
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
    assertThat(telemetry.newTelemetryManager(Paths.get(""), mock(TelemetryHttpClient.class))).isNotNull();
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
    return new WorkspaceSettings(disableTelemetry, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false, false, null, false);
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
  void devNotificationsReceived_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    var eventType = "eventType";
    telemetry.devNotificationsReceived(eventType);
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).devNotificationsReceived(eventType);
  }

  @Test
  void devNotificationsReceived_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.devNotificationsClicked("ignored");
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void devNotificationsClicked_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    var eventType = "eventType";
    telemetry.devNotificationsClicked(eventType);
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).devNotificationsClicked(eventType);
  }

  @Test
  void devNotificationsClicked_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.devNotificationsClicked("ignored");
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void showHotspotRequestReceived_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.showHotspotRequestReceived();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).showHotspotRequestReceived();
  }

  @Test
  void showHotspotRequestReceived_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.showHotspotRequestReceived();
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void taintVulnerabilitiesInvestigatedLocally_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.taintVulnerabilitiesInvestigatedLocally();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).taintVulnerabilitiesInvestigatedLocally();
  }

  @Test
  void taintVulnerabilitiesInvestigatedLocally_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.taintVulnerabilitiesInvestigatedLocally();
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void taintVulnerabilitiesInvestigatedRemotely_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.taintVulnerabilitiesInvestigatedRemotely();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).taintVulnerabilitiesInvestigatedRemotely();
  }

  @Test
  void taintVulnerabilitiesInvestigatedRemotely_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.taintVulnerabilitiesInvestigatedRemotely();
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void addQuickFixAppliedForRule_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.addQuickFixAppliedForRule("repo:key");
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).addQuickFixAppliedForRule("repo:key");
  }

  @Test
  void addQuickFixAppliedForRule_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.addQuickFixAppliedForRule("repo:key");
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void helpAndFeedbackLinkClicked_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.helpAndFeedbackLinkClicked("docs");
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void helpAndFeedbackLinkClicked_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.helpAndFeedbackLinkClicked("suggestFeature");
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).helpAndFeedbackLinkClicked("suggestFeature");
  }

  @Test
  void should_start_disabled_when_storagePath_null() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    var telemetry = new SonarLintTelemetry(mock(SettingsManager.class), mock(ProjectBindingManager.class), mock(NodeJsRuntime.class),
      mock(BackendServiceFacade.class), logTester.getLogger()) {
      @Override
      TelemetryManager newTelemetryManager(Path path, TelemetryHttpClient client) {
        return telemetryManager;
      }
    };
    telemetry.init(null, "product", "version", "ideVersion", "platform", "architecture", new HashMap<>());
    assertThat(telemetry.enabled()).isFalse();
  }

  @Test
  void getStoragePath_should_return_null_when_configuration_missing() {
    assertThat(getStoragePath(null, null)).isNull();
  }

  @Test
  void getStoragePath_should_return_old_path_when_product_key_missing() {
    var oldStorage = "dummy";
    assertThat(getStoragePath(null, oldStorage)).isEqualTo(Paths.get(oldStorage));
  }

  @Test
  void getStoragePath_should_return_new_path_when_product_key_present() {
    var productKey = "vim";
    assertThat(getStoragePath(productKey, "dummy")).isEqualTo(TelemetryPathManager.getPath(productKey));
  }

  @Test
  void addReportedRules() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    var rule = "ruleKey";
    var reportedRuleKeys = Collections.singleton(rule);
    telemetry.addReportedRules(reportedRuleKeys);
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).addReportedRules(reportedRuleKeys);
  }

}
