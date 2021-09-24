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

import com.google.gson.JsonPrimitive;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.sonarlint.core.client.api.common.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.client.api.common.QuickFix;
import org.sonarsource.sonarlint.core.client.api.common.TextEdit;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
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
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
  private AnalysisManager mockAnalysisManager;
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
    mockAnalysisManager = mock(AnalysisManager.class);
    mockStandaloneEngine = mock(StandaloneSonarLintEngine.class);
    standaloneEngineManager = mock(StandaloneEngineManager.class);
    when(standaloneEngineManager.getOrCreateStandaloneEngine()).thenReturn(mockStandaloneEngine);
    mockTelemetry = mock(SonarLintTelemetry.class);
    underTest = new CommandManager(mockClient, mockSettingsManager, bindingManager, mockAnalysisManager, mockTelemetry, standaloneEngineManager);
  }

  @Test
  void getHtmlDescription_appends_extended_description_when_non_empty() {
    String htmlDescription = "foo";
    String extendedDescription = "bar";

    ConnectedRuleDetails ruleDetails = mock(ConnectedRuleDetails.class);
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
    List<Either<Command, CodeAction>> codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(singletonList(new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, "not_sonarlint", "XYZ")))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).isEmpty();
  }

  @Test
  void noDisableRuleForBoundProject() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));

    List<Either<Command, CodeAction>> codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(singletonList(new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ")))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly("SonarLint: Open description of rule 'XYZ'");
  }

  @Test
  void suggestDisableRuleForUnboundProject() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());

    Diagnostic d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    Issue issue = mock(Issue.class);
    when(mockAnalysisManager.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));

    List<Either<Command, CodeAction>> codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(singletonList(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsOnly(
        "SonarLint: Open description of rule 'XYZ'",
        "SonarLint: Deactivate rule 'XYZ'"
      );
  }

  @Test
  void showQuickFixFromAnalyzer() {
    URI fileUri = URI.create(FILE_URI);
    when(bindingManager.getBinding(fileUri)).thenReturn(Optional.empty());

    Diagnostic d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    Issue issue = mock(Issue.class);
    when(mockAnalysisManager.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));

    TextEdit textEdit = mock(TextEdit.class);
    when(textEdit.newText()).thenReturn("");
    when(textEdit.range()).thenReturn(new TextRange(1, 0,1, 1));
    ClientInputFileEdit edit = mock(ClientInputFileEdit.class);
    when(edit.textEdits()).thenReturn(Collections.singletonList(textEdit));
    ClientInputFile target = mock(ClientInputFile.class);
    when(target.uri()).thenReturn(fileUri);
    when(edit.target()).thenReturn(target);
    QuickFix fix = mock(QuickFix.class);
    when(fix.message()).thenReturn("Fix the issue!");
    when(fix.inputFileEdits()).thenReturn(Collections.singletonList(edit));
    when(issue.quickFixes()).thenReturn(Collections.singletonList(fix));

    List<Either<Command, CodeAction>> codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(singletonList(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsExactly(
        "SonarLint: Fix the issue!",
        "SonarLint: Open description of rule 'XYZ'",
        "SonarLint: Deactivate rule 'XYZ'"
      );
  }

  @Test
  void codeActionsForTaint() {
    String connId = "connectionId";
    when(mockBinding.getConnectionId()).thenReturn(connId);
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    WorkspaceSettings mockWorkspacesettings = mock(WorkspaceSettings.class);
    ServerConnectionSettings serverSettings = mock(ServerConnectionSettings.class);
    when(serverSettings.getServerUrl()).thenReturn("https://some.server.url");
    when(mockWorkspacesettings.getServerConnections()).thenReturn(Collections.singletonMap(connId, serverSettings));
    when(mockSettingsManager.getCurrentSettings()).thenReturn(mockWorkspacesettings);

    Diagnostic d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARQUBE_TAINT_SOURCE, "ruleKey");

    ServerIssue issue = mock(ServerIssue.class);
    when(issue.ruleKey()).thenReturn("ruleKey");
    when(issue.creationDate()).thenReturn(Instant.EPOCH);
    ServerIssue.Flow flow = mock(ServerIssue.Flow.class);
    when(issue.getFlows()).thenReturn(Collections.singletonList(flow));
    ServerIssueLocation location = mock(ServerIssueLocation.class);
    when(flow.locations()).thenReturn(Collections.singletonList(location));
    when(issue.key()).thenReturn("SomeIssueKey");
    when(mockAnalysisManager.getTaintVulnerabilityForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));

    List<Either<Command, CodeAction>> codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(singletonList(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly(
      "SonarLint: Open description of rule 'ruleKey'",
      "SonarLint: Show all locations for taint vulnerability 'ruleKey'",
      "SonarLint: Open taint vulnerability 'ruleKey' on 'connectionId'"
    );
  }

  @Test
  void suggestShowAllLocationsForIssueWithFlows() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());

    Diagnostic d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    Issue.Flow flow = mock(Issue.Flow.class);
    List<Issue.Flow> flows = Collections.singletonList(flow);
    Issue issue = mock(Issue.class);
    when(issue.flows()).thenReturn(flows);
    when(mockAnalysisManager.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));

    List<Either<Command, CodeAction>> codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(singletonList(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle())
      .containsOnly(
        "SonarLint: Open description of rule 'XYZ'",
        "SonarLint: Deactivate rule 'XYZ'",
        "SonarLint: Show all locations for issue 'XYZ'"
      );
  }

  @Test
  void openRuleDescriptionForBoundProject() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    ConnectedRuleDetails ruleDetails = mock(ConnectedRuleDetails.class);
    when(ruleDetails.getKey()).thenReturn(FAKE_RULE_KEY);
    when(ruleDetails.getName()).thenReturn("Name");
    when(ruleDetails.getHtmlDescription()).thenReturn("Desc");
    when(ruleDetails.getExtendedDescription()).thenReturn("");
    when(ruleDetails.getType()).thenReturn("Type");
    when(ruleDetails.getSeverity()).thenReturn("Severity");
    when(mockConnectedEngine.getActiveRuleDetails(FAKE_RULE_KEY, "projectKey")).thenReturn(ruleDetails);
    underTest.executeCommand(
      new ExecuteCommandParams(SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND, asList(new JsonPrimitive(FAKE_RULE_KEY), new JsonPrimitive(FILE_URI))),
      NOP_CANCEL_TOKEN);

    verify(mockClient).showRuleDescription(new ShowRuleDescriptionParams(FAKE_RULE_KEY, "Name", "Desc", "Type", "Severity", Collections.emptyList()));
  }

  @Test
  void throwIfUnknownRuleForBoundProject() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.of(mockBinding));
    when(mockConnectedEngine.getActiveRuleDetails(FAKE_RULE_KEY, "projectKey")).thenThrow(new IllegalArgumentException());

    ExecuteCommandParams params = new ExecuteCommandParams(
      SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND,
      asList(new JsonPrimitive(FAKE_RULE_KEY), new JsonPrimitive(FILE_URI)));
    assertThrows(ResponseErrorException.class, () -> underTest.executeCommand(params, NOP_CANCEL_TOKEN));
  }

  @Test
  void openRuleDescriptionForUnboundProject() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());
    StandaloneRuleDetails ruleDetails = mock(StandaloneRuleDetails.class);
    when(ruleDetails.getKey()).thenReturn(FAKE_RULE_KEY);
    when(ruleDetails.getName()).thenReturn("Name");
    when(ruleDetails.getHtmlDescription()).thenReturn("Desc");
    when(ruleDetails.getType()).thenReturn("Type");
    when(ruleDetails.getSeverity()).thenReturn("Severity");
    RulesDefinition.Param apiParam = mock(RulesDefinition.Param.class);
    when(apiParam.name()).thenReturn("intParam");
    when(apiParam.type()).thenReturn(RuleParamType.INTEGER);
    when(apiParam.description()).thenReturn("An integer parameter");
    when(apiParam.defaultValue()).thenReturn("42");
    List<StandaloneRuleParam> params = singletonList(new DefaultStandaloneRuleParam(apiParam));
    when(ruleDetails.paramDetails()).thenReturn(params);
    when(mockStandaloneEngine.getRuleDetails(FAKE_RULE_KEY)).thenReturn(Optional.of(ruleDetails));
    StandaloneSonarLintEngine sonarLintEngine = mock(StandaloneSonarLintEngine.class);
    when(standaloneEngineManager.getOrCreateStandaloneEngine()).thenReturn(sonarLintEngine);
    when(sonarLintEngine.getRuleDetails("javascript:S1234")).thenReturn(Optional.of(ruleDetails));

    underTest.executeCommand(
      new ExecuteCommandParams(SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND, asList(new JsonPrimitive(FAKE_RULE_KEY), new JsonPrimitive(FILE_URI))),
      NOP_CANCEL_TOKEN);

    verify(mockClient).showRuleDescription(
      new ShowRuleDescriptionParams(FAKE_RULE_KEY, "Name", "Desc", "Type", "Severity", params));
  }

  @Test
  void browseTaintVulnerability() {
    String issueUrl = "https://some.sq/issue/id";
    underTest.executeCommand(new ExecuteCommandParams(SONARLINT_BROWSE_TAINT_VULNERABILITY, singletonList(new JsonPrimitive(issueUrl))), NOP_CANCEL_TOKEN);
    verify(mockTelemetry).taintVulnerabilitiesInvestigatedRemotely();
    verify(mockClient).browseTo(issueUrl);
  }

  @Test
  void showTaintVulnerabilityFlows() {
    String issueKey = "someIssueKey";
    String connectionId = "connectionId";
    ServerIssue issue = mock(ServerIssue.class);
    when(issue.ruleKey()).thenReturn("ruleKey");
    when(issue.creationDate()).thenReturn(Instant.EPOCH);
    ServerIssue.Flow flow = mock(ServerIssue.Flow.class);
    when(issue.getFlows()).thenReturn(Collections.singletonList(flow));
    ServerIssueLocation location = mock(ServerIssueLocation.class);
    when(flow.locations()).thenReturn(Collections.singletonList(location));
    when(mockAnalysisManager.getTaintVulnerabilityByKey(issueKey)).thenReturn(Optional.of(issue));

    underTest.executeCommand(new ExecuteCommandParams(SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS, asList(new JsonPrimitive(issueKey), new JsonPrimitive(connectionId))),
      NOP_CANCEL_TOKEN);
    verify(mockAnalysisManager).getTaintVulnerabilityByKey(issueKey);
    verify(mockTelemetry).taintVulnerabilitiesInvestigatedLocally();
  }
}
