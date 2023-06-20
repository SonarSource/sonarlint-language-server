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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.telemetry.TelemetryClientAttributesProvider;
import org.sonarsource.sonarlint.ls.NodeJsRuntime;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

public class TelemetryClientAttributesProviderImpl implements TelemetryClientAttributesProvider {

  private final SettingsManager settingsManager;
  private final ProjectBindingManager bindingManager;
  private final NodeJsRuntime nodeJsRuntime;
  private final Map<String, Object> additionalAttributes;
  private final BackendServiceFacade backendServiceFacade;

  public TelemetryClientAttributesProviderImpl(SettingsManager settingsManager, ProjectBindingManager bindingManager, NodeJsRuntime nodeJsRuntime,
    Map<String, Object> additionalAttributes, BackendServiceFacade backendServiceFacade) {
    this.settingsManager = settingsManager;
    this.bindingManager = bindingManager;
    this.nodeJsRuntime = nodeJsRuntime;
    this.additionalAttributes = additionalAttributes;
    this.backendServiceFacade = backendServiceFacade;
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
    return bindingManager.smartNotificationsDisabled();
  }

  @Override
  public Collection<String> getNonDefaultEnabledRules() {
    var enabled = settingsManager.getCurrentSettings().getIncludedRules()
      .stream().map(RuleKey::toString).collect(Collectors.toSet());
    if (!enabled.isEmpty()) {
      enabled.removeAll(getDefaultEnabledRules());
    }
    return enabled;
  }

  private Set<String> getDefaultEnabledRules() {
    return backendServiceFacade.listAllStandaloneRulesDefinitions().thenApply(response ->
        response.getRulesByKey()
          .values()
          .stream()
          .filter(RuleDefinitionDto::isActiveByDefault)
          .map(RuleDefinitionDto::getKey)
          .collect(Collectors.toSet()))
      .join();
  }

  @Override
  public Collection<String> getDefaultDisabledRules() {
    var disabled = settingsManager.getCurrentSettings().getExcludedRules()
      .stream().map(RuleKey::toString).collect(Collectors.toSet());
    if (!disabled.isEmpty()) {
      var defaultEnabledRules = getDefaultEnabledRules();
      disabled.removeIf(r -> !defaultEnabledRules.contains(r));
    }
    return disabled;
  }

  @Override
  public Map<String, Object> additionalAttributes() {
    return additionalAttributes;
  }
}
