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
package org.sonarsource.sonarlint.ls.telemetry;

import java.util.Set;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddReportedRulesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisDoneOnSingleLanguageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

public class SonarLintTelemetry implements WorkspaceSettingsChangeListener {
  public static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";
  private final BackendServiceFacade backendServiceFacade;
  private final LanguageClientLogOutput logOutput;

  public SonarLintTelemetry(BackendServiceFacade backendServiceFacade, LanguageClientLogOutput logOutput) {
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
    return !"true".equals(System.getProperty(DISABLE_PROPERTY_KEY));
  }

  public void analysisDoneOnMultipleFiles() {
    if (enabled()) {
      backendServiceFacade.getBackendService().getTelemetryService().analysisDoneOnMultipleFiles();
    }
  }

  public void analysisDoneOnSingleLanguage(SonarLanguage language, int analysisTimeMs) {
    if (enabled()) {
      backendServiceFacade.getBackendService().getTelemetryService()
        .analysisDoneOnSingleLanguage(new AnalysisDoneOnSingleLanguageParams(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.valueOf(language.name()), analysisTimeMs));
    }
  }

  public void addReportedRules(Set<String> ruleKeys) {
    if (enabled()) {
      backendServiceFacade.getBackendService().getTelemetryService().addReportedRules(new AddReportedRulesParams(ruleKeys));
    }
  }

  public void devNotificationsClicked(String eventType) {
    if (enabled()) {
      backendServiceFacade.getBackendService().getTelemetryService().devNotificationsClicked(new DevNotificationsClickedParams(eventType));
    }
  }

  public void taintVulnerabilitiesInvestigatedLocally() {
    if (enabled()) {
      backendServiceFacade.getBackendService().getTelemetryService().taintVulnerabilitiesInvestigatedLocally();
    }
  }

  public void taintVulnerabilitiesInvestigatedRemotely() {
    if (enabled()) {
      backendServiceFacade.getBackendService().getTelemetryService().taintVulnerabilitiesInvestigatedRemotely();
    }
  }

  public void addQuickFixAppliedForRule(String ruleKey) {
    if (enabled()) {
      backendServiceFacade.getBackendService().getTelemetryService().addQuickFixAppliedForRule(new AddQuickFixAppliedForRuleParams(ruleKey));
    }
  }

  public void helpAndFeedbackLinkClicked(String itemId) {
    if (enabled()) {
      backendServiceFacade.getBackendService().getTelemetryService().helpAndFeedbackLinkClicked(new HelpAndFeedbackClickedParams(itemId));
    }
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    optOut(newValue.isDisableTelemetry());
  }
}
