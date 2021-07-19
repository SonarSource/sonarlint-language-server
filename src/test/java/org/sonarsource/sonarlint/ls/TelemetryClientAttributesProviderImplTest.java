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

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class TelemetryClientAttributesProviderImplTest {


  TelemetryClientAttributesProviderImpl underTest;

  @BeforeEach
  public void init() {
    BooleanSupplier falseSupplier = () -> false;
    Supplier<String> nodeVersionSupplier = () -> "nodeVersion";
    underTest = new TelemetryClientAttributesProviderImpl(falseSupplier, falseSupplier, falseSupplier, nodeVersionSupplier);
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
  }

}
