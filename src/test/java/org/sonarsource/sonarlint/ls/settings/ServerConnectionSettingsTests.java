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

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;

import static org.assertj.core.api.Assertions.assertThat;

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
  void testToStringHidesToken() {
    assertThat(WITH_ORG).hasToString("ServerConnectionSettings[connectionId=serverId,disableNotifications=false,organizationKey=myOrg,region=EU,serverUrl=serverUrl]");
  }

}
