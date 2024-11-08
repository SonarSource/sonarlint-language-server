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

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintCancelChecker;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.NoBindingSuggestionFoundParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarCloudConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarQubeConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SuggestConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.ChangesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.FileEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.FixSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.LineRangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.info.GetClientLiveInfoResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.AnalysisHelper;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.ForcedAnalysisCoordinator;
import org.sonarsource.sonarlint.ls.SkippedPluginsNotifier;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.CreateConnectionParams;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.connected.api.HostInfoProvider;
import org.sonarsource.sonarlint.ls.connected.events.ServerSentEventsHandlerService;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.domain.TaintIssue;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderBranchManager;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.progress.LSProgressMonitor;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.standalone.notifications.PromotionalNotifications;
import org.sonarsource.sonarlint.ls.util.URIUtils;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SonarLintVSCodeClientTests {
  private static final Set<String> PROXY_PROPERTY_KEYS = Set.of(
    "http.proxyHost",
    "http.proxyPort",
    "http.proxyUser",
    "http.proxyPassword"
  );
  private static final Map<String, String> savedProxyProperties = new HashMap<>();

  @TempDir
  Path basedir;
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();
  private Path workspaceFolderPath;
  private Path fileInAWorkspaceFolderPath;
  private final Path FILE_PYTHON = Path.of("myFile.py");
  SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  SettingsManager settingsManager = mock(SettingsManager.class);
  SmartNotifications smartNotifications = mock(SmartNotifications.class);
  SonarLintVSCodeClient underTest;
  HostInfoProvider server = mock(HostInfoProvider.class);
  ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  OpenFilesCache openFilesCache = mock(OpenFilesCache.class);
  OpenNotebooksCache openNotebooksCache = mock(OpenNotebooksCache.class);
  SkippedPluginsNotifier skippedPluginsNotifier = mock(SkippedPluginsNotifier.class);
  ServerSentEventsHandlerService serverSentEventsHandlerService = mock(ServerSentEventsHandlerService.class);
  @Captor
  ArgumentCaptor<ShowAllLocationsCommand.Param> paramCaptor;
  BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  TaintVulnerabilitiesCache taintVulnerabilitiesCache = mock(TaintVulnerabilitiesCache.class);
  ForcedAnalysisCoordinator forcedAnalysisCoordinator = mock(ForcedAnalysisCoordinator.class);
  DiagnosticPublisher diagnosticPublisher = mock(DiagnosticPublisher.class);
  PromotionalNotifications promotionalNotifications = mock(PromotionalNotifications.class);
  AnalysisHelper analysisHelper = mock(AnalysisHelper.class);
  LSProgressMonitor progressMonitor = mock(LSProgressMonitor.class);
  WorkspaceFolderBranchManager branchManager = mock(WorkspaceFolderBranchManager.class);

  private static final String PEM = """
    subject=CN=localhost,O=SonarSource SA,L=Geneva,ST=Geneva,C=CH
    issuer=CN=localhost,O=SonarSource SA,L=Geneva,ST=Geneva,C=CH
    -----BEGIN CERTIFICATE-----
    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
    -----END CERTIFICATE-----""";

  @BeforeEach
  public void setup() throws IOException {
    underTest = new SonarLintVSCodeClient(client, server, logTester.getLogger(), taintVulnerabilitiesCache,
      skippedPluginsNotifier, promotionalNotifications, progressMonitor);
    underTest.setSmartNotifications(smartNotifications);
    underTest.setSettingsManager(settingsManager);
    underTest.setBindingManager(bindingManager);
    underTest.setServerSentEventsHandlerService(serverSentEventsHandlerService);
    underTest.setBackendServiceFacade(backendServiceFacade);
    underTest.setDiagnosticPublisher(diagnosticPublisher);
    underTest.setAnalysisScheduler(forcedAnalysisCoordinator);
    underTest.setAnalysisTaskExecutor(analysisHelper);
    underTest.setBranchManager(branchManager);
    workspaceFolderPath = basedir.resolve("myWorkspaceFolder");
    Files.createDirectories(workspaceFolderPath);
    fileInAWorkspaceFolderPath = workspaceFolderPath.resolve(FILE_PYTHON);
    Files.createFile(fileInAWorkspaceFolderPath);
    Files.writeString(fileInAWorkspaceFolderPath, """
      print('1234')
      print('aa')
      print('b')
      """);
  }

  @BeforeAll
  static void saveProxyProperties() {
    PROXY_PROPERTY_KEYS.forEach(k -> savedProxyProperties.put(k, System.getProperty(k)));
  }

  @AfterAll
  static void cleanupProxyProperties() {
    PROXY_PROPERTY_KEYS.forEach(k -> {
      var savedPropertyValue = savedProxyProperties.get(k);
      if (savedPropertyValue == null) {
        System.clearProperty(k);
      } else {
        System.setProperty(k, savedPropertyValue);
      }
    });
  }

  @Test
  void openUrlInBrowserTest() throws MalformedURLException {
    var params = new OpenUrlInBrowserParams("https://www.sonarsource.com");

    underTest.openUrlInBrowser(new URL("https://www.sonarsource.com"));

    verify(client).browseTo(params.getUrl());
  }

  @Test
  void shouldCallClientToFindFile() {
    when(client.listFilesInFolder(any())).thenReturn(CompletableFuture.completedFuture(new SonarLintExtendedLanguageClient.FindFileByNamesInScopeResponse(List.of())));

    underTest.listFiles(URI.create("file:///folderUri").toString());

    var expectedClientParams =
      new SonarLintExtendedLanguageClient.FolderUriParams("file:///folderUri");
    verify(client).listFilesInFolder(expectedClientParams);
  }

  @Test
  void shouldSuggestBinding() {
    var suggestions = new HashMap<String, List<BindingSuggestionDto>>();
    suggestions.put("key", List.of());

    underTest.suggestBinding(suggestions);

    var captor = ArgumentCaptor.forClass(SuggestBindingParams.class);
    Awaitility.await().atMost(10L, TimeUnit.SECONDS).untilAsserted(() -> {
      verify(client).suggestBinding(captor.capture());
      assertThat(captor.getValue().getSuggestions()).isEqualTo(suggestions);
    });
  }

  @Test
  void shouldNotSuggestBindingIfAlreadyExists() {
    var suggestions = new HashMap<String, List<BindingSuggestionDto>>();
    String configScopeId = workspaceFolderPath.toUri().toString();
    suggestions.put(configScopeId, List.of());
    var mockBinding = mock(ProjectBinding.class);

    when(bindingManager.getBinding(workspaceFolderPath.toUri())).thenReturn(Optional.of(mockBinding));
    underTest.suggestBinding(suggestions);

    verify(client, never()).suggestBinding(any());
  }

  @Test
  void shouldSuggestConnection() {
    var suggestions = new HashMap<String, List<ConnectionSuggestionDto>>();
    suggestions.put("key", List.of());

    underTest.suggestConnection(suggestions);

    var captor = ArgumentCaptor.forClass(SuggestConnectionParams.class);
    verify(client).suggestConnection(captor.capture());
    assertThat(captor.getValue().getSuggestionsByConfigScopeId()).isEqualTo(suggestions);
  }

  @Test
  void shouldSkipEmptySuggestionConnection() {
    underTest.suggestConnection(Map.of());

    verify(client, never()).suggestBinding(any());
  }

  @Test
  void shouldForwardShowMessage() {
    var message = "Something went wrong";
    underTest.showMessage(org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType.ERROR, message);

    var argCaptor = ArgumentCaptor.forClass(MessageParams.class);

    verify(client).showMessage(argCaptor.capture());
    assertThat(argCaptor.getValue().getMessage()).isEqualTo(message);
    assertThat(argCaptor.getValue().getType()).isEqualTo(MessageType.Error);
  }

  @Test
  void shouldHandleShowSmartNotificationWhenConnectionExists() {
    var workspaceSettings = mock(WorkspaceSettings.class);
    var showSmartNotificationParams = mock(ShowSmartNotificationParams.class);
    when(showSmartNotificationParams.getConnectionId()).thenReturn("testId");
    var serverConnections = Map.of("testId",
      new ServerConnectionSettings("testId",
        "http://localhost:9000",
        "abcdefg",
        null,
        false
      ));
    when(workspaceSettings.getServerConnections()).thenReturn(serverConnections);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    underTest.showSmartNotification(showSmartNotificationParams);

    verify(smartNotifications).showSmartNotification(any(ShowSmartNotificationParams.class), eq(false));
  }

  @Test
  void shouldHandleShowSmartNotificationWhenConnectionExistsForSonarCloud() {
    var workspaceSettings = mock(WorkspaceSettings.class);
    var showSmartNotificationParams = mock(ShowSmartNotificationParams.class);
    when(showSmartNotificationParams.getConnectionId()).thenReturn("testId");
    var serverConnections = Map.of("testId",
      new ServerConnectionSettings("testId",
        "https://sonarcloud.io",
        "abcdefg",
        "test-org",
        false
      ));
    when(workspaceSettings.getServerConnections()).thenReturn(serverConnections);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    underTest.showSmartNotification(showSmartNotificationParams);

    verify(smartNotifications).showSmartNotification(any(ShowSmartNotificationParams.class), eq(true));
  }

  @Test
  void shouldDoNothingOnShowSmartNotificationWhenConnectionIsNotFound() {
    var workspaceSettings = mock(WorkspaceSettings.class);
    var showSmartNotificationParams = mock(ShowSmartNotificationParams.class);
    when(showSmartNotificationParams.getConnectionId()).thenReturn("testId");
    var serverConnections = new HashMap<String, ServerConnectionSettings>();
    when(workspaceSettings.getServerConnections()).thenReturn(serverConnections);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    underTest.showSmartNotification(showSmartNotificationParams);

    verify(smartNotifications, never()).showSmartNotification(any(ShowSmartNotificationParams.class), eq(false));
  }

  @Test
  void shouldAskTheClientToFindFiles() {
    var folderPath = basedir.resolve("someFile");
    var folderUri = folderPath.toUri();
    var fileName1 = "file1";
    var fileName2 = "file2";
    var folderUriParams = new SonarLintExtendedLanguageClient.FolderUriParams(folderUri.toString());
    when(client.listFilesInFolder(folderUriParams)).thenReturn(CompletableFuture.completedFuture(
      new SonarLintExtendedLanguageClient.FindFileByNamesInScopeResponse(List.of(
        new SonarLintExtendedLanguageClient.FoundFileDto(fileName1, folderPath + "/" + fileName1, "foo"),
        new SonarLintExtendedLanguageClient.FoundFileDto(fileName2, folderPath + "/" + fileName2, "bar")
      ))));

    underTest.listFiles(folderUri.toString());

    var argumentCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.FolderUriParams.class);
    verify(client).listFilesInFolder(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue())
      .extracting(SonarLintExtendedLanguageClient.FolderUriParams::getFolderUri).isEqualTo(folderUri.toString());
  }

  @Test
  void shouldCallServerOnGetHostInfo() {
    when(server.getHostInfo()).thenReturn(new GetClientLiveInfoResponse("description"));

    underTest.getClientLiveDescription();

    verify(server).getHostInfo();
  }

  @Test
  void shouldGetHostInfo() {
    var desc = "This is Test";
    when(server.getHostInfo()).thenReturn(new GetClientLiveInfoResponse("This is Test"));
    var result = underTest.getClientLiveDescription();
    assertThat(result).isEqualTo(desc);
  }

  @Test
  void shouldCallClientShowHotspot() {
    var hotspotRule = mock(HotspotDetailsDto.HotspotRule.class);
    var hotspotDetailsDto = new HotspotDetailsDto("key1",
      "message1",
      Path.of("myfolder/myFile"),
      null,
      null,
      "TO_REVIEW",
      "fixed",
      hotspotRule,
      null
    );
    underTest.showHotspot("myFolder", hotspotDetailsDto);
    var argCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ShowHotspotParams.class);
    verify(client).showHotspot(argCaptor.capture());
    assertThat(argCaptor.getValue()).isNotNull();
    assertThat(argCaptor.getValue().getStatus()).isEqualTo("To Review");
    assertThat(argCaptor.getValue().getMessage()).isEqualTo("message1");
  }

  @Test
  void assistCreateConnectionShouldCallClientMethodForSonarQube() {
    String serverUrl = "http://localhost:9000";
    var assistCreatingConnectionParams = new AssistCreatingConnectionParams(new SonarQubeConnectionParams(serverUrl, "tokenName", "tokenValue"));
    when(client.workspaceFolders()).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(client.assistCreatingConnection(any()))
      .thenReturn(CompletableFuture.completedFuture(new AssistCreatingConnectionResponse("newConnectionId")));
    when(settingsManager.getCurrentSettings()).thenReturn(mock(WorkspaceSettings.class));
    when(backendServiceFacade.getBackendService()).thenReturn(mock(BackendService.class));
    underTest.assistCreatingConnection(assistCreatingConnectionParams, null);

    var argCaptor = ArgumentCaptor.forClass(CreateConnectionParams.class);
    verify(client).assistCreatingConnection(argCaptor.capture());
    var sentParams = argCaptor.getValue();
    assertThat(sentParams.serverUrlOrOrganisationKey()).isEqualTo(serverUrl);
    assertThat(sentParams.isSonarCloud()).isFalse();
    assertThat(sentParams.token()).isEqualTo("tokenValue");

    verify(client).showMessage((new MessageParams(MessageType.Info, "Connection to SonarQube Server was successfully created.")));
  }

  @Test
  void assistCreateConnectionShouldCallClientMethodForSonarCloud() {
    String organisationKey = "myOrg";
    var assistCreatingConnectionParams = new AssistCreatingConnectionParams(new SonarCloudConnectionParams(organisationKey, "tokenName", "tokenValue"));
    when(client.workspaceFolders()).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(client.assistCreatingConnection(any()))
      .thenReturn(CompletableFuture.completedFuture(new AssistCreatingConnectionResponse("newConnectionId")));
    when(settingsManager.getCurrentSettings()).thenReturn(mock(WorkspaceSettings.class));
    when(backendServiceFacade.getBackendService()).thenReturn(mock(BackendService.class));
    underTest.assistCreatingConnection(assistCreatingConnectionParams, null);

    var argCaptor = ArgumentCaptor.forClass(CreateConnectionParams.class);
    verify(client).assistCreatingConnection(argCaptor.capture());
    var sentParams = argCaptor.getValue();
    assertThat(sentParams.serverUrlOrOrganisationKey()).isEqualTo(organisationKey);
    assertThat(sentParams.isSonarCloud()).isTrue();
    assertThat(sentParams.token()).isEqualTo("tokenValue");

    verify(client).showMessage((new MessageParams(MessageType.Info, "Connection to SonarQube Cloud was successfully created.")));
  }

  @Test
  void assistCreateConnectionShouldCallClientMethod_noTokenCase() {
    String serverUrl = "http://localhost:9000";
    var assistCreatingConnectionParams = new AssistCreatingConnectionParams(new SonarQubeConnectionParams(serverUrl, null, null));
    when(client.workspaceFolders()).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(client.assistCreatingConnection(any())).thenReturn(CompletableFuture.completedFuture(
      null
    ));
    when(settingsManager.getCurrentSettings()).thenReturn(mock(WorkspaceSettings.class));
    when(backendServiceFacade.getBackendService()).thenReturn(mock(BackendService.class));
    assertThrows(CompletionException.class, () -> underTest.assistCreatingConnection(assistCreatingConnectionParams, null));

    var argCaptor = ArgumentCaptor.forClass(CreateConnectionParams.class);
    verify(client).assistCreatingConnection(argCaptor.capture());
    var sentParams = argCaptor.getValue();
    assertThat(sentParams.serverUrlOrOrganisationKey()).isEqualTo(serverUrl);
    assertThat(sentParams.isSonarCloud()).isFalse();
    assertThat(sentParams.token()).isNull();

    verify(client, never()).showMessage(any());
  }

  @Test
  void assistBindingShouldCallClientMethod() {
    var configScopeId = "folderUri";
    var projectKey = "projectKey";
    var assistBindingParams = new AssistBindingParams("connectionId", projectKey, configScopeId, false);
    when(client.assistBinding(any())).thenReturn(
      CompletableFuture.completedFuture(new SonarLintExtendedLanguageClient.AssistBindingResponse("folderUri")));
    var workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
    underTest.setWorkspaceFoldersManager(workspaceFoldersManager);

    underTest.assistBinding(assistBindingParams, null);

    verify(client).showMessage(new MessageParams(MessageType.Info, "Project '" + configScopeId + "' was successfully bound to '" + projectKey + "'."));
  }

  @Test
  void testNoBindingSuggestionFound() {
    when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(new MessageActionItem("Learn more")));

    var projectKey = "projectKey";
    var messageRequestParams = new ShowMessageRequestParams();
    messageRequestParams.setMessage("SonarQube for VS Code couldn't match the server project '" + projectKey + "' to any of the currently open workspace folders. Please make sure the project is open in the workspace, or try configuring the binding manually.");
    messageRequestParams.setType(MessageType.Error);
    var learnMoreAction = new MessageActionItem("Learn more");
    messageRequestParams.setActions(List.of(learnMoreAction));

    underTest.noBindingSuggestionFound(new NoBindingSuggestionFoundParams(projectKey, false));
    verify(client).showMessageRequest(messageRequestParams);
    verify(client).browseTo("https://docs.sonarsource.com/sonarlint/vs-code/troubleshooting/#troubleshooting-connected-mode-setup");
  }

  @Test
  void checkServerTrusted() {
    when(client.askSslCertificateConfirmation(any())).thenReturn(CompletableFuture.completedFuture(true));

    var response = underTest.checkServerTrusted(List.of(new X509CertificateDto(PEM)), "authType");

    var branchCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.SslCertificateConfirmationParams.class);
    verify(client).askSslCertificateConfirmation(branchCaptor.capture());
    var capturedValue = branchCaptor.getValue();
    Assertions.assertThat(capturedValue.getIssuedBy()).isEqualTo("CN=localhost,O=SonarSource SA,L=Geneva,ST=Geneva,C=CH");
    Assertions.assertThat(capturedValue.getIssuedTo()).isEqualTo("CN=localhost,O=SonarSource SA,L=Geneva,ST=Geneva,C=CH");
    Assertions.assertThat(capturedValue.getSha1Fingerprint()).isEqualTo("E9 7B 2D 15 32 3F CA 0D 9B 6A 25 C3 2A 11 73 1C 96 8B FC 73");
    Assertions.assertThat(capturedValue.getSha256Fingerprint()).isEqualTo("35 A0 22 CB CD 8D 57 55 F8 83 B3 CE 63 2A 42 A1\n" +
      "22 81 83 33 BF 2F 9A E7 E9 D7 81 F0 82 2C AD 58");

    assertThat(response).isTrue();
  }

  @Test
  void testShowSoonUnsupportedVersion() {
    var doNotShowAgainId = "sonarlint.unsupported.myConnection.8.9.9.id";
    var message = "SQ will be unsupported soon";
    var coreParams = new ShowSoonUnsupportedMessageParams(doNotShowAgainId, "configId", message);
    var branchCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ShowSoonUnsupportedVersionMessageParams.class);

    underTest.showSoonUnsupportedMessage(coreParams);
    verify(client).showSoonUnsupportedVersionMessage(branchCaptor.capture());
    verifyNoMoreInteractions(client);
    assertThat(branchCaptor.getValue().getDoNotShowAgainId()).isEqualTo(doNotShowAgainId);
    assertThat(branchCaptor.getValue().getText()).isEqualTo(message);
  }

  @Test
  void checkServerTrustedMalformedCert() {
    when(client.askSslCertificateConfirmation(any())).thenReturn(CompletableFuture.completedFuture(true));

    var response = underTest.checkServerTrusted(List.of(new X509CertificateDto("malformed")), "authType");

    var branchCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.SslCertificateConfirmationParams.class);
    verify(client).askSslCertificateConfirmation(branchCaptor.capture());
    var capturedValue = branchCaptor.getValue();
    Assertions.assertThat(capturedValue.getIssuedBy()).isEmpty();
    Assertions.assertThat(capturedValue.getIssuedTo()).isEmpty();
    Assertions.assertThat(capturedValue.getValidFrom()).isEmpty();
    Assertions.assertThat(capturedValue.getValidTo()).isEmpty();
    Assertions.assertThat(capturedValue.getSha1Fingerprint()).isEmpty();
    Assertions.assertThat(capturedValue.getSha256Fingerprint()).isEmpty();

    assertThat(response).isTrue();
  }

  @Test
  void shouldForwardOpenIssueRequest() {
    var fileUri = fileInAWorkspaceFolderPath.toUri();
    var textRangeDto = new TextRangeDto(1, 2, 3, 4);
    var issueDetailsDto = new IssueDetailsDto(textRangeDto, "rule:S1234",
      "issueKey", FILE_PYTHON, "branch", "PR", "this is wrong",
      "29.09.2023", "print('ddd')", false, List.of());
    var showIssueParams = new ShowIssueParams(fileUri.toString(), issueDetailsDto);

    when(bindingManager.getBinding(fileUri))
      .thenReturn(Optional.of(new ProjectBinding("connectionId", "projectKey")));

    underTest.showIssue(fileUri.toString(), issueDetailsDto);
    verify(client).showIssue(paramCaptor.capture());

    var showAllLocationParams = paramCaptor.getValue();

    assertEquals(showIssueParams.getIssueDetails().getFlows().size(), showAllLocationParams.getFlows().size());
    assertEquals("", showAllLocationParams.getSeverity());
    assertEquals(showIssueParams.getIssueDetails().getMessage(), showAllLocationParams.getMessage());
    assertEquals(showIssueParams.getIssueDetails().getRuleKey(), showAllLocationParams.getRuleKey());
  }

  @Test
  void shouldForwardOpenIssueRequestWithoutRuleDescriptionWhenBindingDoesNotExist() {
    var fileUri = fileInAWorkspaceFolderPath.toUri();
    var textRangeDto = new TextRangeDto(1, 2, 3, 4);
    var issueDetailsDto = new IssueDetailsDto(textRangeDto, "rule:S1234",
      "issueKey", FILE_PYTHON, "bb", null, "this is wrong", "29.09.2023", "print('ddd')",
      false, List.of());
    when(bindingManager.getBindingIfExists(fileUri))
      .thenReturn(Optional.empty());

    underTest.showIssue(fileUri.toString(), issueDetailsDto);
    verify(client).showIssue(paramCaptor.capture());

    var showAllLocationParams = paramCaptor.getValue();

    assertFalse(showAllLocationParams.isShouldOpenRuleDescription());
  }

  @Test
  void shouldForwardOpenIssueRequestWithRuleDescriptionWhenBindingDoesExist() {
    var fileUri = fileInAWorkspaceFolderPath.toUri();
    var textRangeDto = new TextRangeDto(1, 2, 3, 4);
    var issueDetailsDto = new IssueDetailsDto(textRangeDto, "rule:S1234",
      "issueKey", FILE_PYTHON, "bb", null, "this is wrong", "29.09.2023", "print('ddd')",
      false, List.of());
    when(bindingManager.getBindingIfExists(fileUri))
      .thenReturn(Optional.of(new ProjectBinding("connectionId", "projectKey")));

    underTest.showIssue(fileUri.toString(), issueDetailsDto);
    verify(client).showIssue(paramCaptor.capture());

    var showAllLocationParams = paramCaptor.getValue();

    assertTrue(showAllLocationParams.isShouldOpenRuleDescription());
  }


  @Test
  void shouldLogMessagesWithLogLevel() {
    underTest.log(new LogParams(LogLevel.ERROR, null, null, null, Instant.now()));
    assertThat(logTester.logs())
      .anyMatch(log -> log.contains("null"));

    underTest.log(new LogParams(LogLevel.ERROR, "Log message", null, null, Instant.now()));
    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("null"));
    underTest.log(new LogParams(LogLevel.WARN, "Log message", null, null, Instant.now()));
    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("null"));
    underTest.log(new LogParams(LogLevel.INFO, "Log message", null, null, Instant.now()));
    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("null"));
    underTest.log(new LogParams(LogLevel.DEBUG, "Log message", null, null, Instant.now()));
    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("null"));
    underTest.log(new LogParams(LogLevel.TRACE, "Log message", null, null, Instant.now()));
    assertThat(logTester.logs(MessageType.Log))
      .anyMatch(log -> log.contains("null"));
  }

  @Test
  void shouldUpdateTaintsCacheOnTaintsChangedAndPublishDiagnostics() {
    var filePath = Path.of("filePath");
    var workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
    var workspaceFolderWrapper = mock(WorkspaceFolderWrapper.class);
    var workspaceFolderSettings = mock(WorkspaceFolderSettings.class);
    var serverConnectionSettings = mock(ServerConnectionSettings.class);
    when(serverConnectionSettings.isSonarCloudAlias()).thenReturn(true);
    when(workspaceFolderSettings.getConnectionId()).thenReturn("connectionId");
    when(bindingManager.getServerConnectionSettingsFor("connectionId")).thenReturn(serverConnectionSettings);
    when(workspaceFolderWrapper.getSettings()).thenReturn(workspaceFolderSettings);
    underTest.setWorkspaceFoldersManager(workspaceFoldersManager);
    when(workspaceFoldersManager.getFolder(workspaceFolderPath.toUri())).thenReturn(Optional.of(workspaceFolderWrapper));
    var uuid1 = UUID.randomUUID();
    var uuid2 = UUID.randomUUID();
    var uuid3 = UUID.randomUUID();
    var uuid4 = UUID.randomUUID();
    when(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile()).thenReturn(Map.of(filePath.toUri(),
      List.of(getTaintIssue(uuid1), getTaintIssue(uuid2))));

    underTest.didChangeTaintVulnerabilities(workspaceFolderPath.toUri().toString(), Set.of(uuid1),
      List.of(getTaintDto(uuid1), getTaintDto(uuid2)), List.of(getTaintDto(uuid3), getTaintDto(uuid4)));

    verify(taintVulnerabilitiesCache, times(2)).reload(eq(workspaceFolderPath.toUri().resolve(filePath.toString())), any());
    verify(taintVulnerabilitiesCache).removeTaintIssue(URIUtils.getFullFileUriFromFragments(workspaceFolderPath.toUri().toString(), filePath).toString(),
      getTaintIssue(uuid1).getSonarServerKey());
  }

  @Test
  void shouldPopulateTaintsCacheOnAnalysisReadinessChangedAndPublishDiagnostics() throws InterruptedException {
    var filePath = Path.of("filePath");
    var workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
    var workspaceFolderWrapper = mock(WorkspaceFolderWrapper.class);
    when(workspaceFolderWrapper.getUri()).thenReturn(workspaceFolderPath.toUri());
    var serverConnectionSettings = mock(ServerConnectionSettings.class);
    when(serverConnectionSettings.isSonarCloudAlias()).thenReturn(true);
    when(bindingManager.getServerConnectionSettingsFor("connectionId")).thenReturn(serverConnectionSettings);
    var fakeBinding = mock(ProjectBinding.class);
    when(bindingManager.getBinding(workspaceFolderPath.toUri()))
      .thenReturn(Optional.of(fakeBinding));
    when(fakeBinding.connectionId()).thenReturn("connectionId");
    underTest.setWorkspaceFoldersManager(workspaceFoldersManager);
    when(workspaceFoldersManager.getAll()).thenReturn(List.of(workspaceFolderWrapper));
    var uuid1 = UUID.randomUUID();
    var uuid2 = UUID.randomUUID();

    var fakeBackend = mock(BackendService.class);
    when(backendServiceFacade.getBackendService()).thenReturn(fakeBackend);
    when(fakeBackend.getAllTaints(workspaceFolderPath.toUri().toString()))
      .thenReturn(CompletableFuture.completedFuture(new ListAllResponse(List.of(getTaintDto(uuid1), getTaintDto(uuid2)))));
    when(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile()).thenReturn(Map.of());
    doNothing().when(forcedAnalysisCoordinator).analyzeAllUnboundOpenFiles();

    underTest.didChangeAnalysisReadiness(Set.of(workspaceFolderPath.toUri().toString()), true);

    var captor = ArgumentCaptor.forClass(List.class);

    Thread.sleep(1000);
    verify(taintVulnerabilitiesCache).reload(eq(URIUtils.getFullFileUriFromFragments(workspaceFolderPath.toUri().toString(), filePath)), captor.capture());
    var taintIssues = captor.getValue();
    assertThat(taintIssues).hasSize(2);
    assertThat(((TaintIssue) taintIssues.get(0)).getId()).isEqualTo(uuid1);
    assertThat(((TaintIssue) taintIssues.get(1)).getId()).isEqualTo(uuid2);
    verify(diagnosticPublisher).publishDiagnostics(URIUtils.getFullFileUriFromFragments(workspaceFolderPath.toUri().toString(), filePath), true);
  }

  @Test
  void testDefaultProxyBehavior() throws URISyntaxException {
    ProxySelector.setDefault(new ProxySelector() {
      @Override
      public List<Proxy> select(URI uri) {
        if (uri.equals(URI.create("http://foo"))) {
          return List.of(
            new Proxy(Proxy.Type.HTTP, new InetSocketAddress("http://myproxy", 8085)),
            new Proxy(Proxy.Type.HTTP, new InetSocketAddress("http://myproxy2", 8086)));
        }
        return List.of(Proxy.NO_PROXY);
      }

      @Override
      public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {

      }
    });

    var selectProxiesResponse = underTest.selectProxies(new URI("http://foo"));

    assertThat(selectProxiesResponse).extracting(ProxyDto::getType, ProxyDto::getHostname, ProxyDto::getPort)
      .containsExactly(tuple(Proxy.Type.HTTP, "http://myproxy", 8085),
        tuple(Proxy.Type.HTTP, "http://myproxy2", 8086));

    var selectProxiesResponseDirectProxy = underTest.selectProxies(new URI("http://foo2"));

    assertThat(selectProxiesResponseDirectProxy).extracting(ProxyDto::getType, ProxyDto::getHostname, ProxyDto::getPort)
      .containsExactlyInAnyOrder(tuple(Proxy.Type.DIRECT, null, 0));
  }

  @Test
  void testDefaultAuthenticatorBehavior() throws MalformedURLException {

    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        assertThat(getRequestingHost()).isEqualTo("http://foo");
        assertThat(getRequestingURL()).hasToString("http://targethost");
        assertThat(getRequestingPort()).isEqualTo(8085);
        assertThat(getRequestingProtocol()).isEqualTo("protocol");
        assertThat(getRequestingScheme()).isEqualTo("scheme");
        assertThat(getRequestingPrompt()).isEqualTo("prompt");
        assertThat(getRequestorType()).isEqualTo(RequestorType.PROXY);
        return new PasswordAuthentication("username", "password".toCharArray());
      }
    });

    var response = underTest.getProxyPasswordAuthentication("http://foo", 8085, "protocol",
      "prompt", "scheme", new URL("http://targethost"));
    assertThat(response.getProxyUser()).isEqualTo("username");
    assertThat(response.getProxyPassword()).isEqualTo("password");

  }

  @Test
  void shouldUseDefaultAuthenticatorWithSystemProperties() throws MalformedURLException {
    // setup
    System.setProperty("http.proxyHost", "localhost");
    System.setProperty("http.proxyPort", "1234");
    System.setProperty("http.proxyUser", "myUser");
    System.setProperty("http.proxyPassword", "myPass");

    var passwordAuth = Authenticator.requestPasswordAuthentication("localhost", null, 1234,
      "http", "", "", new URL("http://localhost:9000"),
      Authenticator.RequestorType.PROXY);

    assertThat(passwordAuth.getUserName()).isEqualTo("myUser");
    assertThat(new String(passwordAuth.getPassword())).isEqualTo("myPass");
  }

  @Test
  void shouldForwardDetectedSecretToDiagnosticPublisher() {
    underTest.didDetectSecret("configScope");

    verify(diagnosticPublisher, times(1)).didDetectSecret();
  }

  @Test
  void shouldForwardSkippedPluginNotification() {
    var configScopeId = "file:///my/config/scope";
    var language = Language.TS;
    var reason = DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_NODE_JS;
    var minVersion = "18.18";

    underTest.didSkipLoadingPlugin(configScopeId, language, reason, minVersion, null);

    verify(skippedPluginsNotifier, times(1)).notifyOnceForSkippedPlugins(language, reason, minVersion, null);
  }

  @Test
  void shouldForwardConnectedModePromotionNotification() {
    var configScopeId = "file:///my/config/scope";
    var languages = Set.of(Language.PLSQL, Language.TSQL);

    underTest.promoteExtraEnabledLanguagesInConnectedMode(configScopeId, languages);

    verify(promotionalNotifications, times(1)).promoteExtraEnabledLanguagesInConnectedMode(languages);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldForwardShowFixSuggestionRequestToClient() {
    var configScope = "file:///Users/sonarlint-user/project/";
    var issueKey = "AbC235fVfd";
    var suggestionId = UUID.randomUUID().toString();
    var fixSuggestion = new FixSuggestionDto(
      suggestionId,
      "You should really reconsider this",
      new FileEditDto(
        Path.of("src/main/java/com/sonarsource/MyClass.java"),
        List.of(
          new ChangesDto(
            new LineRangeDto(1, 1),
            "System.out.println(\"Hello, World!\");",
            ""
          ),
          new ChangesDto(
            new LineRangeDto(2, 2),
            "System.out.println(\"Hello, World!\");",
            ""
          )
        )
      ));

    underTest.showFixSuggestion(configScope, issueKey, fixSuggestion);

    var argumentCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ShowFixSuggestionParams.class);

    verify(client).showFixSuggestion(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().suggestionId()).isEqualTo(suggestionId);
    assertThat(argumentCaptor.getValue().fileUri()).isEqualTo("file:///Users/sonarlint-user/project/src/main/java/com/sonarsource/MyClass.java");
    assertThat(argumentCaptor.getValue().textEdits().get(0).after()).isEmpty();
    assertThat(argumentCaptor.getValue().textEdits().get(1).before()).isEqualTo("System.out.println(\"Hello, World!\");");
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void shouldForwardShowFixSuggestionRequestToClient_windows() {
    var configScope = "file:///Users/sonarlint-user/project/";
    var issueKey = "AbC235fVfd";
    var suggestionId = UUID.randomUUID().toString();
    var fixSuggestion = new FixSuggestionDto(
      suggestionId,
      "You should really reconsider this",
      new FileEditDto(
        Path.of("src/main/java/com/sonarsource/MyClass.java"),
        List.of(
          new ChangesDto(
            new LineRangeDto(1, 1),
            "System.out.println(\"Hello, World!\");",
            ""
          ),
          new ChangesDto(
            new LineRangeDto(2, 2),
            "System.out.println(\"Hello, World!\");",
            ""
          )
        )
      ));

    underTest.showFixSuggestion(configScope, issueKey, fixSuggestion);

    var argumentCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ShowFixSuggestionParams.class);

    verify(client).showFixSuggestion(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().suggestionId()).isEqualTo(suggestionId);
    assertThat(argumentCaptor.getValue().fileUri()).isEqualTo("file:///C:/Users/sonarlint-user/project/src/main/java/com/sonarsource/MyClass.java");
    assertThat(argumentCaptor.getValue().textEdits().get(0).after()).isEmpty();
    assertThat(argumentCaptor.getValue().textEdits().get(1).before()).isEqualTo("System.out.println(\"Hello, World!\");");
  }

  @Test
  void shouldMatchProjectBranch() throws ConfigScopeNotFoundException {
    var configScopeId = "file:///Users/sonarlint-user/project/";
    var projectBranch = "currentBranch";

    ArgumentCaptor<String> configScopeIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> projectBranchCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<SonarLintCancelChecker> cancelCheckerArgumentCaptor = ArgumentCaptor.forClass(SonarLintCancelChecker.class);

    SonarLintCancelChecker cancelChecker = new SonarLintCancelChecker(new DummyCancelChecker());
    underTest.matchProjectBranch(configScopeId, projectBranch,
      cancelChecker);

    verify(branchManager).matchProjectBranch(configScopeIdCaptor.capture(), projectBranchCaptor.capture(), cancelCheckerArgumentCaptor.capture());
    assertThat(configScopeIdCaptor.getValue()).isEqualTo(configScopeId);
    assertThat(projectBranchCaptor.getValue()).isEqualTo(projectBranch);
    assertThat(cancelCheckerArgumentCaptor.getValue()).isEqualTo(cancelChecker);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldReturnBaseDir() {
    var configScopeId = "file:///my/config/scope";

    assertThat(underTest.getBaseDir(configScopeId)).isEqualTo(Path.of("/my/config/scope"));
  }


  @Test
  @EnabledOnOs(OS.WINDOWS)
  void shouldReturnBaseDirOnWindows() {
    var configScopeId = "file:///my/config/scope";

    assertThat(underTest.getBaseDir(configScopeId)).isEqualTo(Path.of("\\my\\config\\scope"));
  }

  @Test
  void shouldForwardIssueRaisedNotification() {
    var configScopeId = "file:///my/config/scope";
    var raisedIssue = mock(RaisedIssueDto.class);
    var analysisId = UUID.randomUUID();
    var fileUri = URI.create("fileUri");
    var issuesByFileUri = Map.of(fileUri, List.of(raisedIssue));
    underTest.raiseIssues(configScopeId, issuesByFileUri, false, analysisId);
    ArgumentCaptor<Map<URI, List<RaisedFindingDto>>> findingsPerFileCaptor = ArgumentCaptor.forClass(Map.class);

    verify(analysisHelper, times(1)).handleIssues(findingsPerFileCaptor.capture());
    assertThat(findingsPerFileCaptor.getValue().get(fileUri)).isNotNull();
    assertThat(findingsPerFileCaptor.getValue().get(fileUri)).hasSize(1);
  }

  private TaintVulnerabilityDto getTaintDto(UUID uuid) {
    return new TaintVulnerabilityDto(uuid, "serverKey", false, "ruleKey", "message",
      Path.of("filePath"), Instant.now(), org.sonarsource.sonarlint.core.rpc.protocol.common.Either
      .forLeft(new StandardModeDetails(IssueSeverity.MAJOR, RuleType.BUG)), IssueSeverity.MAJOR, RuleType.BUG, List.of(),
      new TextRangeWithHashDto(5, 5, 5, 5, ""), "", CleanCodeAttribute.CONVENTIONAL,
      Map.of(), true);
  }

  private TaintIssue getTaintIssue(UUID uuid) {
    return new TaintIssue(new TaintVulnerabilityDto(uuid, "serverKey", false, "ruleKey", "message",
      Path.of("filePath"), Instant.now(), org.sonarsource.sonarlint.core.rpc.protocol.common.Either
      .forLeft(new StandardModeDetails(IssueSeverity.MAJOR, RuleType.BUG)), IssueSeverity.MAJOR, RuleType.BUG, List.of(),
      new TextRangeWithHashDto(5, 5, 5, 5, ""), "", CleanCodeAttribute.CONVENTIONAL,
      Map.of(), true), "folderUri", true);
  }

  public class DummyCancelChecker implements CancelChecker {
    @Override
    public void checkCanceled() {
      // No-op
    }
  }
}
