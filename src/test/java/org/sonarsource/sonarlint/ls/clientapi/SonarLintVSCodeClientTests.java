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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.info.GetClientLiveInfoResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.AnalysisScheduler;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.CreateConnectionParams;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ServerIssueTrackerWrapper;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.connected.api.HostInfoProvider;
import org.sonarsource.sonarlint.ls.connected.events.ServerSentEventsHandlerService;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.domain.TaintIssue;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.util.URIUtils;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
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
  ServerSentEventsHandlerService serverSentEventsHandlerService = mock(ServerSentEventsHandlerService.class);
  @Captor
  ArgumentCaptor<ShowAllLocationsCommand.Param> paramCaptor;
  SonarLintAnalysisEngine engine = mock(SonarLintAnalysisEngine.class);
  ServerIssueTrackerWrapper serverIssueTrackerWrapper = mock(ServerIssueTrackerWrapper.class);
  BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  TaintVulnerabilitiesCache taintVulnerabilitiesCache = mock(TaintVulnerabilitiesCache.class);
  AnalysisScheduler analysisScheduler = mock(AnalysisScheduler.class);
  DiagnosticPublisher diagnosticPublisher = mock(DiagnosticPublisher.class);

  private static final String PEM = "subject=CN=localhost,O=SonarSource SA,L=Geneva,ST=Geneva,C=CH\n" +
    "issuer=CN=localhost,O=SonarSource SA,L=Geneva,ST=Geneva,C=CH\n" +
    "-----BEGIN CERTIFICATE-----\n" +
    "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\n" +
    "-----END CERTIFICATE-----";

  @BeforeEach
  public void setup() throws IOException {
    underTest = new SonarLintVSCodeClient(client, server, logTester.getLogger(), taintVulnerabilitiesCache, openFilesCache);
    underTest.setSmartNotifications(smartNotifications);
    underTest.setSettingsManager(settingsManager);
    underTest.setBindingManager(bindingManager);
    underTest.setServerSentEventsHandlerService(serverSentEventsHandlerService);
    underTest.setBackendServiceFacade(backendServiceFacade);
    underTest.setDiagnosticPublisher(diagnosticPublisher);
    underTest.setAnalysisScheduler(analysisScheduler);
    workspaceFolderPath = basedir.resolve("myWorkspaceFolder");
    Files.createDirectories(workspaceFolderPath);
    fileInAWorkspaceFolderPath = workspaceFolderPath.resolve(FILE_PYTHON);
    Files.createFile(fileInAWorkspaceFolderPath);
    Files.write(fileInAWorkspaceFolderPath, ("print('1234')\n" +
      "print('aa')\n" +
      "print('b')\n").getBytes(StandardCharsets.UTF_8));
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
    suggestions.put("key", Collections.emptyList());

    underTest.suggestBinding(suggestions);

    var captor = ArgumentCaptor.forClass(SuggestBindingParams.class);
    verify(client).suggestBinding(captor.capture());
    assertThat(captor.getValue().getSuggestions()).isEqualTo(suggestions);
  }

  @Test
  void shouldThrowForShowMessage() {
    assertThrows(UnsupportedOperationException.class, () -> underTest.showMessage(mock(
      org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType.class), ""));
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
  void shouldGetHostInfo() throws ExecutionException, InterruptedException {
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
  void assistCreateConnectionShouldCallClientMethod() {
    String serverUrl = "http://localhost:9000";
    var assistCreatingConnectionParams = new AssistCreatingConnectionParams(serverUrl, "tokenName", "tokenValue");
    when(client.workspaceFolders()).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(client.assistCreatingConnection(any()))
      .thenReturn(CompletableFuture.completedFuture(new AssistCreatingConnectionResponse("newConnectionId")));
    when(settingsManager.getCurrentSettings()).thenReturn(mock(WorkspaceSettings.class));
    when(backendServiceFacade.getBackendService()).thenReturn(mock(BackendService.class));
    underTest.assistCreatingConnection(assistCreatingConnectionParams, null);

    var argCaptor = ArgumentCaptor.forClass(CreateConnectionParams.class);
    verify(client).assistCreatingConnection(argCaptor.capture());
    var sentParams = argCaptor.getValue();
    assertThat(sentParams.getServerUrl()).isEqualTo(serverUrl);
    assertThat(sentParams.isSonarCloud()).isFalse();
    assertThat(sentParams.getToken()).isEqualTo("tokenValue");

    verify(client).showMessage((new MessageParams(MessageType.Info, "Connection to SonarQube was successfully created.")));
  }

  @Test
  void assistCreateConnectionShouldCallClientMethod_noTokenCase() {
    String serverUrl = "http://localhost:9000";
    var assistCreatingConnectionParams = new AssistCreatingConnectionParams(serverUrl, null, null);
    when(client.workspaceFolders()).thenReturn(CompletableFuture.completedFuture(List.of()));
    when(client.assistCreatingConnection(any())).thenReturn(CompletableFuture.completedFuture(
      new AssistCreatingConnectionResponse(null)
    ));
    when(settingsManager.getCurrentSettings()).thenReturn(mock(WorkspaceSettings.class));
    when(backendServiceFacade.getBackendService()).thenReturn(mock(BackendService.class));
    underTest.assistCreatingConnection(assistCreatingConnectionParams, null);

    var argCaptor = ArgumentCaptor.forClass(CreateConnectionParams.class);
    verify(client).assistCreatingConnection(argCaptor.capture());
    var sentParams = argCaptor.getValue();
    assertThat(sentParams.getServerUrl()).isEqualTo(serverUrl);
    assertThat(sentParams.isSonarCloud()).isFalse();
    assertThat(sentParams.getToken()).isNull();

    verify(client, never()).showMessage(any());
  }

  @Test
  void assistBindingShouldCallClientMethod() {
    var configScopeId = "folderUri";
    var projectKey = "projectKey";
    var assistBindingParams = new AssistBindingParams("connectionId", projectKey, configScopeId);
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
    messageRequestParams.setMessage("SonarLint couldn't match SonarQube project '" + projectKey + "' to any of the currently open workspace folders. Please open your project in VSCode and try again.");
    messageRequestParams.setType(MessageType.Error);
    var learnMoreAction = new MessageActionItem("Learn more");
    messageRequestParams.setActions(List.of(learnMoreAction));

    underTest.noBindingSuggestionFound(projectKey);
    verify(client).showMessageRequest(messageRequestParams);
    verify(client).browseTo("https://docs.sonarsource.com/sonarlint/vs-code/troubleshooting/#troubleshooting-connected-mode-setup");
  }

  @Test
  void checkServerTrusted() throws ExecutionException, InterruptedException {
    var params = new CheckServerTrustedParams(List.of(new X509CertificateDto(PEM)), "authType");
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
  void checkServerTrustedMalformedCert() throws ExecutionException, InterruptedException {
    var params = new CheckServerTrustedParams(List.of(new X509CertificateDto("malformed")), "authType");
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
      .thenReturn(Optional.of(new ProjectBinding("connectionId", "projectKey", engine, serverIssueTrackerWrapper)));

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
      .thenReturn(Optional.of(new ProjectBinding("connectionId", "projectKey", null, null)));

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
    when(fakeBinding.getConnectionId()).thenReturn("connectionId");
    underTest.setWorkspaceFoldersManager(workspaceFoldersManager);
    when(workspaceFoldersManager.getAll()).thenReturn(List.of(workspaceFolderWrapper));
    var uuid1 = UUID.randomUUID();
    var uuid2 = UUID.randomUUID();

    var fakeBackend = mock(BackendService.class);
    when(backendServiceFacade.getBackendService()).thenReturn(fakeBackend);
    when(fakeBackend.getAllTaints(workspaceFolderPath.toUri().toString()))
      .thenReturn(CompletableFuture.completedFuture(new ListAllResponse(List.of(getTaintDto(uuid1), getTaintDto(uuid2)))));
    when(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile()).thenReturn(Map.of());
    doNothing().when(analysisScheduler).analyzeAllUnboundOpenFiles();

    underTest.didChangeAnalysisReadiness(Set.of(workspaceFolderPath.toUri().toString()), true);

    var captor = ArgumentCaptor.forClass(List.class);

    Thread.sleep(1000);
    verify(taintVulnerabilitiesCache).reload(eq(URIUtils.getFullFileUriFromFragments(workspaceFolderPath.toUri().toString(), filePath)), captor.capture());
    var taintIssues = captor.getValue();
    assertThat(taintIssues).hasSize(2);
    assertThat(((TaintIssue) taintIssues.get(0)).getId()).isEqualTo(uuid1);
    assertThat(((TaintIssue) taintIssues.get(1)).getId()).isEqualTo(uuid2);
    verify(diagnosticPublisher).publishDiagnostics(URIUtils.getFullFileUriFromFragments(workspaceFolderPath.toUri().toString(), filePath), false);
  }


  private TaintVulnerabilityDto getTaintDto(UUID uuid) {
    return new TaintVulnerabilityDto(uuid, "serverKey", false, "ruleKey", "message",
      Path.of("filePath"), Instant.now(), IssueSeverity.MAJOR, RuleType.BUG, List.of(),
      new TextRangeWithHashDto(5, 5, 5, 5, ""), "", CleanCodeAttribute.CONVENTIONAL,
      Map.of(), true);
  }

  private TaintIssue getTaintIssue(UUID uuid) {
    return new TaintIssue(new TaintVulnerabilityDto(uuid, "serverKey", false, "ruleKey", "message",
      Path.of("filePath"), Instant.now(), IssueSeverity.MAJOR, RuleType.BUG, List.of(),
      new TextRangeWithHashDto(5, 5, 5, 5, ""), "", CleanCodeAttribute.CONVENTIONAL,
      Map.of(), true), "folderUri", true);
  }


}
