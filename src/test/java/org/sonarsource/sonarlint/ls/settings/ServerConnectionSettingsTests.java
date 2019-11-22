/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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

import static org.assertj.core.api.Assertions.assertThat;

class ServerConnectionSettingsTests {

  private static final ServerConnectionSettings WITHOUT_ORG = new ServerConnectionSettings("serverId", "serverUrl", "token", null);
  private static final ServerConnectionSettings WITH_ORG = new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg");

  @Test
  public void testHashCode() {
    assertThat(WITH_ORG.hashCode()).isEqualTo(new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg").hashCode());
    assertThat(WITHOUT_ORG.hashCode()).isEqualTo(new ServerConnectionSettings("serverId", "serverUrl", "token", null).hashCode());
  }

  @Test
  public void testEquals() {
    assertThat(WITH_ORG).isEqualTo(WITH_ORG);
    assertThat(WITH_ORG).isNotEqualTo(null);
    assertThat(WITH_ORG).isNotEqualTo("foo");
    assertThat(WITH_ORG).isEqualTo(new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg"));
    assertThat(WITHOUT_ORG).isEqualTo(new ServerConnectionSettings("serverId", "serverUrl", "token", null));
    assertThat(WITHOUT_ORG).isNotEqualTo(WITH_ORG);

    assertThat(WITH_ORG).isNotEqualTo(new ServerConnectionSettings("serverId2", "serverUrl", "token", "myOrg"));
    assertThat(WITH_ORG).isNotEqualTo(new ServerConnectionSettings("serverId", "serverUrl2", "token", "myOrg"));
    assertThat(WITH_ORG).isNotEqualTo(new ServerConnectionSettings("serverId", "serverUrl", "token2", "myOrg"));
    assertThat(WITH_ORG).isNotEqualTo(new ServerConnectionSettings("serverId", "serverUrl", "token", "myOrg2"));
  }

  @Test
  public void testToString() {
    assertThat(WITH_ORG.toString()).isEqualTo("ServerConnectionSettings[serverId=serverId,serverUrl=serverUrl,token=token,organizationKey=myOrg]");
  }

}
