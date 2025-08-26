/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static java.lang.String.format;

public class EnabledLanguages {

  private final List<Path> analyzers;
  private final LanguageClientLogger lsLogOutput;

  public EnabledLanguages(List<Path> analyzers, LanguageClientLogger lsLogOutput) {
    this.analyzers = analyzers;
    this.lsLogOutput = lsLogOutput;
  }

  private static final Set<Language> STANDALONE_LANGUAGES = EnumSet.of(
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
    Language.YAML
  );

  private static final Set<Language> CONNECTED_ADDITIONAL_LANGUAGES = EnumSet.of(
    Language.APEX,
    Language.COBOL,
    Language.PLSQL,
    Language.TSQL,
    Language.ANSIBLE,
    Language.TEXT,
    Language.GITHUBACTIONS
  );


  public static Set<Language> getStandaloneLanguages() {
    return STANDALONE_LANGUAGES;
  }

  public static Set<Language> getConnectedLanguages() {
    return CONNECTED_ADDITIONAL_LANGUAGES;
  }

  public Set<Path> getEmbeddedPluginsPaths() {
    return Set.copyOf(analyzers);
  }

  public Map<String, Path> getConnectedModeEmbeddedPluginPathsByKey() {
    var plugins = new HashMap<String, Path>();
    Stream.of(ConnectedModeEmbeddedPlugin.values())
      .forEach(plugin -> addPluginPathOrWarn(plugin, plugins));

    analyzers.stream().filter(it -> it.toString().endsWith("sonarlintomnisharp.jar")).findFirst()
      .ifPresent(p -> plugins.put("omnisharp", p));
    return plugins;
  }

  private void addPluginPathOrWarn(ConnectedModeEmbeddedPlugin plugin, Map<String, Path> plugins) {
    analyzers.stream().filter(it -> it.toString().endsWith(plugin.analyzerFileName)).findFirst()
      .ifPresentOrElse(
        pluginPath -> plugins.put(plugin.sonarPluginKey, pluginPath),
        () -> lsLogOutput.warn(format("Embedded plugin not found: %s", plugin.sonarLanguageKey)));
  }

  private enum ConnectedModeEmbeddedPlugin {
    CFAMILY("sonarcfamily.jar", "cpp", "c"),
    HTML("sonarhtml.jar", "web", "web"),
    JS("sonarjs.jar", "javascript", "js"),
    XML("sonarxml.jar", "xml", "xml"),
    TEXT("sonartext.jar", "text", "secrets"),
    GO("sonargo.jar", "go", "go"),
    IAC("sonariac.jar", "iac", "cloudformation");

    private final String analyzerFileName;
    private final String sonarPluginKey;
    private final String sonarLanguageKey;

    ConnectedModeEmbeddedPlugin(String analyzerFileName, String sonarPluginKey, String sonarLanguageKey) {
      this.analyzerFileName = analyzerFileName;
      this.sonarPluginKey = sonarPluginKey;
      this.sonarLanguageKey = sonarLanguageKey;
    }
  }
}
