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
package org.sonarsource.sonarlint.ls.telemetry;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddReportedRulesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisDoneOnSingleLanguageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.telemetry.InternalDebug;
import org.sonarsource.sonarlint.core.telemetry.TelemetryHttpClient;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;
import org.sonarsource.sonarlint.ls.NodeJsRuntime;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;
import org.sonarsource.sonarlint.ls.util.Utils;

public class SonarLintTelemetry implements WorkspaceSettingsChangeListener {
  public static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final BackendServiceFacade backendServiceFacade;
  private final LanguageClientLogOutput logOutput;

  public SonarLintTelemetry(BackendServiceFacade backendServiceFacade) {
    this.backendServiceFacade = backendServiceFacade;
    this.logOutput = logOutput;
  }

  private void optOut(boolean optOut) {
    backendServiceFacade.getTelemetryStatus()
      .thenAccept(status -> {
        if (optOut) {
          if (status.isEnabled()) {
            logOutput.debug("Disabling telemetry");
            backendServiceFacade.disableTelemetry();
          }
        } else {
          if (!status.isEnabled()) {
            logOutput.debug("Enabling telemetry");
            backendServiceFacade.enableTelemetry();
          }
        }
      });
  }

  public boolean enabled() {
    return !"true".equals(System.getProperty(DISABLE_PROPERTY_KEY));
  }

  public void analysisDoneOnMultipleFiles() {
    if (enabled()) {
      backendServiceFacade.getTelemetryService().analysisDoneOnMultipleFiles();
    }
  }

  public void analysisDoneOnSingleLanguage(Language language, int analysisTimeMs) {
    if (enabled()) {
      backendServiceFacade.getTelemetryService()
        .analysisDoneOnSingleLanguage(new AnalysisDoneOnSingleLanguageParams(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.valueOf(language.name()), analysisTimeMs));
    }
  }

  public void addReportedRules(Set<String> ruleKeys) {
    if (enabled()) {
      backendServiceFacade.getTelemetryService().addReportedRules(new AddReportedRulesParams(ruleKeys));
    }
  }

  public void devNotificationsClicked(String eventType) {
    if (enabled()) {
      backendServiceFacade.getTelemetryService().devNotificationsClicked(new DevNotificationsClickedParams(eventType));
    }
  }

  public void taintVulnerabilitiesInvestigatedLocally() {
    if (enabled()) {
      backendServiceFacade.getTelemetryService().taintVulnerabilitiesInvestigatedLocally();
    }
  }

  public void taintVulnerabilitiesInvestigatedRemotely() {
    if (enabled()) {
      backendServiceFacade.getTelemetryService().taintVulnerabilitiesInvestigatedRemotely();
    }
  }

  public void addQuickFixAppliedForRule(String ruleKey) {
    if (enabled()) {
      backendServiceFacade.getTelemetryService().addQuickFixAppliedForRule(new AddQuickFixAppliedForRuleParams(ruleKey));
    }
  }

  public void helpAndFeedbackLinkClicked(String itemId) {
    if (enabled()) {
      backendServiceFacade.getTelemetryService().helpAndFeedbackLinkClicked(new HelpAndFeedbackClickedParams(itemId));
    }
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    optOut(newValue.isDisableTelemetry());
  }
}
