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
package org.sonarsource.sonarlint.ls;

import com.google.gson.JsonPrimitive;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.analysis.api.TextEdit;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.EffectiveIssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.GetEffectiveIssueDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleContextualSectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleContextualSectionWithDefaultContextKeyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleMonolithicDescriptionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleNonContextualSectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleSplitDescriptionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.FileEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.TextEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;
import org.sonarsource.sonarlint.ls.connected.DelegatingHotspot;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.domain.TaintIssue;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;
import testutils.SonarLintLogTester;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_BROWSE_TAINT_VULNERABILITY;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_QUICK_FIX_APPLIED;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_ISSUE_DETAILS_FROM_CODE_ACTION_COMMAND;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_SECURITY_HOTSPOT_FLOWS;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS;
import static org.sonarsource.sonarlint.ls.clientapi.SonarLintVSCodeClient.SONARLINT_SOURCE;
import static org.sonarsource.sonarlint.ls.domain.TaintIssue.SONARCLOUD_TAINT_SOURCE;
import static org.sonarsource.sonarlint.ls.domain.TaintIssue.SONARQUBE_TAINT_SOURCE;
import static org.sonarsource.sonarlint.ls.notebooks.VersionedOpenNotebookTests.createTestNotebookWithThreeCells;

class CommandManagerTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();
  private static final String FAKE_RULE_KEY = "javascript:S1234";
  private static final String FILE_URI = "file://foo.js";
  private static final String CELL_URI = "vscode-notebook-cell:/Users/dda/Documents/jupyterlab-sonarlint/Jupyter%20Demo.ipynb#W2sZmlsZQ%3D%3D";
  private static final TextDocumentIdentifier FAKE_TEXT_DOCUMENT = new TextDocumentIdentifier(FILE_URI);
  private static final TextDocumentIdentifier FAKE_NOTEBOOK_CELL_DOCUMENT = new TextDocumentIdentifier(CELL_URI);
  private static final Range FAKE_RANGE = new Range(new Position(1, 1), new Position(1, 2));
  private static final CancelChecker NOP_CANCEL_TOKEN = () -> {
  };
  private CommandManager underTest;
  private ProjectBindingManager bindingManager;
  private ProjectBinding mockBinding;
  private SonarLintExtendedLanguageClient mockClient;
  private TaintVulnerabilitiesCache mockTaintVulnerabilitiesCache;
  private IssuesCache issuesCache;
  private SettingsManager mockSettingsManager;
  private SonarLintTelemetry mockTelemetry;
  private HotspotsCache securityHotspotsCache;
  private BackendServiceFacade backendServiceFacade;
  private BackendService backendService;
  private WorkspaceFoldersManager workspaceFoldersManager;
  private OpenNotebooksCache openNotebooksCache;

  @BeforeEach
  public void prepareMocks() {
    bindingManager = mock(ProjectBindingManager.class);
    mockSettingsManager = mock(SettingsManager.class);
    mockBinding = mock(ProjectBinding.class);
    when(mockBinding.projectKey()).thenReturn("projectKey");

    mockClient = mock(SonarLintExtendedLanguageClient.class);
    mockTaintVulnerabilitiesCache = mock(TaintVulnerabilitiesCache.class);
    issuesCache = mock(IssuesCache.class);
    mockTelemetry = mock(SonarLintTelemetry.class);
    securityHotspotsCache = mock(HotspotsCache.class);
    backendServiceFacade = mock(BackendServiceFacade.class);
    workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
    openNotebooksCache = mock(OpenNotebooksCache.class);
    backendService = mock(BackendService.class);
    when(backendServiceFacade.getBackendService()).thenReturn(backendService);
    underTest = new CommandManager(mockClient, mockSettingsManager, bindingManager, mockTelemetry,
      mockTaintVulnerabilitiesCache, issuesCache, securityHotspotsCache, backendServiceFacade, workspaceFoldersManager, openNotebooksCache, logTester.getLogger());
  }

  @Test
  void noCodeActionsIfNotSonarLintDiagnostic() {
    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, "not_sonarlint", "XYZ")))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).isEmpty();
  }

  @Test
  void noDisableRuleForBoundProject() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    when(mockBinding.connectionId()).thenReturn("connectionId");
    when(backendService.checkChangeIssueStatusPermitted(any())).thenReturn(CompletableFuture.completedFuture(new CheckStatusChangePermittedResponse(true, null, List.of())));

    var issue = mock(DelegatingFinding.class);
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());
    when(issuesCache.getIssueForDiagnostic(any(URI.class), any())).thenReturn(Optional.of(issue));
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(issue.getFileUri()).thenReturn(URI.create(FILE_URI));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ")))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly("SonarLint: Show issue details for 'XYZ'",
      "SonarLint: Resolve issue violating rule 'XYZ' as...");
  }

  @Test
  void suggestDisableRuleForUnboundProject() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingFinding.class);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsOnly(
        "SonarLint: Show issue details for 'XYZ'",
        "SonarLint: Deactivate rule 'XYZ'");
  }

  @Test
  void showQuickFixFromAnalyzer() {
    var fileUri = URI.create(FILE_URI);
    when(bindingManager.getBinding(fileUri)).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingFinding.class);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());

    var textEdit = mock(TextEditDto.class);
    when(textEdit.newText()).thenReturn("");
    when(textEdit.range()).thenReturn(new TextRangeDto(1, 0, 1, 1));
    var edit = mock(FileEditDto.class);
    when(edit.textEdits()).thenReturn(List.of(textEdit));
    when(edit.target()).thenReturn(fileUri);
    var fix = mock(QuickFixDto.class);
    when(fix.message()).thenReturn("Fix the issue!");
    when(fix.fileEdits()).thenReturn(List.of(edit));
    when(issue.quickFixes()).thenReturn(List.of(fix));

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsExactly(
        "SonarLint: Fix the issue!",
        "SonarLint: Show issue details for 'XYZ'",
        "SonarLint: Deactivate rule 'XYZ'");
  }

  @Test
  void showQuickFixFromAnalyzerForNotebook() {
    var notebookUri = URI.create("file:///Users/dda/Documents/jupyterlab-sonarlint/Jupyter%20Demo.ipynb");
    var fakeNotebook = createTestNotebookWithThreeCells(notebookUri);
    when(bindingManager.getBinding(URI.create(CELL_URI))).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(RaisedFindingDto.class);
    var textEdit = mock(TextEditDto.class);
    when(textEdit.newText()).thenReturn("");
    when(textEdit.range()).thenReturn(new TextRangeDto(1, 0, 1, 1));
    var edit = mock(FileEditDto.class);
    when(edit.textEdits()).thenReturn(List.of(textEdit));
    when(edit.target()).thenReturn(notebookUri);
    var fix = mock(QuickFixDto.class);
    when(fix.message()).thenReturn("Fix the issue!");
    when(fix.fileEdits()).thenReturn(List.of(edit));
    when(issue.getQuickFixes()).thenReturn(List.of(fix));
    var rawIssue = mock(DelegatingFinding.class);
    when(rawIssue.quickFixes()).thenReturn(List.of(fix));
    when(rawIssue.getFinding()).thenReturn(issue);
    when(openNotebooksCache.getFile(notebookUri)).thenReturn(Optional.of(fakeNotebook));
    when(openNotebooksCache.getNotebookUriFromCellUri(URI.create(CELL_URI))).thenReturn(fakeNotebook.getUri());
    when(openNotebooksCache.isKnownCellUri(URI.create(CELL_URI))).thenReturn(true);
    when(issuesCache.getIssueForDiagnostic(fakeNotebook.getUri(), d)).thenReturn(Optional.of(rawIssue));
    when(rawIssue.getIssueId()).thenReturn(UUID.randomUUID());

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_NOTEBOOK_CELL_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsExactly(
        "SonarLint: Fix the issue!",
        "SonarLint: Show issue details for 'XYZ'",
        "SonarLint: Deactivate rule 'XYZ'");
  }

  @ParameterizedTest
  @ValueSource(strings = {SONARQUBE_TAINT_SOURCE, SONARCLOUD_TAINT_SOURCE})
  void codeActionsForTaintWithContext(String taintSource) {
    var connId = "connectionId";
    when(mockBinding.connectionId()).thenReturn(connId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    var mockWorkspacesettings = mock(WorkspaceSettings.class);
    var serverSettings = mock(ServerConnectionSettings.class);
    when(serverSettings.getServerUrl()).thenReturn("https://some.server.url");
    when(mockWorkspacesettings.getServerConnections()).thenReturn(Collections.singletonMap(connId, serverSettings));
    when(mockSettingsManager.getCurrentSettings()).thenReturn(mockWorkspacesettings);
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, taintSource, "ruleKey");

    var issue = mock(TaintIssue.class);
    var issueId = UUID.randomUUID();
    when(issue.getId()).thenReturn(issueId);
    when(issue.getRuleKey()).thenReturn("ruleKey");
    when(issue.getIntroductionDate()).thenReturn(Instant.EPOCH);
    var flow = mock(TaintVulnerabilityDto.FlowDto.class);
    when(issue.getFlows()).thenReturn(List.of(flow));
    var location = mock(TaintVulnerabilityDto.FlowDto.LocationDto.class);
    when(flow.getLocations()).thenReturn(List.of(location));
    when(issue.getRuleKey()).thenReturn("SomeIssueKey");
    when(issue.getRuleDescriptionContextKey()).thenReturn("servlet");
    when(issue.getSonarServerKey()).thenReturn("serverIssueKey");
    when(mockTaintVulnerabilitiesCache.getTaintVulnerabilityForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly(
      "SonarLint: Show issue details for 'ruleKey'",
      "SonarLint: Show all locations for taint vulnerability 'ruleKey'",
      "SonarLint: Open taint vulnerability 'ruleKey' on 'connectionId'",
      "SonarLint: Resolve issue violating rule 'ruleKey' as...");

    assertThat(codeActions.get(0).getRight().getCommand().getArguments()).containsOnly(
      issueId,
      "file://foo.js");
  }


  @Test
  void codeActionsForTaintNoContext() {
    var connId = "connectionId";
    when(mockBinding.connectionId()).thenReturn(connId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    var mockWorkspacesettings = mock(WorkspaceSettings.class);
    var serverSettings = mock(ServerConnectionSettings.class);
    when(serverSettings.getServerUrl()).thenReturn("https://some.server.url");
    when(mockWorkspacesettings.getServerConnections()).thenReturn(Collections.singletonMap(connId, serverSettings));
    when(mockSettingsManager.getCurrentSettings()).thenReturn(mockWorkspacesettings);

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARCLOUD_TAINT_SOURCE, "ruleKey");
    var fakeTaint = mock(TaintIssue.class);
    var taintId = UUID.randomUUID();
    when(fakeTaint.getId()).thenReturn(taintId);
    when(fakeTaint.getSonarServerKey()).thenReturn("serverIssueKey");
    when(fakeTaint.getWorkspaceFolderUri()).thenReturn("file:///my/workspace/folder");
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));

    when(mockTaintVulnerabilitiesCache.getTaintVulnerabilityForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(fakeTaint));

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly(
      "SonarLint: Show issue details for 'ruleKey'",
      "SonarLint: Open taint vulnerability 'ruleKey' on 'connectionId'",
      "SonarLint: Resolve issue violating rule 'ruleKey' as...");

    assertThat(codeActions.get(0).getRight().getCommand().getArguments()).containsOnly(
      taintId,
      "file://foo.js");
    assertThat(codeActions.get(1).getRight().getCommand().getArguments()).containsOnly(
      "https://some.server.url/project/issues?id=projectKey&issues=serverIssueKey&open=serverIssueKey");
  }

  @Test
  void suggestShowAllLocationsForIssueWithFlows() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var flow = mock(IssueFlowDto.class);
    var flows = List.of(flow);
    var issue = mock(DelegatingFinding.class);
    when(issue.flows()).thenReturn(flows);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsOnly(
        "SonarLint: Show issue details for 'XYZ'",
        "SonarLint: Deactivate rule 'XYZ'",
        "SonarLint: Show all locations for issue 'XYZ'");
  }

  @Test
  void openRuleDescriptionForBoundProject() {
    var response = mock(GetEffectiveIssueDetailsResponse.class);
    when(backendService.getEffectiveIssueDetails(anyString(), any())).thenReturn(CompletableFuture.completedFuture(response));
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));
    var details = mock(EffectiveIssueDetailsDto.class);
    when(details.getName()).thenReturn("Name");
    Either<StandardModeDetails, MQRModeDetails> severityDetails = Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG));
    when(details.getSeverityDetails()).thenReturn(severityDetails);
    when(details.getParams()).thenReturn(emptyList());
    when(details.getRuleKey()).thenReturn(FAKE_RULE_KEY);
    when(details.getLanguage()).thenReturn(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS);
    var desc = mock(RuleMonolithicDescriptionDto.class);
    when(desc.getHtmlContent()).thenReturn("Desc");
    when(details.getDescription()).thenReturn(Either.forLeft(desc));
    when(response.getDetails()).thenReturn(details);
    underTest.executeCommand(
      new ExecuteCommandParams(SONARLINT_SHOW_ISSUE_DETAILS_FROM_CODE_ACTION_COMMAND, List.of(new JsonPrimitive(UUID.randomUUID().toString()), new JsonPrimitive(FILE_URI))),
      NOP_CANCEL_TOKEN);

    var captor = ArgumentCaptor.forClass(ShowRuleDescriptionParams.class);
    verify(mockClient).showRuleDescription(captor.capture());

    var actualParam = captor.getValue();
    assertThat(actualParam.getKey()).isEqualTo(FAKE_RULE_KEY);
    assertThat(actualParam.getName()).isEqualTo("Name");
    assertThat(actualParam.getType()).isEqualTo(RuleType.BUG.name());
    assertThat(actualParam.getSeverity()).isEqualTo(IssueSeverity.BLOCKER.name());
    assertThat(actualParam.getLanguageKey()).isEqualTo(SonarLanguage.JS.getSonarLanguageKey());
    assertThat(actualParam.getHtmlDescription()).isEqualTo("Desc");
  }

  @Test
  void browseTaintVulnerability() {
    var issueUrl = "https://some.sq/issue/id";
    underTest.executeCommand(new ExecuteCommandParams(SONARLINT_BROWSE_TAINT_VULNERABILITY, List.of(new JsonPrimitive(issueUrl))), NOP_CANCEL_TOKEN);
    verify(mockTelemetry).taintVulnerabilitiesInvestigatedRemotely();
    verify(mockClient).browseTo(issueUrl);
  }

  @Test
  void showTaintVulnerabilityFlows() {
    var issueKey = "someIssueKey";
    var connectionId = "connectionId";
    var issue = mock(TaintIssue.class);
    when(issue.getRuleKey()).thenReturn("ruleKey");
    var filePath = Path.of("path");
    when(issue.getWorkspaceFolderUri()).thenReturn("file:///user/folder");
    when(issue.getIdeFilePath()).thenReturn(filePath);
    when(issue.getIntroductionDate()).thenReturn(Instant.EPOCH);
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    var flow = mock(TaintVulnerabilityDto.FlowDto.class);
    when(issue.getFlows()).thenReturn(List.of(flow));
    var location = mock(TaintVulnerabilityDto.FlowDto.LocationDto.class);
    when(location.getFilePath()).thenReturn(filePath);
    when(flow.getLocations()).thenReturn(List.of(location));
    when(mockTaintVulnerabilitiesCache.getTaintVulnerabilityByKey(issueKey)).thenReturn(Optional.of(issue));

    underTest.executeCommand(new ExecuteCommandParams(SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS, List.of(new JsonPrimitive(issueKey), new JsonPrimitive(connectionId))),
      NOP_CANCEL_TOKEN);
    verify(mockTaintVulnerabilitiesCache).getTaintVulnerabilityByKey(issueKey);
    verify(mockTelemetry).taintVulnerabilitiesInvestigatedLocally();
  }

  @Test
  void showHotspotFlowsCommandNotFound() {
    var issueKey = "someIssueKey";
    var fileUri = "fileUri";
    underTest.executeCommand(new ExecuteCommandParams(SONARLINT_SHOW_SECURITY_HOTSPOT_FLOWS, List.of(new JsonPrimitive(fileUri), new JsonPrimitive(issueKey))), NOP_CANCEL_TOKEN);

    verify(securityHotspotsCache).get(URI.create("fileUri"));
    verifyNoMoreInteractions(securityHotspotsCache, mockClient);
  }

  @Test
  void showHotspotFlowsCommandSuccess() {
    var issueKey = UUID.randomUUID();
    var fileUri = "fileUri";
    var hotspot = new RaisedHotspotDto(
      issueKey,
      null,
      "rule",
      "ruleName",
      Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)),
      IssueSeverity.BLOCKER,
      RuleType.SECURITY_HOTSPOT,
      CleanCodeAttribute.COMPLETE,
      List.of(new ImpactDto(SoftwareQuality.SECURITY, ImpactSeverity.HIGH)),
      Instant.now(),
      true,
      false,
      null,
      List.of(),
      List.of(),
      null,
      null,
      HotspotStatus.ACKNOWLEDGED
    );

    var delegatingHotspot = new DelegatingHotspot(hotspot, URI.create("fileUri"), HotspotStatus.TO_REVIEW, null);
    when(securityHotspotsCache.get(URI.create("fileUri"))).thenReturn(Map.of(issueKey.toString(), delegatingHotspot));

    underTest.executeCommand(new ExecuteCommandParams(SONARLINT_SHOW_SECURITY_HOTSPOT_FLOWS, List.of(new JsonPrimitive(fileUri), new JsonPrimitive(issueKey.toString()))), NOP_CANCEL_TOKEN);

    verify(securityHotspotsCache).get(URI.create("fileUri"));
    verify(mockClient).showIssueOrHotspot(any());
  }

  @Test
  void getHtmlDescriptionTabsMonolithicShouldReturnNoTabs() {
    var monolithicDesc = new RuleMonolithicDescriptionDto("monolithicHtmlContent");
    var ruleDetails = new EffectiveRuleDetailsDto(null, null, null, Either.forLeft(monolithicDesc), emptyList(), null, null);

    assertThat(CommandManager.getHtmlDescriptionTabs(ruleDetails.getDescription().getLsp4jEither(), "")).isEmpty();
  }

  @Test
  void getHtmlDescriptionTabsSplitNonContextSections() {
    var section1 = new RuleNonContextualSectionDto("nonContextSectionContent1");
    var section2 = new RuleNonContextualSectionDto("nonContextSectionContent2");
    var tab1 = new RuleDescriptionTabDto("title1", Either.forLeft(section1));
    var tab2 = new RuleDescriptionTabDto("title2", Either.forLeft(section2));
    var splitDesc = new RuleSplitDescriptionDto("introHtmlContent", List.of(tab1, tab2));
    var ruleDetails = new EffectiveRuleDetailsDto(null, null, null, Either.forRight(splitDesc), emptyList(), null, null);

    var descriptionTabs = CommandManager.getHtmlDescriptionTabs(ruleDetails.getDescription().getLsp4jEither(), "");

    assertThat(descriptionTabs[0].getTitle()).isEqualTo("title1");
    assertThat(descriptionTabs[1].getTitle()).isEqualTo("title2");
    assertThat(descriptionTabs[1].getDefaultContextKey()).isEmpty();
    assertThat(descriptionTabs[0].getRuleDescriptionTabContextual()).isEmpty();
    assertThat(descriptionTabs[1].getRuleDescriptionTabContextual()).isEmpty();
    assertThat(descriptionTabs[0].hasContextualInformation()).isFalse();
    assertThat(descriptionTabs[1].hasContextualInformation()).isFalse();
    assertThat(descriptionTabs[0].getRuleDescriptionTabNonContextual().getHtmlContent()).isEqualTo("nonContextSectionContent1");
    assertThat(descriptionTabs[1].getRuleDescriptionTabNonContextual().getHtmlContent()).isEqualTo("nonContextSectionContent2");
  }

  @Test
  void getHtmlDescriptionTabsSplitContextSection() {
    var section1 = new RuleContextualSectionDto("sectionContent1", "contextKey1", "name1");
    var section2 = new RuleContextualSectionDto("sectionContent2", "contextKey2", "name2");
    var section3 = new RuleContextualSectionDto("sectionContent3", "contextKey3", "name3");
    var section4 = new RuleContextualSectionDto("sectionContent4", "contextKey4", "name4");
    var section5 = new RuleContextualSectionDto("sectionContent5", "contextKey5", "name5");
    var sectionDto1 = new RuleContextualSectionWithDefaultContextKeyDto("key0", List.of(section1, section2));
    var sectionDto2 = new RuleContextualSectionWithDefaultContextKeyDto("key1", List.of(section3, section4, section5));
    var tab1 = new RuleDescriptionTabDto("title1", Either.forRight(sectionDto1));
    var tab2 = new RuleDescriptionTabDto("title2", Either.forRight(sectionDto2));
    var splitDesc = new RuleSplitDescriptionDto("introHtmlContent", List.of(tab1, tab2));
    var ruleDetails = new EffectiveRuleDetailsDto(null, null, null, Either.forRight(splitDesc), emptyList(), null, null);

    var descriptionTabs = CommandManager.getHtmlDescriptionTabs(ruleDetails.getDescription().getLsp4jEither(), "java");

    assertThat(descriptionTabs[0].getTitle()).isEqualTo("title1");
    assertThat(descriptionTabs[1].getTitle()).isEqualTo("title2");
    assertThat(descriptionTabs[0].getDefaultContextKey()).isEqualTo("java");
    assertThat(descriptionTabs[1].getDefaultContextKey()).isEqualTo("java");
    assertThat(descriptionTabs[0].hasContextualInformation()).isTrue();
    assertThat(descriptionTabs[1].hasContextualInformation()).isTrue();
    assertThat(descriptionTabs[0].getRuleDescriptionTabNonContextual()).isNull();
    assertThat(descriptionTabs[1].getRuleDescriptionTabNonContextual()).isNull();

    assertThat(descriptionTabs[0].getRuleDescriptionTabContextual()).hasSize(2);
    assertThat(descriptionTabs[0].getRuleDescriptionTabContextual()[0].getHtmlContent()).isEqualTo("sectionContent1");
    assertThat(descriptionTabs[0].getRuleDescriptionTabContextual()[0].getContextKey()).isEqualTo("contextKey1");
    assertThat(descriptionTabs[0].getRuleDescriptionTabContextual()[0].getDisplayName()).isEqualTo("name1");

    assertThat(descriptionTabs[1].getRuleDescriptionTabContextual()).hasSize(3);
    assertThat(descriptionTabs[1].getRuleDescriptionTabContextual()[2].getHtmlContent()).isEqualTo("sectionContent5");
    assertThat(descriptionTabs[1].getRuleDescriptionTabContextual()[2].getContextKey()).isEqualTo("contextKey5");
    assertThat(descriptionTabs[1].getRuleDescriptionTabContextual()[2].getDisplayName()).isEqualTo("name5");
  }

  @Test
  void getHtmlDescriptionMonolithic() {
    var monolithicDesc = new RuleMonolithicDescriptionDto("monolithicHtmlContent");
    var ruleDetails = new EffectiveRuleDetailsDto(null, null, null, Either.forLeft(monolithicDesc), emptyList(), null, null);

    assertThat(CommandManager.getHtmlDescription(ruleDetails.getDescription().getLsp4jEither())).isEqualTo("monolithicHtmlContent");
  }

  @Test
  void getHtmlDescriptionSplit() {
    var section1 = new RuleNonContextualSectionDto(null);
    var tab1 = new RuleDescriptionTabDto(null, Either.forLeft(section1));
    var splitDesc = new RuleSplitDescriptionDto("splitHtmlContent", List.of(tab1));
    var ruleDetails = new EffectiveRuleDetailsDto(null, null, null, Either.forRight(splitDesc), emptyList(), null, null);


    assertThat(CommandManager.getHtmlDescription(ruleDetails.getDescription().getLsp4jEither())).isEqualTo("splitHtmlContent");
  }

  @Test
  void quickFixAppliedForRuleShouldTriggerTelemetry() {
    ExecuteCommandParams params = new ExecuteCommandParams();
    params.setCommand(SONARLINT_QUICK_FIX_APPLIED);
    var fakeRule = new JsonPrimitive("javascript:S1234");
    params.setArguments(List.of(fakeRule));

    underTest.executeCommand(params, NOP_CANCEL_TOKEN);
    verify(mockTelemetry).addQuickFixAppliedForRule(fakeRule.getAsString());
  }

  @Test
  void hasResolveIssueActionForBoundProject() {
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));
    when(backendService.checkChangeIssueStatusPermitted(any()))
      .thenReturn(CompletableFuture.completedFuture(new CheckStatusChangePermittedResponse(true, null, Collections.emptyList())));
    var connId = "connectionId";
    when(mockBinding.connectionId()).thenReturn(connId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    var fileUri = URI.create(FILE_URI);

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingFinding.class);
    when(issue.getServerIssueKey()).thenReturn("qwerty");
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));

    var textEdit = mock(TextEdit.class);
    when(textEdit.newText()).thenReturn("");
    when(textEdit.range()).thenReturn(new TextRange(1, 0, 1, 1));
    var edit = mock(ClientInputFileEdit.class);
    when(edit.textEdits()).thenReturn(List.of(textEdit));
    var target = mock(ClientInputFile.class);
    when(target.uri()).thenReturn(fileUri);
    when(edit.target()).thenReturn(target);

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsExactly(
        "SonarLint: Resolve issue violating rule 'XYZ' as...",
        "SonarLint: Show issue details for 'XYZ'");
  }

  @Test
  void doesNotHaveResolveIssueActionWhenIssueStatusChangeNotPermitted() {
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));
    when(backendService.checkChangeIssueStatusPermitted(any()))
      .thenReturn(CompletableFuture.completedFuture(new CheckStatusChangePermittedResponse(false, null, Collections.emptyList())));
    var connId = "connectionId";
    when(mockBinding.connectionId()).thenReturn(connId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    var fileUri = URI.create(FILE_URI);

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingFinding.class);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());

    var textEdit = mock(TextEdit.class);
    when(textEdit.newText()).thenReturn("");
    when(textEdit.range()).thenReturn(new TextRange(1, 0, 1, 1));
    var edit = mock(ClientInputFileEdit.class);
    when(edit.textEdits()).thenReturn(List.of(textEdit));
    var target = mock(ClientInputFile.class);
    when(target.uri()).thenReturn(fileUri);
    when(edit.target()).thenReturn(target);

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsExactly(
        "SonarLint: Show issue details for 'XYZ'");
  }

}
