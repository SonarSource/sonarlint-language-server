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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.plugin.commons.SkipReason;

import static java.util.stream.Collectors.toSet;

public class SkippedPluginsNotifier {

  private final Set<String> displayedMessages = new HashSet<>();

  public static final MessageActionItem ACTION_OPEN_SETTINGS = new MessageActionItem("Open Settings");

  private final SonarLintExtendedLanguageClient client;

  public SkippedPluginsNotifier(SonarLintExtendedLanguageClient client) {
    this.client = client;
  }

  public void notifyOnceForSkippedPlugins(AnalysisResults analysisResults, Collection<PluginDetails> allPlugins) {
    var attemptedLanguages = analysisResults.languagePerFile().values()
      .stream()
      .filter(Objects::nonNull)
      .collect(toSet());
    attemptedLanguages.forEach(l -> {
      final var correspondingPlugin = allPlugins.stream().filter(p -> p.key().equals(l.getPluginKey())).findFirst();
      correspondingPlugin.flatMap(PluginDetails::skipReason).ifPresent(skipReason -> {
        if (skipReason instanceof SkipReason.UnsatisfiedRuntimeRequirement) {
          final var runtimeRequirement = (SkipReason.UnsatisfiedRuntimeRequirement) skipReason;
          final var title = String.format("SonarLint failed to analyze %s code", l.getLabel());
          if (runtimeRequirement.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE) {
            var content = String.format(
              "Java runtime version %s or later is required. Current version is %s.", runtimeRequirement.getMinVersion(), runtimeRequirement.getCurrentVersion());
            showMessageWithOpenSettingsAction(client, formatMessage(title, content), client::openJavaHomeSettings);
          } else if (runtimeRequirement.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS) {
            var content = String.format(
              "Node.js runtime version %s or later is required.", runtimeRequirement.getMinVersion());
            if (runtimeRequirement.getCurrentVersion() != null) {
              content += String.format(" Current version is %s.", runtimeRequirement.getCurrentVersion());
            }
            showMessageWithOpenSettingsAction(client, formatMessage(title, content), client::openPathToNodeSettings);
          }
        }
      });
    });
  }

  private void showMessageWithOpenSettingsAction(SonarLintExtendedLanguageClient client, String message, Runnable callback) {
    if (displayedMessages.add(message)) {
      var params = new ShowMessageRequestParams(List.of(ACTION_OPEN_SETTINGS));
      params.setType(MessageType.Error);
      params.setMessage(message);
      client.showMessageRequest(params).thenAccept(action -> {
        if (ACTION_OPEN_SETTINGS.equals(action)) {
          callback.run();
        }
      });
    }
  }

  /*
   * The LSP specification does not mention anything about how the message part of "showMessage(Request)" will be rendered.
   * The current VSCode implementation uses it to set a DOM node's textContent attribute, which retains only... text content,
   * as specified by the HTML recommendation.
   *
   * See:
   * - https://github.com/microsoft/vscode/blob/7ce60506a9b5df9ef05ac51f8c94e1085a464d17/src/vs/editor/contrib/message/messageController.ts#
   * L155
   * - https://developer.mozilla.org/en-US/docs/Web/API/Node/textContent
   */
  private static String formatMessage(String title, String content) {
    return String.format("%s: %s", title, content);
  }

}
