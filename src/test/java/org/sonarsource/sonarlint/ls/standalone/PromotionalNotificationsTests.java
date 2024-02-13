/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.sonarlint.ls.standalone;

import java.util.List;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.standalone.notifications.PromotionalNotifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotionalNotificationsTests {
  private final SonarLintExtendedLanguageClient client = mock(SonarLintExtendedLanguageClient.class);
  private final SettingsManager settingsManager = mock(SettingsManager.class);
  private final PromotionalNotifications underTest = new PromotionalNotifications(client, settingsManager);

  @Test
  void shouldSendNotification_notConnected_commercialLanguage() {
    var didOpenTextDocumentParams = new DidOpenTextDocumentParams(new TextDocumentItem("file:///1/2/3.cbl", "COBOL", 1, ""));

    when(settingsManager.hasConnectionDefined()).thenReturn(false);

    underTest.didOpen(didOpenTextDocumentParams);

    verify(client).maybeShowWiderLanguageSupportNotification(List.of("COBOL"));
  }

  @Test
  void shouldSendNotification_notConnected_sql() {
    var didOpenTextDocumentParams = new DidOpenTextDocumentParams(new TextDocumentItem("file:///1/2/3.sql", "sql", 1, ""));

    when(settingsManager.hasConnectionDefined()).thenReturn(false);

    underTest.didOpen(didOpenTextDocumentParams);

    verify(client).maybeShowWiderLanguageSupportNotification(List.of("PL/SQL", "T-SQL"));
  }

  @Test
  void shouldSendNotification_notConnected_oraclesql() {
    var didOpenTextDocumentParams = new DidOpenTextDocumentParams(new TextDocumentItem("file:///1/2/3.sql", "oraclesql", 1, ""));

    when(settingsManager.hasConnectionDefined()).thenReturn(false);

    underTest.didOpen(didOpenTextDocumentParams);

    verify(client).maybeShowWiderLanguageSupportNotification(List.of("PL/SQL"));
  }

  @Test
  void shouldNotSendNotification_connected_commercialLanguage() {
    var didOpenTextDocumentParams = new DidOpenTextDocumentParams(new TextDocumentItem("file:///1/2/3.cls", "apex", 1, ""));

    when(settingsManager.hasConnectionDefined()).thenReturn(true);

    underTest.didOpen(didOpenTextDocumentParams);

    verify(client, never()).maybeShowWiderLanguageSupportNotification(any());
  }

  @Test
  void shouldNotSendNotification_notConnected_normalLanguage() {
    var didOpenTextDocumentParams = new DidOpenTextDocumentParams(new TextDocumentItem("file:///1/2/3.py", "python", 1, ""));

    when(settingsManager.hasConnectionDefined()).thenReturn(false);

    underTest.didOpen(didOpenTextDocumentParams);

    verify(client, never()).maybeShowWiderLanguageSupportNotification(any());
  }
}
