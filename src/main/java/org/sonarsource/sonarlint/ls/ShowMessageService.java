/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
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
package org.sonarsource.sonarlint.ls;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;


public class ShowMessageService {

  static final String LEARN_MORE_ABOUT_HOTSPOTS_LINK = "https://github.com/SonarSource/sonarlint-vscode/wiki/security-hotspots";
  private final SonarLintExtendedLanguageClient client;

  public ShowMessageService(SonarLintExtendedLanguageClient client) {
    this.client = client;
  }

  public void sendNotCompatibleServerWarningIfNeeded(String folderUri, boolean isSupported) {
    if (isSupported) {
      var browseAction = new MessageActionItem("Read more");
      ShowMessageRequestParams messageParams = getMessageRequestForNotCompatibleServerWarning(folderUri, browseAction);
      client.showMessageRequest(messageParams).thenAccept(action -> {
        if (browseAction.equals(action)) {
          client.browseTo(LEARN_MORE_ABOUT_HOTSPOTS_LINK);
        }
      });
    }
  }

  @VisibleForTesting
  static ShowMessageRequestParams getMessageRequestForNotCompatibleServerWarning(String folderUri, MessageActionItem browseAction) {
    var messageParams = new ShowMessageRequestParams();
    messageParams.setType(MessageType.Warning);
    messageParams.setMessage(String.format("Folder %s must be bound to SonarQube 9.7+ in order to scan for security hotspots.", folderUri));
    messageParams.setActions(List.of(browseAction));
    return messageParams;
  }

}
