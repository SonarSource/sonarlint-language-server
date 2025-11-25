/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.ls.telemetry;

import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AcceptedBindingSuggestionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingTriggeredParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FindingsFilteredParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionResolvedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ToolCalledParams;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

public class SonarLintTelemetry implements WorkspaceSettingsChangeListener {
  public static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";
  private final BackendServiceFacade backendServiceFacade;
  private final LanguageClientLogger logOutput;

  public SonarLintTelemetry(BackendServiceFacade backendServiceFacade, LanguageClientLogger logOutput) {
    this.backendServiceFacade = backendServiceFacade;
    this.logOutput = logOutput;
  }

  private void optOut(boolean optOut) {
    backendServiceFacade.getBackendService().getTelemetryStatus()
      .thenAccept(status -> {
        if (optOut) {
          if (status.isEnabled()) {
            logOutput.debug("Disabling telemetry");
            backendServiceFacade.getBackendService().disableTelemetry();
          }
        } else {
          if (!status.isEnabled()) {
            logOutput.debug("Enabling telemetry");
            backendServiceFacade.getBackendService().enableTelemetry();
          }
        }
      });
  }

  public boolean enabled() {
    var telemetrySystemPropertyOverride = System.getProperty(DISABLE_PROPERTY_KEY);
    var telemetryDisabledBySystemProperty = "true".equals(telemetrySystemPropertyOverride);
    if (telemetryDisabledBySystemProperty) {
      logOutput.debug("Telemetry is disabled by system property");
    }
    return !telemetryDisabledBySystemProperty;
  }

  public void devNotificationsClicked(String eventType) {
    actIfEnabled(telemetryRpcService -> telemetryRpcService.devNotificationsClicked(new DevNotificationsClickedParams(eventType)));
  }

  public void taintVulnerabilitiesInvestigatedLocally() {
    actIfEnabled(telemetryRpcService -> {
      telemetryRpcService.taintVulnerabilitiesInvestigatedLocally();
      telemetryRpcService.taintInvestigatedLocally();
    });
  }

  public void taintVulnerabilitiesInvestigatedRemotely() {
    actIfEnabled(telemetryRpcService -> {
      telemetryRpcService.taintVulnerabilitiesInvestigatedRemotely();
      telemetryRpcService.taintInvestigatedRemotely();
    });
  }

  public void issueInvestigatedLocally() {
    actIfEnabled(TelemetryRpcService::issueInvestigatedLocally);
  }

  public void addQuickFixAppliedForRule(String ruleKey) {
    actIfEnabled(telemetryRpcService -> telemetryRpcService.addQuickFixAppliedForRule(new AddQuickFixAppliedForRuleParams(ruleKey)));
  }

  public void helpAndFeedbackLinkClicked(String itemId) {
    actIfEnabled(telemetryRpcService -> telemetryRpcService.helpAndFeedbackLinkClicked(new HelpAndFeedbackClickedParams(itemId)));
  }

  public void toolCalled(String toolName, boolean success) {
    actIfEnabled(telemetryRpcService -> telemetryRpcService.toolCalled(new ToolCalledParams(toolName, success)));
  }

  public void addedManualBindings() {
    actIfEnabled(TelemetryRpcService::addedManualBindings);
  }

  public void acceptedBindingSuggestion(AcceptedBindingSuggestionParams params) {
    actIfEnabled(telemetryRpcService -> telemetryRpcService.acceptedBindingSuggestion(params));
  }

  public void fixSuggestionResolved(FixSuggestionResolvedParams params) {
    actIfEnabled(telemetryRpcService -> telemetryRpcService.fixSuggestionResolved(params));
  }

  public void findingFilterApplied(FindingsFilteredParams params) {
    actIfEnabled(telemetryRpcService -> telemetryRpcService.findingsFiltered(params));
  }

  public void dependencyRiskIssueInvestigatedLocally() {
    actIfEnabled(TelemetryRpcService::dependencyRiskInvestigatedLocally);
  }

  public void wholeFolderHotspotsAnalysisTriggered() {
    actIfEnabled(
      telemetryRpcService -> telemetryRpcService.analysisReportingTriggered(new AnalysisReportingTriggeredParams(AnalysisReportingType.WHOLE_FOLDER_HOTSPOTS_SCAN_TYPE)));
  }

  public void currentFileAnalysisTriggered() {
    actIfEnabled(telemetryRpcService -> telemetryRpcService.analysisReportingTriggered(new AnalysisReportingTriggeredParams(AnalysisReportingType.CURRENT_FILE_ANALYSIS_TYPE)));
  }

  private void actIfEnabled(Consumer<TelemetryRpcService> action) {
    if (enabled()) {
      action.accept(backendServiceFacade.getBackendService().getTelemetryService());
    }
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    optOut(newValue.isDisableTelemetry());
  }
}
