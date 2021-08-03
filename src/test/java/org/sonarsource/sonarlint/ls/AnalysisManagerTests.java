/*
 * SonarLint Language Server
 * Copyright (C) 2009-2021 SonarSource SA
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
package org.sonarsource.sonarlint.ls;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.client.api.common.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssueLocation;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.file.FileLanguageCache;
import org.sonarsource.sonarlint.ls.file.FileTypeClassifier;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.java.JavaConfigCache;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceFolderSettings;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.AnalysisManager.convert;

class AnalysisManagerTests {

  AnalysisManager underTest;
  Map<URI, List<ServerIssue>> taintVulnerabilitiesPerFile;
  private EnginesFactory enginesFactory;
  private WorkspaceFoldersManager foldersManager;
  private StandaloneEngineManager standaloneEngineManager;
  private SonarLintExtendedLanguageClient languageClient;

  @BeforeEach
  void prepare() {
    taintVulnerabilitiesPerFile = new ConcurrentHashMap<>();
    FileLanguageCache fileLanguageCache = new FileLanguageCache();
    enginesFactory = mock(EnginesFactory.class);
    foldersManager = mock(WorkspaceFoldersManager.class);
    standaloneEngineManager = mock(StandaloneEngineManager.class);
    languageClient = mock(SonarLintExtendedLanguageClient.class);
    underTest = new AnalysisManager(mock(LanguageClientLogOutput.class), standaloneEngineManager, languageClient, mock(SonarLintTelemetry.class),
      foldersManager, mock(SettingsManager.class), mock(ProjectBindingManager.class), new FileTypeClassifier(fileLanguageCache), fileLanguageCache, mock(JavaConfigCache.class), taintVulnerabilitiesPerFile);

  }

  @Test
  void testNotConvertGlobalIssues() {
    Issue issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(null);
    assertThat(convert(entry("id", issue))).isEmpty();
  }

  @Test
  void testNotConvertSeverity() {
    String id = "id";
    Issue issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(1);
    when(issue.getSeverity()).thenReturn("BLOCKER");
    when(issue.getMessage()).thenReturn("Do this, don't do that");
    assertThat(convert(entry(id, issue)).get().getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn("CRITICAL");
    assertThat(convert(entry(id, issue)).get().getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn("MAJOR");
    assertThat(convert(entry(id, issue)).get().getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn("MINOR");
    assertThat(convert(entry(id, issue)).get().getSeverity()).isEqualTo(DiagnosticSeverity.Information);
    when(issue.getSeverity()).thenReturn("INFO");
    assertThat(convert(entry(id, issue)).get().getSeverity()).isEqualTo(DiagnosticSeverity.Hint);
  }

  @Test
  void testIssueConversion() {
    ServerIssue issue = mock(ServerIssue.class);
    ServerIssue.Flow flow = mock(ServerIssue.Flow.class);
    ServerIssueLocation loc1 = mock(ServerIssueLocation.class);
    ServerIssueLocation loc2 = mock(ServerIssueLocation.class);
    when(flow.locations()).thenReturn(Arrays.asList(loc1, loc2));
    when(issue.getStartLine()).thenReturn(1);
    when(issue.severity()).thenReturn("BLOCKER");
    when(issue.ruleKey()).thenReturn("ruleKey");
    when(issue.getMessage()).thenReturn("message");
    when(issue.key()).thenReturn("issueKey");
    when(issue.getFlows()).thenReturn(Collections.singletonList(flow));

    Diagnostic diagnostic = convert(issue).get();

    assertThat(diagnostic.getMessage()).isEqualTo("message [+2 locations]");
    assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    assertThat(diagnostic.getSource()).isEqualTo("SonarQube Taint Analyzer");
    assertThat(diagnostic.getCode().getLeft()).isEqualTo("ruleKey");
    assertThat(diagnostic.getData()).isEqualTo("issueKey");
  }

  @Test
  void testGetServerIssueForDiagnosticBasedOnLocation() throws Exception {
    URI uri = new URI("/");
    ServerIssue issue = mock(ServerIssue.class);
    when(issue.getStartLine()).thenReturn(228);
    when(issue.getStartLineOffset()).thenReturn(14);
    when(issue.getEndLine()).thenReturn(322);
    when(issue.getEndLineOffset()).thenReturn(14);

    Diagnostic diagnostic = mock(Diagnostic.class);
    when(diagnostic.getCode()).thenReturn(Either.forLeft("ruleKey"));
    Range range = new Range(new Position(227, 14), new Position(321, 14));
    when(diagnostic.getRange()).thenReturn(range);
    when(issue.ruleKey()).thenReturn("ruleKey");
    taintVulnerabilitiesPerFile.put(uri, Collections.singletonList(issue));

    assertThat(underTest.getTaintVulnerabilityForDiagnostic(uri, diagnostic)).hasValue(issue);
  }

  @Test
  void testGetServerIssueForDiagnosticBasedOnKey() throws Exception {
    URI uri = new URI("/");
    ServerIssue issue = mock(ServerIssue.class);
    when(issue.key()).thenReturn("issueKey");

    Diagnostic diagnostic = mock(Diagnostic.class);
    when(diagnostic.getData()).thenReturn("issueKey");
    taintVulnerabilitiesPerFile.put(uri, Collections.singletonList(issue));

    assertThat(underTest.getTaintVulnerabilityForDiagnostic(uri, diagnostic)).hasValue(issue);
  }

  @Test
  void testGetServerIssueForDiagnosticNotFound() throws Exception {
    URI uri = new URI("/");
    ServerIssue issue = mock(ServerIssue.class);
    when(issue.ruleKey()).thenReturn("someRuleKey");
    when(issue.key()).thenReturn("issueKey");

    Diagnostic diagnostic = mock(Diagnostic.class);
    when(diagnostic.getData()).thenReturn("anotherKey");
    when(diagnostic.getCode()).thenReturn(Either.forLeft("anotherRuleKey"));
    taintVulnerabilitiesPerFile.put(uri, Collections.singletonList(issue));

    assertThat(underTest.getTaintVulnerabilityForDiagnostic(uri, diagnostic)).isEmpty();
  }

  @Test
  void testGetServerIssueByKey() throws Exception {
    URI uri = new URI("/");
    ServerIssue issue = mock(ServerIssue.class);
    String issueKey = "key";
    when(issue.key()).thenReturn(issueKey);

    taintVulnerabilitiesPerFile.put(uri, Collections.singletonList(issue));

    assertThat(underTest.getTaintVulnerabilityByKey(issueKey)).hasValue(issue);
    assertThat(underTest.getTaintVulnerabilityByKey("otherKey")).isEmpty();
  }

  @Test
  void dontForwardFileEventToEngineWhenOutsideOfFolder() {
    StandaloneSonarLintEngine sonarLintEngine = mock(StandaloneSonarLintEngine.class);
    when(enginesFactory.createStandaloneEngine()).thenReturn(sonarLintEngine);

    underTest.didChangeWatchedFiles(Collections.singletonList(new FileEvent("uri", FileChangeType.Created)));

    verifyZeroInteractions(sonarLintEngine);
  }

  @Test
  void forwardFileCreatedEventToEngineWhenInsideOfFolder() {
    ArgumentCaptor<ClientModuleFileEvent> fileEventArgumentCaptor = ArgumentCaptor.forClass(ClientModuleFileEvent.class);
    final URI folderURI = URI.create("file:///folder");
    StandaloneSonarLintEngine sonarLintEngine = mock(StandaloneSonarLintEngine.class);
    when(enginesFactory.createStandaloneEngine()).thenReturn(sonarLintEngine);
    WorkspaceFolderWrapper folder = new WorkspaceFolderWrapper(folderURI, new WorkspaceFolder(folderURI.toString(), "folder"));
    folder.setSettings(new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null));
    when(foldersManager.findFolderForFile(any())).thenReturn(Optional.of(folder));
    doNothing().when(sonarLintEngine).fireModuleFileEvent(any(), any());
    when(standaloneEngineManager.getOrCreateStandaloneEngine()).thenReturn(sonarLintEngine);

    underTest.didChangeWatchedFiles(Collections.singletonList(new FileEvent("file:///folder/file.py", FileChangeType.Created)));

    verify(sonarLintEngine).fireModuleFileEvent(eq(folderURI), fileEventArgumentCaptor.capture());
    ClientModuleFileEvent fileEvent = fileEventArgumentCaptor.getValue();
    assertThat(fileEvent.type()).isEqualTo(ModuleFileEvent.Type.CREATED);
  }

  @Test
  void forwardFileModifiedEventToEngineWhenInsideOfFolder() {
    ArgumentCaptor<ClientModuleFileEvent> fileEventArgumentCaptor = ArgumentCaptor.forClass(ClientModuleFileEvent.class);
    final URI folderURI = URI.create("file:///folder");
    StandaloneSonarLintEngine sonarLintEngine = mock(StandaloneSonarLintEngine.class);
    when(enginesFactory.createStandaloneEngine()).thenReturn(sonarLintEngine);
    WorkspaceFolderWrapper folder = new WorkspaceFolderWrapper(folderURI, new WorkspaceFolder(folderURI.toString(), "folder"));
    folder.setSettings(new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null));
    when(foldersManager.findFolderForFile(any())).thenReturn(Optional.of(folder));
    doNothing().when(sonarLintEngine).fireModuleFileEvent(any(), any());
    when(standaloneEngineManager.getOrCreateStandaloneEngine()).thenReturn(sonarLintEngine);

    underTest.didChangeWatchedFiles(Collections.singletonList(new FileEvent("file:///folder/file.py", FileChangeType.Changed)));

    verify(sonarLintEngine).fireModuleFileEvent(eq(folderURI), fileEventArgumentCaptor.capture());
    ClientModuleFileEvent fileEvent = fileEventArgumentCaptor.getValue();
    assertThat(fileEvent.type()).isEqualTo(ModuleFileEvent.Type.MODIFIED);
  }

  @Test
  void forwardFileDeletedEventToEngineWhenInsideOfFolder() {
    ArgumentCaptor<ClientModuleFileEvent> fileEventArgumentCaptor = ArgumentCaptor.forClass(ClientModuleFileEvent.class);
    final URI folderURI = URI.create("file:///folder");
    StandaloneSonarLintEngine sonarLintEngine = mock(StandaloneSonarLintEngine.class);
    when(enginesFactory.createStandaloneEngine()).thenReturn(sonarLintEngine);
    WorkspaceFolderWrapper folder = new WorkspaceFolderWrapper(folderURI, new WorkspaceFolder(folderURI.toString(), "folder"));
    folder.setSettings(new WorkspaceFolderSettings(null, null, Collections.emptyMap(), null));
    when(foldersManager.findFolderForFile(any())).thenReturn(Optional.of(folder));
    doNothing().when(sonarLintEngine).fireModuleFileEvent(any(), any());
    when(standaloneEngineManager.getOrCreateStandaloneEngine()).thenReturn(sonarLintEngine);

    underTest.didChangeWatchedFiles(Collections.singletonList(new FileEvent("file:///folder/file.py", FileChangeType.Deleted)));

    verify(sonarLintEngine).fireModuleFileEvent(eq(folderURI), fileEventArgumentCaptor.capture());
    ClientModuleFileEvent fileEvent = fileEventArgumentCaptor.getValue();
    assertThat(fileEvent.type()).isEqualTo(ModuleFileEvent.Type.DELETED);
  }

  @Test
  void showFirstSecretDetectedNotification() {
    Issue issue = mock(Issue.class);
    when(issue.getRuleKey()).thenReturn("secrets:123");

    underTest.showFirstSecretDetectionNotificationIfNeeded(issue);

    verify(languageClient).showFirstSecretDetectionNotification();
    verifyNoMoreInteractions(languageClient);
  }
}
