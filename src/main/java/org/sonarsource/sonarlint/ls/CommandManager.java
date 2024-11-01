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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
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
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.EffectiveIssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleMonolithicDescriptionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleParamDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleSplitDescriptionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.FileEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.TextEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttributeCategory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.commands.ShowAllLocationsCommand;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.domain.LSLanguage;
import org.sonarsource.sonarlint.ls.domain.MQRModeDetails;
import org.sonarsource.sonarlint.ls.domain.StandardModeDetails;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;
import org.sonarsource.sonarlint.ls.notebooks.VersionedOpenNotebook;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.telemetry.SonarLintTelemetry;
import org.sonarsource.sonarlint.ls.util.EnumLabelsMapper;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.net.URI.create;
import static org.sonarsource.sonarlint.core.client.utils.CleanCodeAttribute.fromDto;
import static org.sonarsource.sonarlint.ls.clientapi.SonarLintVSCodeClient.SONARLINT_SOURCE;
import static org.sonarsource.sonarlint.ls.domain.TaintIssue.SONARCLOUD_TAINT_SOURCE;
import static org.sonarsource.sonarlint.ls.domain.TaintIssue.SONARQUBE_TAINT_SOURCE;
import static org.sonarsource.sonarlint.ls.util.EnumLabelsMapper.cleanCodeAttributeToLabel;
import static org.sonarsource.sonarlint.ls.util.Utils.interrupted;

public class CommandManager {

  // Server side
  static final String SONARLINT_QUICK_FIX_APPLIED = "SonarLint.QuickFixApplied";
  static final String SONARLINT_OPEN_STANDALONE_RULE_DESCRIPTION_COMMAND = "SonarLint.OpenStandaloneRuleDesc";
  static final String SONARLINT_SHOW_ISSUE_DETAILS_FROM_CODE_ACTION_COMMAND = "SonarLint.ShowIssueDetailsCodeAction";
  static final String SONARLINT_SHOW_RULE_DESC_COMMAND = "SonarLint.OpenRuleDesc";
  static final String SONARLINT_BROWSE_TAINT_VULNERABILITY = "SonarLint.BrowseTaintVulnerability";
  static final String SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS = "SonarLint.ShowTaintVulnerabilityFlows";
  static final String SONARLINT_SHOW_SECURITY_HOTSPOT_FLOWS = "SonarLint.ShowSecurityHotspotFlows";
  static final List<String> SONARLINT_SERVERSIDE_COMMANDS = List.of(
    SONARLINT_QUICK_FIX_APPLIED,
    SONARLINT_SHOW_ISSUE_DETAILS_FROM_CODE_ACTION_COMMAND,
    SONARLINT_SHOW_RULE_DESC_COMMAND,
    SONARLINT_OPEN_STANDALONE_RULE_DESCRIPTION_COMMAND,
    SONARLINT_BROWSE_TAINT_VULNERABILITY,
    SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS);
  // Client side
  static final String SONARLINT_DEACTIVATE_RULE_COMMAND = "SonarLint.DeactivateRule";
  static final String RESOLVE_ISSUE = "SonarLint.ResolveIssue";
  static final String SONARLINT_ACTION_PREFIX = "SonarLint: ";

  private final SonarLintExtendedLanguageClient client;
  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private final SonarLintTelemetry telemetry;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final IssuesCache issuesCache;
  private final HotspotsCache securityHotspotsCache;
  private final BackendServiceFacade backendServiceFacade;
  private final WorkspaceFoldersManager workspaceFoldersManager;
  private final OpenNotebooksCache openNotebooksCache;
  private final LanguageClientLogger logOutput;

  CommandManager(SonarLintExtendedLanguageClient client, SettingsManager settingsManager, ProjectBindingManager bindingManager,
    SonarLintTelemetry telemetry, TaintVulnerabilitiesCache taintVulnerabilitiesCache, IssuesCache issuesCache,
    HotspotsCache securityHotspotsCache, BackendServiceFacade backendServiceFacade, WorkspaceFoldersManager workspaceFoldersManager,
    OpenNotebooksCache openNotebooksCache, LanguageClientLogger logOutput) {
    this.client = client;
    this.settingsManager = settingsManager;
    this.bindingManager = bindingManager;
    this.telemetry = telemetry;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.issuesCache = issuesCache;
    this.securityHotspotsCache = securityHotspotsCache;
    this.backendServiceFacade = backendServiceFacade;
    this.workspaceFoldersManager = workspaceFoldersManager;
    this.openNotebooksCache = openNotebooksCache;
    this.logOutput = logOutput;
  }

  public List<Either<Command, CodeAction>> computeCodeActions(CodeActionParams params, CancelChecker cancelToken) {
    var codeActions = new ArrayList<Either<Command, CodeAction>>();
    for (var diagnostic : params.getContext().getDiagnostics()) {
      cancelToken.checkCanceled();
      if (SONARLINT_SOURCE.equals(diagnostic.getSource())) {
        computeCodeActionsForSonarLintIssues(diagnostic, codeActions, params, cancelToken);
      } else if (SONARQUBE_TAINT_SOURCE.equals(diagnostic.getSource()) || SONARCLOUD_TAINT_SOURCE.equals((diagnostic.getSource()))) {
        computeCodeActionsForTaintIssues(diagnostic, codeActions, params);
      }
    }
    return codeActions;
  }

  private void computeCodeActionsForSonarLintIssues(Diagnostic diagnostic, List<Either<Command, CodeAction>> codeActions,
    CodeActionParams params, CancelChecker cancelToken) {
    var uri = create(params.getTextDocument().getUri());
    var binding = bindingManager.getBinding(uri);

    var ruleKey = diagnostic.getCode().getLeft();
    var isNotebookCellUri = openNotebooksCache.isKnownCellUri(uri);
    var issueForDiagnostic = isNotebookCellUri ?
      issuesCache.getIssueForDiagnostic(openNotebooksCache.getNotebookUriFromCellUri(uri), diagnostic) :
      issuesCache.getIssueForDiagnostic(uri, diagnostic);
    var hotspotForDiagnostic = issueForDiagnostic.isPresent() ? Optional.empty() : securityHotspotsCache.getHotspotForDiagnostic(uri, diagnostic);
    Optional<VersionedOpenNotebook> versionedOpenNotebook = isNotebookCellUri ?
      openNotebooksCache.getFile(openNotebooksCache.getNotebookUriFromCellUri(uri)) :
      Optional.empty();
    var hasBinding = binding.isPresent();
    if (issueForDiagnostic.isPresent() || hotspotForDiagnostic.isPresent()) {
      var finding = issueForDiagnostic.orElseGet(() -> (DelegatingFinding) hotspotForDiagnostic.get());
      var quickFixes = isNotebookCellUri && versionedOpenNotebook.isPresent() ?
        versionedOpenNotebook.get().toCellIssue(finding).quickFixes() :
        finding.quickFixes();
      cancelToken.checkCanceled();
      quickFixes.forEach(fix -> {
        var newCodeAction = new CodeAction(SONARLINT_ACTION_PREFIX + fix.message());
        newCodeAction.setKind(CodeActionKind.QuickFix);
        newCodeAction.setDiagnostics(List.of(diagnostic));
        newCodeAction.setEdit(newWorkspaceEdit(fix, null));
        newCodeAction.setCommand(new Command(fix.message(), SONARLINT_QUICK_FIX_APPLIED, List.of(ruleKey)));
        codeActions.add(Either.forRight(newCodeAction));
      });

      if (hasBinding) {
        var projectBindingWrapper = binding.get();
        var resolveIssueCodeAction = createResolveIssueCodeAction(diagnostic, uri, projectBindingWrapper, ruleKey, finding);
        resolveIssueCodeAction.ifPresent(ca -> codeActions.add(Either.forRight(ca)));
      }

      addIssueDetailsCodeAction(params, codeActions, diagnostic, finding.getIssueId());
    }
    issueForDiagnostic.ifPresent(versionedIssue -> addShowAllLocationsCodeAction(versionedIssue, codeActions, diagnostic, ruleKey, isNotebookCellUri));
    if (!hasBinding) {
      var titleDeactivate = String.format("Deactivate rule '%s'", ruleKey);
      codeActions.add(newQuickFix(diagnostic, titleDeactivate, SONARLINT_DEACTIVATE_RULE_COMMAND, List.of(ruleKey)));
    }
  }

  private Optional<CodeAction> createResolveIssueCodeAction(Diagnostic diagnostic, URI uri, ProjectBinding binding, String ruleKey,
    DelegatingFinding raisedFindingDto) {
    if (raisedFindingDto.getIssueId() != null) {
      var issueId = raisedFindingDto.getIssueId();
      var serverIssueKey = raisedFindingDto.getServerIssueKey();
      var key = serverIssueKey == null ? issueId.toString() : serverIssueKey;
      var changeStatusPermittedResponse =
        Utils.safelyGetCompletableFuture(backendServiceFacade.getBackendService().checkChangeIssueStatusPermitted(
          new CheckStatusChangePermittedParams(binding.connectionId(), key)
        ), logOutput);
      if (changeStatusPermittedResponse.isPresent() && changeStatusPermittedResponse.get().isPermitted()) {
        return Optional.of(createResolveIssueCodeAction(diagnostic, ruleKey, key, uri, false));
      }
    }
    return Optional.empty();
  }

  @NotNull
  private CodeAction createResolveIssueCodeAction(Diagnostic diagnostic, String ruleKey, String issueId, URI fileUri, boolean isTaintIssue) {
    var workspace = workspaceFoldersManager.findFolderForFile(fileUri).orElseThrow(() -> new IllegalStateException("No workspace found"));
    var workspaceUri = workspace.getUri();
    var resolveIssueAction = new CodeAction(String.format(SONARLINT_ACTION_PREFIX + "Resolve issue violating rule '%s' as...", ruleKey));
    resolveIssueAction.setKind(CodeActionKind.Empty);
    resolveIssueAction.setDiagnostics(List.of(diagnostic));
    resolveIssueAction.setCommand(new Command("Resolve this issue", RESOLVE_ISSUE, List.of(workspaceUri.toString(), issueId, fileUri, isTaintIssue)));
    return resolveIssueAction;
  }

  private static void addShowAllLocationsCodeAction(DelegatingFinding versionedIssue,
    List<Either<Command, CodeAction>> codeActions, Diagnostic diagnostic, String ruleKey, boolean isNotebook) {
    if (!versionedIssue.flows().isEmpty() && !isNotebook) {
      var titleShowAllLocations = String.format("Show all locations for issue '%s'", ruleKey);
      codeActions.add(newQuickFix(diagnostic, titleShowAllLocations, ShowAllLocationsCommand.ID, List.of(ShowAllLocationsCommand.params(versionedIssue))));
    }
  }

  private void computeCodeActionsForTaintIssues(Diagnostic diagnostic, List<Either<Command, CodeAction>> codeActions, CodeActionParams params) {
    var uri = create(params.getTextDocument().getUri());
    var binding = bindingManager.getBinding(uri);
    var actualBinding = binding.orElseThrow(() -> new IllegalStateException("Binding not found for taint vulnerability"));
    var ruleKey = diagnostic.getCode().getLeft();
    var taintVulnerability = taintVulnerabilitiesCache.getTaintVulnerabilityForDiagnostic(uri, diagnostic);
    taintVulnerability.ifPresent(issue -> {
      var issueKey = issue.getSonarServerKey();
      addIssueDetailsCodeAction(params, codeActions, diagnostic, issue.getId());
      if (!issue.getFlows().isEmpty()) {
        var titleShowAllLocations = String.format("Show all locations for taint vulnerability '%s'", ruleKey);
        codeActions.add(newQuickFix(diagnostic, titleShowAllLocations, SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS, List.of(issueKey, actualBinding.connectionId())));
      }
      var title = String.format("Open taint vulnerability '%s' on '%s'", ruleKey, actualBinding.connectionId());
      var serverUrl = settingsManager.getCurrentSettings().getServerConnections().get(actualBinding.connectionId()).getServerUrl();
      var projectKey = UrlUtils.urlEncode(actualBinding.projectKey());
      var issueUrl = String.format("%s/project/issues?id=%s&issues=%s&open=%s", serverUrl, projectKey, issueKey, issueKey);
      codeActions.add(newQuickFix(diagnostic, title, SONARLINT_BROWSE_TAINT_VULNERABILITY, List.of(issueUrl)));
      codeActions.add(Either.forRight(createResolveIssueCodeAction(diagnostic, ruleKey, issueKey, uri, true)));
    });
  }

  private static WorkspaceEdit newWorkspaceEdit(QuickFixDto fix, @Nullable Integer documentVersion) {
    var edit = new WorkspaceEdit();
    edit.setDocumentChanges(
      fix.fileEdits().stream()
        .map(fileEdit -> newLspDocumentEdit(fileEdit, documentVersion))
        .toList());
    return edit;
  }

  private static Either<TextDocumentEdit, ResourceOperation> newLspDocumentEdit(FileEditDto fileEdit, @Nullable Integer documentVersion) {
    var documentEdit = new TextDocumentEdit();
    documentEdit.setTextDocument(new VersionedTextDocumentIdentifier(fileEdit.target().toString(), documentVersion));
    documentEdit.setEdits(fileEdit.textEdits().stream()
      .map(CommandManager::newLspTextEdit)
      .toList());
    return Either.forLeft(documentEdit);
  }

  private static TextEdit newLspTextEdit(TextEditDto textEdit) {
    var lspEdit = new TextEdit();
    lspEdit.setNewText(textEdit.newText());
    var lspRange = newLspRange(textEdit.range());
    lspEdit.setRange(lspRange);
    return lspEdit;
  }

  private static Range newLspRange(TextRangeDto range) {
    var lspRange = new Range();
    lspRange.setStart(new Position(range.getStartLine() - 1, range.getStartLineOffset()));
    lspRange.setEnd(new Position(range.getEndLine() - 1, range.getEndLineOffset()));
    return lspRange;
  }

  private static void addIssueDetailsCodeAction(CodeActionParams params, List<Either<Command, CodeAction>> codeActions, Diagnostic d, UUID issueKey) {
    var titleShowRuleDesc = "Show issue details for '" + d.getCode().getLeft() + "'";
    List<Object> codeActionParams = List.of(issueKey, params.getTextDocument().getUri());
    codeActions.add(newQuickFix(d, titleShowRuleDesc, SONARLINT_SHOW_ISSUE_DETAILS_FROM_CODE_ACTION_COMMAND, codeActionParams));
  }

  private static Either<Command, CodeAction> newQuickFix(Diagnostic diag, String title, String command, List<Object> params) {
    var newCodeAction = new CodeAction(SONARLINT_ACTION_PREFIX + title);
    newCodeAction.setCommand(new Command(title, command, params));
    newCodeAction.setKind(CodeActionKind.QuickFix);
    newCodeAction.setDiagnostics(List.of(diag));
    if (command.equals(SONARLINT_SHOW_ISSUE_DETAILS_FROM_CODE_ACTION_COMMAND)) {
      newCodeAction.setIsPreferred(true);
    }
    return Either.forRight(newCodeAction);
  }

  public Map<String, List<Rule>> listAllStandaloneRules() {
    var result = new HashMap<String, List<Rule>>();
    try {
      return backendServiceFacade.getBackendService().listAllStandaloneRulesDefinitions()
        .thenApply(response -> {
          response.getRulesByKey().forEach((ruleKey, ruleDefinition) -> {
            var language = ruleDefinition.getLanguage();
            var languageName = LSLanguage.valueOf(language.name()).getLabel();
            result.computeIfAbsent(languageName, k -> new ArrayList<>()).add(Rule.of(ruleDefinition));
          });
          return result;
        }).get();
    } catch (InterruptedException e) {
      interrupted(e, logOutput);
      return Map.of();
    } catch (ExecutionException e) {
      throw new IllegalStateException("Failed to list all standalone rules", e);
    }
  }

  private void openStandaloneRuleDescription(String ruleKey) {
    backendServiceFacade.getBackendService().getStandaloneRuleDetails(ruleKey)
      .thenAccept(detailsResponse -> showStandaloneRuleDescription(ruleKey, detailsResponse))
      .exceptionally(e -> {
        var message = "Can't show rule details for unknown rule with key: " + ruleKey;
        client.showMessage(new MessageParams(MessageType.Error, message));
        logOutput.errorWithStackTrace(message, e);
        return null;
      });
  }

  public CompletableFuture<ShowRuleDescriptionParams> getFindingDetails(String fileUri, String issueKey) {
    var workspaceFolder = getWorkspaceFolderFromFileUri(fileUri);

    return backendServiceFacade.getBackendService().getEffectiveIssueDetails(workspaceFolder, UUID.fromString(issueKey))
      .thenApply(detailsResponse -> createShowRuleDescriptionParams(detailsResponse.getDetails()))
      .exceptionally(e -> {
        var message = "Can't show issue details for unknown issue with key: " + issueKey;
        client.showMessage(new MessageParams(MessageType.Error, message));
        logOutput.errorWithStackTrace(message, e);
        return null;
      });
  }

  public CompletableFuture<ShowRuleDescriptionParams> getEffectiveRuleDetails(String fileUri, String ruleKey) {
    var workspaceFolder = getWorkspaceFolderFromFileUri(fileUri);

    return backendServiceFacade.getBackendService().getEffectiveRuleDetails(workspaceFolder, ruleKey, "")
      .thenApply(detailsResponse -> {
        var details = detailsResponse.details();
        return createShowRuleDescriptionParams(details);
      })
      .exceptionally(e -> {
        var message = "Can't show issue details for unknown rule with key: " + ruleKey;
        client.showMessage(new MessageParams(MessageType.Error, message));
        logOutput.errorWithStackTrace(message, e);
        return null;
      });
  }

  private @Nullable String getWorkspaceFolderFromFileUri(String fileUri) {
    return Optional.of(fileUri)
      .map(URI::create)
      .map(workspaceFoldersManager::findFolderForFile)
      .filter(Optional::isPresent)
      .map(w -> w.get().getUri().toString())
      .orElse(null);
  }

  private void showStandaloneRuleDescription(String ruleKey, GetStandaloneRuleDescriptionResponse ruleDetails) {
    var ruleDefinition = ruleDetails.getRuleDefinition();
    var paramDetails = ruleDefinition.getParamsByKey();
    var showRuleDescriptionParams = createShowRuleDescriptionParams(ruleDefinition, paramDetails, ruleDetails.getDescription().getLsp4jEither(), ruleKey,
      "");
    client.showRuleDescription(showRuleDescriptionParams);
  }

  private static ShowRuleDescriptionParams createShowRuleDescriptionParams(RuleDefinitionDto ruleDetailsDto,
    Map<String, RuleParamDefinitionDto> params, Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description,
    String ruleKey, String ruleContextKey) {
    var ruleName = ruleDetailsDto.getName();
    var language = Language.valueOf(ruleDetailsDto.getLanguage().name());
    var languageKey = SonarLanguage.valueOf(language.name()).getSonarLanguageKey();
    var cleanCodeAttributeAndCategory = getCleanCodeAttributeAndCategory(ruleDetailsDto.getCleanCodeAttribute(),
      CleanCodeAttributeCategory.valueOf(fromDto(ruleDetailsDto.getCleanCodeAttribute()).getCategory().name())
    );
    var cleanCodeAttributeParam = cleanCodeAttributeAndCategory.getLeft();
    var cleanCodeAttributeCategoryParam = cleanCodeAttributeAndCategory.getRight();
    Map<String, String> impacts = ruleDetailsDto.getSoftwareImpacts().stream()
      .collect(Collectors.toMap((ImpactDto impactDto) -> EnumLabelsMapper.softwareQualityToLabel(impactDto.getSoftwareQuality()),
        impactDto -> EnumLabelsMapper.impactSeverityToLabel(impactDto.getImpactSeverity()), (r1, r2) -> r2));
    var htmlDescription = getHtmlDescription(description);
    var htmlDescriptionTabs = getHtmlDescriptionTabs(description, ruleContextKey);
    return new ShowRuleDescriptionParams(ruleKey, ruleName, htmlDescription, htmlDescriptionTabs, languageKey, params,
      org.sonarsource.sonarlint.core.rpc.protocol.common.Either.forRight(new MQRModeDetails(cleanCodeAttributeParam, cleanCodeAttributeCategoryParam, impacts)));
  }

  private static ShowRuleDescriptionParams createShowRuleDescriptionParams(EffectiveRuleDetailsDto details) {
    var isMQRMode = details.getSeverityDetails().isRight();
    var ruleName = details.getName();
    var type = !isMQRMode ? details.getSeverityDetails().getLeft().getType().toString() : null;
    var severity = !isMQRMode ? details.getSeverityDetails().getLeft().getSeverity().toString() : null;
    var language = Language.valueOf(details.getLanguage().name());
    var languageKey = SonarLanguage.valueOf(language.name()).getSonarLanguageKey();
    var cleanCodeAttributeAndCategory = isMQRMode ?
      new ImmutablePair<>(cleanCodeAttributeToLabel(details.getSeverityDetails().getRight().getCleanCodeAttribute()),
        fromDto(details.getSeverityDetails().getRight().getCleanCodeAttribute()).getCategory().getLabel()) :
      new ImmutablePair<>("", "");
    var cleanCodeAttributeParam = cleanCodeAttributeAndCategory.getLeft();
    var cleanCodeAttributeCategoryParam = cleanCodeAttributeAndCategory.getRight();
    Map<String, String> impacts = isMQRMode ?
      details.getSeverityDetails().getRight().getImpacts().stream()
        .collect(Collectors.toMap((ImpactDto impactDto) -> EnumLabelsMapper.softwareQualityToLabel(impactDto.getSoftwareQuality()),
          impactDto -> EnumLabelsMapper.impactSeverityToLabel(impactDto.getImpactSeverity()), (r1, r2) -> r2)) :
      Map.of();
    var htmlDescription = getHtmlDescription(details.getDescription().getLsp4jEither());
    var htmlDescriptionTabs = getHtmlDescriptionTabs(details.getDescription().getLsp4jEither(), "");
    return new ShowRuleDescriptionParams(details.getKey(), ruleName, htmlDescription, htmlDescriptionTabs, languageKey, details.getParams(),
      isMQRMode ? org.sonarsource.sonarlint.core.rpc.protocol.common.Either.forRight(new MQRModeDetails(cleanCodeAttributeParam, cleanCodeAttributeCategoryParam, impacts)) :
        org.sonarsource.sonarlint.core.rpc.protocol.common.Either.forLeft(new StandardModeDetails(type, severity)));
  }

  private static ShowRuleDescriptionParams createShowRuleDescriptionParams(EffectiveIssueDetailsDto issueDetailsDto) {
    var isMQRMode = issueDetailsDto.getSeverityDetails().isRight();
    var ruleName = issueDetailsDto.getName();
    var type = !isMQRMode ? issueDetailsDto.getSeverityDetails().getLeft().getType().toString() : null;
    var severity = !isMQRMode ? issueDetailsDto.getSeverityDetails().getLeft().getSeverity().toString() : null;
    var language = Language.valueOf(issueDetailsDto.getLanguage().name());
    var languageKey = SonarLanguage.valueOf(language.name()).getSonarLanguageKey();
    var cleanCodeAttributeAndCategory = isMQRMode ?
      new ImmutablePair<>(cleanCodeAttributeToLabel(issueDetailsDto.getSeverityDetails().getRight().getCleanCodeAttribute()),
        fromDto(issueDetailsDto.getSeverityDetails().getRight().getCleanCodeAttribute()).getCategory().getLabel()) :
      new ImmutablePair<>("", "");
    var cleanCodeAttributeParam = cleanCodeAttributeAndCategory.getLeft();
    var cleanCodeAttributeCategoryParam = cleanCodeAttributeAndCategory.getRight();
    Map<String, String> impacts = isMQRMode ?
      issueDetailsDto.getSeverityDetails().getRight().getImpacts().stream()
        .collect(Collectors.toMap((ImpactDto impactDto) -> EnumLabelsMapper.softwareQualityToLabel(impactDto.getSoftwareQuality()),
          impactDto -> EnumLabelsMapper.impactSeverityToLabel(impactDto.getImpactSeverity()), (r1, r2) -> r2)) :
      Map.of();
    var htmlDescription = getHtmlDescription(issueDetailsDto.getDescription().getLsp4jEither());
    var htmlDescriptionTabs = getHtmlDescriptionTabs(issueDetailsDto.getDescription().getLsp4jEither(), issueDetailsDto.getRuleDescriptionContextKey());
    return new ShowRuleDescriptionParams(issueDetailsDto.getRuleKey(), ruleName, htmlDescription, htmlDescriptionTabs, languageKey, issueDetailsDto.getParams(),
      isMQRMode ? org.sonarsource.sonarlint.core.rpc.protocol.common.Either.forRight(new MQRModeDetails(cleanCodeAttributeParam, cleanCodeAttributeCategoryParam, impacts)) :
        org.sonarsource.sonarlint.core.rpc.protocol.common.Either.forLeft(new StandardModeDetails(type, severity)));
  }

  private static ImmutablePair<String, String> getCleanCodeAttributeAndCategory(@Nullable CleanCodeAttribute cleanCodeAttribute,
    @Nullable CleanCodeAttributeCategory cleanCodeAttributeCategory) {
    if (cleanCodeAttribute != null) {
      var attributeLabel = cleanCodeAttributeToLabel(cleanCodeAttribute);
      var attributeCategoryLabel = cleanCodeAttributeCategory != null ? EnumLabelsMapper.cleanCodeAttributeCategoryToLabel(cleanCodeAttributeCategory) : "";
      return new ImmutablePair<>(attributeLabel, attributeCategoryLabel);
    }
    return new ImmutablePair<>("", "");
  }

  public void executeCommand(ExecuteCommandParams params, CancelChecker cancelToken) {
    cancelToken.checkCanceled();
    switch (params.getCommand()) {
      case SONARLINT_QUICK_FIX_APPLIED:
        telemetry.addQuickFixAppliedForRule(getAsString(params.getArguments().get(0)));
        break;
      case SONARLINT_OPEN_STANDALONE_RULE_DESCRIPTION_COMMAND:
        handleOpenStandaloneRuleDescriptionCommand(params);
        break;
      case SONARLINT_SHOW_ISSUE_DETAILS_FROM_CODE_ACTION_COMMAND:
        handleShowIssueDetailsFromCodeActionCommand(params);
        break;
      case SONARLINT_SHOW_RULE_DESC_COMMAND:
        handleShowRuleDescriptionCommand(params);
        break;
      case SONARLINT_BROWSE_TAINT_VULNERABILITY:
        handleBrowseTaintVulnerability(params);
        break;
      case SONARLINT_SHOW_TAINT_VULNERABILITY_FLOWS:
        handleShowTaintVulnerabilityFlows(params);
        break;
      case SONARLINT_SHOW_SECURITY_HOTSPOT_FLOWS:
        handleShowHotspotFlows(params);
        break;
      default:
        throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unsupported command: " + params.getCommand(), null));
    }
  }

  private void handleOpenStandaloneRuleDescriptionCommand(ExecuteCommandParams params) {
    var ruleKey = getAsString(params.getArguments().get(0));
    openStandaloneRuleDescription(ruleKey);
  }

  private void handleShowIssueDetailsFromCodeActionCommand(ExecuteCommandParams params) {
    var issueKey = getAsString(params.getArguments().get(0));
    var fileUri = getAsString(params.getArguments().get(1));
    getFindingDetails(fileUri, issueKey)
      .thenAccept(client::showRuleDescription);
  }

  // This method is used for showing a rule description during 'Open issue in IDE' flow, where local issue does not necessarily exist
  private void handleShowRuleDescriptionCommand(ExecuteCommandParams params) {
    var ruleKey = getAsString(params.getArguments().get(0));
    var fileUri = getAsString(params.getArguments().get(1));
    getEffectiveRuleDetails(fileUri, ruleKey)
      .thenAccept(client::showRuleDescription);
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
        client.showIssueOrHotspot(ShowAllLocationsCommand.params(issue, connectionId));
      });
  }

  private void handleShowHotspotFlows(ExecuteCommandParams params) {
    var fileUri = getAsString(params.getArguments().get(0));
    var hotspotKey = getAsString(params.getArguments().get(1));
    var hotspot = securityHotspotsCache.get(create(fileUri)).get(hotspotKey);
    if (hotspot == null) {
      logOutput.error("Hotspot is not found during showing flows");
      return;
    }
    client.showIssueOrHotspot(ShowAllLocationsCommand.params(hotspot));
  }

  // https://github.com/eclipse/lsp4j/issues/126
  private static String getAsString(Object jsonPrimitive) {
    return ((JsonPrimitive) jsonPrimitive).getAsString();
  }

  // visible for testing
  static String getHtmlDescription(Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description) {
    if (description.isLeft()) {
      return description.getLeft().getHtmlContent();
    } else {
      return StringUtils.defaultIfEmpty(description.getRight().getIntroductionHtmlContent(), "");
    }
  }

  static SonarLintExtendedLanguageClient.RuleDescriptionTab[] getHtmlDescriptionTabs(Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description,
    @Nullable String ruleContextKey) {
    if (description.isLeft()) {
      return new SonarLintExtendedLanguageClient.RuleDescriptionTab[0];
    } else {
      return description.getRight().getTabs().stream()
        .map((RuleDescriptionTabDto tab) -> getRuleDescriptionTab(tab, ruleContextKey))
        .toArray(SonarLintExtendedLanguageClient.RuleDescriptionTab[]::new);
    }
  }

  private static SonarLintExtendedLanguageClient.RuleDescriptionTab getRuleDescriptionTab(RuleDescriptionTabDto tab, @Nullable String ruleContextKey) {
    var title = tab.getTitle();
    var content = tab.getContent();
    if (content.isLeft()) {
      var htmlContent = content.getLeft().getHtmlContent();
      var nonContextualDescriptions = new SonarLintExtendedLanguageClient.RuleDescriptionTabNonContextual(htmlContent);
      return new SonarLintExtendedLanguageClient.RuleDescriptionTab(title, nonContextualDescriptions);
    } else {
      var defaultContextKey = StringUtils.isEmpty(ruleContextKey) ? content.getRight().getDefaultContextKey() : ruleContextKey;
      var contextualDescriptions = content.getRight().getContextualSections().stream()
        .map(o -> new SonarLintExtendedLanguageClient.RuleDescriptionTabContextual(o.getHtmlContent(), o.getContextKey(), o.getDisplayName()))
        .toArray(SonarLintExtendedLanguageClient.RuleDescriptionTabContextual[]::new);
      return new SonarLintExtendedLanguageClient.RuleDescriptionTab(title, contextualDescriptions, defaultContextKey);
    }
  }

}
