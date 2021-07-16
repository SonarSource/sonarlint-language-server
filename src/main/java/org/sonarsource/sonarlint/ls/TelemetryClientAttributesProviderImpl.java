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
import java.util.Collections;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.sonarsource.sonarlint.core.telemetry.TelemetryClientAttributesProvider;

public class TelemetryClientAttributesProviderImpl implements TelemetryClientAttributesProvider {


  private final BooleanSupplier usesConnectedMode;
  private final BooleanSupplier usesSonarCloud;
  private final BooleanSupplier devNotificationsDisabled;
  private final Supplier<String> nodeVersion;

  public TelemetryClientAttributesProviderImpl(BooleanSupplier usesConnectedMode, BooleanSupplier usesSonarCloud,
    BooleanSupplier devNotificationsDisabled, Supplier<String> nodeVersion) {
    this.usesConnectedMode = usesConnectedMode;
    this.usesSonarCloud = usesSonarCloud;
    this.devNotificationsDisabled = devNotificationsDisabled;
    this.nodeVersion = nodeVersion;
  }

  @Override
  public boolean usesConnectedMode() {
    return usesConnectedMode.getAsBoolean();
  }

  @Override
  public boolean useSonarCloud() {
    return usesSonarCloud.getAsBoolean();
  }

  @Override
  public Optional<String> nodeVersion() {
    return Optional.ofNullable(nodeVersion.get());
  }

  @Override
  public boolean devNotificationsDisabled() {
    return devNotificationsDisabled.getAsBoolean();
  }

  @Override
  public Collection<String> getNonDefaultEnabledRules() {
    return Collections.emptyList();
  }

  @Override
  public Collection<String> getDefaultDisabledRules() {
    return Collections.emptyList();
  }
}
