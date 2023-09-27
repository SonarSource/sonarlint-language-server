/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.event.DidReceiveServerEventParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.X509CertificateDto;
import org.sonarsource.sonarlint.core.clientapi.client.info.GetClientInfoResponse;
import org.sonarsource.sonarlint.core.clientapi.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.common.TextRangeDto;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.ServerIssueTrackerWrapper;
import org.sonarsource.sonarlint.ls.connected.api.RequestsHandlerServer;
import org.sonarsource.sonarlint.ls.connected.events.ServerSentEventsHandlerService;
import org.sonarsource.sonarlint.ls.connected.notifications.SmartNotifications;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SonarLintVSCodeClientTests {
  @TempDir
  Path basedir;
  private Path workspaceFolderPath;
  private Path fileInAWorkspaceFolderPath;
  private final String FILE_PYTHON = "myFile.py";
  SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  SettingsManager settingsManager = mock(SettingsManager.class);
  SmartNotifications smartNotifications = mock(SmartNotifications.class);
  SonarLintVSCodeClient underTest;
  RequestsHandlerServer server = mock(RequestsHandlerServer.class);
  ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  ServerSentEventsHandlerService serverSentEventsHandlerService = mock(ServerSentEventsHandlerService.class);
  @Captor
  ArgumentCaptor<ShowAllLocationsCommand.Param> paramCaptor;
  ProjectBinding binding = mock(ProjectBinding.class);
  ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
  ServerIssueTrackerWrapper serverIssueTrackerWrapper = mock(ServerIssueTrackerWrapper.class);

  private static final String PEM = "subject=CN=localhost,O=SonarSource SA,L=Geneva,ST=Geneva,C=CH\n" +
    "issuer=CN=localhost,O=SonarSource SA,L=Geneva,ST=Geneva,C=CH\n" +
    "-----BEGIN CERTIFICATE-----\n" +
    "MIIFuzCCA6OgAwIBAgIUU0485256+epwnFU4nHFqUbML9LMwDQYJKoZIhvcNAQEL\n" +
    "BQAwXDELMAkGA1UEBhMCQ0gxDzANBgNVBAgMBkdlbmV2YTEPMA0GA1UEBwwGR2Vu\n" +
    "ZXZhMRcwFQYDVQQKDA5Tb25hclNvdXJjZSBTQTESMBAGA1UEAwwJbG9jYWxob3N0\n" +
    "MB4XDTIzMDYyMjEwMDkzNFoXDTMzMDYxOTEwMDkzNFowXDELMAkGA1UEBhMCQ0gx\n" +
    "DzANBgNVBAgMBkdlbmV2YTEPMA0GA1UEBwwGR2VuZXZhMRcwFQYDVQQKDA5Tb25h\n" +
    "clNvdXJjZSBTQTESMBAGA1UEAwwJbG9jYWxob3N0MIICIjANBgkqhkiG9w0BAQEF\n" +
    "AAOCAg8AMIICCgKCAgEAqJ++BMwWh4nywl8vdAoEson8qSYiAL4sUrEn2ytmtCJR\n" +
    "H3TNuTL5C/C1/gD3B9xIRjiR1EaCowLGgzC9blmtOE4aQYfk59U+QcgEjUdjFPX8\n" +
    "IVT4fE/afIkh4c4+sucZktx8PzO/eX0qh51kN/TUt/PyCOl/16FMlMoiWYlE/Yqg\n" +
    "h/Wf15GQClKdhx6Q2VdMAl5pz+wMjzxbE2pzxfSahdr9ZoNm9PntFxJSKcuqLjsz\n" +
    "/Fn3xgmB6QOsCvUz4UN3C7szumpvhA647dA18abZzqzPA74Uco26R9w1YpsXWPnj\n" +
    "aN6E+pC608RYrra0C2wJnMiiEiLQjoxndjQXbODgeUnTUpDJwpDi9c7uhNhfX7oc\n" +
    "0K9BWr59o4LmdX48bezuXJns07ep4dzBtEnpzA4gpH3h7WlRvAXbADW17Kgsz9l5\n" +
    "26phjSOsKnIDp6kpP3Hg4uZBF/0IqgJw8qsfc2k3itLgdK0ODorpl57nZDr0GHKo\n" +
    "UTCnfX9o5mmbanqpKY5S9tRt0a3/3jl9FQtoZtFUvXgU7HJUHVqFNS6EXXh8bAOF\n" +
    "F02VQwbNZVqqtgiIszn3akOmbx3LAr7U5r5OFAnNeRDpTvVXcCzukmT1v0Lny/km\n" +
    "Q2mZhGdzBj0VRh27e591/ZTvqjVH5RS08BqxWwcqKCvSwd/XtfOXJ0E7qSsCZUsC\n" +
    "AwEAAaN1MHMwCwYDVR0PBAQDAgG2MBMGA1UdJQQMMAoGCCsGAQUFBwMBMB0GA1Ud\n" +
    "DgQWBBS2uKvZ9HILvXk2pvy9TmE+HQDIsjAfBgNVHSMEGDAWgBS2uKvZ9HILvXk2\n" +
    "pvy9TmE+HQDIsjAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4ICAQAK\n" +
    "Hh7c06ZKZG2c474xWeFzMTnvx5kM2C+9PtkM0irqxx+PHtz0BTgWP3ZwArEIxppf\n" +
    "qeWjOqBkK5NlrMz/4MiAMhWqfpJVOr7oXUDNpahA8fzRPnet86vqx7btftjuNybi\n" +
    "GQvc1OSWQQeXCiKQku8MTQ7F2Y2vwxE+WxkmBIZdRL+fRTCfwxYQKMR22WkcRyKe\n" +
    "pw7/UVpnEAWi1fZNbO5kyol0zc/iPWZPIhAz6SN3SWff46/2BlwYnQHKYUrqrQEB\n" +
    "l20SgAri3Jqc6mM87VSmhQLambR6scFaH/vGquOdp07SswLnWPltv9V2ShlyXWZX\n" +
    "nb3RFDGhdYSHXJxA4sw1jbMxJGP6Hq9ii/QzeLwLNloV4IVTXliFxI73Bil4RIu4\n" +
    "CiGtl0uy/1D3hoBc/0lVLngcZfnSs23/5sQbg5XAjwHB6O9eVCXWUVfSUzsBgIcL\n" +
    "uD2kQv79yRPBBo+ABCHc68p+dZgSSyQ7aFOU1CMOhkpELGFVzcn2YceIGRd4Dd0l\n" +
    "vrwymIcBDyvzblV+1Hskhm8tLvhHBDYtyYeN5+fHKSq5dIZDeUhpP/VX2oe5Ykab\n" +
    "5u8k3JnweNKqAwFJPPJtTtV1UYr9tRImyoLsGBtQSS0T38r1RJS6etc4MYWv3ASP\n" +
    "C8AByyAgSt1p8KU4tGX74nn+oeCJApZ1o6Qt1JNiSA==\n" +
    "-----END CERTIFICATE-----";

  @BeforeEach
  public void setup() throws IOException {
    underTest = new SonarLintVSCodeClient(client, server);
    underTest.setSmartNotifications(smartNotifications);
    underTest.setSettingsManager(settingsManager);
    underTest.setBindingManager(bindingManager);
    underTest.setServerSentEventsHandlerService(serverSentEventsHandlerService);
    workspaceFolderPath = basedir.resolve("myWorkspaceFolder");
    Files.createDirectories(workspaceFolderPath);
    fileInAWorkspaceFolderPath = workspaceFolderPath.resolve(FILE_PYTHON);
    Files.createFile(fileInAWorkspaceFolderPath);
    Files.write(fileInAWorkspaceFolderPath, ("print('1234')\n" +
      "print('aa')\n" +
      "print('b')\n").getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void openUrlInBrowserTest() {
    var params = new OpenUrlInBrowserParams("url");

    underTest.openUrlInBrowser(params);

    verify(client).browseTo(params.getUrl());
  }

  @Test
  void shouldCallClientToFindFile() {
    var params = mock(FindFileByNamesInScopeParams.class);
    underTest.findFileByNamesInScope(params);
    var expectedClientParams =
      new SonarLintExtendedLanguageClient.FindFileByNamesInFolder(params.getConfigScopeId(), params.getFilenames());
    verify(client).findFileByNamesInFolder(expectedClientParams);
  }

  @Test
  void shouldSuggestBinding() {
    var suggestions = new HashMap<String, List<BindingSuggestionDto>>();
    suggestions.put("key", Collections.emptyList());
    var params = new SuggestBindingParams(suggestions);
    underTest.suggestBinding(params);

    verify(client).suggestBinding(params);
  }

  @Test
  void shouldThrowForShowMessage() {
    assertThrows(UnsupportedOperationException.class, () -> underTest.showMessage(mock(ShowMessageParams.class)));
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
  void shouldReturnEmptyFutureForStartProgress() throws ExecutionException, InterruptedException {
    assertThat(underTest.startProgress(mock(StartProgressParams.class)).get()).isNull();
  }

  @Test
  void shouldUpdateAllTaintIssuesForDidSynchronizeConfigurationScopes() {
    underTest.didSynchronizeConfigurationScopes(mock(DidSynchronizeConfigurationScopeParams.class));

    verify(bindingManager).updateAllTaintIssues();
  }

  @Test
  void shouldAskTheClientToFindFiles() {
    var folderUri = "file:///some/folder";
    var filesToFind = List.of("file1", "file2");
    var params = new FindFileByNamesInScopeParams(folderUri, filesToFind);
    underTest.findFileByNamesInScope(params);
    var argumentCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.FindFileByNamesInFolder.class);
    verify(client).findFileByNamesInFolder(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).extracting(
      SonarLintExtendedLanguageClient.FindFileByNamesInFolder::getFolderUri,
      SonarLintExtendedLanguageClient.FindFileByNamesInFolder::getFilenames
    ).containsExactly(
      folderUri,
      filesToFind.toArray()
    );
  }

  @Test
  void shouldCallServerOnGetHostInfo() {
    underTest.getClientInfo();
    verify(server).getHostInfo();
  }

  @Test
  void shouldGetHostInfo() throws ExecutionException, InterruptedException {
    var desc = "This is Test";
    when(server.getHostInfo()).thenReturn(new GetClientInfoResponse("This is Test"));
    var result = underTest.getClientInfo().get();
    assertThat(result.getDescription()).isEqualTo(desc);
  }

  @Test
  void shouldCallClientShowHotspot() {
    var showHotspotParams = new ShowHotspotParams("myFolder", new HotspotDetailsDto("key1",
      "message1",
      "myfolder/myFile",
      null,
      null,
      "TO_REVIEW",
      "fixed",
      null,
      null
    ));
    underTest.showHotspot(showHotspotParams);
    verify(client).showHotspot(showHotspotParams.getHotspotDetails());
  }

  @Test
  void assistCreateConnectionShouldCallServerMethod() {
    var assistCreatingConnectionParams = new AssistCreatingConnectionParams("http://localhost:9000");
    var future = underTest.assistCreatingConnection(assistCreatingConnectionParams);
    verify(server).showIssueOrHotspotHandleUnknownServer(assistCreatingConnectionParams.getServerUrl());
    assertThat(future).isNotCompleted();
  }

  @Test
  void assistBindingShouldCallServerMethod() {
    var assistBindingParams = new AssistBindingParams("connectionId", "projectKey");
    var future = underTest.assistBinding(assistBindingParams);

    verify(server).showHotspotOrIssueHandleNoBinding(assistBindingParams);
    assertThat(future).isNotCompleted();
  }

  @Test
  void checkServerTrusted() throws ExecutionException, InterruptedException {
    var params = new CheckServerTrustedParams(List.of(new X509CertificateDto(PEM)), "authType");
    when(client.askSslCertificateConfirmation(any())).thenReturn(CompletableFuture.completedFuture(true));

    var response = underTest.checkServerTrusted(params).get();

    var branchCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.SslCertificateConfirmationParams.class);
    verify(client).askSslCertificateConfirmation(branchCaptor.capture());
    var capturedValue = branchCaptor.getValue();
    Assertions.assertThat(capturedValue.getIssuedBy()).isEqualTo("CN=localhost,O=SonarSource SA,L=Geneva,ST=Geneva,C=CH");
    Assertions.assertThat(capturedValue.getIssuedTo()).isEqualTo("CN=localhost,O=SonarSource SA,L=Geneva,ST=Geneva,C=CH");
    Assertions.assertThat(capturedValue.getSha1Fingerprint()).isEqualTo("E9 7B 2D 15 32 3F CA 0D 9B 6A 25 C3 2A 11 73 1C 96 8B FC 73");
    Assertions.assertThat(capturedValue.getSha256Fingerprint()).isEqualTo("35 A0 22 CB CD 8D 57 55 F8 83 B3 CE 63 2A 42 A1\n" +
      "22 81 83 33 BF 2F 9A E7 E9 D7 81 F0 82 2C AD 58");

    assertThat(response.isTrusted()).isTrue();
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

    var response = underTest.checkServerTrusted(params).get();

    var branchCaptor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.SslCertificateConfirmationParams.class);
    verify(client).askSslCertificateConfirmation(branchCaptor.capture());
    var capturedValue = branchCaptor.getValue();
    Assertions.assertThat(capturedValue.getIssuedBy()).isEmpty();
    Assertions.assertThat(capturedValue.getIssuedTo()).isEmpty();
    Assertions.assertThat(capturedValue.getValidFrom()).isEmpty();
    Assertions.assertThat(capturedValue.getValidTo()).isEmpty();
    Assertions.assertThat(capturedValue.getSha1Fingerprint()).isEmpty();
    Assertions.assertThat(capturedValue.getSha256Fingerprint()).isEmpty();

    assertThat(response.isTrusted()).isTrue();
  }

  @Test
  void shouldForwardServerSentEvent() {
    var serverEvent = new IssueChangedEvent("projectKey", List.of("issueKey"), IssueSeverity.MAJOR, RuleType.BUG, false);
    var params = new DidReceiveServerEventParams("connectionId", serverEvent);
    underTest.didReceiveServerEvent(params);

    verify(serverSentEventsHandlerService).handleEvents(serverEvent);
  }

  @Test
  void shouldForwardOpenIssueRequest() {
    var textRangeDto = new TextRangeDto(1, 2, 3, 4);
    var showIssueParams = new ShowIssueParams(textRangeDto, "connectionId", "rule:S1234",
      "issueKey", FILE_PYTHON, "this is wrong", "29.09.2023", "print('ddd')", false, List.of());
    var fileUri = fileInAWorkspaceFolderPath.toUri();

    when(bindingManager.serverPathToFileUri(showIssueParams.getServerRelativeFilePath()))
      .thenReturn(Optional.of(fileUri));
    when(bindingManager.getBinding(fileUri))
      .thenReturn(Optional.of(new ProjectBindingWrapper("connectionId", binding, engine, serverIssueTrackerWrapper)));

    underTest.showIssue(showIssueParams);
    verify(client).showIssue(paramCaptor.capture());

    var showAllLocationParams = paramCaptor.getValue();

    assertEquals(showIssueParams.getFlows().size(), showAllLocationParams.getFlows().size());
    assertEquals("", showAllLocationParams.getSeverity());
    assertEquals(showIssueParams.getMessage(), showAllLocationParams.getMessage());
    assertEquals(showIssueParams.getRuleKey(), showAllLocationParams.getRuleKey());
  }

  @Test
  void shouldNotForwardOpenIssueRequestWhenBindingDoesNotExist() {
    var textRangeDto = new TextRangeDto(1, 2, 3, 4);
    var showIssueParams = new ShowIssueParams(textRangeDto, "connectionId", "rule:S1234",
      "issueKey", FILE_PYTHON, "this is wrong", "29.09.2023", "print('ddd')", false, List.of());
    var fileUri = fileInAWorkspaceFolderPath.toUri();

    when(bindingManager.serverPathToFileUri(showIssueParams.getServerRelativeFilePath()))
      .thenReturn(Optional.of(fileUri));
    when(bindingManager.getBinding(fileUri))
      .thenReturn(Optional.empty());

    underTest.showIssue(showIssueParams);
    verify(client, never()).showIssue(any(ShowAllLocationsCommand.Param.class));
  }
}
