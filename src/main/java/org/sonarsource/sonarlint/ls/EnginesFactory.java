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
package org.sonarsource.sonarlint.ls;

import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.analysis.api.ClientModulesProvider;
import org.sonarsource.sonarlint.core.client.legacy.analysis.EngineConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;

import static java.lang.String.format;

public class EnginesFactory {

  public static Path sonarLintUserHomeOverride = null;
  private final LanguageClientLogOutput logOutput;
  private final Collection<Path> standaloneAnalyzers;
  private final Map<String, Path> embeddedPluginsToPath;
  private String omnisharpDirectory;


  private static final Language[] STANDALONE_LANGUAGES = {
    Language.AZURERESOURCEMANAGER,
    Language.CPP,
    Language.C,
    Language.CLOUDFORMATION,
    Language.CS,
    Language.CSS,
    Language.DOCKER,
    Language.GO,
    Language.HTML,
    Language.IPYTHON,
    Language.JAVA,
    Language.JS,
    Language.JSON,
    Language.KUBERNETES,
    Language.PHP,
    Language.PYTHON,
    Language.SECRETS,
    Language.TERRAFORM,
    Language.TS,
    Language.XML,
    Language.YAML,
  };

  private static final Language[] CONNECTED_ADDITIONAL_LANGUAGES = {
    Language.APEX,
    Language.COBOL,
    Language.PLSQL,
    Language.TSQL
  };

  private final NodeJsRuntime nodeJsRuntime;
  private final ClientModulesProvider modulesProvider;
  private BackendServiceFacade backendServiceFacade;
  private final AtomicReference<Boolean> shutdown = new AtomicReference<>(false);

  public EnginesFactory(Collection<Path> standaloneAnalyzers, Map<String, Path> embeddedPluginsToPath,
    LanguageClientLogOutput globalLogOutput, NodeJsRuntime nodeJsRuntime, ClientModulesProvider modulesProvider, BackendServiceFacade backendServiceFacade) {
    this.standaloneAnalyzers = standaloneAnalyzers;
    this.embeddedPluginsToPath = embeddedPluginsToPath;
    this.logOutput = globalLogOutput;
    this.nodeJsRuntime = nodeJsRuntime;
    this.modulesProvider = modulesProvider;
    this.backendServiceFacade = backendServiceFacade;
  }

  public void setOmnisharpDirectory(String omnisharpDirectory) {
    this.omnisharpDirectory = omnisharpDirectory;
  }

  public SonarLintAnalysisEngine createEngine(@Nullable String connectionId) {
    if (shutdown.get().equals(true)) {
      throw new IllegalStateException("Language server is shutting down, won't create engine");
    }

    logOutput.log("Starting standalone SonarLint engine...", ClientLogOutput.Level.DEBUG);
    logOutput.log(format("Using %d analyzers", standaloneAnalyzers.size()), ClientLogOutput.Level.DEBUG);
    try {
      var configuration = EngineConfiguration.builder()
        .setSonarLintUserHome(sonarLintUserHomeOverride)
        .setModulesProvider(modulesProvider)
        .setExtraProperties(getExtraProperties())
        .setLogOutput(logOutput).build();

      waitForBackendInit();
      var engine = new SonarLintAnalysisEngine(configuration, backendServiceFacade.getBackendService().getBackend(), connectionId);
      logOutput.log("Standalone SonarLint engine started", ClientLogOutput.Level.DEBUG);
      return engine;
    } catch (Exception e) {
      logOutput.log(format("Error starting standalone SonarLint engine %s", e), ClientLogOutput.Level.ERROR);
      throw new IllegalStateException(e);
    }
  }

  private void waitForBackendInit() throws InterruptedException {
    var counter = 0;
    while (!backendServiceFacade.isInitialized().get() && counter < 10) {
      Thread.sleep(200);
      counter++;
    }
  }

  @NotNull
  private Map<String, String> getExtraProperties() {
    if (omnisharpDirectory == null) {
      return Map.of();
    } else {
      return Map.of(
        "sonar.cs.internal.omnisharpNet6Location", Path.of(omnisharpDirectory, "net6").toString(),
        "sonar.cs.internal.omnisharpWinLocation", Path.of(omnisharpDirectory, "net472").toString(),
        "sonar.cs.internal.omnisharpMonoLocation", Path.of(omnisharpDirectory, "mono").toString()
      );
    }
  }

  public static Set<Language> getStandaloneLanguages() {
    return EnumSet.copyOf(List.of(STANDALONE_LANGUAGES));
  }

  public static Set<Language> getConnectedLanguages() {
    return Set.of(CONNECTED_ADDITIONAL_LANGUAGES);
  }

  public static boolean isConnectedLanguage(@Nullable Language language) {
    return language != null && getConnectedLanguages().contains(language);
  }

  public void shutdown() {
    shutdown.set(true);
  }

}
