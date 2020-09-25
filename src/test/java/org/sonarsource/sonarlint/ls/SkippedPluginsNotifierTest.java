/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason;
import org.sonarsource.sonarlint.core.container.model.DefaultLoadedAnalyzer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SkippedPluginsNotifierTest {

  private SonarLintExtendedLanguageClient languageClient = mock(SonarLintExtendedLanguageClient.class);
  ShowMessageRequestParams params;
  @BeforeEach
  public void init() {
    MessageActionItem actionItem = new MessageActionItem("Open Java Settings");
    ArrayList<MessageActionItem> actionItems = new ArrayList<>();
    actionItems.add(actionItem);
    params = new ShowMessageRequestParams(actionItems);
    params.setType(MessageType.Error);
    params.setMessage("Rules not available" + System.lineSeparator() +
      "Some analyzers from connection '' can not be loaded." + System.lineSeparator() +
      System.lineSeparator() +
      " - 'name' requires Java runtime version minVersion or later. Current version is currentVersion." + System.lineSeparator() +
      System.lineSeparator() +
      "Learn [how to configure](https://code.visualstudio.com/docs/java/java-tutorial#_setting-up-visual-studio-code-for-java-development) JRE path for VSCode.");
    CompletableFuture<MessageActionItem> completableFuture = CompletableFuture.completedFuture(actionItem);

    when(languageClient.showMessageRequest(params)).thenReturn(completableFuture);
  }

  @Test
  public void no_job_if_no_plugins() {
    SkippedPluginsNotifier.notifyForSkippedPlugins(Collections.emptyList(), "", languageClient);

    verifyNoMoreInteractions(languageClient);
  }


  @Test
  public void send_notification() {
    DefaultLoadedAnalyzer plugin =
      new DefaultLoadedAnalyzer("key", "name", "version",
        new SkipReason.UnsatisfiedRuntimeRequirement(SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE, "currentVersion", "minVersion"));

    ArrayList<PluginDetails> pluginDetails = new ArrayList<>();
    pluginDetails.add(plugin);


    SkippedPluginsNotifier.notifyForSkippedPlugins(pluginDetails, "", languageClient);

    verify(languageClient).showMessageRequest(params);
    verify(languageClient).openJavaHomeSettings();
    verifyNoMoreInteractions(languageClient);
  }

}
