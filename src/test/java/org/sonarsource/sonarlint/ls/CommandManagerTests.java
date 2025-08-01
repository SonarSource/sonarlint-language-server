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
import java.util.concurrent.CompletionException;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.EffectiveIssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.GetEffectiveIssueDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixChangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsResponse;
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
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.TextEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;
import org.sonarsource.sonarlint.ls.connected.DelegatingHotspot;
import org.sonarsource.sonarlint.ls.connected.DelegatingIssue;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_BROWSE_TAINT_VULNERABILITY;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_QUICK_FIX_APPLIED;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_ISSUE_DETAILS_FROM_CODE_ACTION_COMMAND;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_RULE_DESC_COMMAND;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_SECURITY_HOTSPOT_FLOWS;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SUGGEST_FIX_COMMAND;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SUGGEST_FIX_FROM_CODE_ACTION_COMMAND;
import static org.sonarsource.sonarlint.ls.clientapi.SonarLintVSCodeClient.SONARLINT_SOURCE;
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
    var raisedFinding = mock(RaisedIssueDto.class);
    when(issue.getFinding()).thenReturn(raisedFinding);
    when(raisedFinding.isAiCodeFixable()).thenReturn(false);
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(issue.getFileUri()).thenReturn(URI.create(FILE_URI));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ")))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly("SonarQube: Show issue details for 'XYZ'",
      "SonarQube: Resolve issue violating rule 'XYZ' as...");
  }

  @Test
  void suggestDisableRuleForUnboundProject() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingFinding.class);
    var raisedFinding = mock(RaisedIssueDto.class);
    when(issue.getFinding()).thenReturn(raisedFinding);
    when(raisedFinding.isAiCodeFixable()).thenReturn(false);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsOnly(
        "SonarQube: Show issue details for 'XYZ'",
        "SonarQube: Deactivate rule 'XYZ'");
  }

  @Test
  void showQuickFixFromAnalyzer() {
    var fileUri = URI.create(FILE_URI);
    when(bindingManager.getBinding(fileUri)).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingFinding.class);
    var raisedFinding = mock(RaisedIssueDto.class);
    when(issue.getFinding()).thenReturn(raisedFinding);
    when(raisedFinding.isAiCodeFixable()).thenReturn(false);
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
        "SonarQube: Fix the issue!",
        "SonarQube: Show issue details for 'XYZ'",
        "SonarQube: Deactivate rule 'XYZ'");
  }

  @Test
  void no_AICodeFix_if_issue_has_quickfixes() {
    var fileUri = URI.create(FILE_URI);
    when(bindingManager.getBinding(fileUri)).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingFinding.class);
    var raisedFinding = mock(RaisedIssueDto.class);
    when(issue.getFinding()).thenReturn(raisedFinding);
    when(raisedFinding.isAiCodeFixable()).thenReturn(true);
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
        "SonarQube: Fix the issue!",
        "SonarQube: Show issue details for 'XYZ'",
        "SonarQube: Deactivate rule 'XYZ'");
  }

  @Test
  void should_show_AICodeFix_if_no_quickfixes_and_is_fixable() {
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));
    when(backendService.checkChangeIssueStatusPermitted(any()))
      .thenReturn(CompletableFuture.completedFuture(new CheckStatusChangePermittedResponse(true, null, Collections.emptyList())));
    var connId = "connectionId";
    when(mockBinding.connectionId()).thenReturn(connId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingFinding.class);
    var raisedFinding = mock(RaisedIssueDto.class);
    when(issue.getFinding()).thenReturn(raisedFinding);
    when(raisedFinding.isAiCodeFixable()).thenReturn(true);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());
    when(issue.quickFixes()).thenReturn(List.of());

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsExactly("SonarQube: Resolve issue violating rule 'XYZ' as...",
        "SonarQube: Show issue details for 'XYZ'",
        "SonarQube: ✧˖° Fix with AI CodeFix 'Foo'");
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
    when(issue.getSeverityMode()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));
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
        "SonarQube: Fix the issue!",
        "SonarQube: Show issue details for 'XYZ'",
        "SonarQube: Deactivate rule 'XYZ'");
  }

  @Test
  void suggestShowAllLocationsForIssueWithFlows() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var flow = mock(IssueFlowDto.class);
    var flows = List.of(flow);
    var issue = mock(DelegatingFinding.class);
    var raisedFinding = mock(RaisedIssueDto.class);
    when(issue.getFinding()).thenReturn(raisedFinding);
    when(raisedFinding.isAiCodeFixable()).thenReturn(false);
    when(issue.flows()).thenReturn(flows);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsOnly(
        "SonarQube: Show issue details for 'XYZ'",
        "SonarQube: Deactivate rule 'XYZ'",
        "SonarQube: Show all locations for issue 'XYZ'");
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
    assertThat(actualParam.getLanguageKey()).isEqualTo("js");
    assertThat(actualParam.getHtmlDescription()).isEqualTo("Desc");
  }

  @Test
  void shouldShowRuleDescriptionDuringOpenIssueInIDE() {
    var response = mock(GetEffectiveRuleDetailsResponse.class);
    when(backendService.getEffectiveRuleDetails(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(response));

    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));

    var details = mock(EffectiveRuleDetailsDto.class);
    Either<StandardModeDetails, MQRModeDetails> severityDetails = Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG));
    when(details.getSeverityDetails()).thenReturn(severityDetails);
    when(details.getParams()).thenReturn(emptyList());
    when(details.getKey()).thenReturn(FAKE_RULE_KEY);
    when(details.getLanguage()).thenReturn(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS);
    var desc = mock(RuleMonolithicDescriptionDto.class);
    when(desc.getHtmlContent()).thenReturn("Desc");
    when(details.getDescription()).thenReturn(Either.forLeft(desc));
    when(response.details()).thenReturn(details);
    when(details.getName()).thenReturn("Name");

    underTest.executeCommand(
      new ExecuteCommandParams(SONARLINT_SHOW_RULE_DESC_COMMAND, List.of(new JsonPrimitive(FAKE_RULE_KEY), new JsonPrimitive(FILE_URI))),
      NOP_CANCEL_TOKEN);

    var captor = ArgumentCaptor.forClass(ShowRuleDescriptionParams.class);
    verify(mockClient).showRuleDescription(captor.capture());

    var actualParam = captor.getValue();
    assertThat(actualParam.getKey()).isEqualTo(FAKE_RULE_KEY);
    assertThat(actualParam.getName()).isEqualTo("Name");
    assertThat(actualParam.getType()).isEqualTo(RuleType.BUG.name());
    assertThat(actualParam.getSeverity()).isEqualTo(IssueSeverity.BLOCKER.name());
    assertThat(actualParam.getLanguageKey()).isEqualTo("js");
    assertThat(actualParam.getHtmlDescription()).isEqualTo("Desc");
  }

  @Test
  void browseTaintVulnerability() {
    var issueUrl = "https://some.server.url/project/issues?id=projectKey&issues=id&open=id";
    var issueKey = "id";
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

    underTest.executeCommand(new ExecuteCommandParams(SONARLINT_BROWSE_TAINT_VULNERABILITY, List.of(new JsonPrimitive(issueKey), new JsonPrimitive(FILE_URI))), NOP_CANCEL_TOKEN);

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
    when(issue.getSeverityMode()).thenReturn(Either.forLeft(new StandardModeDetails(IssueSeverity.BLOCKER, RuleType.BUG)));
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
  void showIssueFlows() {
    var issueId = UUID.randomUUID();
    var fileUri = URI.create(FILE_URI);
    var finding = mock(DelegatingFinding.class);
    when(finding.getIssueId()).thenReturn(issueId);
    when(finding.getFileUri()).thenReturn(fileUri);
    when(issuesCache.getIssueById(any(URI.class), eq(issueId.toString())))
      .thenReturn(Optional.of(finding));

    underTest.executeCommand(
      new ExecuteCommandParams(CommandManager.SONARLINT_SHOW_ISSUE_FLOWS, List.of(new JsonPrimitive(issueId.toString()), new JsonPrimitive(FILE_URI))),
      NOP_CANCEL_TOKEN);

    var captor = ArgumentCaptor.forClass(ShowAllLocationsCommand.Param.class);

    verify(mockClient).showIssueOrHotspot(captor.capture());
    assertThat(captor.getValue().getFileUri()).isEqualTo(fileUri);
  }

  @Test
  void showIssueFlows_issueNotFound() {
    var issueId = UUID.randomUUID();
    var fileUri = URI.create(FILE_URI);
    var finding = mock(DelegatingFinding.class);
    when(finding.getIssueId()).thenReturn(issueId);
    when(finding.getFileUri()).thenReturn(fileUri);
    when(issuesCache.getIssueById(any(), any()))
      .thenReturn(Optional.empty());

    underTest.executeCommand(
      new ExecuteCommandParams(CommandManager.SONARLINT_SHOW_ISSUE_FLOWS, List.of(new JsonPrimitive(issueId.toString()), new JsonPrimitive(FILE_URI))),
      NOP_CANCEL_TOKEN);

    verify(mockClient, never()).showIssueOrHotspot(any());
  }

  @Test
  void showIssueDetails_issueNotFound() {
    when(backendService.getEffectiveIssueDetails(any(), any())).thenReturn(CompletableFuture.failedFuture(new CompletionException(new IllegalStateException("Issue not found"))));
    underTest.executeCommand(
      new ExecuteCommandParams(SONARLINT_SHOW_ISSUE_DETAILS_FROM_CODE_ACTION_COMMAND, List.of(new JsonPrimitive(UUID.randomUUID().toString()), new JsonPrimitive(FILE_URI))),
      NOP_CANCEL_TOKEN);

    var captor = ArgumentCaptor.forClass(MessageParams.class);
    verify(mockClient).showMessage(captor.capture());

    var actualParam = captor.getValue();
    assertThat(actualParam.getMessage()).contains("Can't show issue details for unknown issue with key: ");
    assertThat(actualParam.getType()).isEqualTo(MessageType.Error);
  }

  @Test
  void showIssueDetails_ruleNotFound() {
    when(backendService.getEffectiveRuleDetails(any(), any(), any())).thenReturn(CompletableFuture.failedFuture(new CompletionException(new IllegalStateException("Rule not found"))));
    underTest.executeCommand(
      new ExecuteCommandParams(SONARLINT_SHOW_RULE_DESC_COMMAND, List.of(new JsonPrimitive(FAKE_RULE_KEY), new JsonPrimitive(FILE_URI))),
      NOP_CANCEL_TOKEN);

    var captor = ArgumentCaptor.forClass(MessageParams.class);
    verify(mockClient).showMessage(captor.capture());

    var actualParam = captor.getValue();
    assertThat(actualParam.getMessage()).contains("Can't show issue details for unknown rule with key: javascript:S1234");
    assertThat(actualParam.getType()).isEqualTo(MessageType.Error);
  }

  @Test
  void showTaintVulnerabilityFlows_MQR() {
    var issueKey = "someIssueKey";
    var connectionId = "connectionId";
    var issue = mock(TaintIssue.class);
    when(issue.getRuleKey()).thenReturn("ruleKey");
    var filePath = Path.of("path");
    when(issue.getWorkspaceFolderUri()).thenReturn("file:///user/folder");
    when(issue.getIdeFilePath()).thenReturn(filePath);
    when(issue.getIntroductionDate()).thenReturn(Instant.EPOCH);
    when(issue.getSeverityMode()).thenReturn(Either.forRight(new MQRModeDetails(CleanCodeAttribute.TRUSTWORTHY, List.of())));
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

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingIssue.class);
    when(issue.getServerIssueKey()).thenReturn("qwerty");
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());
    var raisedFinding = mock(RaisedIssueDto.class);
    when(issue.getFinding()).thenReturn(raisedFinding);
    when(raisedFinding.isAiCodeFixable()).thenReturn(false);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsExactly(
        "SonarQube: Resolve issue violating rule 'XYZ' as...",
        "SonarQube: Show issue details for 'XYZ'");
  }

  @Test
  void shouldShowFixSuggestionToClient() {
    var issueId = UUID.randomUUID().toString();
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    var suggestFixCommand = new ExecuteCommandParams(SONARLINT_SUGGEST_FIX_FROM_CODE_ACTION_COMMAND, List.of(new JsonPrimitive(folderWrapper.getUri().toString()),
      new JsonPrimitive(issueId), new JsonPrimitive(FILE_URI)));

    var fixChangeDto = new SuggestFixChangeDto(1, 2, "new Code()");

    var fixSuggestionId = UUID.randomUUID();
    when(backendService.suggestFix(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(new SuggestFixResponse(fixSuggestionId, "this is why", List.of(fixChangeDto))));

    underTest.executeCommand(suggestFixCommand, NOP_CANCEL_TOKEN);

    verify(mockClient).startProgressNotification(any(SonarLintExtendedLanguageClient.StartProgressNotificationParams.class));
    verify(mockClient).endProgressNotification(any(SonarLintExtendedLanguageClient.EndProgressNotificationParams.class));
    var captor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ShowFixSuggestionParams.class);
    verify(mockClient).showFixSuggestion(captor.capture());
    SonarLintExtendedLanguageClient.ShowFixSuggestionParams showFixSuggestionParams = captor.getValue();
    assertThat(showFixSuggestionParams.isLocal()).isTrue();
    assertThat(showFixSuggestionParams.suggestionId()).isEqualTo(fixSuggestionId.toString());
    assertThat(showFixSuggestionParams.fileUri()).isEqualTo(FILE_URI);
    assertThat(showFixSuggestionParams.textEdits()).hasSize(1);
    assertThat(showFixSuggestionParams.textEdits().get(0).after()).isEqualTo("new Code()");
  }

  @Test
  void shouldShowTaintFixSuggestionToClient() {
    var issueId = UUID.randomUUID().toString();
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(any())).thenReturn(Optional.of(folderWrapper));
    var suggestTaintFixCommand = new ExecuteCommandParams(SONARLINT_SUGGEST_FIX_COMMAND, List.of(new JsonPrimitive(issueId),
      new JsonPrimitive(FILE_URI)));

    var fixChangeDto = new SuggestFixChangeDto(1, 2, "new Code()");

    var fixSuggestionId = UUID.randomUUID();
    when(backendService.suggestFix(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(new SuggestFixResponse(fixSuggestionId, "this is why", List.of(fixChangeDto))));

    underTest.executeCommand(suggestTaintFixCommand, NOP_CANCEL_TOKEN);

    verify(mockClient).startProgressNotification(any(SonarLintExtendedLanguageClient.StartProgressNotificationParams.class));
    verify(mockClient).endProgressNotification(any(SonarLintExtendedLanguageClient.EndProgressNotificationParams.class));
    var captor = ArgumentCaptor.forClass(SonarLintExtendedLanguageClient.ShowFixSuggestionParams.class);
    verify(mockClient).showFixSuggestion(captor.capture());
    SonarLintExtendedLanguageClient.ShowFixSuggestionParams showFixSuggestionParams = captor.getValue();
    assertThat(showFixSuggestionParams.isLocal()).isTrue();
    assertThat(showFixSuggestionParams.suggestionId()).isEqualTo(fixSuggestionId.toString());
    assertThat(showFixSuggestionParams.fileUri()).isEqualTo(FILE_URI);
    assertThat(showFixSuggestionParams.textEdits()).hasSize(1);
    assertThat(showFixSuggestionParams.textEdits().get(0).after()).isEqualTo("new Code()");
  }

  @Test
  void shouldShowWarningNotificationIfAiCodeFixGenerationFails() {
    var issueId = UUID.randomUUID().toString();
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    var suggestFixCommand = new ExecuteCommandParams(SONARLINT_SUGGEST_FIX_FROM_CODE_ACTION_COMMAND, List.of(new JsonPrimitive(folderWrapper.getUri().toString()),
      new JsonPrimitive(issueId), new JsonPrimitive(FILE_URI)));

    when(backendService.suggestFix(any(), any()))
      .thenReturn(CompletableFuture.failedFuture(
        new ResponseErrorException(
          new ResponseError(
            SonarLintRpcErrorCode.ISSUE_NOT_FOUND,
            "Issue was not found",
            null
          )
        ))
      );

    underTest.executeCommand(suggestFixCommand, NOP_CANCEL_TOKEN);

    verify(mockClient).startProgressNotification(any(SonarLintExtendedLanguageClient.StartProgressNotificationParams.class));
    verify(mockClient).endProgressNotification(any(SonarLintExtendedLanguageClient.EndProgressNotificationParams.class));
    var captor = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
    verify(mockClient).showMessageRequest(captor.capture());
    var showMessageRequestParams = captor.getValue();
    assertThat(showMessageRequestParams.getType()).isEqualTo(MessageType.Warning);
    assertThat(showMessageRequestParams.getMessage()).isEqualTo("Something went wrong while generating AI CodeFix. SonarQube was not able to generate a fix for this issue.");
    assertThat(showMessageRequestParams.getActions()).hasSize(2);
    assertThat(showMessageRequestParams.getActions().get(0).getTitle()).isEqualTo("Show Logs");
    assertThat(showMessageRequestParams.getActions().get(1).getTitle()).isEqualTo("Fix Manually");
  }

  @Test
  void shouldShowShowIssueDetailsIfAiCodeFixGenerationFailsAndUserChoseTheAction() {
    // mock issue details response
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

    var issueId = UUID.randomUUID().toString();
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    var suggestFixCommand = new ExecuteCommandParams(SONARLINT_SUGGEST_FIX_FROM_CODE_ACTION_COMMAND, List.of(new JsonPrimitive(folderWrapper.getUri().toString()),
      new JsonPrimitive(issueId), new JsonPrimitive(FILE_URI)));

    when(mockClient.showMessageRequest(any()))
      .thenReturn(CompletableFuture.completedFuture(new MessageActionItem("Fix Manually")));
    when(backendService.suggestFix(any(), any()))
      .thenReturn(CompletableFuture.failedFuture(
        new ResponseErrorException(
          new ResponseError(
            SonarLintRpcErrorCode.ISSUE_NOT_FOUND,
            "Issue was not found",
            null
          )
        ))
      );

    underTest.executeCommand(suggestFixCommand, NOP_CANCEL_TOKEN);

    verify(mockClient).startProgressNotification(any(SonarLintExtendedLanguageClient.StartProgressNotificationParams.class));
    verify(mockClient).endProgressNotification(any(SonarLintExtendedLanguageClient.EndProgressNotificationParams.class));
    verify(mockClient).showRuleDescription(any(ShowRuleDescriptionParams.class));
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

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingFinding.class);
    var raisedFinding = mock(RaisedIssueDto.class);
    when(issue.getFinding()).thenReturn(raisedFinding);
    when(raisedFinding.isAiCodeFixable()).thenReturn(false);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsExactly(
        "SonarQube: Show issue details for 'XYZ'");
  }

}
