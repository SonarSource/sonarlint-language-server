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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryClientAttributesProviderImplTest {

  private TelemetryClientAttributesProviderImpl underTest;
  private WorkspaceSettings workspaceSettings;
  private StandaloneEngineManager standaloneEngineManager;
  private StandaloneSonarLintEngine standaloneSonarLintEngine;
  private Map<String, Object> additionalAttributes;

  @BeforeEach
  public void init() {
    NodeJsRuntime nodeJsRuntime = mock(NodeJsRuntime.class);
    when(nodeJsRuntime.nodeVersion()).thenReturn("nodeVersion");
    SettingsManager settingsManager = mock(SettingsManager.class);
    workspaceSettings = mock(WorkspaceSettings.class);
    standaloneEngineManager = mock(StandaloneEngineManager.class);
    standaloneSonarLintEngine = mock(StandaloneSonarLintEngine.class);
    when(standaloneEngineManager.getOrCreateStandaloneEngine()).thenReturn(standaloneSonarLintEngine);
    when(settingsManager.getCurrentSettings()).thenReturn(workspaceSettings);
    when(workspaceSettings.getExcludedRules()).thenReturn(Collections.emptyList());
    when(workspaceSettings.getIncludedRules()).thenReturn(Collections.emptyList());
    additionalAttributes = new HashMap<>();
    underTest = new TelemetryClientAttributesProviderImpl(settingsManager, mock(ProjectBindingManager.class), nodeJsRuntime, standaloneEngineManager, additionalAttributes);
  }

  @Test
  void testNodeVersion() {
    assertThat(underTest.nodeVersion()).isPresent();
    assertThat(underTest.nodeVersion()).contains("nodeVersion");
  }

  @Test
  void testServerConnection() {
    assertThat(underTest.useSonarCloud()).isFalse();
    assertThat(underTest.usesConnectedMode()).isFalse();
  }

  @Test
  void testDevNotifications() {
    assertThat(underTest.devNotificationsDisabled()).isFalse();
  }

  @Test
  void testTelemetry() {
    assertThat(underTest.getDefaultDisabledRules()).isEmpty();
    assertThat(underTest.getNonDefaultEnabledRules()).isEmpty();
    assertThat(underTest.additionalAttributes()).isEmpty();
  }

  @Test
  void testAdditionalAttributes() {
    additionalAttributes.put("key", "value");
    assertThat(underTest.additionalAttributes()).containsEntry("key", "value");
  }

  @Test
  void testGetNonDefaultEnabledRules() {
    List<RuleKey> rules = new ArrayList<>();
    RuleKey ruleKey1 = mock(RuleKey.class);
    RuleKey ruleKey2 = mock(RuleKey.class);
    when(ruleKey1.toString()).thenReturn("ruleKey1");
    when(ruleKey2.toString()).thenReturn("ruleKey2");
    rules.add(ruleKey1);
    rules.add(ruleKey2);
    when(workspaceSettings.getIncludedRules()).thenReturn(rules);
    when(standaloneSonarLintEngine.getAllRuleDetails()).thenReturn(Collections.emptyList());

    Collection<String> nonDefaultEnabledRules = underTest.getNonDefaultEnabledRules();

    assertThat(nonDefaultEnabledRules).containsExactly("ruleKey2", "ruleKey1");
  }

  @Test
  void testGetNonDefaultEnabledFilteringRules() {
    List<RuleKey> rules = new ArrayList<>();
    RuleKey ruleKey1 = mock(RuleKey.class);
    RuleKey ruleKey2 = mock(RuleKey.class);
    when(ruleKey1.toString()).thenReturn("ruleKey1");
    when(ruleKey2.toString()).thenReturn("ruleKey2");
    rules.add(ruleKey1);
    rules.add(ruleKey2);
    StandaloneRuleDetails standaloneRule1 = mock(StandaloneRuleDetails.class);
    when(standaloneRule1.getKey()).thenReturn("ruleKey2");
    when(standaloneRule1.isActiveByDefault()).thenReturn(true);
    when(workspaceSettings.getIncludedRules()).thenReturn(rules);
    when(standaloneSonarLintEngine.getAllRuleDetails()).thenReturn(Collections.singletonList(standaloneRule1));

    Collection<String> nonDefaultEnabledRules = underTest.getNonDefaultEnabledRules();

    assertThat(nonDefaultEnabledRules).containsExactly("ruleKey1");
  }

  @Test
  void testGetDefaultDisabledRules() {
    List<RuleKey> rules = new ArrayList<>();
    RuleKey ruleKey1 = mock(RuleKey.class);
    RuleKey ruleKey2 = mock(RuleKey.class);
    when(ruleKey1.toString()).thenReturn("ruleKey1");
    when(ruleKey2.toString()).thenReturn("ruleKey2");
    rules.add(ruleKey1);
    rules.add(ruleKey2);
    when(workspaceSettings.getExcludedRules()).thenReturn(rules);
    when(standaloneSonarLintEngine.getAllRuleDetails()).thenReturn(Collections.emptyList());

    Collection<String> nonDefaultEnabledRules = underTest.getDefaultDisabledRules();

    assertThat(nonDefaultEnabledRules).isEmpty();
  }

  @Test
  void testGetDefaultDisabledFilteringRules() {
    List<RuleKey> rules = new ArrayList<>();
    RuleKey ruleKey1 = mock(RuleKey.class);
    RuleKey ruleKey2 = mock(RuleKey.class);
    when(ruleKey1.toString()).thenReturn("ruleKey1");
    when(ruleKey2.toString()).thenReturn("ruleKey2");
    rules.add(ruleKey1);
    rules.add(ruleKey2);
    StandaloneRuleDetails standaloneRule1 = mock(StandaloneRuleDetails.class);
    when(standaloneRule1.getKey()).thenReturn("ruleKey1");
    when(standaloneRule1.isActiveByDefault()).thenReturn(true);
    when(workspaceSettings.getExcludedRules()).thenReturn(rules);
    when(standaloneSonarLintEngine.getAllRuleDetails()).thenReturn(Collections.singletonList(standaloneRule1));

    Collection<String> nonDefaultEnabledRules = underTest.getDefaultDisabledRules();

    assertThat(nonDefaultEnabledRules).containsExactly("ruleKey1");
  }

}
