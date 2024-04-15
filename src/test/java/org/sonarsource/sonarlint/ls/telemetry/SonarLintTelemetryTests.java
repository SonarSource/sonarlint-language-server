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

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.GetStatusResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddReportedRulesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisDoneOnSingleLanguageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarLintTelemetryTests {
  private SonarLintTelemetry telemetry;
  private BackendServiceFacade backendServiceFacade;
  private BackendService backendService;
  private TelemetryRpcService telemetryService;

  @RegisterExtension
  public SonarLintLogTester logTester = new SonarLintLogTester();

  @BeforeEach
  public void setUp() {
    this.backendServiceFacade = mock(BackendServiceFacade.class);
    this.backendService = mock(BackendService.class);
    this.telemetryService = mock(TelemetryRpcService.class);
    when(backendServiceFacade.getBackendService()).thenReturn(backendService);
    when(backendService.getTelemetryService()).thenReturn(telemetryService);
    this.telemetry = createTelemetry();
  }

  @AfterEach
  public void after() {
    System.clearProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY);
  }

  private SonarLintTelemetry createTelemetry() {
    return  new SonarLintTelemetry(backendServiceFacade, logTester.getLogger());
  }

  @Test
  void disable_property_should_disable_telemetry() {
    assertThat(createTelemetry().enabled()).isTrue();
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    assertThat(createTelemetry().enabled()).isFalse();
  }

  @Test
  void optOut_should_trigger_disable_telemetry() {
    when(backendService.getTelemetryStatus()).thenReturn(CompletableFuture.completedFuture(new GetStatusResponse(true)));
    telemetry.onChange(newWorkspaceSettingsWithTelemetrySetting(false), newWorkspaceSettingsWithTelemetrySetting(true));

    verify(backendService).disableTelemetry();
  }

  @Test
  void should_not_opt_out_twice() {
    when(backendService.getTelemetryStatus()).thenReturn(CompletableFuture.completedFuture(new GetStatusResponse(false)));
    telemetry.onChange(null, newWorkspaceSettingsWithTelemetrySetting(true));

    verify(backendService, never()).disableTelemetry();
  }

  @Test
  void optIn_should_trigger_enable_telemetry() {
    when(backendService.getTelemetryStatus()).thenReturn(CompletableFuture.completedFuture(new GetStatusResponse(false)));
    telemetry.onChange(null, newWorkspaceSettingsWithTelemetrySetting(false));

    verify(backendService).enableTelemetry();
  }

  @Test
  void analysisDoneOnMultipleFiles_should_trigger_analysisDoneOnMultipleFiles_when_enabled() {
    telemetry.analysisDoneOnMultipleFiles();
    verify(telemetryService).analysisDoneOnMultipleFiles();
  }

  @Test
  void analysisDoneOnMultipleFiles_should_not_trigger_analysisDoneOnMultipleFiles_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.analysisDoneOnMultipleFiles();

    verify(telemetryService, never()).analysisDoneOnMultipleFiles();
  }

  @Test
  void analysisDoneOnSingleFile_should_trigger_analysisDoneOnSingleFile_when_enabled() {
    ArgumentCaptor<AnalysisDoneOnSingleLanguageParams> argument = ArgumentCaptor.forClass(AnalysisDoneOnSingleLanguageParams.class);
    telemetry.analysisDoneOnSingleLanguage(SonarLanguage.JAVA, 1000);

    verify(telemetryService).analysisDoneOnSingleLanguage(argument.capture());
    assertThat(argument.getValue().getAnalysisTimeMs()).isEqualTo(1000);
    assertThat(argument.getValue().getLanguage()).isEqualTo(Language.JAVA);
  }

  @Test
  void analysisDoneOnSingleFile_should_not_trigger_analysisDoneOnSingleFile_when_language_is_null() {
    telemetry.analysisDoneOnSingleLanguage(null, 1000);

    verify(telemetryService, never()).analysisDoneOnSingleLanguage(any());

  }

  @Test
  void analysisDoneOnSingleFile_should_not_trigger_analysisDoneOnSingleFile_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.analysisDoneOnSingleLanguage(SonarLanguage.JAVA, 1000);

    verify(telemetryService, never()).analysisDoneOnSingleLanguage(any());

  }

  @Test
  void devNotificationsClicked_when_enabled() {
    ArgumentCaptor<DevNotificationsClickedParams> argument = ArgumentCaptor.forClass(DevNotificationsClickedParams.class);
    telemetry.devNotificationsClicked("eventType");

    verify(telemetryService).devNotificationsClicked(argument.capture());
    assertThat(argument.getValue().getEventType()).isEqualTo("eventType");
  }

  @Test
  void devNotificationsClicked_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.analysisDoneOnSingleLanguage(SonarLanguage.JAVA, 1000);

    verify(telemetryService, never()).devNotificationsClicked(any());
  }

  @Test
  void taintVulnerabilitiesInvestigatedLocally_when_enabled() {
    telemetry.taintVulnerabilitiesInvestigatedLocally();
    verify(telemetryService).taintVulnerabilitiesInvestigatedLocally();
  }

  @Test
  void taintVulnerabilitiesInvestigatedLocally_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.taintVulnerabilitiesInvestigatedLocally();

    verify(telemetryService, never()).taintVulnerabilitiesInvestigatedLocally();
  }

  @Test
  void taintVulnerabilitiesInvestigatedRemotely_when_enabled() {
    telemetry.taintVulnerabilitiesInvestigatedRemotely();
    verify(telemetryService).taintVulnerabilitiesInvestigatedRemotely();
  }

  @Test
  void taintVulnerabilitiesInvestigatedRemotely_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.taintVulnerabilitiesInvestigatedRemotely();

    verify(telemetryService, never()).taintVulnerabilitiesInvestigatedRemotely();
  }

  @Test
  void addQuickFixAppliedForRule_when_enabled() {
    ArgumentCaptor<AddQuickFixAppliedForRuleParams> argument = ArgumentCaptor.forClass(AddQuickFixAppliedForRuleParams.class);
    telemetry.addQuickFixAppliedForRule("repo:key");

    verify(telemetryService).addQuickFixAppliedForRule(argument.capture());
    assertThat(argument.getValue().getRuleKey()).isEqualTo("repo:key");
  }

  @Test
  void addQuickFixAppliedForRule_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.addQuickFixAppliedForRule("repo:key");

    verify(telemetryService, never()).addQuickFixAppliedForRule(any());
  }

  @Test
  void helpAndFeedbackLinkClicked_when_disabled() {
    ArgumentCaptor<HelpAndFeedbackClickedParams> argument = ArgumentCaptor.forClass(HelpAndFeedbackClickedParams.class);
    telemetry.helpAndFeedbackLinkClicked("docs");

    verify(telemetryService).helpAndFeedbackLinkClicked(argument.capture());
    assertThat(argument.getValue().getItemId()).isEqualTo("docs");
  }

  @Test
  void helpAndFeedbackLinkClicked_when_enabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.helpAndFeedbackLinkClicked("docs");

    verify(telemetryService, never()).helpAndFeedbackLinkClicked(any());
  }

  @Test
  void addedAutomaticBindings_when_disabled() {
    telemetry.addedAutomaticBindings();

    verify(telemetryService).addedAutomaticBindings();
  }

  @Test
  void addedAutomaticBindings_when_enabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.addedAutomaticBindings();

    verify(telemetryService, never()).addedAutomaticBindings();
  }

  @Test
  void addedImportedBindings_when_disabled() {
    telemetry.addedImportedBindings();

    verify(telemetryService).addedImportedBindings();
  }

  @Test
  void addedImportedBindings_when_enabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.addedImportedBindings();

    verify(telemetryService, never()).addedImportedBindings();
  }

  @Test
  void addedManualBindings_when_disabled() {
    telemetry.addedManualBindings();

    verify(telemetryService).addedManualBindings();
  }

  @Test
  void addedManualBindings_when_enabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.addedManualBindings();

    verify(telemetryService, never()).addedManualBindings();
  }

  @Test
  void addReportedRules() {
    var rule = "ruleKey";
    var reportedRuleKeys = Collections.singleton(rule);
    ArgumentCaptor<AddReportedRulesParams> argument = ArgumentCaptor.forClass(AddReportedRulesParams.class);
    telemetry.addReportedRules(reportedRuleKeys);

    verify(telemetryService).addReportedRules(argument.capture());
    assertThat(argument.getValue().getRuleKeys()).containsOnly("ruleKey");
  }

  private static WorkspaceSettings newWorkspaceSettingsWithTelemetrySetting(boolean disableTelemetry) {
    return new WorkspaceSettings(disableTelemetry, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(),
      Collections.emptyMap(), false, false, "/path/to/node", false);
  }
}
