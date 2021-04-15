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
package org.sonarsource.sonarlint.ls.settings;

import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.ls.http.ApacheHttpClient;

@Immutable
public class ServerConnectionSettings {
  static final String SONARCLOUD_URL = "https://sonarcloud.io";
  static final String[] SONARCLOUD_ALIAS = {"https://sonarqube.com", "https://www.sonarqube.com", "https://www.sonarcloud.io", SONARCLOUD_URL};

  private final String connectionId;
  private final String serverUrl;
  private final String token;
  private final boolean disableNotifications;

  @Nullable
  private final String organizationKey;
  private final EndpointParamsAndHttpClient serverConfiguration;

  public ServerConnectionSettings(String connectionId, String serverUrl, String token, @Nullable String organizationKey,
                                  boolean disableNotifications, ApacheHttpClient httpClient) {
    this.connectionId = connectionId;
    this.serverUrl = serverUrl;
    this.token = token;
    this.organizationKey = organizationKey;
    this.disableNotifications = disableNotifications;
    this.serverConfiguration = createServerConfiguration(httpClient);
  }

  private EndpointParamsAndHttpClient createServerConfiguration(ApacheHttpClient httpClient) {
    EndpointParams endpointParams = new EndpointParams(getServerUrl(), isSonarCloudAlias(), getOrganizationKey());
    return new EndpointParamsAndHttpClient(endpointParams, httpClient.withToken(getToken()));
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

  public String getOrganizationKey() {
    return organizationKey;
  }

  public boolean isSonarCloudAlias() {
    return Arrays.asList(SONARCLOUD_ALIAS).contains(serverUrl);
  }

  public boolean isDevNotificationsDisabled() {
    return disableNotifications;
  }

  public EndpointParamsAndHttpClient getServerConfiguration() {
    return serverConfiguration;
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
    ServerConnectionSettings other = (ServerConnectionSettings) obj;
    return Objects.equals(connectionId, other.connectionId) && Objects.equals(serverUrl, other.serverUrl) && Objects.equals(token, other.token)
      && Objects.equals(organizationKey, other.organizationKey) && this.disableNotifications == other.disableNotifications;
  }

  @Override
  public String toString() {
    return new ReflectionToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).setExcludeFieldNames(new String[]{"serverConfiguration"}).toString();
  }

  public static class EndpointParamsAndHttpClient {
    private final EndpointParams endpointParams;
    private final ApacheHttpClient httpClient;

    public EndpointParamsAndHttpClient(EndpointParams endpointParams, ApacheHttpClient httpClient) {
      this.endpointParams = endpointParams;
      this.httpClient = httpClient;
    }

    public EndpointParams getEndpointParams() {
      return endpointParams;
    }

    public ApacheHttpClient getHttpClient() {
      return httpClient;
    }
  }
}
