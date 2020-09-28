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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason;
import org.sonarsource.sonarlint.core.container.model.DefaultLoadedAnalyzer;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.client.api.common.Language.getLanguagesByPluginKey;

public class SkippedPluginsNotifierTest {

  private static SonarLintExtendedLanguageClient languageClient = mock(SonarLintExtendedLanguageClient.class);
  private static final String SERVER_ID = "serverId";
  private static final String SKIPPED_PLUGIN_MESSAGE = "analysis will not be available until following requirements are satisfied";
  private static final String INCOMPATIBLE_PLUGIN_API_MESSAGE = "is not compatible with this version of SonarLint. Ensure you are using the latest version of SonarLint and check SonarLint output for details.";
  private static final String UNSATISFIED_DEPENDENCY_MESSAGE = "is missing dependency";
  private static final String INCOMPATIBLE_PLUGIN_VERSION_MESSAGE_1 = "is too old for SonarLint. Current version is";
  private static final String INCOMPATIBLE_PLUGIN_VERSION_MESSAGE_2 = "Minimal supported version is";
  private static final String INCOMPATIBLE_PLUGIN_VERSION_MESSAGE_3 = "Please update your binding.";
  private static final String UNSATISFIED_RUNTIME_REQUIREMENT_JRE_MESSAGE = "requires Java runtime version";
  private static final String UNSATISFIED_RUNTIME_REQUIREMENT_NODEJS_MESSAGE = "requires Node.js runtime version";
  private static final String UNSATISFIED_RUNTIME_REQUIREMENT_COMMON_MESSAGE = "or later. Current version is";
  private static final String VM_TIPS_MESSAGE = "Learn [how to configure](https://code.visualstudio.com/docs/java/java-tutorial#_setting-up-visual-studio-code-for-java-development) JRE path for VSCode.";

  private static List<PluginDetails> pluginDetails = new ArrayList<>();
  private List<Language> skippedLanguages;
  private DefaultLoadedAnalyzer analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE =
    new DefaultLoadedAnalyzer("key", "name", "version",
      new SkipReason.UnsatisfiedRuntimeRequirement(SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE, "currentJavaVersion", "minJavaVersion"));
  private DefaultLoadedAnalyzer analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs =
    new DefaultLoadedAnalyzer("key", "name", "version",
      new SkipReason.UnsatisfiedRuntimeRequirement(SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS, "currentNodeJsVersion", "minNodeJsVersion"));
  private DefaultLoadedAnalyzer analyzerSkipReasonIncompatiblePluginApi = new DefaultLoadedAnalyzer("key", "analyzerSkipReasonIncompatiblePluginApi", "version", SkipReason.IncompatiblePluginApi.INSTANCE);
  private DefaultLoadedAnalyzer analyzerSkipReasonUnsatisfiedDependency =
    new DefaultLoadedAnalyzer("key", "analyzerSkipReasonUnsatisfiedDependency", "version", new SkipReason.UnsatisfiedDependency("dependencyKey"));
  private DefaultLoadedAnalyzer analyzerSkipReasonIncompatiblePluginVersion =
    new DefaultLoadedAnalyzer("key", "name", "version", new SkipReason.IncompatiblePluginVersion("minVersion"));
  private DefaultLoadedAnalyzer analyzerJavaNoSkipReason =
    new DefaultLoadedAnalyzer("java", "java", "version", null);
  private DefaultLoadedAnalyzer analyzerJavaSkipReasonIncompatiblePluginApi =
    new DefaultLoadedAnalyzer("java", "java", "version", SkipReason.IncompatiblePluginApi.INSTANCE);

  @BeforeAll
  public static void initAll() {
    MessageActionItem actionItem = new MessageActionItem("Open Java Settings");
    CompletableFuture<MessageActionItem> completableFuture = CompletableFuture.completedFuture(actionItem);

    when(languageClient.showMessageRequest(any(ShowMessageRequestParams.class))).thenReturn(completableFuture);
  }


  @Test
  public void no_job_if_no_plugins() {
    SkippedPluginsNotifier.notifyForSkippedPlugins(Collections.emptyList(), "", languageClient);

    verifyNoMoreInteractions(languageClient);
  }


  @Test
  public void send_notification() {

    pluginDetails.add(analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE);

    SkippedPluginsNotifier.notifyForSkippedPlugins(pluginDetails, "", languageClient);

    verify(languageClient).showMessageRequest(any(ShowMessageRequestParams.class));
    verify(languageClient).openJavaHomeSettings();
    verifyNoMoreInteractions(languageClient);
  }

  @Test
  public void build_long_message_connection_id() {
    String longMessage = SkippedPluginsNotifier.buildLongMessage(SERVER_ID, Collections.emptyList(), Collections.emptyList());

    assertThat(longMessage).contains("from connection").contains(SERVER_ID);
  }

  @Test
  public void build_long_message_no_languages() {
    String longMessage = SkippedPluginsNotifier.buildLongMessage(SERVER_ID, pluginDetails, Collections.emptyList());

    assertThat(longMessage).doesNotContain(SKIPPED_PLUGIN_MESSAGE);
  }

  @Test
  public void build_long_message_java_language() {
    pluginDetails.clear();
    pluginDetails.add(analyzerJavaSkipReasonIncompatiblePluginApi);
    updateSkippedLanguages();
    String longMessage = SkippedPluginsNotifier.buildLongMessage(SERVER_ID, pluginDetails, skippedLanguages);
    Language javaLanguage = (Language) getLanguagesByPluginKey(analyzerJavaSkipReasonIncompatiblePluginApi.key()).toArray()[0];

    assertThat(longMessage).contains(SKIPPED_PLUGIN_MESSAGE).contains(javaLanguage.getLanguageKey());
  }

  @Test
  public void build_long_message_incompatible_api() {
    pluginDetails.clear();
    pluginDetails.add(analyzerSkipReasonIncompatiblePluginApi);
    updateSkippedLanguages();
    String longMessage = SkippedPluginsNotifier.buildLongMessage(SERVER_ID, pluginDetails, skippedLanguages);

    assertThat(longMessage).contains(INCOMPATIBLE_PLUGIN_API_MESSAGE).contains(analyzerSkipReasonIncompatiblePluginApi.name());
  }

  @Test
  public void build_long_message_unsatisfied_dependency() {
    pluginDetails.clear();
    pluginDetails.add(analyzerSkipReasonUnsatisfiedDependency);
    updateSkippedLanguages();
    String longMessage = SkippedPluginsNotifier.buildLongMessage(SERVER_ID, pluginDetails, skippedLanguages);
    String dependencyKey = ((SkipReason.UnsatisfiedDependency) analyzerSkipReasonUnsatisfiedDependency.skipReason().get()).getDependencyKey();

    assertThat(longMessage).contains(UNSATISFIED_DEPENDENCY_MESSAGE)
      .contains(analyzerSkipReasonUnsatisfiedDependency.name())
      .contains(dependencyKey);
  }

  @Test
  public void build_long_message_incompatible_version() {
    pluginDetails.clear();
    pluginDetails.add(analyzerSkipReasonIncompatiblePluginVersion);
    updateSkippedLanguages();
    String longMessage = SkippedPluginsNotifier.buildLongMessage(SERVER_ID, pluginDetails, skippedLanguages);
    String minVersion = ((SkipReason.IncompatiblePluginVersion) analyzerSkipReasonIncompatiblePluginVersion.skipReason().get()).getMinVersion();

    assertThat(longMessage)
      .contains(INCOMPATIBLE_PLUGIN_VERSION_MESSAGE_1)
      .contains(analyzerSkipReasonIncompatiblePluginVersion.name())
      .contains(INCOMPATIBLE_PLUGIN_VERSION_MESSAGE_2)
      .contains(analyzerSkipReasonIncompatiblePluginVersion.version())
      .contains(INCOMPATIBLE_PLUGIN_VERSION_MESSAGE_3)
      .contains(minVersion);
  }

  @Test
  public void build_long_message_unsatisfied_runtime_requirement_jre() {
    pluginDetails.clear();
    pluginDetails.add(analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE);
    updateSkippedLanguages();
    String longMessage = SkippedPluginsNotifier.buildLongMessage(SERVER_ID, pluginDetails, skippedLanguages);
    SkipReason.UnsatisfiedRuntimeRequirement runtimeRequirement = (SkipReason.UnsatisfiedRuntimeRequirement) analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE.skipReason().get();
    String minVersion = runtimeRequirement.getMinVersion();
    String currentVersion = runtimeRequirement.getCurrentVersion();

    assertThat(longMessage)
      .contains(analyzerSkipReasonUnsatisfiedRuntimeRequirementJRE.name())
      .contains(UNSATISFIED_RUNTIME_REQUIREMENT_JRE_MESSAGE)
      .contains(minVersion)
      .contains(UNSATISFIED_RUNTIME_REQUIREMENT_COMMON_MESSAGE)
      .contains(currentVersion)
      .contains(VM_TIPS_MESSAGE);
  }

  @Test
  public void build_long_message_unsatisfied_runtime_requirement_nodejs() {
    pluginDetails.clear();
    pluginDetails.add(analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs);
    updateSkippedLanguages();
    String longMessage = SkippedPluginsNotifier.buildLongMessage(SERVER_ID, pluginDetails, skippedLanguages);
    SkipReason.UnsatisfiedRuntimeRequirement runtimeRequirement = (SkipReason.UnsatisfiedRuntimeRequirement) analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs.skipReason().get();
    String minVersion = runtimeRequirement.getMinVersion();
    String currentVersion = runtimeRequirement.getCurrentVersion();

    assertThat(longMessage)
      .contains(analyzerSkipReasonUnsatisfiedRuntimeRequirementNodeJs.name())
      .contains(UNSATISFIED_RUNTIME_REQUIREMENT_NODEJS_MESSAGE)
      .contains(minVersion)
      .contains(UNSATISFIED_RUNTIME_REQUIREMENT_COMMON_MESSAGE)
      .contains(currentVersion);
  }


  private void updateSkippedLanguages() {
    skippedLanguages = pluginDetails.stream()
      .flatMap(p -> getLanguagesByPluginKey(p.key()).stream())
      .filter(l -> EnginesFactory.getStandaloneLanguages().contains(l))
      .collect(toList());
  }
}
