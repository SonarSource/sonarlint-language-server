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

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.GetStatusResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingTriggeredParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FindingsFilteredParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionResolvedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ToolCalledParams;
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
    return new SonarLintTelemetry(backendServiceFacade, logTester.getLogger());
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
  void devNotificationsClicked_when_enabled() {
    ArgumentCaptor<DevNotificationsClickedParams> argument = ArgumentCaptor.forClass(DevNotificationsClickedParams.class);
    telemetry.devNotificationsClicked("eventType");

    verify(telemetryService).devNotificationsClicked(argument.capture());
    assertThat(argument.getValue().getEventType()).isEqualTo("eventType");
  }

  @Test
  void taintVulnerabilitiesInvestigatedLocally_when_enabled() {
    telemetry.taintVulnerabilitiesInvestigatedLocally();
    verify(telemetryService).taintVulnerabilitiesInvestigatedLocally();
    verify(telemetryService).taintInvestigatedLocally();
  }

  @Test
  void taintVulnerabilitiesInvestigatedLocally_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.taintVulnerabilitiesInvestigatedLocally();

    verify(telemetryService, never()).taintVulnerabilitiesInvestigatedLocally();
    verify(telemetryService, never()).taintInvestigatedLocally();
  }

  @Test
  void taintVulnerabilitiesInvestigatedRemotely_when_enabled() {
    telemetry.taintVulnerabilitiesInvestigatedRemotely();
    verify(telemetryService).taintVulnerabilitiesInvestigatedRemotely();
    verify(telemetryService).taintInvestigatedRemotely();
  }

  @Test
  void taintVulnerabilitiesInvestigatedRemotely_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.taintVulnerabilitiesInvestigatedRemotely();

    verify(telemetryService, never()).taintVulnerabilitiesInvestigatedRemotely();
    verify(telemetryService, never()).taintInvestigatedRemotely();
  }

  @Test
  void issueInvestigatedLocally_when_enabled() {
    telemetry.issueInvestigatedLocally();
    verify(telemetryService).issueInvestigatedLocally();
  }

  @Test
  void issueInvestigatedLocally_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.issueInvestigatedLocally();

    verify(telemetryService, never()).issueInvestigatedLocally();
  }

  @Test
  void filterApplied_when_enabled() {
    var telemetryParams = new FindingsFilteredParams("severity");
    telemetry.findingFilterApplied(telemetryParams);
    verify(telemetryService).findingsFiltered(telemetryParams);
  }

  @Test
  void filterApplied_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.findingFilterApplied(new FindingsFilteredParams("severity"));
    verify(telemetryService, never()).findingsFiltered(any());
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
  void helpAndFeedbackLinkClicked_when_enabled() {
    ArgumentCaptor<HelpAndFeedbackClickedParams> argument = ArgumentCaptor.forClass(HelpAndFeedbackClickedParams.class);
    telemetry.helpAndFeedbackLinkClicked("docs");

    verify(telemetryService).helpAndFeedbackLinkClicked(argument.capture());
    assertThat(argument.getValue().getItemId()).isEqualTo("docs");
  }

  @Test
  void helpAndFeedbackLinkClicked_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.helpAndFeedbackLinkClicked("docs");

    verify(telemetryService, never()).helpAndFeedbackLinkClicked(any());
  }

  @Test
  void toolCalled_when_enabled() {
    ArgumentCaptor<ToolCalledParams> argument = ArgumentCaptor.forClass(ToolCalledParams.class);
    telemetry.toolCalled("lm.sonarqube_list_potential_security_issues", true);

    verify(telemetryService).toolCalled(argument.capture());
    assertThat(argument.getValue().getToolName()).isEqualTo("lm.sonarqube_list_potential_security_issues");
    assertThat(argument.getValue().isSucceeded()).isTrue();
  }

  @Test
  void toolCalled_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.toolCalled("lm.sonarqube_list_potential_security_issues", true);

    verify(telemetryService, never()).toolCalled(any());
  }

  @Test
  void addedAutomaticBindings_when_enabled() {
    telemetry.addedAutomaticBindings();

    verify(telemetryService).addedAutomaticBindings();
  }

  @Test
  void addedAutomaticBindings_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.addedAutomaticBindings();

    verify(telemetryService, never()).addedAutomaticBindings();
  }

  @Test
  void addedImportedBindings_when_enabled() {
    telemetry.addedImportedBindings();

    verify(telemetryService).addedImportedBindings();
  }

  @Test
  void addedImportedBindings_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.addedImportedBindings();

    verify(telemetryService, never()).addedImportedBindings();
  }

  @Test
  void addedManualBindings_when_enabled() {
    telemetry.addedManualBindings();

    verify(telemetryService).addedManualBindings();
  }

  @Test
  void addedManualBindings_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry.addedManualBindings();

    verify(telemetryService, never()).addedManualBindings();
  }

  @Test
  void fixSuggestionResolved_when_enabled() {
    var params = new FixSuggestionResolvedParams(UUID.randomUUID().toString(), FixSuggestionStatus.ACCEPTED, null);
    telemetry.fixSuggestionResolved(params);

    verify(telemetryService).fixSuggestionResolved(params);
  }

  @Test
  void fixSuggestionResolved_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    var params = new FixSuggestionResolvedParams(UUID.randomUUID().toString(), FixSuggestionStatus.ACCEPTED, null);
    telemetry.fixSuggestionResolved(params);

    verify(telemetryService, never()).fixSuggestionResolved(any());
  }

  @Test
  void wholeFolderHotspotsScan_when_enabled() {
    var argument = ArgumentCaptor.forClass(AnalysisReportingTriggeredParams.class);

    telemetry.wholeFolderHotspotsAnalysisTriggered();

    verify(telemetryService).analysisReportingTriggered(argument.capture());
    assertThat(argument.getValue().getAnalysisType()).isEqualTo(AnalysisReportingType.WHOLE_FOLDER_HOTSPOTS_SCAN_TYPE);
  }

  @Test
  void wholeFolderHotspotsScan_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");

    telemetry.wholeFolderHotspotsAnalysisTriggered();

    verify(telemetryService, never()).analysisReportingTriggered(any());
  }

  @Test
  void currentFileAnalysisTriggered_when_enabled() {
    var argument = ArgumentCaptor.forClass(AnalysisReportingTriggeredParams.class);

    telemetry.currentFileAnalysisTriggered();

    verify(telemetryService).analysisReportingTriggered(argument.capture());
    assertThat(argument.getValue().getAnalysisType()).isEqualTo(AnalysisReportingType.CURRENT_FILE_ANALYSIS_TYPE);
  }

  @Test
  void currentFileAnalysisTriggered_when_disabled() {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");

    telemetry.currentFileAnalysisTriggered();

    verify(telemetryService, never()).analysisReportingTriggered(any());
  }

  private static WorkspaceSettings newWorkspaceSettingsWithTelemetrySetting(boolean disableTelemetry) {
    return new WorkspaceSettings(disableTelemetry, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(),
      Collections.emptyMap(), false, "/path/to/node", false, true, "");
  }
}
