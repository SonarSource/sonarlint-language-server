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

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.JsTsRequirementsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarCloudAlternativeEnvironmentDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarQubeCloudRegionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SslConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.telemetry.TelemetryInitParams;

public class BackendServiceFacade {

  public static final String MONITORING_DISABLED_PROPERTY_KEY = "sonarlint.monitoring.disabled";

  private static final int DEFAULT_INIT_TIMEOUT_SECONDS = 60;

  private final int initTimeoutSeconds;
  private final BackendService backendService;
  private final BackendInitParams initParams;
  private final ConfigurationScopeDto rootConfigurationScope;
  private final ClientJsonRpcLauncher clientLauncher;
  private final LanguageClientLogger lsLogOutput;
  private SettingsManager settingsManager;
  private TelemetryInitParams telemetryInitParams;
  private SonarLintTelemetry telemetry;
  private final CountDownLatch initLatch = new CountDownLatch(1);


  private String omnisharpDirectory;
  private String csharpOssPath;
  private String csharpEnterprisePath;

  public BackendServiceFacade(SonarLintRpcClientDelegate rpcClient, LanguageClientLogger lsLogOutput, SonarLintExtendedLanguageClient client) {
    this(rpcClient, lsLogOutput, client, DEFAULT_INIT_TIMEOUT_SECONDS);
  }

  BackendServiceFacade(SonarLintRpcClientDelegate rpcClient, LanguageClientLogger lsLogOutput, SonarLintExtendedLanguageClient client, int initTimeoutSeconds) {
    this.initTimeoutSeconds = initTimeoutSeconds;
    this.lsLogOutput = lsLogOutput;
    var clientToServerOutputStream = new PipedOutputStream();
    try {
      var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);
      var serverToClientOutputStream = new PipedOutputStream();
      var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);
      new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
      clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, rpcClient);
      this.backendService = new BackendService(clientLauncher.getServerProxy(), lsLogOutput, client);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    this.initParams = new BackendInitParams();
    this.rootConfigurationScope = new ConfigurationScopeDto(BackendService.ROOT_CONFIGURATION_SCOPE, null, false, BackendService.ROOT_CONFIGURATION_SCOPE,
      new BindingConfigurationDto(null, null, false)
    );
  }

  public BackendService getBackendService() {
    try {
      var initialized = initLatch.await(initTimeoutSeconds, TimeUnit.SECONDS);
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

  public void setOmnisharpDirectory(String omnisharpDirectory) {
    this.omnisharpDirectory = omnisharpDirectory;
  }

  public void setCsharpOssPath(String csharpOssPath) {
    this.csharpOssPath = csharpOssPath;
  }

  public void setCsharpEnterprisePath(String csharpEnterprisePath) {
    this.csharpEnterprisePath = csharpEnterprisePath;
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
    var backendCapabilities = getBackendCapabilities();
    var clientNodeJsPath = StringUtils.isEmpty(initParams.getClientNodePath()) ? null : Path.of(initParams.getClientNodePath());
    var eslintBridgeServerBundlePath = StringUtils.isEmpty(initParams.getEslintBridgeServerPath()) ? null : Path.of(initParams.getEslintBridgeServerPath());
    var languageSpecificRequirements = getLanguageSpecificRequirements(clientNodeJsPath, eslintBridgeServerBundlePath);
    var sonarLintUserHomePath = initParams.getSonarlintUserHome() == null ? null : Path.of(initParams.getSonarlintUserHome());
    return new InitializeParams(
      new ClientConstantInfoDto("Visual Studio Code", initParams.getUserAgent()),
      new TelemetryClientConstantAttributesDto(initParams.getTelemetryProductKey(),
        telemetryInitParams.productName(),
        telemetryInitParams.productVersion(),
        telemetryInitParams.ideVersion(),
        telemetryInitParams.additionalAttributes()),
      getHttpConfiguration(),
      getSonarCloudAlternativeEnvironment(),
      backendCapabilities,
      initParams.getStorageRoot(),
      sonarLintUserHomePath,
      initParams.getEmbeddedPluginPaths(),
      initParams.getConnectedModeEmbeddedPluginPathsByKey(),
      initParams.getEnabledLanguagesInStandaloneMode(),
      initParams.getExtraEnabledLanguagesInConnectedMode(),
      null,
      initParams.getSonarQubeConnections(),
      initParams.getSonarCloudConnections(),
      initParams.getSonarlintUserHome(),
      initParams.getStandaloneRuleConfigByKey(),
      initParams.isFocusOnNewCode(),
      languageSpecificRequirements,
      true,
      null
    );
  }

  @NotNull
  EnumSet<BackendCapability> getBackendCapabilities() {
    var backendCapabilities = EnumSet.of(BackendCapability.SMART_NOTIFICATIONS, BackendCapability.PROJECT_SYNCHRONIZATION,
      BackendCapability.EMBEDDED_SERVER, BackendCapability.SERVER_SENT_EVENTS, BackendCapability.DATAFLOW_BUG_DETECTION,
      BackendCapability.FULL_SYNCHRONIZATION, BackendCapability.SECURITY_HOTSPOTS, BackendCapability.ISSUE_STREAMING,
      BackendCapability.SCA_SYNCHRONIZATION);
    if (telemetry != null && telemetry.enabled()) {
      backendCapabilities.add(BackendCapability.TELEMETRY);
    }
    if (shouldEnableMonitoring()) {
      backendCapabilities.add(BackendCapability.MONITORING);
    }
    return backendCapabilities;
  }

  boolean shouldEnableMonitoring() {
    var monitoringDisabledByProperty = "true".equals(System.getProperty(MONITORING_DISABLED_PROPERTY_KEY));
    if (monitoringDisabledByProperty) {
      lsLogOutput.debug("Monitoring is disabled by system property");
    }
    return !monitoringDisabledByProperty;
  }

  @NotNull
  private LanguageSpecificRequirements getLanguageSpecificRequirements(@Nullable Path clientNodeJsPath, @Nullable Path eslintBridgeSeverPath) {
    return new LanguageSpecificRequirements(
      new JsTsRequirementsDto(clientNodeJsPath, eslintBridgeSeverPath),
      getOmnisharpRequirements()
    );
  }

  @CheckForNull
  private OmnisharpRequirementsDto getOmnisharpRequirements() {
    var pathToOssCsharp = csharpOssPath == null ? null : Path.of(csharpOssPath);
    var pathToEnterpriseCsharp = csharpEnterprisePath == null ? null : Path.of(csharpEnterprisePath);
    if (omnisharpDirectory == null || pathToOssCsharp == null || pathToEnterpriseCsharp == null) {
      return null;
    }
    return new OmnisharpRequirementsDto(Path.of(omnisharpDirectory, "mono"),
      Path.of(omnisharpDirectory, "net6"),
      Path.of(omnisharpDirectory, "net472"),
      pathToOssCsharp,
      pathToEnterpriseCsharp);
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
    var sonarCloudApiUrl = System.getProperty("sonarlint.internal.sonarcloud.api.url");
    var sonarCloudUsUrl = System.getProperty("sonarlint.internal.sonarcloud.us.url");
    var sonarCloudUsWebSocketUrl = System.getProperty("sonarlint.internal.sonarcloud.us.websocket.url");
    var sonarCloudUsApiUrl = System.getProperty("sonarlint.internal.sonarcloud.us.api.url");

    var alternativeEnvironments = new EnumMap<SonarCloudRegion, SonarQubeCloudRegionDto>(SonarCloudRegion.class);
    if (sonarCloudUrl != null && sonarCloudApiUrl != null && sonarCloudWebSocketUrl != null) {
      alternativeEnvironments.put(SonarCloudRegion.EU,
        new SonarQubeCloudRegionDto(URI.create(sonarCloudUrl), URI.create(sonarCloudApiUrl), URI.create(sonarCloudWebSocketUrl)));
    }
    if (sonarCloudUsUrl != null && sonarCloudUsApiUrl != null && sonarCloudUsWebSocketUrl != null) {
      alternativeEnvironments.put(SonarCloudRegion.US,
        new SonarQubeCloudRegionDto(URI.create(sonarCloudUsUrl), URI.create(sonarCloudUsApiUrl), URI.create(sonarCloudUsWebSocketUrl)));
    }
    return alternativeEnvironments.isEmpty() ? null : new SonarCloudAlternativeEnvironmentDto(alternativeEnvironments);
  }

  @Nullable
  private static Path getPathProperty(String propertyName) {
    var property = System.getProperty(propertyName);
    return property == null ? null : Paths.get(property);
  }

  @Nullable
  public static Duration getTimeoutProperty(String propertyName) {
    var property = System.getProperty(propertyName);
    return Optional.ofNullable(property)
      .map(s -> {
        try {
          return Duration.ofMinutes(Integer.parseInt(s));
        } catch (NumberFormatException e) {
          return Duration.parse(s);
        }
      }).orElse(null);
  }

  public void shutdown() {
    try {
      backendService.shutdown().get(10, TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException e) {
      lsLogOutput.errorWithStackTrace("Unable to shutdown the SonartLint backend", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      try {
        clientLauncher.close();
      } catch (Exception e) {
        lsLogOutput.errorWithStackTrace("Unable to stop the SonartLint client launcher", e);
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

  public void setTelemetry(SonarLintTelemetry telemetry) {
    this.telemetry = telemetry;
  }

  public CountDownLatch getInitLatch() {
    return initLatch;
  }
}
