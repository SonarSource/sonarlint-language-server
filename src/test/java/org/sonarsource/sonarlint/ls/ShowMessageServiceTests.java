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

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.ShowMessageService.LEARN_MORE_ABOUT_HOTSPOTS_LINK;
import static org.sonarsource.sonarlint.ls.ShowMessageService.getMessageRequestForNotCompatibleServerWarning;

class ShowMessageServiceTests {

  private final SonarLintExtendedLanguageClient mockClient = mock(SonarLintExtendedLanguageClient.class);
  private final ShowMessageService underTest = new ShowMessageService(mockClient);

  @Test
  void shouldSendNotCompatibleServerWarning() {
    var browseAction = new MessageActionItem("Read more");
    var folderUri = "folderUri";
    ShowMessageRequestParams messageParams = getMessageRequestForNotCompatibleServerWarning(folderUri, browseAction);
    when(mockClient.showMessageRequest(messageParams)).thenReturn((CompletableFuture.completedFuture(new MessageActionItem("Read more"))));
    underTest.sendNotCompatibleServerWarningIfNeeded(folderUri);

    verify(mockClient, times(1)).showMessageRequest(messageParams);
    verify(mockClient, times(1)).browseTo(LEARN_MORE_ABOUT_HOTSPOTS_LINK);
  }

}
