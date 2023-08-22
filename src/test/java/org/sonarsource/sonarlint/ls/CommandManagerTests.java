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
package org.sonarsource.sonarlint.ls;

import com.google.gson.JsonPrimitive;
import java.net.URI;
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
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.analysis.api.TextEdit;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleContextualSectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleContextualSectionWithDefaultContextKeyDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleMonolithicDescriptionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleNonContextualSectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleSplitDescriptionDto;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.ls.IssuesCache.VersionedIssue;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.DelegatingIssue;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.connected.domain.TaintIssue;
import org.sonarsource.sonarlint.ls.connected.sync.ServerSynchronizer;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.AnalysisScheduler.SONARCLOUD_TAINT_SOURCE;
import static org.sonarsource.sonarlint.ls.AnalysisScheduler.SONARLINT_SOURCE;
import static org.sonarsource.sonarlint.ls.AnalysisScheduler.SONARQUBE_TAINT_SOURCE;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_BROWSE_TAINT_VULNERABILITY;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_QUICK_FIX_APPLIED;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_SECURITY_HOTSPOT_FLOWS;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_UPDATE_ALL_BINDINGS_COMMAND;
import static org.sonarsource.sonarlint.ls.notebooks.VersionedOpenNotebookTests.createTestNotebookWithThreeCells;

class CommandManagerTests {

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
  private ProjectBindingWrapper mockBinding;
  private ConnectedSonarLintEngine mockConnectedEngine;
  private SonarLintExtendedLanguageClient mockClient;
  private TaintVulnerabilitiesCache mockTaintVulnerabilitiesCache;
  private IssuesCache issuesCache;
  private SettingsManager mockSettingsManager;
  private SonarLintTelemetry mockTelemetry;
  private ServerSynchronizer serverSynchronizer;
  private IssuesCache securityHotspotsCache;
  private BackendServiceFacade backendServiceFacade;
  private WorkspaceFoldersManager workspaceFoldersManager;
  private OpenNotebooksCache openNotebooksCache;

  @BeforeEach
  public void prepareMocks() {
    bindingManager = mock(ProjectBindingManager.class);
    mockSettingsManager = mock(SettingsManager.class);
    mockBinding = mock(ProjectBindingWrapper.class);
    mockConnectedEngine = mock(ConnectedSonarLintEngine.class);
    when(mockBinding.getEngine()).thenReturn(mockConnectedEngine);
    when(mockBinding.getBinding()).thenReturn(new ProjectBinding("projectKey", "sqPathPrefix", "idePathPrefix"));

    mockClient = mock(SonarLintExtendedLanguageClient.class);
    mockTaintVulnerabilitiesCache = mock(TaintVulnerabilitiesCache.class);
    issuesCache = mock(IssuesCache.class);
    mockTelemetry = mock(SonarLintTelemetry.class);
    serverSynchronizer = mock(ServerSynchronizer.class);
    securityHotspotsCache = mock(IssuesCache.class);
    backendServiceFacade = mock(BackendServiceFacade.class);
    workspaceFoldersManager = mock(WorkspaceFoldersManager.class);
    openNotebooksCache = mock(OpenNotebooksCache.class);

    underTest = new CommandManager(mockClient, mockSettingsManager, bindingManager, serverSynchronizer, mockTelemetry,
      mockTaintVulnerabilitiesCache, issuesCache, securityHotspotsCache, backendServiceFacade, workspaceFoldersManager, openNotebooksCache);
  }

  @Test
  void updateAllBinding() {
    underTest.executeCommand(new ExecuteCommandParams(SONARLINT_UPDATE_ALL_BINDINGS_COMMAND, emptyList()), NOP_CANCEL_TOKEN);

    verify(serverSynchronizer).updateAllBindings(NOP_CANCEL_TOKEN, null);
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

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ")))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly("SonarLint: Open description of rule 'XYZ'");
  }

  @Test
  void suggestDisableRuleForUnboundProject() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(Issue.class);
    var versionedIssue = new VersionedIssue(issue, 1);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(versionedIssue));

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsOnly(
        "SonarLint: Open description of rule 'XYZ'",
        "SonarLint: Deactivate rule 'XYZ'");
  }

  @Test
  void showQuickFixFromAnalyzer() {
    var fileUri = URI.create(FILE_URI);
    when(bindingManager.getBinding(fileUri)).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(Issue.class);
    var versionedIssue = new VersionedIssue(issue, 1);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(versionedIssue));

    var textEdit = mock(TextEdit.class);
    when(textEdit.newText()).thenReturn("");
    when(textEdit.range()).thenReturn(new TextRange(1, 0, 1, 1));
    var edit = mock(ClientInputFileEdit.class);
    when(edit.textEdits()).thenReturn(List.of(textEdit));
    var target = mock(ClientInputFile.class);
    when(target.uri()).thenReturn(fileUri);
    when(edit.target()).thenReturn(target);
    var fix = mock(QuickFix.class);
    when(fix.message()).thenReturn("Fix the issue!");
    when(fix.inputFileEdits()).thenReturn(List.of(edit));
    when(issue.quickFixes()).thenReturn(List.of(fix));

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsExactly(
        "SonarLint: Fix the issue!",
        "SonarLint: Open description of rule 'XYZ'",
        "SonarLint: Deactivate rule 'XYZ'");
  }

  @Test
  void showQuickFixFromAnalyzerForNotebook() {
    var notebookUri = URI.create("file:///Users/dda/Documents/jupyterlab-sonarlint/Jupyter%20Demo.ipynb");
    var fakeNotebook = createTestNotebookWithThreeCells(notebookUri);
    when(bindingManager.getBinding(URI.create(CELL_URI))).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(Issue.class);
    var textEdit = mock(TextEdit.class);
    when(textEdit.newText()).thenReturn("");
    when(textEdit.range()).thenReturn(new TextRange(1, 0, 1, 1));
    var edit = mock(ClientInputFileEdit.class);
    when(edit.textEdits()).thenReturn(List.of(textEdit));
    var target = mock(ClientInputFile.class);
    when(target.uri()).thenReturn(notebookUri);
    when(edit.target()).thenReturn(target);
    var fix = mock(QuickFix.class);
    when(fix.message()).thenReturn("Fix the issue!");
    when(fix.inputFileEdits()).thenReturn(List.of(edit));
    when(issue.quickFixes()).thenReturn(List.of(fix));
    var versionedIssue = new VersionedIssue(issue, 1);
    when(openNotebooksCache.getFile(notebookUri)).thenReturn(Optional.of(fakeNotebook));
    when(openNotebooksCache.getNotebookUriFromCellUri(URI.create(CELL_URI))).thenReturn(fakeNotebook.getUri());
    when(openNotebooksCache.isKnownCellUri(URI.create(CELL_URI))).thenReturn(true);
    when(issuesCache.getIssueForDiagnostic(fakeNotebook.getUri(), d)).thenReturn(Optional.of(versionedIssue));

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_NOTEBOOK_CELL_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsExactly(
        "SonarLint: Fix the issue!",
        "SonarLint: Open description of rule 'XYZ'",
        "SonarLint: Deactivate rule 'XYZ'");
  }

  @ParameterizedTest
  @ValueSource(strings = {SONARQUBE_TAINT_SOURCE, SONARCLOUD_TAINT_SOURCE})
  void codeActionsForTaintWithContext(String taintSource) {
    var connId = "connectionId";
    when(mockBinding.getConnectionId()).thenReturn(connId);
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
    when(issue.getRuleKey()).thenReturn("ruleKey");
    when(issue.getCreationDate()).thenReturn(Instant.EPOCH);
    var flow = mock(ServerTaintIssue.Flow.class);
    when(issue.getFlows()).thenReturn(List.of(flow));
    var location = mock(ServerTaintIssue.ServerIssueLocation.class);
    when(flow.locations()).thenReturn(List.of(location));
    when(issue.getKey()).thenReturn("SomeIssueKey");
    when(issue.getRuleDescriptionContextKey()).thenReturn("servlet");
    when(mockTaintVulnerabilitiesCache.getTaintVulnerabilityForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly(
      "SonarLint: Open description of rule 'ruleKey'",
      "SonarLint: Show all locations for taint vulnerability 'ruleKey'",
      "SonarLint: Open taint vulnerability 'ruleKey' on 'connectionId'",
      "SonarLint: Resolve issue violating rule 'ruleKey' as...");

    assertThat(codeActions.get(0).getRight().getCommand().getArguments()).containsOnly(
      "ruleKey",
      "file://foo.js",
      "servlet");
  }


  @Test
  void codeActionsForTaintNoContext() {
    var connId = "connectionId";
    when(mockBinding.getConnectionId()).thenReturn(connId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    var mockWorkspacesettings = mock(WorkspaceSettings.class);
    var serverSettings = mock(ServerConnectionSettings.class);
    when(serverSettings.getServerUrl()).thenReturn("https://some.server.url");
    when(mockWorkspacesettings.getServerConnections()).thenReturn(Collections.singletonMap(connId, serverSettings));
    when(mockSettingsManager.getCurrentSettings()).thenReturn(mockWorkspacesettings);

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARCLOUD_TAINT_SOURCE, "ruleKey");

    when(mockTaintVulnerabilitiesCache.getTaintVulnerabilityForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.empty());

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly(
      "SonarLint: Open description of rule 'ruleKey'");

    assertThat(codeActions.get(0).getRight().getCommand().getArguments()).containsOnly(
      "ruleKey",
      "file://foo.js",
      "");
  }

  @Test
  void suggestShowAllLocationsForIssueWithFlows() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var flow = mock(Flow.class);
    var flows = List.of(flow);
    var issue = mock(Issue.class);
    var versionedIssue = new VersionedIssue(issue, 1);
    when(issue.flows()).thenReturn(flows);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(versionedIssue));
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsOnly(
        "SonarLint: Open description of rule 'XYZ'",
        "SonarLint: Deactivate rule 'XYZ'",
        "SonarLint: Show all locations for issue 'XYZ'");
  }

  @Test
  void openRuleDescriptionForBoundProject() {
    var ruleDetails = mock(ConnectedRuleDetails.class);
    when(ruleDetails.getKey()).thenReturn(FAKE_RULE_KEY);
    when(ruleDetails.getName()).thenReturn("Name");
    when(ruleDetails.getHtmlDescription()).thenReturn("Desc");
    when(ruleDetails.getExtendedDescription()).thenReturn("");
    when(ruleDetails.getType()).thenReturn(RuleType.BUG);
    when(ruleDetails.getDefaultSeverity()).thenReturn(IssueSeverity.BLOCKER);
    var response = mock(GetEffectiveRuleDetailsResponse.class);
    when(backendServiceFacade.getEffectiveRuleDetails(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(response));
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));
    var details = mock(EffectiveRuleDetailsDto.class);
    when(details.getName()).thenReturn("Name");
    when(details.getType()).thenReturn(RuleType.BUG);
    when(details.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(details.getParams()).thenReturn(emptyList());
    when(details.getKey()).thenReturn(FAKE_RULE_KEY);
    when(details.getLanguage()).thenReturn(Language.JS);
    when(details.getCleanCodeAttribute()).thenReturn(Optional.of(CleanCodeAttribute.COMPLETE));
    when(details.getDefaultImpacts()).thenReturn(Map.of(SoftwareQuality.SECURITY, ImpactSeverity.MEDIUM));
    var desc = mock(RuleMonolithicDescriptionDto.class);
    when(desc.getHtmlContent()).thenReturn("Desc");
    when(details.getDescription()).thenReturn(Either.forLeft(desc));
    when(response.details()).thenReturn(details);
    underTest.executeCommand(
      new ExecuteCommandParams(SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND, List.of(new JsonPrimitive(FAKE_RULE_KEY), new JsonPrimitive(FILE_URI), new JsonPrimitive(""))),
      NOP_CANCEL_TOKEN);

    verify(mockClient).showRuleDescription(new ShowRuleDescriptionParams(FAKE_RULE_KEY, "Name", "Desc",
      new SonarLintExtendedLanguageClient.RuleDescriptionTab[0], RuleType.BUG, Language.JS.getLanguageKey(),
      IssueSeverity.BLOCKER, Collections.emptyList(), CleanCodeAttribute.COMPLETE.getIssueLabel(),
      CleanCodeAttributeCategory.INTENTIONAL.getIssueLabel(), Map.of(SoftwareQuality.SECURITY.getDisplayLabel(), ImpactSeverity.MEDIUM.getDisplayLabel()))
    );
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
    when(issue.getCreationDate()).thenReturn(Instant.EPOCH);
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    var flow = mock(ServerTaintIssue.Flow.class);
    when(issue.getFlows()).thenReturn(List.of(flow));
    var location = mock(ServerTaintIssue.ServerIssueLocation.class);
    when(flow.locations()).thenReturn(List.of(location));
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
    var issueKey = "someIssueKey";
    var fileUri = "fileUri";
    var issue = new Issue() {

      @Nullable
      @Override
      public TextRange getTextRange() {
        return null;
      }

      @Nullable
      @Override
      public String getMessage() {
        return "";
      }

      @Nullable
      @Override
      public ClientInputFile getInputFile() {
        return null;
      }

      @Override
      public IssueSeverity getSeverity() {
        return IssueSeverity.BLOCKER;
      }

      @Override
      public RuleType getType() {
        return RuleType.SECURITY_HOTSPOT;
      }

      @Override
      public Optional<CleanCodeAttribute> getCleanCodeAttribute() {
        return Optional.empty();
      }

      @Nullable
      @Override
      public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
        return null;
      }

      @Override
      public String getRuleKey() {
        return "";
      }

      @Override
      public List<Flow> flows() {
        return emptyList();
      }

      @Override
      public List<QuickFix> quickFixes() {
        return null;
      }

      @Override
      public Optional<String> getRuleDescriptionContextKey() {
        return Optional.empty();
      }

      @Override
      public Optional<VulnerabilityProbability> getVulnerabilityProbability() {
        return Optional.empty();
      }
    };
    var versionedIssue = new VersionedIssue(issue, 1);
    when(securityHotspotsCache.get(URI.create("fileUri"))).thenReturn(Map.of(issueKey, versionedIssue));

    underTest.executeCommand(new ExecuteCommandParams(SONARLINT_SHOW_SECURITY_HOTSPOT_FLOWS, List.of(new JsonPrimitive(fileUri), new JsonPrimitive(issueKey))), NOP_CANCEL_TOKEN);

    verify(securityHotspotsCache).get(URI.create("fileUri"));
    verify(mockClient).showIssueOrHotspot(any());
  }

  @Test
  void getHtmlDescriptionTabsMonolithicShouldReturnNoTabs() {
    var monolithicDesc = new RuleMonolithicDescriptionDto("monolithicHtmlContent");
    var ruleDetails = new EffectiveRuleDetailsDto(null, null, null, null,  null, null, Either.forLeft(monolithicDesc), emptyList(), null);

    assertThat(CommandManager.getHtmlDescriptionTabs(ruleDetails.getDescription(), "")).isEmpty();
  }

  @Test
  void getHtmlDescriptionTabsSplitNonContextSections() {
    var section1 = new RuleNonContextualSectionDto("nonContextSectionContent1");
    var section2 = new RuleNonContextualSectionDto("nonContextSectionContent2");
    var tab1 = new RuleDescriptionTabDto("title1", Either.forLeft(section1));
    var tab2 = new RuleDescriptionTabDto("title2", Either.forLeft(section2));
    var splitDesc = new RuleSplitDescriptionDto("introHtmlContent", List.of(tab1, tab2));
    var ruleDetails = new EffectiveRuleDetailsDto(null, null, null, null,  null, null,Either.forRight(splitDesc), emptyList(), null);

    var descriptionTabs = CommandManager.getHtmlDescriptionTabs(ruleDetails.getDescription(), "");

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
    var ruleDetails = new EffectiveRuleDetailsDto(null, null, null, null,  null, null, Either.forRight(splitDesc), emptyList(), null);

    var descriptionTabs = CommandManager.getHtmlDescriptionTabs(ruleDetails.getDescription(), "java");

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
    var ruleDetails = new EffectiveRuleDetailsDto(null, null, null, null,  null, null,Either.forLeft(monolithicDesc), emptyList(), null);

    assertThat(CommandManager.getHtmlDescription(ruleDetails.getDescription())).isEqualTo("monolithicHtmlContent");
  }

  @Test
  void getHtmlDescriptionSplit() {
    var section1 = new RuleNonContextualSectionDto(null);
    var tab1 = new RuleDescriptionTabDto(null, Either.forLeft(section1));
    var splitDesc = new RuleSplitDescriptionDto("splitHtmlContent", List.of(tab1));
    var ruleDetails = new EffectiveRuleDetailsDto(null, null, null, null,  null, null,Either.forRight(splitDesc), emptyList(), null);


    assertThat(CommandManager.getHtmlDescription(ruleDetails.getDescription())).isEqualTo("splitHtmlContent");
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
  void hasResolveIssueActionForBoundProject(){
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));
    when(backendServiceFacade.checkChangeIssueStatusPermitted(any()))
      .thenReturn(CompletableFuture.completedFuture(new CheckStatusChangePermittedResponse(true, null, Collections.emptyList())));
    var connId = "connectionId";
    when(mockBinding.getConnectionId()).thenReturn(connId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    var fileUri = URI.create(FILE_URI);

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingIssue.class);
    var versionedIssue = new VersionedIssue(issue, 1);
    when(issue.getServerIssueKey()).thenReturn("qwerty");
    when(issue.getIssueId()).thenReturn(UUID.randomUUID());
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(versionedIssue));

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
        "SonarLint: Open description of rule 'XYZ'");
  }

  @Test
  void doesNotHaveResolveIssueActionWhenIssueStatusChangeNotPermitted(){
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));
    when(backendServiceFacade.checkChangeIssueStatusPermitted(any()))
      .thenReturn(CompletableFuture.completedFuture(new CheckStatusChangePermittedResponse(false, null, Collections.emptyList())));
    var connId = "connectionId";
    when(mockBinding.getConnectionId()).thenReturn(connId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    var fileUri = URI.create(FILE_URI);

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingIssue.class);
    var versionedIssue = new VersionedIssue(issue, 1);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(versionedIssue));

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
        "SonarLint: Open description of rule 'XYZ'");
  }

  @Test
  void doesNotHaveResolveIssueActionForBoundProjectForIssueWithoutKey(){
    var folderWrapper = mock(WorkspaceFolderWrapper.class);
    when(folderWrapper.getUri()).thenReturn(URI.create("file:///"));
    when(workspaceFoldersManager.findFolderForFile(URI.create(FILE_URI))).thenReturn(Optional.of(folderWrapper));
    when(backendServiceFacade.checkChangeIssueStatusPermitted(any()))
      .thenReturn(CompletableFuture.completedFuture(new CheckStatusChangePermittedResponse(true, null, Collections.emptyList())));
    var connId = "connectionId";
    when(mockBinding.getConnectionId()).thenReturn(connId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    var fileUri = URI.create(FILE_URI);

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var issue = mock(DelegatingIssue.class);
    var versionedIssue = new VersionedIssue(issue, 1);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(versionedIssue));

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
        "SonarLint: Open description of rule 'XYZ'");
  }
}
