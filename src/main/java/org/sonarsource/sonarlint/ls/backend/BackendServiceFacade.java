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
package org.sonarsource.sonarlint.ls.backend;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarCloudAlternativeEnvironmentDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SslConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.telemetry.TelemetryInitParams;

public class BackendServiceFacade {

  public static final String ROOT_CONFIGURATION_SCOPE = "<root>";

  private final BackendService backendService;
  private final BackendInitParams initParams;
  private final ConfigurationScopeDto rootConfigurationScope;
  private final BackendJsonRpcLauncher serverLauncher;
  private final ClientJsonRpcLauncher clientLauncher;
  private final LanguageClientLogger lsLogOutput;
  private SettingsManager settingsManager;
  private TelemetryInitParams telemetryInitParams;
  private final CountDownLatch initLatch = new CountDownLatch(1);
  private SonarLintTelemetry telemetry;

  public BackendServiceFacade(SonarLintRpcClientDelegate rpcClient, LanguageClientLogger lsLogOutput, SonarLintExtendedLanguageClient client) {
    this.lsLogOutput = lsLogOutput;
    var clientToServerOutputStream = new PipedOutputStream();
    PipedInputStream clientToServerInputStream = null;
    try {
      clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);
      var serverToClientOutputStream = new PipedOutputStream();
      var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);
      serverLauncher = new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
      clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, rpcClient);
      this.backendService = new BackendService(clientLauncher.getServerProxy(), lsLogOutput, client);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    this.initParams = new BackendInitParams();
    this.rootConfigurationScope = new ConfigurationScopeDto(ROOT_CONFIGURATION_SCOPE, null, false, ROOT_CONFIGURATION_SCOPE,
      new BindingConfigurationDto(null, null, false)
    );
  }

  public BackendService getBackendService() {
    try {
      var initialized = initLatch.await(10, TimeUnit.SECONDS);
      if (initialized) {
        return backendService;
      } else {
        throw new IllegalStateException("Backend service not initialized in time");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted", e);
    }
  }

  public void setSettingsManager(SettingsManager settingsManager) {
    this.settingsManager = settingsManager;
  }

  public BackendInitParams getInitParams() {
    return initParams;
  }

  private void initOnce(Map<String, ServerConnectionSettings> connections) {
    if (initLatch.getCount() != 0) {
      var sqConnections = BackendService.extractSonarQubeConnections(connections);
      var scConnections = BackendService.extractSonarCloudConnections(connections);
      initParams.setSonarQubeConnections(sqConnections);
      initParams.setSonarCloudConnections(scConnections);
      initParams.setStandaloneRuleConfigByKey(settingsManager.getStandaloneRuleConfigByKey());
      initParams.setFocusOnNewCode(settingsManager.getCurrentSettings().isFocusOnNewCode());
      backendService.initialize(toInitParams(initParams));
      backendService.addConfigurationScopes(new DidAddConfigurationScopesParams(List.of(rootConfigurationScope)));
      initLatch.countDown();
    }
  }

  private InitializeParams toInitParams(BackendInitParams initParams) {
    var telemetryEnabled = telemetry != null && telemetry.enabled();
    return new InitializeParams(
      new ClientConstantInfoDto("Visual Studio Code", initParams.getUserAgent()),
      new TelemetryClientConstantAttributesDto(initParams.getTelemetryProductKey(),
        telemetryInitParams.getProductName(),
        telemetryInitParams.getProductVersion(),
        telemetryInitParams.getIdeVersion(),
        telemetryInitParams.getAdditionalAttributes()),
      getHttpConfiguration(),
      getSonarCloudAlternativeEnvironment(),
      new FeatureFlagsDto(true, true, true,
        true, initParams.isEnableSecurityHotspots(), true, true, true, telemetryEnabled),
      initParams.getStorageRoot(),
      Path.of(initParams.getSonarlintUserHome()),
      initParams.getEmbeddedPluginPaths(),
      initParams.getConnectedModeEmbeddedPluginPathsByKey(),
      initParams.getEnabledLanguagesInStandaloneMode(),
      initParams.getExtraEnabledLanguagesInConnectedMode(),
      initParams.getSonarQubeConnections(),
      initParams.getSonarCloudConnections(),
      initParams.getSonarlintUserHome(),
      initParams.getStandaloneRuleConfigByKey(),
      initParams.isFocusOnNewCode(),
      StringUtils.isEmpty(initParams.getClientNodePath()) ? null : Path.of(initParams.getClientNodePath())
    );
  }

  private static HttpConfigurationDto getHttpConfiguration() {
    return new HttpConfigurationDto(
      new SslConfigurationDto(getPathProperty("sonarlint.ssl.trustStorePath"), System.getProperty("sonarlint.ssl.trustStorePassword"),
        System.getProperty("sonarlint.ssl.trustStoreType"), getPathProperty("sonarlint.ssl.keyStorePath"), System.getProperty("sonarlint.ssl.keyStorePassword"),
        System.getProperty("sonarlint.ssl.keyStoreType")),
      getTimeoutProperty("sonarlint.http.connectTimeout"), getTimeoutProperty("sonarlint.http.socketTimeout"), getTimeoutProperty("sonarlint.http.connectionRequestTimeout"),
      getTimeoutProperty("sonarlint.http.responseTimeout"));
  }

  @Nullable
  private static SonarCloudAlternativeEnvironmentDto getSonarCloudAlternativeEnvironment() {
    var sonarCloudUrl = System.getProperty("sonarlint.internal.sonarcloud.url");
    var sonarCloudWebSocketUrl = System.getProperty("sonarlint.internal.sonarcloud.websocket.url");
    if (sonarCloudUrl != null && sonarCloudWebSocketUrl != null) {
      return new SonarCloudAlternativeEnvironmentDto(URI.create(sonarCloudUrl), URI.create(sonarCloudWebSocketUrl));
    }
    return null;
  }

  @Nullable
  private static Path getPathProperty(String propertyName) {
    var property = System.getProperty(propertyName);
    return property == null ? null : Paths.get(property);
  }

  @Nullable
  private static Duration getTimeoutProperty(String propertyName) {
    var property = System.getProperty(propertyName);
    return property == null ? null : Duration.parse(property);
  }

  public void shutdown() {
    try {
      backendService.shutdown().get(10, TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException e) {
      lsLogOutput.error("Unable to shutdown the SonartLint backend", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      try {
        serverLauncher.close();
      } catch (Exception e) {
        lsLogOutput.error("Unable to stop the SonartLint server launcher", e);
      }
      try {
        clientLauncher.close();
      } catch (Exception e) {
        lsLogOutput.error("Unable to stop the SonartLint client launcher", e);
      }
    }
  }

  public void initialize(Map<String, ServerConnectionSettings> serverConnections) {
    initOnce(serverConnections);
  }

  public void setTelemetryInitParams(TelemetryInitParams telemetryInitParams) {
    this.telemetryInitParams = telemetryInitParams;
  }

  public TelemetryInitParams getTelemetryInitParams() {
    return telemetryInitParams;
  }

  public CountDownLatch getInitLatch() {
    return initLatch;
  }

  public void setTelemetry(SonarLintTelemetry telemetry) {
    this.telemetry = telemetry;
  }
}
