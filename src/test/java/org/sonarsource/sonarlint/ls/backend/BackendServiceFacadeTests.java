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
package org.sonarsource.sonarlint.ls.backend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.backend.BackendServiceFacade.MONITORING_DISABLED_PROPERTY_KEY;

class BackendServiceFacadeTests {

  public static final String SONARLINT_HTTP_CONNECTION_TIMEOUT = "sonarlint.http.connectTimeout";
  public static final String SONARLINT_HTTP_SOCKET_TIMEOUT = "sonarlint.http.socketTimeout";
  SonarLintRpcClientDelegate backend = mock(SonarLintRpcClientDelegate.class);
  BackendServiceFacade underTest = new BackendServiceFacade(backend, mock(LanguageClientLogger.class), mock(SonarLintExtendedLanguageClient.class), 0);

  @AfterEach
  void tearDown() {
    System.clearProperty(MONITORING_DISABLED_PROPERTY_KEY);
  }

  @Test
  void shouldFailIfBackendNotInitialized() {
    assertThrows(IllegalStateException.class, () -> underTest.getBackendService());
  }

  @Test
  void shouldReturnDurationInMinutes() {
    System.setProperty(SONARLINT_HTTP_CONNECTION_TIMEOUT, "3");

    var result = BackendServiceFacade.getTimeoutProperty(SONARLINT_HTTP_CONNECTION_TIMEOUT);

    assertThat(result).isNotNull();
    assertThat(result.toMinutes()).isEqualTo(3);
  }

  @Test
  void shouldReturnDurationInSeconds() {
    System.setProperty(SONARLINT_HTTP_CONNECTION_TIMEOUT, "PT3S");

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
    System.setProperty(MONITORING_DISABLED_PROPERTY_KEY, "true");

    var result = underTest.shouldEnableMonitoring();

    assertThat(result).isFalse();
  }

  @Test
  void shouldEnableMonitoringWhenNotDisabled() {
    System.setProperty(MONITORING_DISABLED_PROPERTY_KEY, "false");

    var result = underTest.shouldEnableMonitoring();

    assertThat(result).isTrue();
  }

  @Test
  void shouldComputeBackendCapabilities() {
    // make sure monitoring is disabled
    System.setProperty(MONITORING_DISABLED_PROPERTY_KEY, "true");

    // make sure telemetry is disabled
    SonarLintTelemetry telemetryService = mock(SonarLintTelemetry.class);
    underTest.setTelemetry(telemetryService);
    when(telemetryService.enabled()).thenReturn(false);

    var backendInitParams = mock(BackendInitParams.class);
    var backendCapabilities = underTest.getBackendCapabilities(backendInitParams);

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
      .doesNotContain(BackendCapability.MONITORING);

  }

  @Test
  void shouldComputeBackendCapabilities_withTelemetryAndMonitoring() {
    // make sure monitoring is disabled
    System.setProperty(MONITORING_DISABLED_PROPERTY_KEY, "false");

    // make sure telemetry is disabled
    SonarLintTelemetry telemetryService = mock(SonarLintTelemetry.class);
    underTest.setTelemetry(telemetryService);
    when(telemetryService.enabled()).thenReturn(true);

    var backendInitParams = mock(BackendInitParams.class);
    var backendCapabilities = underTest.getBackendCapabilities(backendInitParams);

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
      .contains(BackendCapability.MONITORING);

  }

}
