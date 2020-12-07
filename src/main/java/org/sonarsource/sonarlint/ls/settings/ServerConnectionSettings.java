/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

@Immutable
public class ServerConnectionSettings {
  static final String SONARCLOUD_URL = "https://sonarcloud.io";
  static final String[] SONARCLOUD_ALIAS = {"https://sonarqube.com", "https://www.sonarqube.com", "https://www.sonarcloud.io", SONARCLOUD_URL};

  private final String connectionId;
  private final String serverUrl;
  private final String token;

  @Nullable
  private final String organizationKey;

  public ServerConnectionSettings(String connectionId, String serverUrl, String token, @Nullable String organizationKey) {
    this.connectionId = connectionId;
    this.serverUrl = serverUrl;
    this.token = token;
    this.organizationKey = organizationKey;
  }

  public String getConnectionId() {
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

  @Override
  public int hashCode() {
    return Objects.hash(connectionId, serverUrl, token, organizationKey);
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
      && Objects.equals(organizationKey, other.organizationKey);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
