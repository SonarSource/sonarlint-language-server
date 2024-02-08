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
package org.sonarsource.sonarlint.ls.standalone.notifications;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.ls.EnginesFactory;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;

public class PromotionalNotifications {
  private final SonarLintExtendedLanguageClient client;
  private final ProjectBindingManager bindingManager;

  public PromotionalNotifications(SonarLintExtendedLanguageClient client, ProjectBindingManager bindingManager) {
    this.client = client;
    this.bindingManager = bindingManager;
  }

  public void didOpen(DidOpenTextDocumentParams didOpenTextDocumentParams) {
    var isConnected = bindingManager.usesConnectedMode();
    if (!isConnected) {
      var connectedLanguageForOpenedFile = EnginesFactory.getConnectedLanguages().stream().filter(additionalLanguage ->
        Objects.equals(additionalLanguage.getLanguageKey().toLowerCase(Locale.ENGLISH),
          didOpenTextDocumentParams.getTextDocument().getLanguageId().toLowerCase(Locale.ENGLISH))).findFirst();
      var didOpenSQLFile = didOpenTextDocumentParams.getTextDocument().getLanguageId().equals("sql");

      if (connectedLanguageForOpenedFile.isPresent()) {
        client.maybeShowWiderLanguageSupportNotification(List.of(connectedLanguageForOpenedFile.get().getLabel()));
      } else if (didOpenSQLFile) {
        client.maybeShowWiderLanguageSupportNotification(List.of(Language.PLSQL.getLabel(), Language.TSQL.getLabel()));
      }
    }
  }

}
