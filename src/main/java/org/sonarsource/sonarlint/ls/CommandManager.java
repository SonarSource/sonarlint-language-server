/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;

import static java.net.URI.create;
import static org.sonarsource.sonarlint.ls.AnalysisManager.SONARLINT_SOURCE;

public class CommandManager {

  // Server side
  static final String SONARLINT_OPEN_STANDALONE_RULE_DESCRIPTION_COMMAND = "SonarLint.OpenStandaloneRuleDesc";
  static final String SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND = "SonarLint.OpenRuleDescCodeAction";
  static final String SONARLINT_UPDATE_ALL_BINDINGS_COMMAND = "SonarLint.UpdateAllBindings";
  static final List<String> SONARLINT_SERVERSIDE_COMMANDS = Arrays.asList(
    SONARLINT_UPDATE_ALL_BINDINGS_COMMAND, SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND, SONARLINT_OPEN_STANDALONE_RULE_DESCRIPTION_COMMAND);
  // Client side
  static final String SONARLINT_DEACTIVATE_RULE_COMMAND = "SonarLint.DeactivateRule";

  private final SonarLintExtendedLanguageClient client;
  private final ProjectBindingManager bindingManager;
  private final AnalysisManager analysisManager;

  CommandManager(SonarLintExtendedLanguageClient client, ProjectBindingManager bindingManager, AnalysisManager analysisManager) {
    this.client = client;
    this.bindingManager = bindingManager;
    this.analysisManager = analysisManager;
  }

  public List<Either<Command, CodeAction>> computeCodeActions(CodeActionParams params, CancelChecker cancelToken) {
    List<Either<Command, CodeAction>> commands = new ArrayList<>();
    URI uri = create(params.getTextDocument().getUri());
    Optional<ProjectBindingWrapper> binding = bindingManager.getBinding(uri);
    for (Diagnostic d : params.getContext().getDiagnostics()) {
      cancelToken.checkCanceled();
      if (SONARLINT_SOURCE.equals(d.getSource())) {
        String ruleKey = d.getCode().getLeft();
        cancelToken.checkCanceled();
        String titleShowRuleDesc = String.format("Open description of SonarLint rule '%s'", ruleKey);
        CodeAction actionShowRuleDesc = new CodeAction(titleShowRuleDesc);
        actionShowRuleDesc
          .setCommand(new Command(titleShowRuleDesc, SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND, Arrays.asList(ruleKey, params.getTextDocument().getUri())));
        actionShowRuleDesc.setDiagnostics(Arrays.asList(d));
        actionShowRuleDesc.setKind(CodeActionKind.QuickFix);
        commands.add(Either.forRight(actionShowRuleDesc));
        if (!binding.isPresent()) {
          String titleDeactivate = String.format("Deactivate rule '%s'", ruleKey);
          CodeAction actionDeactivate = new CodeAction(titleDeactivate);
          actionDeactivate.setCommand(new Command(titleDeactivate, SONARLINT_DEACTIVATE_RULE_COMMAND, Collections.singletonList(ruleKey)));
          actionDeactivate.setDiagnostics(Arrays.asList(d));
          actionDeactivate.setKind(CodeActionKind.QuickFix);
          commands.add(Either.forRight(actionDeactivate));
        }
      }
    }
    return commands;
  }

  public Map<String, List<Rule>> listAllStandaloneRules() {
    Map<String, List<Rule>> result = new HashMap<>();
    Map<String, String> languagesNameByKey = analysisManager.getOrCreateStandaloneEngine().getAllLanguagesNameByKey();
    analysisManager.getOrCreateStandaloneEngine().getAllRuleDetails()
      .forEach(d -> {
        String languageName = languagesNameByKey.get(d.getLanguageKey());
        if (!result.containsKey(languageName)) {
          result.put(languageName, new ArrayList<>());
        }
        result.get(languageName).add(Rule.of(d));
      });
    return result;
  }

  private void openRuleDescription(@Nullable ProjectBindingWrapper binding, String ruleKey) {
    RuleDetails ruleDetails;
    if (binding == null) {
      ruleDetails = analysisManager.getOrCreateStandaloneEngine().getRuleDetails(ruleKey)
        .orElseThrow(() -> unknownRule(ruleKey));
    } else {
      ConnectedSonarLintEngine engine = binding.getEngine();
      try {
        ruleDetails = engine.getRuleDetails(ruleKey);
      } catch (IllegalArgumentException e) {
        throw unknownRule(ruleKey);
      }
    }
    String ruleName = ruleDetails.getName();
    String htmlDescription = getHtmlDescription(ruleDetails);
    String type = ruleDetails.getType();
    String severity = ruleDetails.getSeverity();
    client.showRuleDescription(new ShowRuleDescriptionParams(ruleKey, ruleName, htmlDescription, type, severity));
  }

  private ResponseErrorException unknownRule(String ruleKey) {
    return new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unknown rule with key: " + ruleKey, null));
  }

  public void executeCommand(ExecuteCommandParams params, CancelChecker cancelToken) {
    switch (params.getCommand()) {
      case SONARLINT_UPDATE_ALL_BINDINGS_COMMAND:
        bindingManager.updateAllBindings();
        break;
      case SONARLINT_OPEN_STANDALONE_RULE_DESCRIPTION_COMMAND:
        handleOpenStandaloneRuleDescriptionCommand(params);
        break;
      case SONARLINT_OPEN_RULE_DESCRIPTION_FROM_CODE_ACTION_COMMAND:
        handleOpenRuleDescriptionFromCodeActionCommand(params);
        break;
      default:
        throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unsupported command: " + params.getCommand(), null));
    }
  }

  private void handleOpenStandaloneRuleDescriptionCommand(ExecuteCommandParams params) {
    String ruleKey = getAsString(params.getArguments().get(0));
    openRuleDescription(null, ruleKey);
  }

  private void handleOpenRuleDescriptionFromCodeActionCommand(ExecuteCommandParams params) {
    String ruleKey = getAsString(params.getArguments().get(0));
    URI uri = create(getAsString(params.getArguments().get(1)));
    Optional<ProjectBindingWrapper> binding = bindingManager.getBinding(uri);
    openRuleDescription(binding.orElse(null), ruleKey);
  }

  // https://github.com/eclipse/lsp4j/issues/126
  private static String getAsString(Object jsonPrimitive) {
    return ((JsonPrimitive) jsonPrimitive).getAsString();
  }

  // visible for testing
  static String getHtmlDescription(RuleDetails ruleDetails) {
    String htmlDescription = ruleDetails.getHtmlDescription();
    String extendedDescription = ruleDetails.getExtendedDescription();
    if (!extendedDescription.isEmpty()) {
      htmlDescription += "<div>" + extendedDescription + "</div>";
    }
    return htmlDescription;
  }

}
