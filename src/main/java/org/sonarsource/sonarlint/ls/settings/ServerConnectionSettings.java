/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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
package org.sonarsource.sonarlint.ls.settings;

import java.util.List;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;

@Immutable
public class ServerConnectionSettings {
  private final String connectionId;
  private final String serverUrl;
  private String token;
  private final boolean disableNotifications;

  @Nullable
  private final String organizationKey;
  @Nullable
  private final SonarCloudRegion region;
  private final EndpointParams endpointParams;
  private ValidateConnectionParams validateConnectionParams;

  public ServerConnectionSettings(String connectionId, String serverUrl, String token, @Nullable String organizationKey,
    boolean disableNotifications, @Nullable SonarCloudRegion region) {
    this.connectionId = connectionId;
    this.serverUrl = serverUrl;
    this.token = token;
    this.organizationKey = organizationKey;
    this.disableNotifications = disableNotifications;
    this.region = region;
    this.endpointParams = createEndpointParams();
    // Don't create validateConnectionParams during construction to avoid exceptions
    this.validateConnectionParams = null;
  }

  private EndpointParams createEndpointParams() {
    return new EndpointParams(getServerUrl(), isSonarCloudAlias(), getOrganizationKey());
  }

  private ValidateConnectionParams createValidateConnectionParams() {
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("Token cannot be null or empty for connection validation");
    }
    
    Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> connectionDto = isSonarCloudAlias() ?
      Either.forRight(new TransientSonarCloudConnectionDto(getOrganizationKey(), Either.forLeft(new TokenDto(token)), getRegion())) :
      Either.forLeft(new TransientSonarQubeConnectionDto(getServerUrl(), Either.forLeft(new TokenDto(token))));
    return new ValidateConnectionParams(connectionDto);
  }

  String getConnectionId() {
    return connectionId;
  }

  public String getServerUrl() {
    return serverUrl;
  }

  public String getToken() {
    return token;
  }

  @CheckForNull
  public String getOrganizationKey() {
    return organizationKey;
  }

  @Nullable
  public SonarCloudRegion getRegion() {
    return region;
  }

  public boolean isSonarCloudAlias() {
    return isSonarCloudAlias(serverUrl);
  }

  public static String getSonarCloudUrl() {
    return System.getProperty("sonarlint.internal.sonarcloud.url") != null ?
      System.getProperty("sonarlint.internal.sonarcloud.url") :
      "https://sonarcloud.io";
  }

  public static String getSonarCloudUSUrl() {
    return System.getProperty("sonarlint.internal.sonarcloud.us.url") != null ?
      System.getProperty("sonarlint.internal.sonarcloud.us.url") : "https://sonarqube.us";
  }

  public static boolean isSonarCloudAlias(String serverUrl) {
    var possibleSonarCloudUrls = List.of("https://sonarqube.com", "https://www.sonarqube.com", "https://www.sonarcloud.io",
      getSonarCloudUSUrl(), getSonarCloudUrl());
    return possibleSonarCloudUrls.contains(serverUrl);
  }

  public boolean isSmartNotificationsDisabled() {
    return disableNotifications;
  }

  public EndpointParams getEndpointParams() {
    return endpointParams;
  }

  public ValidateConnectionParams getValidateConnectionParams() {
    if (validateConnectionParams == null) {
      validateConnectionParams = createValidateConnectionParams();
    }
    return validateConnectionParams;
  }

  public void setToken(String token) {
    this.token = token;
    // Reset validateConnectionParams so it will be recreated with a new token
    this.validateConnectionParams = null;
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectionId, serverUrl, token, organizationKey, disableNotifications);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    var other = (ServerConnectionSettings) obj;
    return Objects.equals(connectionId, other.connectionId) && Objects.equals(serverUrl, other.serverUrl) && Objects.equals(token, other.token)
      && Objects.equals(organizationKey, other.organizationKey) && this.disableNotifications == other.disableNotifications;
  }

  @Override
  public String toString() {
    return new ReflectionToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).setExcludeFieldNames("endpointParams", "token", "validateConnectionParams").toString();
  }
}
