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
package org.sonarsource.sonarlint.ls.clientapi;

import java.net.Authenticator;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPropertiesAuthenticatorTest {

  private static final Set<String> PROXY_PROPERTY_KEYS = Set.of(
    "http.proxyHost",
    "http.proxyPort",
    "http.proxyUser",
    "http.proxyPassword"
  );
  private final Map<String, String> savedProxyProperties = new HashMap<>();
  private static URL proxiedUrl;
  private static Authenticator defaultAuthenticator;

  @BeforeAll
  static void initializeTestVariables() throws Exception {
    defaultAuthenticator = Authenticator.getDefault();
    Authenticator.setDefault(new SystemPropertiesAuthenticator());
    proxiedUrl = new URL("https://somehost.example.net");
  }

  @AfterAll
  static void restoreAuthenticator() {
    Authenticator.setDefault(defaultAuthenticator);
  }

  @BeforeEach
  void saveProxyProperties() {
    PROXY_PROPERTY_KEYS.forEach(k -> savedProxyProperties.put(k, System.getProperty(k)));
  }

  @AfterEach
  void cleanupProxyProperties() {
    PROXY_PROPERTY_KEYS.forEach(k -> {
      var savedPropertyValue = savedProxyProperties.get(k);
      if (savedPropertyValue == null) {
        System.clearProperty(k);
      } else {
        System.setProperty(k, savedPropertyValue);
      }
    });
  }


  @Test
  void shouldReturnNullIfRequestModeIsNotProxy() {
    assertThat(Authenticator.requestPasswordAuthentication("somehost.example.net", null, 80, "http", "Security", "http")).isNull();
  }

  @Test
  void shouldReturnNullIfProxyHostNotSet() {
    System.clearProperty("http.proxyHost");
    assertThat(Authenticator.requestPasswordAuthentication("proxy.example.net", null, 8000, "http", "Security", "http", proxiedUrl, Authenticator.RequestorType.PROXY)).isNull();
  }

  @Test
  void shouldReturnNullIfProxyPortNotSet() {
    System.setProperty("http.proxyHost", "proxy.example.net");
    System.clearProperty("http.proxyPort");
    assertThat(Authenticator.requestPasswordAuthentication("proxy.example.net", null, 8000, "http", "Security", "http", proxiedUrl, Authenticator.RequestorType.PROXY)).isNull();
  }

  @Test
  void shouldReturnNullIfProxyUserNotSet() {
    System.setProperty("http.proxyHost", "proxy.example.net");
    System.setProperty("http.proxyPort", "8000");
    System.clearProperty("http.proxyUser");
    assertThat(Authenticator.requestPasswordAuthentication("proxy.example.net", null, 8000, "http", "Security", "http", proxiedUrl, Authenticator.RequestorType.PROXY)).isNull();
  }

  @Test
  void shouldReturnNullIfProxyHostDiffers() {
    System.setProperty("http.proxyHost", "proxy.example.net");
    System.setProperty("http.proxyPort", "8000");
    assertThat(Authenticator.requestPasswordAuthentication("otherproxy.example.net", null, 8000, "http", "Security", "http", proxiedUrl, Authenticator.RequestorType.PROXY)).isNull();
  }

  @Test
  void shouldReturnNullIfProxyPortDiffers() {
    System.setProperty("http.proxyHost", "proxy.example.net");
    System.setProperty("http.proxyPort", "3128");
    assertThat(Authenticator.requestPasswordAuthentication("proxy.example.net", null, 8000, "http", "Security", "http", proxiedUrl, Authenticator.RequestorType.PROXY)).isNull();
  }

  @Test
  void shouldReturnCredentialsWithEmptyPassword() {
    var myProxyUser = "myProxyUser";

    System.setProperty("http.proxyHost", "proxy.example.net");
    System.setProperty("http.proxyPort", "8000");
    System.setProperty("http.proxyUser", myProxyUser);
    System.clearProperty("http.proxyPassword");

    var credentials = Authenticator.requestPasswordAuthentication("proxy.example.net", null, 8000, "http", "Security", "http", proxiedUrl, Authenticator.RequestorType.PROXY);
    assertThat(credentials.getUserName()).isEqualTo(myProxyUser);
    assertThat(credentials.getPassword()).isEmpty();
  }

  @Test
  void shouldReturnFullCredentials() {
    var myProxyUser = "myProxyUser";
    var myPassword = "mySecurePassword";

    System.setProperty("http.proxyHost", "proxy.example.net");
    System.setProperty("http.proxyPort", "8000");
    System.setProperty("http.proxyUser", myProxyUser);
    System.setProperty("http.proxyPassword", myPassword);

    var credentials = Authenticator.requestPasswordAuthentication("proxy.example.net", null, 8000, "http", "Security", "http", proxiedUrl, Authenticator.RequestorType.PROXY);
    assertThat(credentials.getUserName()).isEqualTo(myProxyUser);
    assertThat(credentials.getPassword()).containsExactly(myPassword.toCharArray());
  }
}
