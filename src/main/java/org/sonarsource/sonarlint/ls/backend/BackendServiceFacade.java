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
import java.util.Locale;
import java.util.Optional;
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.ls.EnabledLanguages;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintLanguageServerInitializationOptions;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.RulesConfiguration;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

public class BackendServiceFacade {

  public static final String MONITORING_DISABLED_PROPERTY_KEY = "sonarlint.monitoring.disabled";
  public static final String FLIGHT_RECORDER_ENABLED_PROPERTY_KEY = "sonarlint.flightrecorder.enabled";

  private static final String CURSOR_APP_NAME = "Cursor";
  private static final String WINDSURF_APP_NAME = "Windsurf";
  private static final String VSCODE_APP_NAME = "Visual Studio Code";

  private final BackendService backendService;
  private final ConfigurationScopeDto rootConfigurationScope;
  private final ClientJsonRpcLauncher clientLauncher;
  private final LanguageClientLogger lsLogOutput;
  private final EnabledLanguages enabledLanguages;
  private SonarLintTelemetry telemetry;

  public BackendServiceFacade(SonarLintRpcClientDelegate rpcClient, LanguageClientLogger lsLogOutput, SonarLintExtendedLanguageClient client, EnabledLanguages enabledLanguages) {
    this.lsLogOutput = lsLogOutput;
    this.enabledLanguages = enabledLanguages;
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

    this.rootConfigurationScope = new ConfigurationScopeDto(BackendService.ROOT_CONFIGURATION_SCOPE, null, false, BackendService.ROOT_CONFIGURATION_SCOPE,
      new BindingConfigurationDto(null, null, false));
  }

  public BackendService getBackendService() {
    return backendService;
  }

  private InitializeParams toInitParams(SonarLintLanguageServerInitializationOptions initializationOptions, String appName, String clientVersion,
    List<SonarQubeConnectionConfigurationDto> sonarQubeServerConnections, List<SonarCloudConnectionConfigurationDto> sonarQubeCloudConnections) {
    var ideVersion = appName + " " + clientVersion;
    var productVersion = initializationOptions.productVersion();
    var userAgent = "SonarQube for IDE (SonarLint) - Visual Studio Code " + productVersion + " - " + clientVersion;
    var backendCapabilities = getBackendCapabilities();
    var clientNodeJsPath = StringUtils.isBlank(initializationOptions.clientNodePath()) ? null : Path.of(initializationOptions.clientNodePath());
    var eslintBridgeServerBundlePath = StringUtils.isBlank(initializationOptions.eslintBridgeServerPath()) ? null : Path.of(initializationOptions.eslintBridgeServerPath());
    var languageSpecificRequirements = getLanguageSpecificRequirements(initializationOptions, clientNodeJsPath, eslintBridgeServerBundlePath);
    var standaloneRulesConfiguration = RulesConfiguration.parse(initializationOptions.rules());
    var standaloneRuleConfigByKey = SettingsManager.getStandaloneRuleConfigByKey(standaloneRulesConfiguration);
    var overriddenUserHome = SettingsManager.getSonarLintUserHomeOverride();
    Path storageRoot = null;
    String sonarLintUserHome = null;
    Path workDir = null;
    if (overriddenUserHome != null) {
      storageRoot = overriddenUserHome.resolve("storage");
      sonarLintUserHome = overriddenUserHome.toString();
      workDir = Path.of(sonarLintUserHome);
    }

    return new InitializeParams(
      new ClientConstantInfoDto(determineIdeName(appName), userAgent),
      new TelemetryClientConstantAttributesDto(
        determineProductKey(appName, initializationOptions.productKey()),
        initializationOptions.productName(),
        initializationOptions.productVersion(),
        ideVersion,
        initializationOptions.additionalAttributes()),
      getHttpConfiguration(),
      getSonarCloudAlternativeEnvironment(),
      backendCapabilities,
      storageRoot,
      workDir,
      enabledLanguages.getEmbeddedPluginsPaths(),
      enabledLanguages.getConnectedModeEmbeddedPluginPathsByKey(),
      EnabledLanguages.getStandaloneLanguages(),
      EnabledLanguages.getConnectedLanguages(),
      null,
      sonarQubeServerConnections,
      sonarQubeCloudConnections,
      sonarLintUserHome,
      standaloneRuleConfigByKey,
      initializationOptions.focusOnNewCode(),
      languageSpecificRequirements,
      initializationOptions.automaticAnalysis(),
      null,
      LogLevel.DEBUG);
  }

  static String determineIdeName(String appName) {
    if (appName.toLowerCase(Locale.ROOT).contains(CURSOR_APP_NAME.toLowerCase(Locale.ROOT))) {
      return CURSOR_APP_NAME;
    }
    if (appName.toLowerCase(Locale.ROOT).contains(WINDSURF_APP_NAME.toLowerCase(Locale.ROOT))) {
      return WINDSURF_APP_NAME;
    }
    return VSCODE_APP_NAME;
  }

  static String determineProductKey(String appName, String clientProductKey) {
    if (appName.toLowerCase(Locale.ROOT).contains(CURSOR_APP_NAME.toLowerCase(Locale.ROOT))) {
      return CURSOR_APP_NAME.toLowerCase(Locale.ROOT);
    }
    if (appName.toLowerCase(Locale.ROOT).contains(WINDSURF_APP_NAME.toLowerCase(Locale.ROOT))) {
      return WINDSURF_APP_NAME.toLowerCase(Locale.ROOT);
    }
    return clientProductKey;
  }

  @NotNull
  EnumSet<BackendCapability> getBackendCapabilities() {
    var backendCapabilities = EnumSet.of(BackendCapability.SMART_NOTIFICATIONS, BackendCapability.PROJECT_SYNCHRONIZATION,
      BackendCapability.EMBEDDED_SERVER, BackendCapability.SERVER_SENT_EVENTS, BackendCapability.DATAFLOW_BUG_DETECTION,
      BackendCapability.FULL_SYNCHRONIZATION, BackendCapability.SECURITY_HOTSPOTS, BackendCapability.ISSUE_STREAMING,
      BackendCapability.SCA_SYNCHRONIZATION, BackendCapability.CONTEXT_GENERATION, BackendCapability.GESSIE_TELEMETRY);
    if (telemetry != null && telemetry.enabled()) {
      backendCapabilities.add(BackendCapability.TELEMETRY);
    }
    if (shouldEnableMonitoring()) {
      backendCapabilities.add(BackendCapability.MONITORING);
    }
    if (shouldEnableFlightRecorder()) {
      backendCapabilities.add(BackendCapability.FLIGHT_RECORDER);
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

  boolean shouldEnableFlightRecorder() {
    var flightRecorderEnabledByProperty = "true".equals(System.getProperty(FLIGHT_RECORDER_ENABLED_PROPERTY_KEY));
    if (flightRecorderEnabledByProperty) {
      lsLogOutput.debug("Flight recorder is enabled by system property");
    }
    return flightRecorderEnabledByProperty;
  }

  @NotNull
  private static LanguageSpecificRequirements getLanguageSpecificRequirements(SonarLintLanguageServerInitializationOptions initializationOptions, @Nullable Path clientNodeJsPath,
    @Nullable Path eslintBridgeSeverPath) {
    return new LanguageSpecificRequirements(
      new JsTsRequirementsDto(clientNodeJsPath, eslintBridgeSeverPath),
      getOmnisharpRequirements(initializationOptions));
  }

  @CheckForNull
  private static OmnisharpRequirementsDto getOmnisharpRequirements(SonarLintLanguageServerInitializationOptions initializationOptions) {
    var pathToOssCsharp = initializationOptions.csharpOssPath() == null ? null : Path.of(initializationOptions.csharpOssPath());
    var pathToEnterpriseCsharp = initializationOptions.csharpEnterprisePath() == null ? null : Path.of(initializationOptions.csharpEnterprisePath());
    var omnisharpDirectory = initializationOptions.omnisharpDirectory();
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
      lsLogOutput.errorWithStackTrace("Unable to shutdown the SonarLint backend", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      try {
        clientLauncher.close();
      } catch (Exception e) {
        lsLogOutput.errorWithStackTrace("Unable to stop the SonarLint client launcher", e);
      }
    }
  }

  public void initialize(SonarLintLanguageServerInitializationOptions initializationOptions, String appName, String clientVersion,
    List<SonarQubeConnectionConfigurationDto> sonarQubeServerConnections, List<SonarCloudConnectionConfigurationDto> sonarQubeCloudConnections) {
    backendService.initialize(toInitParams(initializationOptions, appName, clientVersion, sonarQubeServerConnections, sonarQubeCloudConnections));
    backendService.addConfigurationScopes(new DidAddConfigurationScopesParams(List.of(rootConfigurationScope)));
  }

  public void setTelemetry(SonarLintTelemetry telemetry) {
    this.telemetry = telemetry;
  }

}
