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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerConnectionSettingsTests {

  private static final ServerConnectionSettings WITHOUT_ORG = new ServerConnectionSettings("serverId", "serverUrl", "token", null, false, SonarCloudRegion.EU);
  private static final ServerConnectionSettings WITH_ORG = new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", false, SonarCloudRegion.EU);

  @Test
  void testHashCode() {
    assertThat(new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", false, SonarCloudRegion.EU)).hasSameHashCodeAs(WITH_ORG);
    assertThat(new ServerConnectionSettings("serverId", "serverUrl", "token", null, false, SonarCloudRegion.EU)).hasSameHashCodeAs(WITHOUT_ORG);
  }

  @Test
  void testSetters() {
    var oldSetting = new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", false, SonarCloudRegion.EU);
    oldSetting.setToken("newToken");
    var compareTo = new ServerConnectionSettings("serverId", "serverUrl", "newToken", "myOrg", false, SonarCloudRegion.EU);
    assertThat(oldSetting).hasSameHashCodeAs(compareTo);
  }

  @Test
  void testEquals() {
    assertThat(WITH_ORG)
      .isEqualTo(WITH_ORG)
      .isEqualTo(new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg", false, SonarCloudRegion.EU))
      .isNotEqualTo(null)
      .isNotEqualTo("foo")
      .isNotEqualTo(new ServerConnectionSettings("serverId2", "serverUrl", "token", "myOrg", false, SonarCloudRegion.EU))
      .isNotEqualTo(new ServerConnectionSettings("serverId", "serverUrl2", "token", "myOrg", false, SonarCloudRegion.EU))
      .isNotEqualTo(new ServerConnectionSettings("serverId", "serverUrl", "token2", "myOrg", false, SonarCloudRegion.EU))
      .isNotEqualTo(new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg2", false, SonarCloudRegion.EU))
      .isNotEqualTo(new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg2", true, SonarCloudRegion.EU));
    assertThat(WITHOUT_ORG)
      .isEqualTo(new ServerConnectionSettings("serverId", "serverUrl", "token", null, false, SonarCloudRegion.EU))
      .isNotEqualTo(WITH_ORG);
  }

  @Test
  void testIsSonarCloudAlias() {
    Assertions.assertTrue(ServerConnectionSettings.isSonarCloudAlias("https://sonarqube.us"));
    Assertions.assertTrue(ServerConnectionSettings.isSonarCloudAlias("https://sonarcloud.io"));
    Assertions.assertTrue(ServerConnectionSettings.isSonarCloudAlias("https://sonarqube.com"));
    Assertions.assertFalse(ServerConnectionSettings.isSonarCloudAlias("http://localhost:1234"));

    // Set system property
    System.setProperty("sonarlint.internal.sonarcloud.us.url", "http://localhost:1234");
    Assertions.assertTrue(ServerConnectionSettings.isSonarCloudAlias("http://localhost:1234"));
    System.clearProperty("sonarlint.internal.sonarcloud.us.url");

    Assertions.assertFalse(ServerConnectionSettings.isSonarCloudAlias("http://localhost:5678"));
    System.setProperty("sonarlint.internal.sonarcloud.url", "http://localhost:5678");
    Assertions.assertTrue(ServerConnectionSettings.isSonarCloudAlias("http://localhost:5678"));
    System.clearProperty("sonarlint.internal.sonarcloud.url");
  }

  @Test
  void testToStringHidesToken() {
    assertThat(WITH_ORG).hasToString("ServerConnectionSettings[connectionId=serverId,disableNotifications=false,organizationKey=myOrg,region=EU,serverUrl=serverUrl]");
  }

  @Test
  void shouldRejectNullTokenInValidation() {
    var settings = new ServerConnectionSettings("test", "http://localhost:8080", null, null, false, null);
    assertThatThrownBy(settings::getValidateConnectionParams)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Token cannot be null or empty for connection validation");
  }

  @Test
  void shouldRejectEmptyTokenInValidation() {
    var settings = new ServerConnectionSettings("test", "http://localhost:8080", "", null, false, null);
    assertThatThrownBy(settings::getValidateConnectionParams)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Token cannot be null or empty for connection validation");
  }

}
