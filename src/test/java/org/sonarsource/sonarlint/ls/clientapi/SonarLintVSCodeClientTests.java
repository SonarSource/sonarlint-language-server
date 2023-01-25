/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SonarLintVSCodeClientTests {

  SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  SonarLintVSCodeClient underTest = new SonarLintVSCodeClient(client);

  @Test
  void openUrlInBrowserTest() {
    var params = new OpenUrlInBrowserParams("url");

    underTest.openUrlInBrowser(params);

    verify(client).browseTo(params.getUrl());
  }

  @Test
  void shouldReturnNullForHttpClient() {
    assertThat(underTest.getHttpClient("")).isNull();
  }


  @Test
  void shouldThrowForFindFile() {

    assertThrows(UnsupportedOperationException.class, () -> {
      underTest.findFileByNamesInScope(mock(FindFileByNamesInScopeParams.class));
    });
  }

  @Test
  void shouldThrowForSuggestBinding() {

    assertThrows(UnsupportedOperationException.class, () -> {
      underTest.suggestBinding(mock(SuggestBindingParams.class));
    });
  }

  @Test
  void shouldThrowForHttpClientNoAuth() {

    assertThrows(UnsupportedOperationException.class, () -> {
      underTest.getHttpClientNoAuth("");
    });
  }

  @Test
  void shouldThrowForShowMessage() {

    assertThrows(UnsupportedOperationException.class, () -> {
      underTest.showMessage(mock(ShowMessageParams.class));
    });
  }

  @Test
  void shouldThrowForGetHostInfo() {

    assertThrows(UnsupportedOperationException.class, () -> {
      underTest.getHostInfo();
    });
  }

  @Test
  void shouldThrowForShowHotspot() {

    assertThrows(UnsupportedOperationException.class, () -> {
      underTest.showHotspot(mock(ShowHotspotParams.class));
    });
  }

  @Test
  void shouldThrowForAssistCreatingConnection() {

    assertThrows(UnsupportedOperationException.class, () -> {
      underTest.assistCreatingConnection(mock(AssistCreatingConnectionParams.class));
    });
  }

  @Test
  void shouldThrowForAssistBinding() {

    assertThrows(UnsupportedOperationException.class, () -> {
      underTest.assistBinding(mock(AssistBindingParams.class));
    });
  }
}
