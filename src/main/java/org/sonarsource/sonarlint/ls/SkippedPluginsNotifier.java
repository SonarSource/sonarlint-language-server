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
package org.sonarsource.sonarlint.ls;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;

public class SkippedPluginsNotifier {

  private static final Set<String> displayedMessages = new HashSet<>();

  public static final MessageActionItem ACTION_OPEN_SETTINGS = new MessageActionItem("Open Settings");

  private SkippedPluginsNotifier() {
  }

  public static void notifyOnceForSkippedPlugins(AnalysisResults analysisResults, Collection<PluginDetails> allPlugins, SonarLintExtendedLanguageClient client) {
    Set<Language> attemptedLanguages = analysisResults.languagePerFile().values()
      .stream()
      .filter(Objects::nonNull)
      .collect(toSet());
    attemptedLanguages.forEach(l -> {
      final Optional<PluginDetails> correspondingPlugin = allPlugins.stream().filter(p -> p.key().equals(l.getPluginKey())).findFirst();
      correspondingPlugin.flatMap(PluginDetails::skipReason).ifPresent(skipReason -> {
        if (skipReason instanceof SkipReason.UnsatisfiedRuntimeRequirement) {
          final SkipReason.UnsatisfiedRuntimeRequirement runtimeRequirement = (SkipReason.UnsatisfiedRuntimeRequirement) skipReason;
          final String title = String.format("SonarLint failed to analyze %s code", l.getLabel());
          if (runtimeRequirement.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE) {
            String content = String.format(
              "Java runtime version %s or later is required. Current version is %s.",runtimeRequirement.getMinVersion(), runtimeRequirement.getCurrentVersion()
            );
            showMessageWithOpenSettingsAction(client, formatMessage(title, content), client::openJavaHomeSettings);
          } else if (runtimeRequirement.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS) {
            String content = String.format(
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

  private static void showMessageWithOpenSettingsAction(SonarLintExtendedLanguageClient client, String message, Supplier<CompletableFuture<Void>> callback) {
    if (displayedMessages.add(message)) {
      ShowMessageRequestParams params = new ShowMessageRequestParams(singletonList(ACTION_OPEN_SETTINGS));
      params.setType(MessageType.Error);
      params.setMessage(message);
      client.showMessageRequest(params).thenAccept(action -> {
        if (ACTION_OPEN_SETTINGS.equals(action)) {
          callback.get();
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
   * - https://github.com/microsoft/vscode/blob/7ce60506a9b5df9ef05ac51f8c94e1085a464d17/src/vs/editor/contrib/message/messageController.ts#L155
   * - https://developer.mozilla.org/en-US/docs/Web/API/Node/textContent
   */
  private static String formatMessage(String title, String content) {
    return String.format("%s: %s", title, content);
  }

  @VisibleForTesting
  static void clearMessages() {
    displayedMessages.clear();
  }
}
