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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.analysis.api.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;

import static java.net.URI.create;
import static java.util.Objects.requireNonNull;
import static org.sonarsource.sonarlint.ls.AnalysisManager.SONARLINT_SOURCE;
import static org.sonarsource.sonarlint.ls.AnalysisManager.SONARQUBE_TAINT_SOURCE;

public class CommandManager {

  // Server side
  static final String SONARLINT_QUICK_FIX_APPLIED = "SonarLint.QuickFixApplied";
  static final String SONARLINT_OPEN_STANDALONE_RULE_DESCRIPTION_COMMAND = "SonarLint.OpenStandaloneRuleDesc";
  static final String SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND = "SonarLint.OpenRuleDescCodeAction";
  static final String SONARLINT_UPDATE_ALL_BINDINGS_COMMAND = "SonarLint.UpdateAllBindings";
  static final String SONARLINT_BROWSE_TAINT_VULNERABILITY = "SonarLint.BrowseTaintVulnerability";
  static final String SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS = "SonarLint.ShowTaintVulnerabilityFlows";
  static final List<String> SONARLINT_SERVERSIDE_COMMANDS = List.of(
    SONARLINT_QUICK_FIX_APPLIED,
    SONARLINT_UPDATE_ALL_BINDINGS_COMMAND,
    SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND,
    SONARLINT_OPEN_STANDALONE_RULE_DESCRIPTION_COMMAND,
    SONARLINT_BROWSE_TAINT_VULNERABILITY,
    SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS);
  // Client side
  static final String SONARLINT_DEACTIVATE_RULE_COMMAND = "SonarLint.DeactivateRule";

  static final String SONARLINT_ACTION_PREFIX = "SonarLint: ";

  private final SonarLintExtendedLanguageClient client;
  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private final SonarLintTelemetry telemetry;
  private final StandaloneEngineManager standaloneEngineManager;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final IssuesCache issuesCache;

  CommandManager(SonarLintExtendedLanguageClient client, SettingsManager settingsManager, ProjectBindingManager bindingManager,
    SonarLintTelemetry telemetry, StandaloneEngineManager standaloneEngineManager, TaintVulnerabilitiesCache taintVulnerabilitiesCache, IssuesCache issuesCache) {
    this.client = client;
    this.settingsManager = settingsManager;
    this.bindingManager = bindingManager;
    this.telemetry = telemetry;
    this.standaloneEngineManager = standaloneEngineManager;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.issuesCache = issuesCache;
  }

  public List<Either<Command, CodeAction>> computeCodeActions(CodeActionParams params, CancelChecker cancelToken) {
    var codeActions = new ArrayList<Either<Command, CodeAction>>();
    var uri = create(params.getTextDocument().getUri());
    var binding = bindingManager.getBinding(uri);
    for (var diagnostic : params.getContext().getDiagnostics()) {
      cancelToken.checkCanceled();
      if (SONARLINT_SOURCE.equals(diagnostic.getSource())) {
        var ruleKey = diagnostic.getCode().getLeft();
        cancelToken.checkCanceled();
        var issueForDiagnostic = issuesCache.getIssueForDiagnostic(uri, diagnostic);
        issueForDiagnostic.ifPresent(versionnedIssue -> versionnedIssue.getIssue().quickFixes().forEach(fix -> {
          var newCodeAction = new CodeAction(SONARLINT_ACTION_PREFIX + fix.message());
          newCodeAction.setKind(CodeActionKind.QuickFix);
          newCodeAction.setDiagnostics(List.of(diagnostic));
          newCodeAction.setEdit(newWorkspaceEdit(fix, versionnedIssue.getDocumentVersion()));
          newCodeAction.setCommand(new Command(fix.message(), SONARLINT_QUICK_FIX_APPLIED, List.of(ruleKey)));
          codeActions.add(Either.forRight(newCodeAction));
        }));
        addRuleDescriptionCodeAction(params, codeActions, diagnostic, ruleKey);
        issueForDiagnostic.ifPresent(versionnedIssue -> {
          if (!versionnedIssue.getIssue().flows().isEmpty()) {
            var titleShowAllLocations = String.format("Show all locations for issue '%s'", ruleKey);
            codeActions.add(newQuickFix(diagnostic, titleShowAllLocations, ShowAllLocationsCommand.ID, List.of(ShowAllLocationsCommand.params(versionnedIssue.getIssue()))));
          }
        });
        if (binding.isEmpty()) {
          var titleDeactivate = String.format("Deactivate rule '%s'", ruleKey);
          codeActions.add(newQuickFix(diagnostic, titleDeactivate, SONARLINT_DEACTIVATE_RULE_COMMAND, List.of(ruleKey)));
        }
      } else if (SONARQUBE_TAINT_SOURCE.equals(diagnostic.getSource())) {
        var actualBinding = binding.orElseThrow(() -> new IllegalStateException("Binding not found for taint vulnerability"));
        var ruleKey = diagnostic.getCode().getLeft();
        addRuleDescriptionCodeAction(params, codeActions, diagnostic, ruleKey);
        taintVulnerabilitiesCache.getTaintVulnerabilityForDiagnostic(uri, diagnostic).ifPresent(issue -> {
          if (!issue.getFlows().isEmpty()) {
            var titleShowAllLocations = String.format("Show all locations for taint vulnerability '%s'", ruleKey);
            codeActions.add(newQuickFix(diagnostic, titleShowAllLocations, SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS, List.of(issue.key(), actualBinding.getConnectionId())));
          }
          var title = String.format("Open taint vulnerability '%s' on '%s'", ruleKey, actualBinding.getConnectionId());
          var serverUrl = settingsManager.getCurrentSettings().getServerConnections().get(actualBinding.getConnectionId()).getServerUrl();
          var projectKey = UrlUtils.urlEncode(actualBinding.getBinding().projectKey());
          var issueUrl = String.format("%s/project/issues?id=%s&issues=%s&open=%s", serverUrl, projectKey, issue.key(), issue.key());
          codeActions.add(newQuickFix(diagnostic, title, SONARLINT_BROWSE_TAINT_VULNERABILITY, List.of(issueUrl)));
        });
      }
    }
    return codeActions;
  }

  private static WorkspaceEdit newWorkspaceEdit(QuickFix fix, @Nullable Integer documentVersion) {
    var edit = new WorkspaceEdit();
    edit.setDocumentChanges(
      fix.inputFileEdits().stream()
        .map(fileEdit -> newLspDocumentEdit(fileEdit, documentVersion))
        .collect(Collectors.toList()));
    return edit;
  }

  private static Either<TextDocumentEdit, ResourceOperation> newLspDocumentEdit(ClientInputFileEdit fileEdit, @Nullable Integer documentVersion) {
    var documentEdit = new TextDocumentEdit();
    documentEdit.setTextDocument(new VersionedTextDocumentIdentifier(fileEdit.target().uri().toString(), documentVersion));
    documentEdit.setEdits(fileEdit.textEdits().stream()
      .map(CommandManager::newLspTextEdit)
      .collect(Collectors.toList()));
    return Either.forLeft(documentEdit);
  }

  private static TextEdit newLspTextEdit(org.sonarsource.sonarlint.core.analysis.api.TextEdit textEdit) {
    var lspEdit = new TextEdit();
    lspEdit.setNewText(textEdit.newText());
    var lspRange = newLspRange(textEdit.range());
    lspEdit.setRange(lspRange);
    return lspEdit;
  }

  private static Range newLspRange(TextRange range) {
    requireNonNull(range.getStartLine());
    requireNonNull(range.getStartLineOffset());
    requireNonNull(range.getEndLine());
    requireNonNull(range.getEndLineOffset());
    var lspRange = new Range();
    lspRange.setStart(new Position(range.getStartLine() - 1, range.getStartLineOffset()));
    lspRange.setEnd(new Position(range.getEndLine() - 1, range.getEndLineOffset()));
    return lspRange;
  }

  private static void addRuleDescriptionCodeAction(CodeActionParams params, List<Either<Command, CodeAction>> codeActions, Diagnostic d, String ruleKey) {
    var titleShowRuleDesc = String.format("Open description of rule '%s'", ruleKey);
    codeActions.add(newQuickFix(d, titleShowRuleDesc, SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND, List.of(ruleKey, params.getTextDocument().getUri())));
  }

  private static Either<Command, CodeAction> newQuickFix(Diagnostic diag, String title, String command, List<Object> params) {
    var newCodeAction = new CodeAction(SONARLINT_ACTION_PREFIX + title);
    newCodeAction.setCommand(new Command(title, command, params));
    newCodeAction.setKind(CodeActionKind.QuickFix);
    newCodeAction.setDiagnostics(List.of(diag));
    return Either.forRight(newCodeAction);
  }

  public Map<String, List<Rule>> listAllStandaloneRules() {
    var result = new HashMap<String, List<Rule>>();
    standaloneEngineManager.getOrCreateStandaloneEngine().getAllRuleDetails()
      .forEach(d -> {
        var languageName = d.getLanguage().getLabel();
        result.computeIfAbsent(languageName, k -> new ArrayList<>()).add(Rule.of(d));
      });
    return result;
  }

  private void openRuleDescription(@Nullable ProjectBindingWrapper binding, String ruleKey) {
    if (binding == null) {
      var ruleDetails = standaloneEngineManager.getOrCreateStandaloneEngine().getRuleDetails(ruleKey)
        .orElseThrow(() -> unknownRule(ruleKey));
      var paramDetails = ruleDetails.paramDetails();
      showRuleDescription(ruleKey, ruleDetails, paramDetails);
    } else {
      var engine = binding.getEngine();
      try {
        var serverConfiguration = bindingManager.getServerConfigurationFor(binding.getConnectionId());
        Objects.requireNonNull(serverConfiguration);
        engine.getActiveRuleDetails(serverConfiguration.getEndpointParams(), serverConfiguration.getHttpClient(), ruleKey, binding.getBinding().projectKey())
          .thenAccept(details -> showRuleDescription(ruleKey, details, Collections.emptyList()));
      } catch (IllegalArgumentException ignored) {
        throw unknownRule(ruleKey);
      }
    }
  }

  private void showRuleDescription(String ruleKey, RuleDetails ruleDetails, Collection<StandaloneRuleParam> paramDetails) {
    var ruleName = ruleDetails.getName();
    var htmlDescription = getHtmlDescription(ruleDetails);
    var type = ruleDetails.getType();
    var severity = ruleDetails.getSeverity();
    client.showRuleDescription(new ShowRuleDescriptionParams(ruleKey, ruleName, htmlDescription, type, severity, paramDetails));
  }

  private static ResponseErrorException unknownRule(String ruleKey) {
    return new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unknown rule with key: " + ruleKey, null));
  }

  public void executeCommand(ExecuteCommandParams params, CancelChecker cancelToken) {
    switch (params.getCommand()) {
      case SONARLINT_QUICK_FIX_APPLIED:
        telemetry.addQuickFixAppliedForRule(getAsString(params.getArguments().get(0)));
        break;
      case SONARLINT_UPDATE_ALL_BINDINGS_COMMAND:
        bindingManager.updateAllBindings(cancelToken, params.getWorkDoneToken());
        break;
      case SONARLINT_OPEN_STANDALONE_RULE_DESCRIPTION_COMMAND:
        handleOpenStandaloneRuleDescriptionCommand(params);
        break;
      case SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND:
        handleOpenRuleDescriptionFromCodeActionCommand(params);
        break;
      case SONARLINT_BROWSE_TAINT_VULNERABILITY:
        handleBrowseTaintVulnerability(params);
        break;
      case SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS:
        handleShowTaintVulnerabilityFlows(params);
        break;
      default:
        throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unsupported command: " + params.getCommand(), null));
    }
  }

  private void handleOpenStandaloneRuleDescriptionCommand(ExecuteCommandParams params) {
    var ruleKey = getAsString(params.getArguments().get(0));
    openRuleDescription(null, ruleKey);
  }

  private void handleOpenRuleDescriptionFromCodeActionCommand(ExecuteCommandParams params) {
    var ruleKey = getAsString(params.getArguments().get(0));
    var uri = create(getAsString(params.getArguments().get(1)));
    var binding = bindingManager.getBinding(uri);
    openRuleDescription(binding.orElse(null), ruleKey);
  }

  private void handleBrowseTaintVulnerability(ExecuteCommandParams params) {
    var taintUrl = getAsString(params.getArguments().get(0));
    telemetry.taintVulnerabilitiesInvestigatedRemotely();
    client.browseTo(taintUrl);
  }

  private void handleShowTaintVulnerabilityFlows(ExecuteCommandParams params) {
    var issueKey = getAsString(params.getArguments().get(0));
    var connectionId = getAsString(params.getArguments().get(1));
    taintVulnerabilitiesCache.getTaintVulnerabilityByKey(issueKey)
      .ifPresent(issue -> {
        telemetry.taintVulnerabilitiesInvestigatedLocally();
        client.showTaintVulnerability(ShowAllLocationsCommand.params(issue, connectionId, bindingManager::serverPathToFileUri));
      });
  }

  // https://github.com/eclipse/lsp4j/issues/126
  private static String getAsString(Object jsonPrimitive) {
    return ((JsonPrimitive) jsonPrimitive).getAsString();
  }

  // visible for testing
  static String getHtmlDescription(RuleDetails ruleDetails) {
    var htmlDescription = ruleDetails.getHtmlDescription();
    if (ruleDetails instanceof ConnectedRuleDetails) {
      var extendedDescription = ((ConnectedRuleDetails) ruleDetails).getExtendedDescription();
      if (!extendedDescription.isEmpty()) {
        htmlDescription += "<div>" + extendedDescription + "</div>";
      }
    }
    return htmlDescription;
  }

}
