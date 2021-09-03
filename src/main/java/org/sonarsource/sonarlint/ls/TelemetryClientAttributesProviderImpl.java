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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.telemetry.TelemetryClientAttributesProvider;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.standalone.StandaloneEngineManager;

public class TelemetryClientAttributesProviderImpl implements TelemetryClientAttributesProvider {

  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private final NodeJsRuntime nodeJsRuntime;
  private final StandaloneEngineManager standaloneEngineManager;
  private final Map<String, Object> additionalAttributes;

  public TelemetryClientAttributesProviderImpl(SettingsManager settingsManager, ProjectBindingManager bindingManager, NodeJsRuntime nodeJsRuntime,
    StandaloneEngineManager standaloneEngineManager, Map<String, Object> additionalAttributes) {
    this.settingsManager = settingsManager;
    this.bindingManager = bindingManager;
    this.nodeJsRuntime = nodeJsRuntime;
    this.standaloneEngineManager = standaloneEngineManager;
    this.additionalAttributes = additionalAttributes;
  }

  @Override
  public boolean usesConnectedMode() {
    return bindingManager.usesConnectedMode();
  }

  @Override
  public boolean useSonarCloud() {
    return bindingManager.usesSonarCloud();
  }

  @Override
  public Optional<String> nodeVersion() {
    return Optional.ofNullable(nodeJsRuntime.nodeVersion());
  }

  @Override
  public boolean devNotificationsDisabled() {
    return bindingManager.devNotificationsDisabled();
  }

  @Override
  public Collection<String> getNonDefaultEnabledRules() {
    Set<String> enabled = settingsManager.getCurrentSettings().getIncludedRules()
      .stream().map(RuleKey::toString).collect(Collectors.toSet());
    if (!enabled.isEmpty()) {
      enabled.removeAll(getDefaultEnabledRules());
    }
    return enabled;
  }

  private Set<String> getDefaultEnabledRules() {
    return standaloneEngineManager.getOrCreateStandaloneEngine().getAllRuleDetails().stream()
      .filter(StandaloneRuleDetails::isActiveByDefault)
      .map(StandaloneRuleDetails::getKey)
      .collect(Collectors.toSet());
  }

  @Override
  public Collection<String> getDefaultDisabledRules() {
    Set<String> disabled = settingsManager.getCurrentSettings().getExcludedRules()
      .stream().map(RuleKey::toString).collect(Collectors.toSet());
    if (!disabled.isEmpty()) {
      Set<String> defaultEnabledRules = getDefaultEnabledRules();
      disabled.removeIf(r -> !defaultEnabledRules.contains(r));
    }
    return disabled;
  }

  @Override
  public Map<String, Object> additionalAttributes() {
    return additionalAttributes;
  }
}
