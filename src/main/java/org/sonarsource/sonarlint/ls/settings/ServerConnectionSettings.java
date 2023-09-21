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
package org.sonarsource.sonarlint.ls.settings;

import java.util.List;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.common.TokenDto;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

@Immutable
public class ServerConnectionSettings {
  static final String SONARCLOUD_URL = "https://sonarcloud.io";
  static final String[] SONARCLOUD_ALIAS = {"https://sonarqube.com", "https://www.sonarqube.com", "https://www.sonarcloud.io", SONARCLOUD_URL};

  private final String connectionId;
  private final String serverUrl;
  private String token;
  private final boolean disableNotifications;

  @Nullable
  private final String organizationKey;
  private final EndpointParams endpointParams;
  private ValidateConnectionParams validateConnectionParams;

  public ServerConnectionSettings(String connectionId, String serverUrl, String token, @Nullable String organizationKey,
    boolean disableNotifications) {
    this.connectionId = connectionId;
    this.serverUrl = serverUrl;
    this.token = token;
    this.organizationKey = organizationKey;
    this.disableNotifications = disableNotifications;
    this.endpointParams = createEndpointParams();
    this.validateConnectionParams = createValidateConnectionParams();
  }

  private EndpointParams createEndpointParams() {
    return new EndpointParams(getServerUrl(), isSonarCloudAlias(), getOrganizationKey());
  }

  private ValidateConnectionParams createValidateConnectionParams() {
    Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> connectionDto = isSonarCloudAlias() ?
      Either.forRight(new TransientSonarCloudConnectionDto(getOrganizationKey(), Either.forLeft(new TokenDto(getToken())))) :
      Either.forLeft(new TransientSonarQubeConnectionDto(getServerUrl(), Either.forLeft(new TokenDto(getToken()))));
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

  public boolean isSonarCloudAlias() {
    return isSonarCloudAlias(serverUrl);
  }

  public static boolean isSonarCloudAlias(String serverUrl) {
    return List.of(SONARCLOUD_ALIAS).contains(serverUrl);
  }

  public boolean isSmartNotificationsDisabled() {
    return disableNotifications;
  }

  public EndpointParams getEndpointParams() {
    return endpointParams;
  }

  public ValidateConnectionParams getValidateConnectionParams() {
    return validateConnectionParams;
  }

  public void setToken(String token) {
    this.token = token;
    this.validateConnectionParams = createValidateConnectionParams();
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
    return new ReflectionToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).setExcludeFieldNames("endpointParams", "validateConnectionParams").toString();
  }
}
