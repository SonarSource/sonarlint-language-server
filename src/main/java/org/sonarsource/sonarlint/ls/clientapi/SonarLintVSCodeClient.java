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
package org.sonarsource.sonarlint.ls.clientapi;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import nl.altindag.ssl.util.CertificateUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintCancelChecker;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.NoBindingSuggestionFoundParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SuggestConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.embeddedserver.EmbeddedServerStartedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.FixSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.flightrecorder.FlightRecorderStartedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.ls.AnalysisHelper;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.SkippedPluginsNotifier;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.CreateConnectionParams;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.connected.DependencyRisksCache;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.connected.api.HostInfoProvider;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.domain.DependencyRisk;
import org.sonarsource.sonarlint.ls.domain.TaintIssue;
import org.sonarsource.sonarlint.ls.embeddedserver.EmbeddedServerManager;
import org.sonarsource.sonarlint.ls.flightrecorder.FlightRecorderManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderBranchManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.progress.LSProgressMonitor;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.standalone.notifications.PromotionalNotifications;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.sonarsource.sonarlint.ls.backend.BackendService.ROOT_CONFIGURATION_SCOPE;
import static org.sonarsource.sonarlint.ls.util.URIUtils.getFullFileUriFromFragments;
import static org.sonarsource.sonarlint.ls.util.Utils.convertMessageType;

public class SonarLintVSCodeClient implements SonarLintRpcClientDelegate {

  public static final String SONARLINT_SOURCE = "sonarqube";
  private final SonarLintExtendedLanguageClient client;
  private SettingsManager settingsManager;
  private SmartNotifications smartNotifications;
  private final HostInfoProvider hostInfoProvider;
  private final LanguageClientLogger logOutput;
  private ProjectBindingManager bindingManager;
  private BackendServiceFacade backendServiceFacade;
  private WorkspaceFolderBranchManager branchManager;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final DependencyRisksCache dependencyRisksCache;
  private WorkspaceFoldersManager workspaceFoldersManager;
  private DiagnosticPublisher diagnosticPublisher;
  private final ScheduledExecutorService bindingSuggestionsHandler;
  private final SkippedPluginsNotifier skippedPluginsNotifier;
  private final PromotionalNotifications promotionalNotifications;
  private final FlightRecorderManager flightRecorderManager;
  private final EmbeddedServerManager embeddedServerManager;
  private final LSProgressMonitor progressMonitor;

  private AnalysisHelper analysisHelper;

  public SonarLintVSCodeClient(SonarLintExtendedLanguageClient client, HostInfoProvider hostInfoProvider, LanguageClientLogger logOutput,
    TaintVulnerabilitiesCache taintVulnerabilitiesCache, DependencyRisksCache dependencyRisksCache, SkippedPluginsNotifier skippedPluginsNotifier,
    PromotionalNotifications promotionalNotifications, FlightRecorderManager flightRecorderManager, EmbeddedServerManager embeddedServerManager) {
    this.client = client;
    this.hostInfoProvider = hostInfoProvider;
    this.logOutput = logOutput;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.dependencyRisksCache = dependencyRisksCache;
    this.skippedPluginsNotifier = skippedPluginsNotifier;
    this.promotionalNotifications = promotionalNotifications;
    this.flightRecorderManager = flightRecorderManager;
    this.embeddedServerManager = embeddedServerManager;
    this.progressMonitor = new LSProgressMonitor(client);
    var bindingSuggestionsThreadFactory = Utils.threadFactory("Binding suggestion handler", false);
    bindingSuggestionsHandler = Executors.newSingleThreadScheduledExecutor(bindingSuggestionsThreadFactory);
    initializeDefaultProxyAuthenticator();
  }

  private static void initializeDefaultProxyAuthenticator() {
    Authenticator.setDefault(new SystemPropertiesAuthenticator());
  }

  @Override
  public void suggestBinding(Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope) {
    bindingSuggestionsHandler.schedule(() -> {
      var relevantSuggestionsPerConfigScope = new ConcurrentHashMap<String, List<BindingSuggestionDto>>();
      suggestionsByConfigScope.forEach((configScopeId, suggestions) -> {
        var maybeBinding = bindingManager.getBinding(URI.create(configScopeId));
        if (maybeBinding.isEmpty()) {
          relevantSuggestionsPerConfigScope.put(configScopeId, suggestions);
        }
      });
      if (!relevantSuggestionsPerConfigScope.isEmpty()) {
        client.suggestBinding(new SuggestBindingParams(relevantSuggestionsPerConfigScope));
      }
    }, 5L, TimeUnit.SECONDS);
  }

  @Override
  public void openUrlInBrowser(URL url) {
    client.browseTo(url.toString());
  }

  @Override
  public void showMessage(org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType type, String text) {
    client.showMessage(new MessageParams(convertMessageType(type), text));
  }

  @Override
  public void log(LogParams params) {
    var prefix = String.format("[%s : %s] ", params.getLoggerName(), params.getThreadName());
    var rawMessage = params.getMessage();
    var sanitizedMessage = rawMessage != null ? prefix.concat(rawMessage) : "null";
    var level = params.getLevel();
    logOutput.log(sanitizedMessage, level);
    var stackTrace = params.getStackTrace();
    if (stackTrace != null) {
      logOutput.log(prefix.concat(stackTrace), level);
    }
  }

  @Override
  public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams coreParams) {
    var clientParams = new SonarLintExtendedLanguageClient.ShowSoonUnsupportedVersionMessageParams(
      coreParams.getDoNotShowAgainId(), coreParams.getText());
    client.showSoonUnsupportedVersionMessage(clientParams);
  }

  @Override
  public void showSmartNotification(ShowSmartNotificationParams showSmartNotificationParams) {
    var connectionOpt = settingsManager.getCurrentSettings().getServerConnections().get(showSmartNotificationParams.getConnectionId());
    if (connectionOpt == null) {
      return;
    }
    smartNotifications.showSmartNotification(showSmartNotificationParams, connectionOpt.isSonarCloudAlias());
  }

  @Override
  public String getClientLiveDescription() {
    return hostInfoProvider.getHostInfo().getDescription();
  }

  @Override
  public void showHotspot(String configurationScopeId, HotspotDetailsDto hotspotDetails) {
    var rule = hotspotDetails.getRule();
    var clientRule = new SonarLintExtendedLanguageClient.ShowHotspotParams.HotspotRule(rule.getKey(), rule.getName(), rule.getSecurityCategory(),
      rule.getVulnerabilityProbability(), rule.getRiskDescription(), rule.getVulnerabilityDescription(), rule.getFixRecommendations());
    var reviewStatus = HotspotStatus.TO_REVIEW.name().equals(hotspotDetails.getStatus()) ? "To Review" : "Reviewed";
    var showHotspotParams = new SonarLintExtendedLanguageClient.ShowHotspotParams(hotspotDetails.getKey(), hotspotDetails.getMessage(),
      hotspotDetails.getIdeFilePath().toString(),
      hotspotDetails.getTextRange(), hotspotDetails.getAuthor(), reviewStatus, hotspotDetails.getResolution(),
      clientRule);
    client.showHotspot(showHotspotParams);
  }

  @Override
  public void showIssue(String folderUri, IssueDetailsDto issueDetails) {
    var maybeBinding = bindingManager.getBindingIfExists(URI.create(folderUri));
    if (maybeBinding.isPresent()) {
      logOutput.debug("Show issue with description");
      var projectBinding = maybeBinding.get();
      client.showIssue(new ShowAllLocationsCommand.Param(new ShowIssueParams(folderUri, issueDetails), projectBinding.connectionId(), true));
    } else {
      logOutput.debug("Show issue without description");
      client.showIssue(new ShowAllLocationsCommand.Param(new ShowIssueParams(folderUri, issueDetails), null, false));
    }
  }

  @Override
  public void showFixSuggestion(String configurationScopeId, String issueKey, FixSuggestionDto fixSuggestion) {
    var textEdits = fixSuggestion.fileEdit().changes();
    var fullFileUri = getFullFileUriFromFragments(configurationScopeId, fixSuggestion.fileEdit().idePath());
    client.showFixSuggestion(new SonarLintExtendedLanguageClient.ShowFixSuggestionParams(fixSuggestion.suggestionId(), textEdits, fullFileUri.toString(), false));
  }

  @Override
  public AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, SonarLintCancelChecker cancelChecker) {
    var tokenValue = params.getTokenValue();
    var workspaceFoldersFuture = client.workspaceFolders();
    var isSonarCloud = params.getConnectionParams().isRight();
    var assistCreatingConnectionFuture = client.assistCreatingConnection(new CreateConnectionParams(isSonarCloud,
      isSonarCloud ? params.getConnectionParams().getRight().getOrganizationKey() : params.getConnectionParams().getLeft().getServerUrl(),
      tokenValue, isSonarCloud ? params.getConnectionParams().getRight().getRegion() : null));
    return workspaceFoldersFuture.thenCombine(assistCreatingConnectionFuture, (workspaceFolders, assistCreatingConnectionResponse) -> {
      var newConnectionId = assistCreatingConnectionResponse.getNewConnectionId();
      if (newConnectionId != null) {
        var serverProductName = isSonarCloud ? "SonarQube Cloud" : "SonarQube Server";
        client.showMessage(new MessageParams(MessageType.Info, format("Connection to %s was successfully created.", serverProductName)));
        return new AssistCreatingConnectionResponse(newConnectionId);
      } else {
        throw new CancellationException("Automatic connection setup was cancelled");
      }
    }).join();
  }

  @Override
  public AssistBindingResponse assistBinding(AssistBindingParams params, SonarLintCancelChecker cancelChecker) {
    workspaceFoldersManager.updateAnalysisReadiness(Set.of(params.getConfigScopeId()), false);
    return client.assistBinding(params)
      .thenApply(response -> {
        var configurationScopeId = response.getConfigurationScopeId();
        var pathParts = configurationScopeId.split("/");
        var projectName = pathParts[pathParts.length - 1];
        client.showMessage(new MessageParams(MessageType.Info, "Project '" + projectName + "' was successfully bound to '" + params.getProjectKey() + "'."));
        return new AssistBindingResponse(configurationScopeId);
      }).join();
  }

  @Override
  public void noBindingSuggestionFound(NoBindingSuggestionFoundParams params) {
    var messageRequestParams = new ShowMessageRequestParams();
    messageRequestParams.setMessage("SonarQube for VS Code couldn't match the server project '" + params.getProjectKey() + "' to any of the currently " +
      "open workspace folders. Please make sure the project is open in the workspace, or try configuring the binding manually.");
    messageRequestParams.setType(MessageType.Error);
    var learnMoreAction = new MessageActionItem("Learn more");
    messageRequestParams.setActions(List.of(learnMoreAction));
    client.showMessageRequest(messageRequestParams)
      .thenAccept(action -> {
        if (learnMoreAction.equals(action)) {
          client.browseTo("https://docs.sonarsource.com/sonarqube-for-vs-code/troubleshooting/#troubleshooting-connected-mode-setup");
        }
      });
  }

  @Override
  public void startProgress(StartProgressParams startProgressParams) {
    progressMonitor.createAndStartProgress(startProgressParams);
  }

  @Override
  public void reportProgress(ReportProgressParams reportProgressParams) {
    if (reportProgressParams.getNotification().isLeft()) {
      progressMonitor.reportProgress(reportProgressParams);
    } else if (reportProgressParams.getNotification().isRight()) {
      progressMonitor.end(reportProgressParams);
    }
  }

  @Override
  public void didSynchronizeConfigurationScopes(Set<String> configurationScopeIds) {
    // no-op
  }

  @Nullable
  @Override
  public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) {
    var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
    var serverUrlOrOrganization = connectionSettings.isSonarCloudAlias() ? (connectionSettings.getRegion() + "_" + connectionSettings.getOrganizationKey())
      : connectionSettings.getServerUrl();
    try {
      return Either.forLeft(new TokenDto(client.getTokenForServer(serverUrlOrOrganization).get()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logOutput.errorWithStackTrace("Can't get token for server " + serverUrlOrOrganization, e);
      return null;
    } catch (ExecutionException e) {
      logOutput.errorWithStackTrace("Can't get token for server " + serverUrlOrOrganization, e);
      return null;
    }
  }

  @Override
  public TelemetryClientLiveAttributesResponse getTelemetryLiveAttributes() {
    try {
      var hasJoinedIdeLabs = client.hasJoinedIdeLabs().get();
      var hasEnabledIdeLabs = settingsManager.getCurrentSettings().isIdeLabsEnabled();
      return new TelemetryClientLiveAttributesResponse(
        Map.of("ideLabsJoined", hasJoinedIdeLabs, "ideLabsEnabled", hasJoinedIdeLabs && hasEnabledIdeLabs));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logOutput.errorWithStackTrace("Cannot determine if user has Joined IDE Labs or not", e);
      return new TelemetryClientLiveAttributesResponse(Map.of());
    } catch (ExecutionException e) {
      logOutput.errorWithStackTrace("Cannot determine if user has Joined IDE Labs or not", e);
      return new TelemetryClientLiveAttributesResponse(Map.of());
    }
  }

  @Override
  public List<ProxyDto> selectProxies(URI uri) {
    var proxies = ProxySelector.getDefault().select(uri);
    return proxies.stream().map(SonarLintVSCodeClient::convert).toList();
  }

  private static ProxyDto convert(Proxy proxy) {
    if (proxy.type() == Proxy.Type.DIRECT) {
      return ProxyDto.NO_PROXY;
    }
    var address = (InetSocketAddress) proxy.address();
    var server = address.getHostString();
    var port = address.getPort();
    return new ProxyDto(proxy.type(), server, port);
  }

  @Override
  public GetProxyPasswordAuthenticationResponse getProxyPasswordAuthentication(String host, int port, String protocol, String prompt, String scheme, URL targetHost) {
    // use null addr, because the authentication fails if it does not exactly match the expected realm's host
    var passwordAuthentication = Authenticator.requestPasswordAuthentication(host, null, port, protocol, prompt, scheme,
      targetHost, Authenticator.RequestorType.PROXY);
    return new GetProxyPasswordAuthenticationResponse(passwordAuthentication != null ? passwordAuthentication.getUserName() : null,
      passwordAuthentication != null ? new String(passwordAuthentication.getPassword()) : null);
  }

  @Override
  public boolean checkServerTrusted(List<X509CertificateDto> chain, String authType) {
    var certs = CertificateUtils.parsePemCertificate(chain.get(0).getPem());
    var sha1fingerprint = "";
    var sha256fingerprint = "";
    X509Certificate untrustedCert = null;
    try {
      untrustedCert = (X509Certificate) certs.get(0);
      sha1fingerprint = Utils.formatSha1Fingerprint(DigestUtils.sha1Hex(untrustedCert.getEncoded()));
      sha256fingerprint = Utils.formatSha256Fingerprint(DigestUtils.sha256Hex(untrustedCert.getEncoded()));
    } catch (CertificateEncodingException | IndexOutOfBoundsException e) {
      logOutput.errorWithStackTrace("Certificate encoding is malformed, SHA fingerprints will not be displayed.", e);
    }
    var confirmationParams = new SonarLintExtendedLanguageClient.SslCertificateConfirmationParams(
      untrustedCert == null ? "" : untrustedCert.getSubjectX500Principal().getName(),
      untrustedCert == null ? "" : untrustedCert.getIssuerX500Principal().getName(),
      untrustedCert == null ? "" : untrustedCert.getNotBefore().toString(),
      untrustedCert == null ? "" : untrustedCert.getNotAfter().toString(),
      sha1fingerprint,
      sha256fingerprint);

    return client.askSslCertificateConfirmation(confirmationParams).join();
  }

  @Nullable
  @Override
  public String matchSonarProjectBranch(String configurationScopeId, String mainBranchName, Set<String> allBranchesNames, SonarLintCancelChecker cancelChecker) {
    return branchManager.matchSonarProjectBranch(configurationScopeId, mainBranchName, allBranchesNames, cancelChecker);
  }

  @Override
  public void didChangeMatchedSonarProjectBranch(String configScopeId, String newMatchedBranchName) {
    client.setReferenceBranchNameForFolder(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.of(configScopeId,
      newMatchedBranchName));
  }

  @Override
  public void didChangeDependencyRisks(String configScopeId, Set<UUID> closedDependencyRiskIds, List<DependencyRiskDto> addedDependencyRisks,
    List<DependencyRiskDto> updatedDependencyRisks) {
    closedDependencyRiskIds.forEach(id -> dependencyRisksCache.removeDependencyRisk(configScopeId, id.toString()));

    var folderUri = URI.create(configScopeId);
    addedDependencyRisks.forEach(dependencyRiskDto -> {
      var dependencyRisk = new DependencyRisk(dependencyRiskDto, configScopeId);
      dependencyRisksCache.addDependencyRisk(folderUri, dependencyRisk);
    });

    updatedDependencyRisks.forEach(dependencyRiskDto -> {
      var dependencyRisk = new DependencyRisk(dependencyRiskDto, configScopeId);
      dependencyRisksCache.removeDependencyRisk(configScopeId, dependencyRisk.getId().toString());
      dependencyRisksCache.addDependencyRisk(folderUri, dependencyRisk);
    });

    diagnosticPublisher.publishDependencyRisks(folderUri);
  }

  @Override
  public void didChangeTaintVulnerabilities(String folderUri, Set<UUID> closedTaintVulnerabilityIds,
    List<TaintVulnerabilityDto> addedTaintVulnerabilities, List<TaintVulnerabilityDto> updatedTaintVulnerabilities) {
    var addedTaintVulnerabilitiesByFile = addedTaintVulnerabilities.stream()
      .collect(groupingBy(taintVulnerabilityDto -> getFullFileUriFromFragments(folderUri, taintVulnerabilityDto.getIdeFilePath()), toList()));
    var updatedTaintVulnerabilitiesByFile = updatedTaintVulnerabilities.stream()
      .collect(groupingBy(taintVulnerabilityDto -> getFullFileUriFromFragments(folderUri, taintVulnerabilityDto.getIdeFilePath()), toList()));

    // Remove taints that were closed
    taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().values().stream().flatMap(Collection::stream)
      .filter(taintIssue -> closedTaintVulnerabilityIds.contains(taintIssue.getId()))
      .forEach(taintIssue -> taintVulnerabilitiesCache.removeTaintIssue(
        getFullFileUriFromFragments(folderUri, taintIssue.getIdeFilePath()).toString(), taintIssue.getSonarServerKey()));

    workspaceFoldersManager.getFolder(URI.create(folderUri))
      .map(workspaceFolderWrapper -> Objects.requireNonNull(bindingManager
        .getServerConnectionSettingsFor(workspaceFolderWrapper.getSettings().getConnectionId())).isSonarCloudAlias())
      .ifPresent(isSonarCloud -> updateTaintVulnerabilitiesCache(addedTaintVulnerabilitiesByFile, updatedTaintVulnerabilitiesByFile, folderUri, isSonarCloud));
  }

  private void updateTaintVulnerabilitiesCache(Map<URI, List<TaintVulnerabilityDto>> addedTaints, Map<URI, List<TaintVulnerabilityDto>> updateTaints,
    String folderUri, boolean isSonarCloud) {
    var existingTaintVulnerabilitiesPerFile = taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile();

    // add new ones
    handleAddedTaints(addedTaints, folderUri, isSonarCloud, existingTaintVulnerabilitiesPerFile);

    // update existing ones
    handleUpdatedTaints(updateTaints, folderUri, isSonarCloud, existingTaintVulnerabilitiesPerFile);
  }

  private void handleAddedTaints(Map<URI, List<TaintVulnerabilityDto>> addedTaints, String folderUri, boolean isSonarCloud,
    Map<URI, List<TaintIssue>> existingTaintVulnerabilitiesPerFile) {
    addedTaints.forEach((fileUri, added) -> {
      var addedTaintIssuesForFile = dtosToTaintIssues(folderUri, added, isSonarCloud);
      if (existingTaintVulnerabilitiesPerFile.containsKey(fileUri)) {
        addedTaintIssuesForFile.addAll(existingTaintVulnerabilitiesPerFile.get(fileUri));
      }
      taintVulnerabilitiesCache.reload(fileUri, addedTaintIssuesForFile);
      diagnosticPublisher.publishTaints(fileUri);
    });
  }

  private void handleUpdatedTaints(Map<URI, List<TaintVulnerabilityDto>> updateTaints, String folderUri, boolean isSonarCloud,
    Map<URI, List<TaintIssue>> existingTaintVulnerabilitiesPerFile) {
    updateTaints.forEach((fileUri, updates) -> {
      if (existingTaintVulnerabilitiesPerFile.containsKey(fileUri)) {
        updates.forEach(dto -> {
          if (taintVulnerabilitiesCache.getTaintVulnerabilityByKey(dto.getSonarServerKey()).isPresent()) {
            taintVulnerabilitiesCache.removeTaintIssue(fileUri.toString(), dto.getSonarServerKey());
          }
          if (!dto.isResolved()) {
            taintVulnerabilitiesCache.add(fileUri, new TaintIssue(dto, folderUri, isSonarCloud));
          }
        });
      } else {
        taintVulnerabilitiesCache.reload(fileUri, dtosToTaintIssues(folderUri, updates, isSonarCloud));
      }
      diagnosticPublisher.publishTaints(fileUri);
    });
  }

  @Override
  public List<ClientFileDto> listFiles(String configScopeId) {
    return configScopeIdAsUri(configScopeId)
      .map(configScopeIdAsUri -> CompletableFutures.computeAsync(c -> {
        var response = client.listFilesInFolder(new SonarLintExtendedLanguageClient.FolderUriParams(configScopeId)).join();
        var folderPath = Path.of(configScopeIdAsUri);
        return response.getFoundFiles().stream()
          .map(file -> {
            var filePath = Path.of(file.getFilePath());
            return new ClientFileDto(filePath.toUri(), folderPath.relativize(filePath), configScopeId, null, StandardCharsets.UTF_8.name(), filePath,
              file.getContent(), null, true);
          })
          .toList();
      }).join())
      .orElse(List.of());
  }

  private static Optional<URI> configScopeIdAsUri(String configScopeId) {
    try {
      return Optional.of(URI.create(configScopeId));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  @Override
  public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
    workspaceFoldersManager.updateAnalysisReadiness(configurationScopeIds, areReadyForAnalysis);
    if (areReadyForAnalysis) {
      // for each open configScopeId do didOpen for each open file
      Collection<WorkspaceFolderWrapper> all = workspaceFoldersManager.getAll();
      all.forEach(folderWrapper -> {
        var folderUri = folderWrapper.getUri();

        CompletableFutures.computeAsync(cancelChecker -> {
          getNewCodeDefinitionAndSubmitToClient(folderUri.toString());
          return null;
        });
      });
      initializeTaintCache(configurationScopeIds);
    }
  }

  private void initializeTaintCache(Set<String> configurationScopeIds) {
    configurationScopeIds.forEach(configurationScopeId -> {
      if (Objects.equals(configurationScopeId, ROOT_CONFIGURATION_SCOPE)) {
        return;
      }
      var binding = bindingManager.getBinding(URI.create(configurationScopeId));
      if (binding.isPresent()) {
        var isSonarCloud = Objects.requireNonNull(bindingManager.getServerConnectionSettingsFor(binding.get().connectionId())).isSonarCloudAlias();
        CompletableFutures.computeAsync(cancelChecker -> {
          var taints = backendServiceFacade.getBackendService().getAllTaints(configurationScopeId).join();

          var taintsByFile = taints.getTaintVulnerabilities()
            .stream()
            .collect(groupingBy(taintVulnerabilityDto -> getFullFileUriFromFragments(configurationScopeId, taintVulnerabilityDto.getIdeFilePath()), toList()));

          taintsByFile.forEach((fileUri, t) -> {
            var vulnerabilities = dtosToTaintIssues(configurationScopeId, t, isSonarCloud);
            taintVulnerabilitiesCache.reload(fileUri, vulnerabilities);
            diagnosticPublisher.publishTaints(fileUri);
          });

          return null;
        });
      }
    });
  }

  @Override
  public void suggestConnection(Map<String, List<ConnectionSuggestionDto>> configScopesToConnectionSuggestions) {
    if (configScopesToConnectionSuggestions.isEmpty()) {
      return;
    }
    client.suggestConnection(new SuggestConnectionParams(configScopesToConnectionSuggestions));
  }

  @NotNull
  private static ArrayList<TaintIssue> dtosToTaintIssues(String configurationScopeId, List<TaintVulnerabilityDto> t, Boolean isSonarCloud) {
    return t.stream()
      .map(dto -> new TaintIssue(dto, configurationScopeId, isSonarCloud))
      .filter(tv -> !tv.isResolved())
      .collect(Collectors.toCollection(ArrayList::new));
  }

  public void setSettingsManager(SettingsManager settingsManager) {
    this.settingsManager = settingsManager;
  }

  public void setBindingManager(ProjectBindingManager bindingManager) {
    this.bindingManager = bindingManager;
  }

  public void setSmartNotifications(SmartNotifications smartNotifications) {
    this.smartNotifications = smartNotifications;
  }

  public void setBackendServiceFacade(BackendServiceFacade backendServiceFacade) {
    this.backendServiceFacade = backendServiceFacade;
  }

  private void getNewCodeDefinitionAndSubmitToClient(String folderUri) {
    backendServiceFacade.getBackendService().getNewCodeDefinition(folderUri)
      .handle((response, e) -> {
        if (e != null) {
          return new SonarLintExtendedLanguageClient.SubmitNewCodeDefinitionParams(folderUri, response.getDescription(), false);
        }
        return new SonarLintExtendedLanguageClient.SubmitNewCodeDefinitionParams(folderUri, response.getDescription(), response.isSupported());
      })
      .thenAccept(client::submitNewCodeDefinition);
  }

  public void setBranchManager(WorkspaceFolderBranchManager branchManager) {
    this.branchManager = branchManager;
  }

  public void setAnalysisTaskExecutor(AnalysisHelper analysisTaskExecutor) {
    this.analysisHelper = analysisTaskExecutor;
  }

  public void setWorkspaceFoldersManager(WorkspaceFoldersManager workspaceFoldersManager) {
    this.workspaceFoldersManager = workspaceFoldersManager;
  }

  public void setDiagnosticPublisher(DiagnosticPublisher diagnosticPublisher) {
    this.diagnosticPublisher = diagnosticPublisher;
  }

  @Override
  public void didSkipLoadingPlugin(String configurationScopeId, Language language,
    DidSkipLoadingPluginParams.SkipReason reason, String minVersion, @Nullable String currentVersion) {
    skippedPluginsNotifier.notifyOnceForSkippedPlugins(language, reason, minVersion, currentVersion);
  }

  @Override
  public void didDetectSecret(String configScopeId) {
    diagnosticPublisher.didDetectSecret();
  }

  @Override
  public void promoteExtraEnabledLanguagesInConnectedMode(String configurationScopeId, Set<Language> languagesToPromote) {
    promotionalNotifications.promoteExtraEnabledLanguagesInConnectedMode(languagesToPromote);
  }

  @Override
  public Path getBaseDir(String configurationScopeId) {
    try {
      return Paths.get(URI.create(configurationScopeId));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Override
  public void raiseIssues(String configurationScopeId, Map<URI, List<RaisedIssueDto>> issuesByFileUri,
    boolean isIntermediatePublication, @Nullable UUID analysisId) {
    var findings = issuesByFileUri.entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
        .map(i -> (RaisedFindingDto) i)
        .toList()));
    analysisHelper.handleIssues(findings);
  }

  @Override
  public void raiseHotspots(String configurationScopeId, Map<URI, List<RaisedHotspotDto>> hotspotsByFileUri,
    boolean isIntermediatePublication, @Nullable UUID analysisId) {
    analysisHelper.handleHotspots(hotspotsByFileUri);
  }

  @Override
  public Set<String> getFileExclusions(String configurationScopeId) {
    var excludes = settingsManager.getCurrentSettings().getAnalysisExcludes();
    return excludes.isEmpty() ? Collections.emptySet()
      : Arrays.stream(excludes.split(","))
        .collect(Collectors.toSet());
  }

  @Override
  public Map<String, String> getInferredAnalysisProperties(String configurationScopeId, List<URI> filesToAnalyze) {
    return analysisHelper.getInferredAnalysisProperties(configurationScopeId, filesToAnalyze);
  }

  @Override
  public void invalidToken(String connectionId) {
    client.notifyInvalidToken(new SonarLintExtendedLanguageClient.NotifyInvalidTokenParams(connectionId));
  }

  @Override
  public void flightRecorderStarted(FlightRecorderStartedParams params) {
    flightRecorderManager.onFlightRecorderStarted(params.getSessionId());
  }

  @Override
  public void embeddedServerStarted(EmbeddedServerStartedParams params) {
    embeddedServerManager.embeddedServerStarted(params.getPort());
  }
}
