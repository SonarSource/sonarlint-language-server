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
package org.sonarsource.sonarlint.ls;

import com.google.gson.JsonPrimitive;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.analysis.api.TextEdit;
import org.sonarsource.sonarlint.core.analysis.api.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssueLocation;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.container.standalone.rule.DefaultStandaloneRuleParam;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamDefinition;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamType;
import org.sonarsource.sonarlint.ls.IssuesCache.VersionnedIssue;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.AnalysisManager.SONARLINT_SOURCE;
import static org.sonarsource.sonarlint.ls.AnalysisManager.SONARQUBE_TAINT_SOURCE;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_BROWSE_TAINT_VULNERABILITY;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS;
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_UPDATE_ALL_BINDINGS_COMMAND;
import static org.sonarsource.sonarlint.ls.CommandManager.getHtmlDescription;

class CommandManagerTests {

  private static final String FAKE_RULE_KEY = "javascript:S1234";
  private static final String FILE_URI = "file://foo.js";
  private static final TextDocumentIdentifier FAKE_TEXT_DOCUMENT = new TextDocumentIdentifier(FILE_URI);
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
  private StandaloneSonarLintEngine mockStandaloneEngine;
  private SettingsManager mockSettingsManager;
  private SonarLintTelemetry mockTelemetry;
  private StandaloneEngineManager standaloneEngineManager;

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
    mockStandaloneEngine = mock(StandaloneSonarLintEngine.class);
    standaloneEngineManager = mock(StandaloneEngineManager.class);
    when(standaloneEngineManager.getOrCreateStandaloneEngine()).thenReturn(mockStandaloneEngine);
    mockTelemetry = mock(SonarLintTelemetry.class);
    underTest = new CommandManager(mockClient, mockSettingsManager, bindingManager, mockTelemetry, standaloneEngineManager, mockTaintVulnerabilitiesCache, issuesCache);
  }

  @Test
  void getHtmlDescription_appends_extended_description_when_non_empty() {
    var htmlDescription = "foo";
    var extendedDescription = "bar";

    var ruleDetails = mock(ConnectedRuleDetails.class);
    when(ruleDetails.getHtmlDescription()).thenReturn(htmlDescription);
    when(ruleDetails.getExtendedDescription()).thenReturn("");

    assertThat(getHtmlDescription(ruleDetails)).isEqualTo(htmlDescription);

    when(ruleDetails.getExtendedDescription()).thenReturn(extendedDescription);
    assertThat(getHtmlDescription(ruleDetails)).isEqualTo(htmlDescription + "<div>" + extendedDescription + "</div>");
  }

  @Test
  void updateAllBinding() {
    underTest.executeCommand(new ExecuteCommandParams(SONARLINT_UPDATE_ALL_BINDINGS_COMMAND, emptyList()), NOP_CANCEL_TOKEN);

    verify(bindingManager).updateAllBindings(NOP_CANCEL_TOKEN, null);
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
    var versionnedIssue = new VersionnedIssue(issue, 1);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(versionnedIssue));

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
    var versionnedIssue = new VersionnedIssue(issue, 1);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(versionnedIssue));

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
  void codeActionsForTaint() {
    var connId = "connectionId";
    when(mockBinding.getConnectionId()).thenReturn(connId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    var mockWorkspacesettings = mock(WorkspaceSettings.class);
    var serverSettings = mock(ServerConnectionSettings.class);
    when(serverSettings.getServerUrl()).thenReturn("https://some.server.url");
    when(mockWorkspacesettings.getServerConnections()).thenReturn(Collections.singletonMap(connId, serverSettings));
    when(mockSettingsManager.getCurrentSettings()).thenReturn(mockWorkspacesettings);

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARQUBE_TAINT_SOURCE, "ruleKey");

    var issue = mock(ServerIssue.class);
    when(issue.ruleKey()).thenReturn("ruleKey");
    when(issue.creationDate()).thenReturn(Instant.EPOCH);
    var flow = mock(ServerIssue.Flow.class);
    when(issue.getFlows()).thenReturn(List.of(flow));
    var location = mock(ServerIssueLocation.class);
    when(flow.locations()).thenReturn(List.of(location));
    when(issue.key()).thenReturn("SomeIssueKey");
    when(mockTaintVulnerabilitiesCache.getTaintVulnerabilityForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));

    var codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(List.of(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly(
      "SonarLint: Open description of rule 'ruleKey'",
      "SonarLint: Show all locations for taint vulnerability 'ruleKey'",
      "SonarLint: Open taint vulnerability 'ruleKey' on 'connectionId'");
  }

  @Test
  void suggestShowAllLocationsForIssueWithFlows() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());

    var d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    var flow = mock(Flow.class);
    var flows = List.of(flow);
    var issue = mock(Issue.class);
    var versionnedIssue = new VersionnedIssue(issue, 1);
    when(issue.flows()).thenReturn(flows);
    when(issuesCache.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(versionnedIssue));

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
    var connectionId = "connId";
    when(mockBinding.getConnectionId()).thenReturn(connectionId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    var ruleDetails = mock(ConnectedRuleDetails.class);
    when(ruleDetails.getKey()).thenReturn(FAKE_RULE_KEY);
    when(ruleDetails.getName()).thenReturn("Name");
    when(ruleDetails.getHtmlDescription()).thenReturn("Desc");
    when(ruleDetails.getExtendedDescription()).thenReturn("");
    when(ruleDetails.getType()).thenReturn("Type");
    when(ruleDetails.getSeverity()).thenReturn("Severity");
    when(bindingManager.getServerConfigurationFor(connectionId)).thenReturn(mock(ServerConnectionSettings.EndpointParamsAndHttpClient.class));
    when(mockConnectedEngine.getActiveRuleDetails(null, null, FAKE_RULE_KEY, "projectKey")).thenReturn(CompletableFuture.completedFuture(ruleDetails));
    underTest.executeCommand(
      new ExecuteCommandParams(SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND, List.of(new JsonPrimitive(FAKE_RULE_KEY), new JsonPrimitive(FILE_URI))),
      NOP_CANCEL_TOKEN);

    verify(mockClient).showRuleDescription(new ShowRuleDescriptionParams(FAKE_RULE_KEY, "Name", "Desc", "Type", "Severity", Collections.emptyList()));
  }

  @Test
  void throwIfUnknownRuleForBoundProject() {
    var connectionId = "connId";
    when(mockBinding.getConnectionId()).thenReturn(connectionId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    when(bindingManager.getServerConfigurationFor(connectionId)).thenReturn(mock(ServerConnectionSettings.EndpointParamsAndHttpClient.class));
    when(mockConnectedEngine.getActiveRuleDetails(null, null, FAKE_RULE_KEY, "projectKey")).thenThrow(new IllegalArgumentException());

    var params = new ExecuteCommandParams(
      SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND,
      List.of(new JsonPrimitive(FAKE_RULE_KEY), new JsonPrimitive(FILE_URI)));
    assertThrows(ResponseErrorException.class, () -> underTest.executeCommand(params, NOP_CANCEL_TOKEN));
  }

  @Test
  void openRuleDescriptionForUnboundProject() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());
    var ruleDetails = mock(StandaloneRuleDetails.class);
    when(ruleDetails.getKey()).thenReturn(FAKE_RULE_KEY);
    when(ruleDetails.getName()).thenReturn("Name");
    when(ruleDetails.getHtmlDescription()).thenReturn("Desc");
    when(ruleDetails.getType()).thenReturn("Type");
    when(ruleDetails.getSeverity()).thenReturn("Severity");
    var apiParam = mock(SonarLintRuleParamDefinition.class);
    when(apiParam.name()).thenReturn("intParam");
    when(apiParam.type()).thenReturn(SonarLintRuleParamType.INTEGER);
    when(apiParam.description()).thenReturn("An integer parameter");
    when(apiParam.defaultValue()).thenReturn("42");
    List<StandaloneRuleParam> params = List.of(new DefaultStandaloneRuleParam(apiParam));
    when(ruleDetails.paramDetails()).thenReturn(params);
    when(mockStandaloneEngine.getRuleDetails(FAKE_RULE_KEY)).thenReturn(Optional.of(ruleDetails));
    var sonarLintEngine = mock(StandaloneSonarLintEngine.class);
    when(standaloneEngineManager.getOrCreateStandaloneEngine()).thenReturn(sonarLintEngine);
    when(sonarLintEngine.getRuleDetails("javascript:S1234")).thenReturn(Optional.of(ruleDetails));

    underTest.executeCommand(
      new ExecuteCommandParams(SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND, List.of(new JsonPrimitive(FAKE_RULE_KEY), new JsonPrimitive(FILE_URI))),
      NOP_CANCEL_TOKEN);

    verify(mockClient).showRuleDescription(
      new ShowRuleDescriptionParams(FAKE_RULE_KEY, "Name", "Desc", "Type", "Severity", params));
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
    var issue = mock(ServerIssue.class);
    when(issue.ruleKey()).thenReturn("ruleKey");
    when(issue.creationDate()).thenReturn(Instant.EPOCH);
    var flow = mock(ServerIssue.Flow.class);
    when(issue.getFlows()).thenReturn(List.of(flow));
    var location = mock(ServerIssueLocation.class);
    when(flow.locations()).thenReturn(List.of(location));
    when(mockTaintVulnerabilitiesCache.getTaintVulnerabilityByKey(issueKey)).thenReturn(Optional.of(issue));

    underTest.executeCommand(new ExecuteCommandParams(SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS, List.of(new JsonPrimitive(issueKey), new JsonPrimitive(connectionId))),
      NOP_CANCEL_TOKEN);
    verify(mockTaintVulnerabilitiesCache).getTaintVulnerabilityByKey(issueKey);
    verify(mockTelemetry).taintVulnerabilitiesInvestigatedLocally();
  }
}
