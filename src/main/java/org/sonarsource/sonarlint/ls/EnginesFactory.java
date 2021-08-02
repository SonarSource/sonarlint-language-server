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

import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.ModulesProvider;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;

public class EnginesFactory {

  private static final Logger LOG = Loggers.get(EnginesFactory.class);

  private final LanguageClientLogOutput lsLogOutput;
  private final Collection<URL> standaloneAnalyzers;
  @CheckForNull
  private Path typeScriptPath;
  private static final Language[] STANDALONE_LANGUAGES = {
    Language.HTML,
    Language.JAVA,
    Language.JS,
    Language.PHP,
    Language.PYTHON,
    Language.SECRETS,
    Language.TS
  };

  private static final Language[] CONNECTED_ADDITIONAL_LANGUAGES = {
    Language.APEX,
    Language.PLSQL
  };

  private final NodeJsRuntime nodeJsRuntime;
  private final ModulesProvider modulesProvider;
  private final Collection<URL> extraAnalyzers;

  public EnginesFactory(Collection<URL> standaloneAnalyzers, LanguageClientLogOutput lsLogOutput, NodeJsRuntime nodeJsRuntime, ModulesProvider modulesProvider, Collection<URL> extraAnalyzers) {
    this.standaloneAnalyzers = standaloneAnalyzers;
    this.lsLogOutput = lsLogOutput;
    this.nodeJsRuntime = nodeJsRuntime;
    this.modulesProvider = modulesProvider;
    this.extraAnalyzers = extraAnalyzers;
  }

  public StandaloneSonarLintEngine createStandaloneEngine() {
    LOG.debug("Starting standalone SonarLint engine...");
    LOG.debug("Using {} analyzers", standaloneAnalyzers.size());

    try {
      StandaloneGlobalConfiguration configuration = StandaloneGlobalConfiguration.builder()
        .setExtraProperties(prepareExtraProps())
        .addEnabledLanguages(STANDALONE_LANGUAGES)
        .setNodeJs(nodeJsRuntime.getNodeJsPath(), nodeJsRuntime.getNodeJsVersion())
        .addPlugins(standaloneAnalyzers.toArray(new URL[0]))
        .addPlugins(extraAnalyzers.toArray(new URL[0]))
        .setModulesProvider(modulesProvider)
        .setLogOutput(lsLogOutput)
        .build();

      StandaloneSonarLintEngine engine = newStandaloneEngine(configuration);
      LOG.debug("Standalone SonarLint engine started");
      return engine;
    } catch (Exception e) {
      LOG.error("Error starting standalone SonarLint engine", e);
      throw new IllegalStateException(e);
    }
  }

  StandaloneSonarLintEngine newStandaloneEngine(StandaloneGlobalConfiguration configuration) {
    return new StandaloneSonarLintEngineImpl(configuration);
  }

  public ConnectedSonarLintEngine createConnectedEngine(String connectionId) {
    ConnectedGlobalConfiguration.Builder builder = ConnectedGlobalConfiguration.builder()
      .setConnectionId(connectionId)
      .setExtraProperties(prepareExtraProps())
      .addEnabledLanguages(STANDALONE_LANGUAGES)
      .addEnabledLanguages(CONNECTED_ADDITIONAL_LANGUAGES)
      .setNodeJs(nodeJsRuntime.getNodeJsPath(), nodeJsRuntime.getNodeJsVersion())
      .setModulesProvider(modulesProvider)
      .setLogOutput(lsLogOutput);

    extraAnalyzers.forEach(analyzer-> builder.addExtraPlugin(guessPluginKey(analyzer.getPath()), analyzer));
    ConnectedSonarLintEngine engine = newConnectedEngine(builder.build());

    LOG.debug("SonarLint engine started for connection '{}'", connectionId);
    return engine;
  }

  static String guessPluginKey(String pluginUrl) {
    if (pluginUrl.contains("sonarsecrets")) {
      return Language.SECRETS.getPluginKey();
    }
    throw new IllegalStateException("Unknown analyzer.");
  }

  private Map<String, String> prepareExtraProps() {
    Map<String, String> extraProperties = new HashMap<>();
    if (typeScriptPath != null) {
      extraProperties.put(AnalysisManager.TYPESCRIPT_PATH_PROP, typeScriptPath.toString());
    }
    return extraProperties;
  }

  ConnectedSonarLintEngine newConnectedEngine(ConnectedGlobalConfiguration configuration) {
    return new ConnectedSonarLintEngineImpl(configuration);
  }

  public void initialize(@Nullable Path typeScriptPath) {
    this.typeScriptPath = typeScriptPath;
  }

  public static Set<Language> getStandaloneLanguages() {
    return EnumSet.copyOf(Arrays.asList(STANDALONE_LANGUAGES));
  }

}
