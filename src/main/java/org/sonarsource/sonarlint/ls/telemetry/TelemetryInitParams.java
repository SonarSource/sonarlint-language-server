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

import java.util.Map;
import javax.annotation.Nullable;

public class TelemetryInitParams {

  @Nullable
  private final String productKey;
  @Nullable
  private final String telemetryStorage;
  private final String productName;
  private final String productVersion;
  private final String ideVersion;
  private final String platform;
  private final String architecture;
  private final Map<String, Object> additionalAttributes;

  public TelemetryInitParams(@Nullable String productKey, String telemetryStorage,
    String productName, String productVersion, String ideVersion,
    String platform, String architecture, Map<String, Object> additionalAttributes) {
    this.productKey = productKey;
    this.telemetryStorage = telemetryStorage;
    this.productName = productName;
    this.productVersion = productVersion;
    this.ideVersion = ideVersion;
    this.platform = platform;
    this.architecture = architecture;
    this.additionalAttributes = additionalAttributes;
  }

  @Nullable
  public String getProductKey() {
    return productKey;
  }

  @Nullable
  public String getTelemetryStorage() {
    return telemetryStorage;
  }

  public String getProductName() {
    return productName;
  }

  public String getProductVersion() {
    return productVersion;
  }

  public String getIdeVersion() {
    return ideVersion;
  }

  public String getPlatform() {
    return platform;
  }

  public String getArchitecture() {
    return architecture;
  }

  public Map<String, Object> getAdditionalAttributes() {
    return additionalAttributes;
  }
}
