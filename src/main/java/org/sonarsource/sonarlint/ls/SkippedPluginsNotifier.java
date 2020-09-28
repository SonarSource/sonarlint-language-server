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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason;

import static java.util.stream.Collectors.toList;
import static org.sonarsource.sonarlint.core.client.api.common.Language.getLanguagesByPluginKey;

public class SkippedPluginsNotifier {

  private static final Logger LOG = Loggers.get(SkippedPluginsNotifier.class);

  private static final Set<String> displayedMessages = new HashSet<>();

  private SkippedPluginsNotifier() {
  }

  public static void notifyForSkippedPlugins(Collection<PluginDetails> allPlugins, @Nullable String connectionId, SonarLintExtendedLanguageClient client) {
    List<PluginDetails> skippedPlugins = allPlugins.stream().filter(p -> p.skipReason().isPresent()).collect(toList());
    if (!skippedPlugins.isEmpty()) {
      List<Language> skippedLanguages = skippedPlugins.stream()
        .flatMap(p -> getLanguagesByPluginKey(p.key()).stream())
        .filter(l -> EnginesFactory.getStandaloneLanguages().contains(l))
        .collect(toList());
      String longMessage = buildLongMessage(connectionId, skippedPlugins, skippedLanguages);
      String notificationTitle;
      if (skippedLanguages.isEmpty()) {
        notificationTitle = "Rules not available";
      } else {
        notificationTitle = "Language analysis not available";
      }
      String message = notificationTitle + "\n" + longMessage;

      if (displayedMessages.add(message)) {
        openJavaSettingsRequest(client, message);
      }
    }
  }

  private static void openJavaSettingsRequest(SonarLintExtendedLanguageClient client, String message) {
    ArrayList<MessageActionItem> actionItems = new ArrayList<>();
    MessageActionItem actionItem = new MessageActionItem("Open Java Settings");
    // if it's java requirements failed
    actionItems.add(actionItem);
    ShowMessageRequestParams params = new ShowMessageRequestParams(actionItems);
    params.setType(MessageType.Error);
    params.setMessage(message);
    client.showMessageRequest(params).thenAccept(action -> {
      client.openJavaHomeSettings();
    });
  }

  static String buildLongMessage(@Nullable String connectionId, List<PluginDetails> skippedPlugins, List<Language> skippedLanguages) {
    boolean includeVMtips = false;
    StringBuilder longMessage = new StringBuilder();
    longMessage.append("Some analyzers");
    if (connectionId != null) {
      longMessage.append(" from connection '").append(connectionId).append("'");
    }
    longMessage.append(" can not be loaded.\n\n");
    if (!skippedLanguages.isEmpty()) {
      longMessage.append(String.format("%s analysis will not be available until following requirements are satisfied:%n",
        skippedLanguages.stream()
          .map(Language::getLanguageKey)
          .collect(Collectors.joining(", "))));
    }
    for (PluginDetails skippedPlugin : skippedPlugins) {
      SkipReason skipReason = skippedPlugin.skipReason().orElseThrow(IllegalStateException::new);
      if (skipReason instanceof SkipReason.IncompatiblePluginApi) {
        // Should never occurs in standalone mode
        longMessage.append(String.format(
          " - '%s' is not compatible with this version of SonarLint. Ensure you are using the latest version of SonarLint and check SonarLint output for details.%n",
          skippedPlugin.name()));
      } else if (skipReason instanceof SkipReason.UnsatisfiedDependency) {
        // Should never occurs in standalone mode
        SkipReason.UnsatisfiedDependency skipReasonCasted = (SkipReason.UnsatisfiedDependency) skipReason;
        longMessage.append(String.format(" - '%s' is missing dependency '%s'%n", skippedPlugin.name(), skipReasonCasted.getDependencyKey()));
      } else if (skipReason instanceof SkipReason.IncompatiblePluginVersion) {
        // Should never occurs in standalone mode
        SkipReason.IncompatiblePluginVersion skipReasonCasted = (SkipReason.IncompatiblePluginVersion) skipReason;
        longMessage.append(String.format(" - '%s' is too old for SonarLint. Current version is %s. Minimal supported version is %s. Please update your binding.%n",
          skippedPlugin.name(),
          skippedPlugin.version(), skipReasonCasted.getMinVersion()));
      } else if (skipReason instanceof SkipReason.UnsatisfiedRuntimeRequirement) {
        SkipReason.UnsatisfiedRuntimeRequirement skipReasonCasted = (SkipReason.UnsatisfiedRuntimeRequirement) skipReason;
        if (skipReasonCasted.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE) {
          includeVMtips = true;
          longMessage.append(String.format(" - '%s' requires Java runtime version %s or later. Current version is %s.%n", skippedPlugin.name(),
            skipReasonCasted.getMinVersion(), skipReasonCasted.getCurrentVersion()));
        } else if (skipReasonCasted.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS) {
          longMessage.append(String.format(" - '%s' requires Node.js runtime version %s or later. Current version is %s.%n", skippedPlugin.name(),
            skipReasonCasted.getMinVersion(), skipReasonCasted.getCurrentVersion()));
          longMessage.append("\n").append("Learn [how to configure](https://code.visualstudio.com/docs/nodejs/nodejs-tutorial) Node.js for VSCode.");
        }
        includeVMtips = includeVMtips && skipReasonCasted.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE;
      }
    }
    if (includeVMtips) {
      longMessage
        .append("\nLearn [how to configure](https://code.visualstudio.com/docs/java/java-tutorial#_setting-up-visual-studio-code-for-java-development) JRE path for VSCode.");
    }
    return longMessage.toString();
  }

}
