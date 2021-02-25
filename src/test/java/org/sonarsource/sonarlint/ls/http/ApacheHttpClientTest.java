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
package org.sonarsource.sonarlint.ls.http;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

class ApacheHttpClientTest {

  ApacheHttpClient underTest = ApacheHttpClient.create();

  @Test
  void get_request_test() {
    HttpClient.Response response = underTest.get("http://sonarsource.com");
    String responseString = response.bodyAsString();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(responseString).isNotEmpty();
  }

  @Test
  void post_request_test() {
    HttpClient.Response response = underTest.post("http://sonarsource.com", "image/jpeg", "");
    String responseString = response.bodyAsString();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(responseString).isNotEmpty();
  }

  @Test
  void delete_request_test() {
    HttpClient.Response response = underTest.delete("http://sonarsource.com", "image/jpeg", "");
    String responseString = response.bodyAsString();

    assertThat(response.isSuccessful()).isFalse();
    assertThat(responseString).isNotEmpty();
  }

  @Test
  void basic_auth_test() {
    ApacheHttpClient basicAuthClient = underTest.withToken("token");
    HttpClient.Response response = basicAuthClient.get("http://sonarsource.com");
    String responseString = response.bodyAsString();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(responseString).isNotEmpty();
  }

}
