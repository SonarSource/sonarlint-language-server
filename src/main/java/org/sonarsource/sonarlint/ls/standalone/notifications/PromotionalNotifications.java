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

import java.util.Set;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;

public class PromotionalNotifications {
  private final SonarLintExtendedLanguageClient client;

  public PromotionalNotifications(SonarLintExtendedLanguageClient client) {
    this.client = client;
  }

  public void promoteExtraEnabledLanguagesInConnectedMode(Set<Language> languagesToPromote) {
    // TODO no notif coming for COBOL files
    client.maybeShowWiderLanguageSupportNotification(
      languagesToPromote
        .stream()
        .map(l -> org.sonarsource.sonarlint.core.client.utils.Language.valueOf(l.name())
          .getLabel())
        .toList());
  }

}
