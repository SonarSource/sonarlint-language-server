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
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.container.standalone.rule.DefaultStandaloneRuleParam;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;

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
import static org.sonarsource.sonarlint.ls.CommandManager.SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND;
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

  @BeforeEach
  public void prepareMocks() {
    bindingManager = mock(ProjectBindingManager.class);
    mockBinding = mock(ProjectBindingWrapper.class);
    mockConnectedEngine = mock(ConnectedSonarLintEngine.class);
    when(mockBinding.getEngine()).thenReturn(mockConnectedEngine);
    when(mockBinding.getBinding()).thenReturn(new ProjectBinding("projectKey", "sqPathPrefix", "idePathPrefix"));

    mockClient = mock(SonarLintExtendedLanguageClient.class);
    mockAnalysisManager = mock(AnalysisManager.class);
    mockStandaloneEngine = mock(StandaloneSonarLintEngine.class);
    when(mockAnalysisManager.getOrCreateStandaloneEngine()).thenReturn(mockStandaloneEngine);
    underTest = new CommandManager(mockClient, bindingManager, mockAnalysisManager);
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

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly("Open description of SonarLint rule 'XYZ'");
  }

  @Test
  void suggestDisableRuleForUnboundProject() {
    when(bindingManager.getBinding(URI.create(FILE_URI))).thenReturn(Optional.empty());

    Diagnostic d = new Diagnostic(FAKE_RANGE, "Foo", DiagnosticSeverity.Error, SONARLINT_SOURCE, "XYZ");

    Issue issue = mock(Issue.class);
    when(mockAnalysisManager.getIssueForDiagnostic(any(URI.class), eq(d))).thenReturn(Optional.of(issue));

    List<Either<Command, CodeAction>> codeActions = underTest.computeCodeActions(new CodeActionParams(FAKE_TEXT_DOCUMENT, FAKE_RANGE,
      new CodeActionContext(singletonList(d))), NOP_CANCEL_TOKEN);

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly("Open description of SonarLint rule 'XYZ'", "Deactivate rule 'XYZ'");
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

    assertThat(codeActions).extracting(c -> c.getRight().getTitle()).containsOnly("Open description of SonarLint rule 'XYZ'", "Deactivate rule 'XYZ'", "Show all locations for issue 'XYZ'");
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
    underTest.executeCommand(
      new ExecuteCommandParams(SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND, asList(new JsonPrimitive(FAKE_RULE_KEY), new JsonPrimitive(FILE_URI))),
      NOP_CANCEL_TOKEN);

    verify(mockClient).showRuleDescription(
      new ShowRuleDescriptionParams(FAKE_RULE_KEY, "Name", "Desc", "Type", "Severity", params));
  }

}
