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
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.sonarsource.sonarlint.core.client.utils.Language;
import org.sonarsource.sonarlint.ls.AnalysisClientInputFile;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;

import static org.sonarsource.sonarlint.ls.EnginesFactory.isConnectedLanguage;

public class PromotionalNotifications {
  private final SonarLintExtendedLanguageClient client;
  private final SettingsManager settingsManager;

  public PromotionalNotifications(SonarLintExtendedLanguageClient client, SettingsManager settingsManager) {
    this.client = client;
    this.settingsManager = settingsManager;
  }

  public void didOpen(DidOpenTextDocumentParams didOpenTextDocumentParams) {
    var clientLanguageIdLowerCase = didOpenTextDocumentParams.getTextDocument().getLanguageId().toLowerCase(Locale.ENGLISH);
    var isConnected = settingsManager.hasConnectionDefined();
    if (!isConnected) {
      var sonarLanguage = AnalysisClientInputFile
        .toSqLanguage(clientLanguageIdLowerCase);
      var didOpenSQLFile = clientLanguageIdLowerCase.contains("sql");

      var rpcLanguage = sonarLanguage == null ? null : org.sonarsource.sonarlint.core.rpc.protocol.common.Language.valueOf(sonarLanguage.name());
      if (isConnectedLanguage(rpcLanguage)) {
        client.maybeShowWiderLanguageSupportNotification(List.of(Language.fromDto(rpcLanguage).getLabel()));
      } else if (didOpenSQLFile) {
        client.maybeShowWiderLanguageSupportNotification(List.of(Language.PLSQL.getLabel(), Language.TSQL.getLabel()));
      }
    }
  }

}
