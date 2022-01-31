/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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

import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientModulesProvider;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;

public class EnginesFactory {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final LanguageClientLogOutput logOutput;
  private final Collection<Path> standaloneAnalyzers;
  @CheckForNull
  private Path typeScriptPath;
  private static final Language[] STANDALONE_LANGUAGES = {
    Language.HTML,
    Language.JAVA,
    Language.JS,
    Language.PHP,
    Language.PYTHON,
    Language.SECRETS,
    Language.TS,
    Language.XML
  };

  private static final Language[] CONNECTED_ADDITIONAL_LANGUAGES = {
    Language.APEX,
    Language.PLSQL
  };

  private final NodeJsRuntime nodeJsRuntime;
  private final ClientModulesProvider modulesProvider;
  private final Collection<Path> extraAnalyzers;

  public EnginesFactory(Collection<Path> standaloneAnalyzers, LanguageClientLogOutput globalLogOutput, NodeJsRuntime nodeJsRuntime, ClientModulesProvider modulesProvider,
    Collection<Path> extraAnalyzers) {
    this.standaloneAnalyzers = standaloneAnalyzers;
    this.logOutput = globalLogOutput;
    this.nodeJsRuntime = nodeJsRuntime;
    this.modulesProvider = modulesProvider;
    this.extraAnalyzers = extraAnalyzers;
  }

  public StandaloneSonarLintEngine createStandaloneEngine() {
    LOG.debug("Starting standalone SonarLint engine...");
    LOG.debug("Using {} analyzers", standaloneAnalyzers.size());

    try {
      var configuration = StandaloneGlobalConfiguration.builder()
        .setExtraProperties(prepareExtraProps())
        .addEnabledLanguages(STANDALONE_LANGUAGES)
        .setNodeJs(nodeJsRuntime.getNodeJsPath(), nodeJsRuntime.getNodeJsVersion())
        .addPlugins(standaloneAnalyzers.toArray(Path[]::new))
        .addPlugins(extraAnalyzers.toArray(Path[]::new))
        .setModulesProvider(modulesProvider)
        .setLogOutput(logOutput)
        .build();

      var engine = newStandaloneEngine(configuration);
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
    var builder = ConnectedGlobalConfiguration.builder()
      .setConnectionId(connectionId)
      .setExtraProperties(prepareExtraProps())
      .addEnabledLanguages(STANDALONE_LANGUAGES)
      .addEnabledLanguages(CONNECTED_ADDITIONAL_LANGUAGES)
      .setNodeJs(nodeJsRuntime.getNodeJsPath(), nodeJsRuntime.getNodeJsVersion())
      .setModulesProvider(modulesProvider)
      .setLogOutput(logOutput);

    extraAnalyzers.forEach(analyzer -> builder.addExtraPlugin(guessPluginKey(analyzer.toString()), analyzer));
    var engine = newConnectedEngine(builder.build());

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
    var extraProperties = new HashMap<String, String>();
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
    return EnumSet.copyOf(List.of(STANDALONE_LANGUAGES));
  }

}
