/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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
package org.sonarsource.sonarlint.ls.connected.events;

import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBranches;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.ls.DiagnosticPublisher;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.connected.notifications.TaintVulnerabilityRaisedNotification;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClientProvider;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.util.Utils.textRangeWithHashFromTextRange;

class ServerSentEventsTests {
    @TempDir
    Path basedir;
    private Path workspaceFolderPath;
    private Path fileInAWorkspaceFolderPath;
    ConcurrentMap<URI, Optional<ProjectBindingWrapper>> folderBindingCache;
    private ServerSentEventsHandlerService underTest;
    private final SettingsManager settingsManager = mock(SettingsManager.class);
    private final WorkspaceFoldersManager foldersManager = mock(WorkspaceFoldersManager.class);
    private final EnginesFactory enginesFactory = mock(EnginesFactory.class);
    private final ConnectedSonarLintEngine fakeEngine = mock(ConnectedSonarLintEngine.class);
    SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
    private TaintVulnerabilitiesCache taintVulnerabilitiesCache;
    private final DiagnosticPublisher diagnosticPublisher = mock(DiagnosticPublisher.class);
    private static final String CONNECTION_ID = "myServer";
    private static final ApacheHttpClientProvider httpClientProvider = mock(ApacheHttpClientProvider.class);
    private static final ServerConnectionSettings GLOBAL_SETTINGS = new ServerConnectionSettings(CONNECTION_ID, "http://foo", "token", null, false, httpClientProvider);
    private static final ServerConnectionSettings GLOBAL_SETTINGS_DISABLED_NOTIFICATIONS = new ServerConnectionSettings(CONNECTION_ID, "http://foo", "token", null, true, httpClientProvider);
    private static final String FILE_PHP = "fileInAWorkspaceFolderPath.php";
    private static final String PROJECT_KEY = "myProject";
    private static final String BRANCH_NAME = "main";
    private static final String CURRENT_BRANCH_NAME = "currentBranch";
    private static final String ISSUE_KEY1 = "TEST_ISSUE_KEY1";
    private static final String ISSUE_KEY2 = "TEST_ISSUE_KEY2";
    private static final Instant CREATION_DATE = Instant.now();
    private static final String RULE_KEY = "javasecurity:S3649";
    private static final IssueSeverity ISSUE_SEVERITY = IssueSeverity.BLOCKER;
    private static final IssueSeverity NEW_ISSUE_SEVERITY = IssueSeverity.MINOR;
    private static final RuleType NEW_RULE_TYPE = RuleType.CODE_SMELL;
    private static final RuleType RULE_TYPE = RuleType.VULNERABILITY;
    private static TaintVulnerabilityRaisedEvent.Location MAIN_LOCATION;
    private static final List<TaintVulnerabilityRaisedEvent.Flow> FLOWS = new ArrayList<>();
    ProjectBindingManager projectBindingManager;
    TaintVulnerabilityRaisedNotification taintVulnerabilityRaisedNotification = mock(TaintVulnerabilityRaisedNotification.class);
    WorkspaceFoldersManager workspaceFoldersManager = mock(WorkspaceFoldersManager.class);

    @BeforeEach
    public void prepare() throws IOException, ExecutionException, InterruptedException {
        workspaceFolderPath = basedir.resolve("myWorkspaceFolder");
        Files.createDirectories(workspaceFolderPath);
        fileInAWorkspaceFolderPath = workspaceFolderPath.resolve(FILE_PHP);
        Files.createFile(fileInAWorkspaceFolderPath);

        when(fakeEngine.getServerBranches(any(String.class))).thenReturn(new ProjectBranches(Set.of(BRANCH_NAME), BRANCH_NAME));

        folderBindingCache = new ConcurrentHashMap<>();
        taintVulnerabilitiesCache = new TaintVulnerabilitiesCache();

        projectBindingManager = new ProjectBindingManager(enginesFactory, foldersManager, settingsManager, client, folderBindingCache, null, taintVulnerabilitiesCache, diagnosticPublisher);
        projectBindingManager.setBranchResolver(uri -> Optional.of(BRANCH_NAME));

        underTest = new ServerSentEventsHandler(projectBindingManager, taintVulnerabilitiesCache, taintVulnerabilityRaisedNotification, settingsManager, workspaceFoldersManager);

        MAIN_LOCATION = new TaintVulnerabilityRaisedEvent.Location(fileInAWorkspaceFolderPath.toUri().toString(),
                "Change this code to not construct SQL queries directly from user-controlled data.",
                new TaintVulnerabilityRaisedEvent.Location.TextRange(1, 2, 3, 4, "blablabla"));
    }

    @Test
    void shouldPopulateEmptyCacheOnTaintVulnerabilityRaisedEvent(){
        prepareForServerEventTests();

        ServerTaintIssue existingIssue = new ServerTaintIssue(ISSUE_KEY1, false, RULE_KEY, MAIN_LOCATION.getMessage(),
                fileInAWorkspaceFolderPath.toUri().toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
        when(fakeEngine.getServerTaintIssues(any(ProjectBinding.class), eq(BRANCH_NAME), eq(FILE_PHP))).thenReturn(List.of(existingIssue));
        when(settingsManager.getCurrentSettings()).thenReturn(newWorkspaceSettingsWithServers(Map.of(CONNECTION_ID, GLOBAL_SETTINGS)));

        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).isNull();

        TaintVulnerabilityRaisedEvent fakeEvent = new TaintVulnerabilityRaisedEvent(ISSUE_KEY1, PROJECT_KEY, BRANCH_NAME, CREATION_DATE, RULE_KEY,
                ISSUE_SEVERITY, RULE_TYPE, MAIN_LOCATION, FLOWS);

        underTest.handleEvents(fakeEvent);

        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).hasSize(1);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1)).isNotEmpty();
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1).get().getRuleKey()).isEqualTo(RULE_KEY);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1).get().isResolved()).isFalse();
    }

    @Test
    void shouldUpdateCacheOnTaintVulnerabilityRaisedEvent(){
        prepareForServerEventTests();
        List<ServerTaintIssue> issuesList = new ArrayList<>();

        // there is already 1 issue in cache
        ServerTaintIssue existingIssue = new ServerTaintIssue(ISSUE_KEY1, false, RULE_KEY, MAIN_LOCATION.getMessage(),
                fileInAWorkspaceFolderPath.toUri().toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
        issuesList.add(existingIssue);
        taintVulnerabilitiesCache.reload(fileInAWorkspaceFolderPath.toUri(), issuesList);

        ServerTaintIssue newIssue = new ServerTaintIssue(ISSUE_KEY2, false, RULE_KEY, MAIN_LOCATION.getMessage(),
                fileInAWorkspaceFolderPath.toUri().toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
        issuesList.add(newIssue);

        when(fakeEngine.getServerTaintIssues(any(ProjectBinding.class), eq(BRANCH_NAME), eq(FILE_PHP))).thenReturn(issuesList);
        when(settingsManager.getCurrentSettings()).thenReturn(newWorkspaceSettingsWithServers(Map.of(CONNECTION_ID, GLOBAL_SETTINGS)));

        // Event for new issue is received
        TaintVulnerabilityRaisedEvent fakeEvent = new TaintVulnerabilityRaisedEvent(ISSUE_KEY2, PROJECT_KEY, BRANCH_NAME, CREATION_DATE, RULE_KEY,
                ISSUE_SEVERITY, RULE_TYPE, MAIN_LOCATION, FLOWS);
        underTest.handleEvents(fakeEvent);

        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).hasSize(2);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1)).isNotEmpty();
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY2)).isNotEmpty();
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY2).get().getRuleKey()).isEqualTo(RULE_KEY);
    }

    @Test
    void shouldUpdateCacheOnTaintVulnerabilityClosedEvent(){
        prepareForServerEventTests();
        List<ServerTaintIssue> issuesList = new ArrayList<>();

        // there is already 1 issue in cache
        ServerTaintIssue existingIssue = new ServerTaintIssue(ISSUE_KEY1, false, RULE_KEY, MAIN_LOCATION.getMessage(),
                fileInAWorkspaceFolderPath.toUri().toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
        issuesList.add(existingIssue);
        taintVulnerabilitiesCache.reload(fileInAWorkspaceFolderPath.toUri(), issuesList);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).hasSize(1);

        when(fakeEngine.getServerTaintIssues(any(ProjectBinding.class), eq(BRANCH_NAME), eq(FILE_PHP))).thenReturn(new ArrayList<>());

        TaintVulnerabilityClosedEvent fakeEvent = new TaintVulnerabilityClosedEvent(PROJECT_KEY, ISSUE_KEY1);
        underTest.handleEvents(fakeEvent);

        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).isEmpty();
    }

    @Test
    void shouldDoNothingOnTaintVulnerabilityClosedEventWhenIssueDoesNotExistLocally(){
        prepareForServerEventTests();
        // Initially cache is empty
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).isNull();

        TaintVulnerabilityClosedEvent fakeEvent = new TaintVulnerabilityClosedEvent(PROJECT_KEY, ISSUE_KEY1);
        underTest.handleEvents(fakeEvent);

        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).isNull();
    }

    @Test
    void shouldUpdateExistingTaintIssueOnIssueChangedEvent(){
        prepareForServerEventTests();
        // Initially cache is empty
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).isNull();
        List<ServerTaintIssue> issuesList = new ArrayList<>();

        // there is already 1 issue in cache
        ServerTaintIssue existingIssue = new ServerTaintIssue(ISSUE_KEY1, false, RULE_KEY, MAIN_LOCATION.getMessage(),
                fileInAWorkspaceFolderPath.toUri().toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
        issuesList.add(existingIssue);
        taintVulnerabilitiesCache.reload(fileInAWorkspaceFolderPath.toUri(), issuesList);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).hasSize(1);

        IssueChangedEvent fakeEvent = new IssueChangedEvent(PROJECT_KEY, List.of(ISSUE_KEY1), NEW_ISSUE_SEVERITY, NEW_RULE_TYPE, null);
        existingIssue.setSeverity(NEW_ISSUE_SEVERITY);
        existingIssue.setType(NEW_RULE_TYPE);
        when(fakeEngine.getServerTaintIssues(any(ProjectBinding.class), eq(BRANCH_NAME), eq(FILE_PHP))).thenReturn(issuesList);
        underTest.handleEvents(fakeEvent);

        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).hasSize(1);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1)).isNotEmpty();
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1).get().getType()).isEqualTo(NEW_RULE_TYPE);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1).get().isResolved()).isFalse();
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1).get().getSeverity()).isEqualTo(NEW_ISSUE_SEVERITY);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1).get().getTextRange()).isEqualTo(textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
    }

    @Test
    void shouldUpdateExistingTaintIssueOnIssueChangedEventOnlyWithProvidedField(){
        prepareForServerEventTests();
        // Initially cache is empty
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).isNull();
        List<ServerTaintIssue> issuesList = new ArrayList<>();

        // there is already 1 issue in cache
        ServerTaintIssue existingIssue = new ServerTaintIssue(ISSUE_KEY1, false, RULE_KEY, MAIN_LOCATION.getMessage(),
                fileInAWorkspaceFolderPath.toUri().toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
        issuesList.add(existingIssue);
        taintVulnerabilitiesCache.reload(fileInAWorkspaceFolderPath.toUri(), issuesList);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).hasSize(1);

        IssueChangedEvent fakeEvent = new IssueChangedEvent(PROJECT_KEY, List.of(ISSUE_KEY1), null, null, true);
        existingIssue.setResolved(fakeEvent.getResolved());
        when(fakeEngine.getServerTaintIssues(any(ProjectBinding.class), eq(BRANCH_NAME), eq(FILE_PHP))).thenReturn(issuesList);
        underTest.handleEvents(fakeEvent);

        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).hasSize(1);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1)).isNotEmpty();
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1).get().getType()).isEqualTo(RULE_TYPE);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1).get().isResolved()).isTrue();
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1).get().getSeverity()).isEqualTo(ISSUE_SEVERITY);
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilityByKey(ISSUE_KEY1).get().getTextRange()).isEqualTo(textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
    }

    @Test
    void shouldDoNothingOnTaintIssueOnIssueChangedEventWhenIssueDoesNotExistLocally(){
        // Initially cache is empty
        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).isNull();

        IssueChangedEvent fakeEvent = new IssueChangedEvent(PROJECT_KEY, List.of(ISSUE_KEY1), NEW_ISSUE_SEVERITY, NEW_RULE_TYPE, null);
        underTest.handleEvents(fakeEvent);

        assertThat(taintVulnerabilitiesCache.getTaintVulnerabilitiesPerFile().get(fileInAWorkspaceFolderPath.toUri())).isNull();
    }

    @Test
    void shouldTriggerNotificationOnTaintVulnerabilityRaisedEvent() {
        prepareForServerEventTests();
        List<ServerTaintIssue> issuesList = new ArrayList<>();

        ServerTaintIssue newIssue = new ServerTaintIssue(ISSUE_KEY2, false, RULE_KEY, MAIN_LOCATION.getMessage(),
          fileInAWorkspaceFolderPath.toUri().toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
        issuesList.add(newIssue);

        when(fakeEngine.getServerTaintIssues(any(ProjectBinding.class), eq(BRANCH_NAME), eq(FILE_PHP))).thenReturn(issuesList);
        when(settingsManager.getCurrentSettings()).thenReturn(newWorkspaceSettingsWithServers(Map.of(CONNECTION_ID, GLOBAL_SETTINGS)));
        when(workspaceFoldersManager.findFolderForFile(any(URI.class))).thenReturn(Optional.of(new WorkspaceFolderWrapper(workspaceFolderPath.toUri(), new WorkspaceFolder(workspaceFolderPath.toString()))));

        // Event for new issue is received
        TaintVulnerabilityRaisedEvent fakeEvent = new TaintVulnerabilityRaisedEvent(ISSUE_KEY2, PROJECT_KEY, BRANCH_NAME, CREATION_DATE, RULE_KEY,
          ISSUE_SEVERITY, RULE_TYPE, MAIN_LOCATION, FLOWS);
        underTest.handleEvents(fakeEvent);

        verify(taintVulnerabilityRaisedNotification, times(1)).showTaintVulnerabilityNotification(fakeEvent, CONNECTION_ID, false);
    }

    @Test
    void shouldNotTriggerNotificationOnTaintVulnerabilityRaisedEventWhenNotificationsAreDisabled() {
        prepareForServerEventTests();
        List<ServerTaintIssue> issuesList = new ArrayList<>();

        ServerTaintIssue newIssue = new ServerTaintIssue(ISSUE_KEY2, false, RULE_KEY, MAIN_LOCATION.getMessage(),
          fileInAWorkspaceFolderPath.toUri().toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
        issuesList.add(newIssue);

        when(fakeEngine.getServerTaintIssues(any(ProjectBinding.class), eq(BRANCH_NAME), eq(FILE_PHP))).thenReturn(issuesList);
        when(settingsManager.getCurrentSettings()).thenReturn(newWorkspaceSettingsWithServers(Map.of(CONNECTION_ID, GLOBAL_SETTINGS_DISABLED_NOTIFICATIONS)));
        when(workspaceFoldersManager.findFolderForFile(any(URI.class))).thenReturn(Optional.of(new WorkspaceFolderWrapper(workspaceFolderPath.toUri(), new WorkspaceFolder(workspaceFolderPath.toString()))));

        // Event for new issue is received
        TaintVulnerabilityRaisedEvent fakeEvent = new TaintVulnerabilityRaisedEvent(ISSUE_KEY2, PROJECT_KEY, BRANCH_NAME, CREATION_DATE, RULE_KEY,
          ISSUE_SEVERITY, RULE_TYPE, MAIN_LOCATION, FLOWS);
        underTest.handleEvents(fakeEvent);

        verify(taintVulnerabilityRaisedNotification, never()).showTaintVulnerabilityNotification(fakeEvent, CONNECTION_ID, false);
    }

    @Test
    void shouldNotTriggerNotificationOnTaintVulnerabilityRaisedEventWhenBranchDoesNotMatch() {
        prepareForServerEventTests();
        List<ServerTaintIssue> issuesList = new ArrayList<>();

        ServerTaintIssue newIssue = new ServerTaintIssue(ISSUE_KEY2, false, RULE_KEY, MAIN_LOCATION.getMessage(),
          fileInAWorkspaceFolderPath.toUri().toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
        issuesList.add(newIssue);

        when(fakeEngine.getServerTaintIssues(any(ProjectBinding.class), eq(CURRENT_BRANCH_NAME), eq(FILE_PHP))).thenReturn(issuesList);
        when(settingsManager.getCurrentSettings()).thenReturn(newWorkspaceSettingsWithServers(Map.of(CONNECTION_ID, GLOBAL_SETTINGS_DISABLED_NOTIFICATIONS)));
        when(workspaceFoldersManager.findFolderForFile(any(URI.class))).thenReturn(Optional.of(new WorkspaceFolderWrapper(workspaceFolderPath.toUri(), new WorkspaceFolder(workspaceFolderPath.toString()))));

        // Event for new issue is received
        TaintVulnerabilityRaisedEvent fakeEvent = new TaintVulnerabilityRaisedEvent(ISSUE_KEY2, PROJECT_KEY, CURRENT_BRANCH_NAME, CREATION_DATE, RULE_KEY,
          ISSUE_SEVERITY, RULE_TYPE, MAIN_LOCATION, FLOWS);
        underTest.handleEvents(fakeEvent);

        verify(taintVulnerabilityRaisedNotification, never()).showTaintVulnerabilityNotification(fakeEvent, CONNECTION_ID, false);
    }

    @Test
    void shouldNotTriggerNotificationOnTaintVulnerabilityRaisedEventWhenConnectionDoesNotExist() {
        prepareForServerEventTests();
        List<ServerTaintIssue> issuesList = new ArrayList<>();

        ServerTaintIssue newIssue = new ServerTaintIssue(ISSUE_KEY2, false, RULE_KEY, MAIN_LOCATION.getMessage(),
          fileInAWorkspaceFolderPath.toUri().toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
        issuesList.add(newIssue);

        when(fakeEngine.getServerTaintIssues(any(ProjectBinding.class), eq(CURRENT_BRANCH_NAME), eq(FILE_PHP))).thenReturn(issuesList);
        when(settingsManager.getCurrentSettings()).thenReturn(newWorkspaceSettingsWithServers(Collections.emptyMap()));
        when(workspaceFoldersManager.findFolderForFile(any(URI.class))).thenReturn(Optional.of(new WorkspaceFolderWrapper(workspaceFolderPath.toUri(), new WorkspaceFolder(workspaceFolderPath.toString()))));

        // Event for new issue is received
        TaintVulnerabilityRaisedEvent fakeEvent = new TaintVulnerabilityRaisedEvent(ISSUE_KEY2, PROJECT_KEY, CURRENT_BRANCH_NAME, CREATION_DATE, RULE_KEY,
          ISSUE_SEVERITY, RULE_TYPE, MAIN_LOCATION, FLOWS);
        underTest.handleEvents(fakeEvent);

        verify(taintVulnerabilityRaisedNotification, never()).showTaintVulnerabilityNotification(fakeEvent, CONNECTION_ID, false);
    }

    @Test
    void shouldNotTriggerNotificationOnTaintVulnerabilityRaisedEventWhenBindingDoesNotExist() {
        prepareForServerEventTests();
        var projectBindingWrapperMock = mock(ProjectBindingWrapper.class);
        when(projectBindingWrapperMock.getBinding()).thenReturn(null);

        List<ServerTaintIssue> issuesList = new ArrayList<>();

        ServerTaintIssue newIssue = new ServerTaintIssue(ISSUE_KEY2, false, RULE_KEY, MAIN_LOCATION.getMessage(),
          fileInAWorkspaceFolderPath.toUri().toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION.getTextRange()));
        issuesList.add(newIssue);

        when(fakeEngine.getServerTaintIssues(any(ProjectBinding.class), eq(CURRENT_BRANCH_NAME), eq(FILE_PHP))).thenReturn(issuesList);
        when(settingsManager.getCurrentSettings()).thenReturn(newWorkspaceSettingsWithServers(Collections.emptyMap()));
        when(workspaceFoldersManager.findFolderForFile(any(URI.class))).thenReturn(Optional.of(new WorkspaceFolderWrapper(workspaceFolderPath.toUri(), new WorkspaceFolder(workspaceFolderPath.toString()))));

        // Event for new issue is received
        TaintVulnerabilityRaisedEvent fakeEvent = new TaintVulnerabilityRaisedEvent(ISSUE_KEY2, PROJECT_KEY, CURRENT_BRANCH_NAME, CREATION_DATE, RULE_KEY,
          ISSUE_SEVERITY, RULE_TYPE, MAIN_LOCATION, FLOWS);
        underTest.handleEvents(fakeEvent);

        verify(taintVulnerabilityRaisedNotification, never()).showTaintVulnerabilityNotification(fakeEvent, CONNECTION_ID, false);
    }

    private void prepareForServerEventTests(){
        mockFileInAFolder();
        var projectBindingWrapperMock = mock(ProjectBindingWrapper.class);
        var projectBinding = mock(ProjectBinding.class);
        when(projectBinding.projectKey()).thenReturn(PROJECT_KEY);
        when(projectBindingWrapperMock.getBinding()).thenReturn(projectBinding);
        folderBindingCache.put(workspaceFolderPath.toUri(), Optional.of(projectBindingWrapperMock));
        when(projectBindingWrapperMock.getEngine()).thenReturn(fakeEngine);
        when(projectBinding.serverPathToIdePath(fileInAWorkspaceFolderPath.toUri().toString())).thenReturn(Optional.of(fileInAWorkspaceFolderPath.toString()));
        when(projectBindingWrapperMock.getConnectionId()).thenReturn(CONNECTION_ID);
    }

    private static WorkspaceSettings newWorkspaceSettingsWithServers(Map<String, ServerConnectionSettings> servers) {
        return new WorkspaceSettings(false, servers, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false, false, "");
    }

    private void mockFileInAFolder() {
        var folderWrapper = spy(new WorkspaceFolderWrapper(workspaceFolderPath.toUri(), new WorkspaceFolder(workspaceFolderPath.toUri().toString())));
        when(foldersManager.findFolderForFile(fileInAWorkspaceFolderPath.toUri())).thenReturn(Optional.of(folderWrapper));
    }
}
