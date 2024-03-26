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
package org.sonarsource.sonarlint.ls.clientapi;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import nl.altindag.ssl.util.CertificateUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.CreateConnectionParams;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.connected.api.HostInfoProvider;
import org.sonarsource.sonarlint.ls.connected.events.ServerSentEventsHandlerService;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.domain.TaintIssue;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderBranchManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.util.URIUtils;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class SonarLintVSCodeClient implements SonarLintRpcClientDelegate {

  public static final String SONARLINT_SOURCE = "sonarlint";
  private final SonarLintExtendedLanguageClient client;
  private SettingsManager settingsManager;
  private SmartNotifications smartNotifications;
  private final HostInfoProvider hostInfoProvider;
  private final LanguageClientLogOutput logOutput;
  private ProjectBindingManager bindingManager;
  private ServerSentEventsHandlerService serverSentEventsHandlerService;
  private BackendServiceFacade backendServiceFacade;
  private WorkspaceFolderBranchManager branchManager;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final OpenFilesCache openFilesCache;
  private WorkspaceFoldersManager workspaceFoldersManager;
  private AnalysisScheduler analysisScheduler;
  private DiagnosticPublisher diagnosticPublisher;

  public SonarLintVSCodeClient(SonarLintExtendedLanguageClient client, HostInfoProvider hostInfoProvider,
    LanguageClientLogOutput logOutput, TaintVulnerabilitiesCache taintVulnerabilitiesCache, OpenFilesCache openFilesCache) {
    this.client = client;
    this.hostInfoProvider = hostInfoProvider;
    this.logOutput = logOutput;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.openFilesCache = openFilesCache;
    initializeDefaultProxyAuthenticator();
  }

  private static void initializeDefaultProxyAuthenticator() {
    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        if (getRequestorType() == RequestorType.PROXY) {
          var protocol = getRequestingProtocol().toLowerCase(Locale.ROOT);
          var host = System.getProperty(protocol + ".proxyHost", "");
          var port = System.getProperty(protocol + ".proxyPort", "80");
          var user = System.getProperty(protocol + ".proxyUser", "");
          var password = System.getProperty(protocol + ".proxyPassword", "");

          if (getRequestingHost().equalsIgnoreCase(host) && Integer.parseInt(port) == getRequestingPort()) {
            // Seems to be OK.
            return new PasswordAuthentication(user, password.toCharArray());
          }
        }
        return null;
      }
    });
  }

  @Override
  public void suggestBinding(Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope) {
    if (!suggestionsByConfigScope.isEmpty()) {
      client.suggestBinding(new SuggestBindingParams(suggestionsByConfigScope));
    }
  }

  @Override
  public void openUrlInBrowser(URL url) {
    client.browseTo(url.toString());
  }


  @Override
  public void showMessage(org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType type, String text) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void log(LogParams params) {
    var rawMessage = params.getMessage();
    var sanitizedMessage = rawMessage != null ? rawMessage : "null";
    var level = convertLogLevel(params.getLevel());
    logOutput.log(sanitizedMessage, level);
    var stackTrace = params.getStackTrace();
    if (stackTrace != null) {
      logOutput.log(stackTrace, level);
    }
  }

  private static ClientLogOutput.Level convertLogLevel(LogLevel level) {
    switch (level) {
      case ERROR -> {
        return ClientLogOutput.Level.ERROR;
      }
      case WARN -> {
        return ClientLogOutput.Level.WARN;
      }
      case INFO -> {
        return ClientLogOutput.Level.INFO;
      }
      case DEBUG -> {
        return ClientLogOutput.Level.DEBUG;
      }
      case TRACE -> {
        return ClientLogOutput.Level.TRACE;
      }
    }
    throw new IllegalArgumentException("Unknown log level");
  }

  @Override
  public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams coreParams) {
    var clientParams = new SonarLintExtendedLanguageClient.ShowSoonUnsupportedVersionMessageParams(
      coreParams.getDoNotShowAgainId(), coreParams.getText()
    );
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
    var reviewStatus = HotspotReviewStatus.TO_REVIEW.name().equals(hotspotDetails.getStatus()) ? "To Review" : "Reviewed";
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
      client.showIssue(new ShowAllLocationsCommand.Param(new ShowIssueParams(folderUri, issueDetails), projectBinding.getConnectionId(), true));
    } else {
      logOutput.debug("Show issue without description");
      client.showIssue(new ShowAllLocationsCommand.Param(new ShowIssueParams(folderUri, issueDetails), null, false));
    }
  }

  @Override
  public AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, CancelChecker cancelChecker) {
    var tokenValue = params.getTokenValue();
    var workspaceFoldersFuture = client.workspaceFolders();
    var assistCreatingConnectionFuture = client.assistCreatingConnection(new CreateConnectionParams(false, params.getServerUrl(), tokenValue));
    return workspaceFoldersFuture.thenCombine(assistCreatingConnectionFuture, (workspaceFolders, assistCreatingConnectionResponse) -> {
      var currentConnections = getCurrentConnections(params, assistCreatingConnectionResponse);
      var newConnectionId = assistCreatingConnectionResponse.getNewConnectionId();
      if (newConnectionId != null) {
        client.showMessage(new MessageParams(MessageType.Info, "Connection to SonarQube was successfully created."));
        backendServiceFacade.getBackendService().didChangeConnections(currentConnections);
      }
      return new AssistCreatingConnectionResponse(newConnectionId);
    }).join();
  }

  @NotNull
  private HashMap<String, ServerConnectionSettings> getCurrentConnections(AssistCreatingConnectionParams params,
    @Nullable SonarLintExtendedLanguageClient.AssistCreatingConnectionResponse assistCreatingConnectionResponse) {
    if (assistCreatingConnectionResponse == null) {
      throw new CancellationException("Automatic connection setup was cancelled");
    }
    var newConnection = new ServerConnectionSettings(assistCreatingConnectionResponse.getNewConnectionId(), params.getServerUrl(), params.getTokenValue(), null, false);
    var currentConnections = new HashMap<>(settingsManager.getCurrentSettings().getServerConnections());
    currentConnections.put(assistCreatingConnectionResponse.getNewConnectionId(), newConnection);
    return currentConnections;
  }

  @Override
  public AssistBindingResponse assistBinding(AssistBindingParams params, CancelChecker cancelChecker) {
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
  public void noBindingSuggestionFound(String projectKey) {
    var messageRequestParams = new ShowMessageRequestParams();
    messageRequestParams.setMessage("SonarLint couldn't match SonarQube project '" + projectKey + "' to any of the currently " +
      "open workspace folders. Please open your project in VSCode and try again.");
    messageRequestParams.setType(MessageType.Error);
    var learnMoreAction = new MessageActionItem("Learn more");
    messageRequestParams.setActions(List.of(learnMoreAction));
    client.showMessageRequest(messageRequestParams)
      .thenAccept(action -> {
        if (learnMoreAction.equals(action)) {
          client.browseTo("https://docs.sonarsource.com/sonarlint/vs-code/troubleshooting/#troubleshooting-connected-mode-setup");
        }
      });
  }

  @Override
  public void startProgress(StartProgressParams startProgressParams) {
    // no-op
  }

  @Override
  public void reportProgress(ReportProgressParams reportProgressParams) {
    // no-op
  }

  @Override
  public void didSynchronizeConfigurationScopes(Set<String> configurationScopeIds) {
    configurationScopeIds.forEach(this::getNewCodeDefinitionAndSubmitToClient);
  }

  @Nullable
  @Override
  public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) {
    var connectionSettings = settingsManager.getCurrentSettings().getServerConnections().get(connectionId);
    if (connectionSettings == null) return null;
    var token = connectionSettings.getToken();
    return Either.forLeft(new TokenDto(token));
  }

  @Override
  public TelemetryClientLiveAttributesResponse getTelemetryLiveAttributes() {
    return new TelemetryClientLiveAttributesResponse(backendServiceFacade.getTelemetryInitParams().getAdditionalAttributes());
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
      logOutput.error("Certificate encoding is malformed, SHA fingerprints will not be displayed", e);
    }
    var actualSonarLintUserHome = Optional.ofNullable(EnginesFactory.sonarLintUserHomeOverride).orElse(SonarLintUserHome.get());
    var confirmationParams = new SonarLintExtendedLanguageClient.SslCertificateConfirmationParams(
      untrustedCert == null ? "" : untrustedCert.getSubjectX500Principal().getName(),
      untrustedCert == null ? "" : untrustedCert.getIssuerX500Principal().getName(),
      untrustedCert == null ? "" : untrustedCert.getNotAfter().toString(),
      untrustedCert == null ? "" : untrustedCert.getNotBefore().toString(),
      sha1fingerprint,
      sha256fingerprint,
      actualSonarLintUserHome.toString()
    );

    return client.askSslCertificateConfirmation(confirmationParams).join();
  }

  @Override
  public void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent event) {
    serverSentEventsHandlerService.handleHotspotEvent(event);
  }


  @Nullable
  @Override
  public String matchSonarProjectBranch(String configurationScopeId, String mainBranchName, Set<String> allBranchesNames, CancelChecker cancelChecker) {
    return branchManager.matchSonarProjectBranch(configurationScopeId, mainBranchName, allBranchesNames, cancelChecker);
  }

  @Override
  public void didChangeMatchedSonarProjectBranch(String configScopeId, String newMatchedBranchName) {
    client.setReferenceBranchNameForFolder(SonarLintExtendedLanguageClient.ReferenceBranchForFolder.of(configScopeId,
      newMatchedBranchName));
  }

  @Override
  public void didChangeTaintVulnerabilities(String folderUri, Set<UUID> closedTaintVulnerabilityIds,
    List<TaintVulnerabilityDto> addedTaintVulnerabilities, List<TaintVulnerabilityDto> updatedTaintVulnerabilities) {
    var addedTaintVulnerabilitiesByFile = addedTaintVulnerabilities.stream()
      .collect(groupingBy(taintVulnerabilityDto -> URIUtils.getFullFileUriFromFragments(folderUri, taintVulnerabilityDto.getIdeFilePath()), toList()));
    var updatedTaintVulnerabilitiesByFile = updatedTaintVulnerabilities.stream()
      .collect(groupingBy(taintVulnerabilityDto -> URIUtils.getFullFileUriFromFragments(folderUri, taintVulnerabilityDto.getIdeFilePath()), toList()));

    // Remove taints that were closed
    taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().values().stream().flatMap(Collection::stream)
      .filter(taintIssue -> closedTaintVulnerabilityIds.contains(taintIssue.getId()))
      .forEach(taintIssue -> taintVulnerabilitiesCache.removeTaintIssue(
        URIUtils.getFullFileUriFromFragments(folderUri, taintIssue.getIdeFilePath()).toString(), taintIssue.getSonarServerKey()));

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
      diagnosticPublisher.publishDiagnostics(fileUri, false);
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
      diagnosticPublisher.publishDiagnostics(fileUri, false);
    });
  }

  @Override
  public List<ClientFileDto> listFiles(String folderUri) {
    return CompletableFutures.computeAsync(c -> {
      var response = client.listFilesInFolder(new SonarLintExtendedLanguageClient.FolderUriParams(folderUri)).join();
      var folderPath = Path.of(URI.create(folderUri));
      return response.getFoundFiles().stream()
        .map(file -> {
          var filePath = Path.of(file.getFilePath());
          return new ClientFileDto(filePath.toUri(), folderPath.relativize(filePath), folderUri, null, StandardCharsets.UTF_8.name(), filePath,
            file.getContent());
        })
        .toList();
    }).join();
  }

  @Override
  public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
    workspaceFoldersManager.updateAnalysisReadiness(configurationScopeIds, areReadyForAnalysis);
    if (areReadyForAnalysis) {
      // for each open folderUri do didOpen for each open file
      Collection<WorkspaceFolderWrapper> all = workspaceFoldersManager.getAll();
      all.forEach(folderWrapper -> {
        var folderUri = folderWrapper.getUri();
        Collection<VersionedOpenFile> openFiles = openFilesCache.getAll();
        for (var openFile : openFiles) {
          Optional<WorkspaceFolderWrapper> folderForFileOpt = workspaceFoldersManager.findFolderForFile(openFile.getUri());
          if (folderForFileOpt.isPresent() && folderForFileOpt.get().getUri().equals(folderUri)) {
            analysisScheduler.didOpen(openFile);
          }
        }
        analysisScheduler.analyzeAllUnboundOpenFiles();
      });
      initializeTaintCache(configurationScopeIds);
    }
  }

  private void initializeTaintCache(Set<String> configurationScopeIds) {
    configurationScopeIds.forEach(configurationScopeId -> {
      var binding = bindingManager.getBinding(URI.create(configurationScopeId));
      if (binding.isPresent()) {
        var isSonarCloud = Objects.requireNonNull(bindingManager.getServerConnectionSettingsFor(binding.get().getConnectionId())).isSonarCloudAlias();
        CompletableFutures.computeAsync(cancelChecker -> {
          var taints = backendServiceFacade.getBackendService().getAllTaints(configurationScopeId).join();

          var taintsByFile = taints.getTaintVulnerabilities()
            .stream()
            .collect(groupingBy(taintVulnerabilityDto ->
              URIUtils.getFullFileUriFromFragments(configurationScopeId, taintVulnerabilityDto.getIdeFilePath()), toList()));

          taintsByFile.forEach((fileUri, t) -> {
            var vulnerabilities = dtosToTaintIssues(configurationScopeId, t, isSonarCloud);
            taintVulnerabilitiesCache.reload(fileUri, vulnerabilities);
            diagnosticPublisher.publishDiagnostics(fileUri, false);
          });

          return null;
        });
      }
    });
  }

  @NotNull
  private static ArrayList<TaintIssue> dtosToTaintIssues(String configurationScopeId, List<TaintVulnerabilityDto> t, Boolean isSonarCloud) {
    return t.stream()
      .map(dto ->
        new TaintIssue(dto, configurationScopeId, isSonarCloud))
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

  public void setServerSentEventsHandlerService(ServerSentEventsHandlerService serverSentEventsHandlerService) {
    this.serverSentEventsHandlerService = serverSentEventsHandlerService;
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

  public WorkspaceFolderBranchManager getBranchManager() {
    return branchManager;
  }

  public void setWorkspaceFoldersManager(WorkspaceFoldersManager workspaceFoldersManager) {
    this.workspaceFoldersManager = workspaceFoldersManager;
  }

  public void setAnalysisScheduler(AnalysisScheduler analysisScheduler) {
    this.analysisScheduler = analysisScheduler;
  }

  public void setDiagnosticPublisher(DiagnosticPublisher diagnosticPublisher) {
    this.diagnosticPublisher = diagnosticPublisher;
  }
}
