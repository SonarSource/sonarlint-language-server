/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.ls.backend;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.ls.EnabledLanguages;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.backend.BackendServiceFacade.FLIGHT_RECORDER_ENABLED_PROPERTY_KEY;
import static org.sonarsource.sonarlint.ls.backend.BackendServiceFacade.MONITORING_DISABLED_PROPERTY_KEY;

@ExtendWith(SystemStubsExtension.class)
class BackendServiceFacadeTests {

  @SystemStub
  private SystemProperties systemProperties;

  public static final String SONARLINT_HTTP_CONNECTION_TIMEOUT = "sonarlint.http.connectTimeout";
  public static final String SONARLINT_HTTP_SOCKET_TIMEOUT = "sonarlint.http.socketTimeout";
  SonarLintRpcClientDelegate backend = mock(SonarLintRpcClientDelegate.class);
  BackendServiceFacade underTest = new BackendServiceFacade(backend, mock(LanguageClientLogger.class), mock(SonarLintExtendedLanguageClient.class),
    new EnabledLanguages(List.of(), null));

  @Test
  void shouldReturnDurationInMinutes() {
    systemProperties.set(SONARLINT_HTTP_CONNECTION_TIMEOUT, "3");

    var result = BackendServiceFacade.getTimeoutProperty(SONARLINT_HTTP_CONNECTION_TIMEOUT);

    assertThat(result).isNotNull();
    assertThat(result.toMinutes()).isEqualTo(3);
  }

  @Test
  void shouldReturnDurationInSeconds() {
    systemProperties.set(SONARLINT_HTTP_CONNECTION_TIMEOUT, "PT3S");

    var result = BackendServiceFacade.getTimeoutProperty(SONARLINT_HTTP_CONNECTION_TIMEOUT);

    assertThat(result).isNotNull();
    assertThat(result.toSeconds()).isEqualTo(3);
  }

  @Test
  void shouldReturnNullTimeout() {
    var result = BackendServiceFacade.getTimeoutProperty(SONARLINT_HTTP_SOCKET_TIMEOUT);

    assertThat(result).isNull();
  }

  @Test
  void shouldNotEnableMonitoringWhenDisabled() {
    systemProperties.set(MONITORING_DISABLED_PROPERTY_KEY, "true");

    var result = underTest.shouldEnableMonitoring();

    assertThat(result).isFalse();
  }

  @Test
  void shouldEnableMonitoringWhenNotDisabled() {
    systemProperties.set(MONITORING_DISABLED_PROPERTY_KEY, "false");

    var result = underTest.shouldEnableMonitoring();

    assertThat(result).isTrue();
  }

  @Test
  void shouldComputeBackendCapabilities() {
    // make sure monitoring is disabled
    systemProperties.set(MONITORING_DISABLED_PROPERTY_KEY, "true");

    // make sure telemetry is disabled
    SonarLintTelemetry telemetryService = mock(SonarLintTelemetry.class);
    underTest.setTelemetry(telemetryService);
    when(telemetryService.enabled()).thenReturn(false);

    var backendCapabilities = underTest.getBackendCapabilities();

    assertThat(backendCapabilities)
      .isNotNull()
      .isNotEmpty()
      .contains(BackendCapability.SMART_NOTIFICATIONS)
      .contains(BackendCapability.SECURITY_HOTSPOTS)
      .contains(BackendCapability.PROJECT_SYNCHRONIZATION)
      .contains(BackendCapability.EMBEDDED_SERVER)
      .contains(BackendCapability.DATAFLOW_BUG_DETECTION)
      .contains(BackendCapability.FULL_SYNCHRONIZATION)
      .contains(BackendCapability.SERVER_SENT_EVENTS)
      .doesNotContain(BackendCapability.TELEMETRY)
      .doesNotContain(BackendCapability.MONITORING)
      .doesNotContain(BackendCapability.FLIGHT_RECORDER);

  }

  @Test
  void shouldComputeBackendCapabilities_withTelemetryAndMonitoring() {
    // make sure monitoring is not disabled
    systemProperties.set(MONITORING_DISABLED_PROPERTY_KEY, "false");

    // make sure telemetry is not disabled
    SonarLintTelemetry telemetryService = mock(SonarLintTelemetry.class);
    underTest.setTelemetry(telemetryService);
    when(telemetryService.enabled()).thenReturn(true);

    var backendCapabilities = underTest.getBackendCapabilities();

    assertThat(backendCapabilities)
      .isNotNull()
      .isNotEmpty()
      .contains(BackendCapability.SMART_NOTIFICATIONS)
      .contains(BackendCapability.SECURITY_HOTSPOTS)
      .contains(BackendCapability.PROJECT_SYNCHRONIZATION)
      .contains(BackendCapability.EMBEDDED_SERVER)
      .contains(BackendCapability.DATAFLOW_BUG_DETECTION)
      .contains(BackendCapability.FULL_SYNCHRONIZATION)
      .contains(BackendCapability.SERVER_SENT_EVENTS)
      .contains(BackendCapability.TELEMETRY)
      .contains(BackendCapability.MONITORING)
      .doesNotContain(BackendCapability.FLIGHT_RECORDER);

  }

  @Test
  void shouldComputeBackendCapabilities_withFlightRecorder() {
    // make sure monitoring is not disabled
    systemProperties.set(MONITORING_DISABLED_PROPERTY_KEY, "false");
    // make sure flight recorder is enabled
    systemProperties.set(FLIGHT_RECORDER_ENABLED_PROPERTY_KEY, "true");

    // make sure telemetry is not disabled
    SonarLintTelemetry telemetryService = mock(SonarLintTelemetry.class);
    underTest.setTelemetry(telemetryService);
    when(telemetryService.enabled()).thenReturn(true);

    var backendCapabilities = underTest.getBackendCapabilities();

    assertThat(backendCapabilities)
      .isNotNull()
      .isNotEmpty()
      .contains(BackendCapability.SMART_NOTIFICATIONS)
      .contains(BackendCapability.SECURITY_HOTSPOTS)
      .contains(BackendCapability.PROJECT_SYNCHRONIZATION)
      .contains(BackendCapability.EMBEDDED_SERVER)
      .contains(BackendCapability.DATAFLOW_BUG_DETECTION)
      .contains(BackendCapability.FULL_SYNCHRONIZATION)
      .contains(BackendCapability.SERVER_SENT_EVENTS)
      .contains(BackendCapability.TELEMETRY)
      .contains(BackendCapability.MONITORING)
      .contains(BackendCapability.FLIGHT_RECORDER);

  }

  @Test
  void should_use_cursor_as_product_key_if_present_in_app_name() {
    var productKey = BackendServiceFacade.determineProductKey("Cursor", null);

    assertThat(productKey).isEqualTo("cursor");
  }

  @Test
  void should_use_windsurf_as_product_key_if_present_in_app_name() {
    var productKey = BackendServiceFacade.determineProductKey("Windsurf", null);

    assertThat(productKey).isEqualTo("windsurf");
  }

  @Test
  void should_use_kiro_as_product_key_if_present_in_app_name() {
    var productKey = BackendServiceFacade.determineProductKey("Kiro", null);

    assertThat(productKey).isEqualTo("kiro");
  }

  @Test
  void should_use_client_product_key_if_unknown_app_name() {
    var productKey = BackendServiceFacade.determineProductKey("XXX", "vscode");

    assertThat(productKey).isEqualTo("vscode");
  }

  @Test
  void should_return_cursor_as_ide_name_if_present_in_app_name() {
    var ideName = BackendServiceFacade.determineIdeName("CuRsOR");

    assertThat(ideName).isEqualTo("Cursor");
  }

  @Test
  void should_return_windsurf_as_ide_name_if_present_in_app_name() {
    var ideName = BackendServiceFacade.determineIdeName("windsurf");

    assertThat(ideName).isEqualTo("Windsurf");
  }

  @Test
  void should_return_windsurf_as_ide_name_when_contained_in_app_name() {
    var ideName = BackendServiceFacade.determineIdeName("Windsurf Next");

    assertThat(ideName).isEqualTo("Windsurf");
  }

  @Test
  void should_return_kiro_as_ide_name_if_present_in_app_name() {
    var ideName = BackendServiceFacade.determineIdeName("kiro");

    assertThat(ideName).isEqualTo("Kiro");
  }

  @Test
  void should_return_kiro_as_ide_name_when_contained_in_app_name() {
    var ideName = BackendServiceFacade.determineIdeName("Kiro Preview");

    assertThat(ideName).isEqualTo("Kiro");
  }

  @Test
  void should_return_vscode_as_ide_name_if_unknown_app_name() {
    var ideName = BackendServiceFacade.determineIdeName("Unknown IDE");

    assertThat(ideName).isEqualTo("Visual Studio Code");
  }

  @Test
  void should_return_vscode_as_ide_name_for_standard_vscode() {
    var ideName = BackendServiceFacade.determineIdeName("Visual Studio CODE");

    assertThat(ideName).isEqualTo("Visual Studio Code");
  }

}
