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

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.analysis.api.ClientModulesProvider;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class EnginesFactoryTests {
  private EnginesFactory underTest;

  @BeforeEach
  void prepare() {
    var standaloneAnalysers = List.of(
      Paths.get("plugin1.jar"),
      Paths.get("plugin2.jar"),
      Paths.get("sonarjs.jar"),
      Paths.get("sonarhtml.jar"),
      Paths.get("sonarxml.jar"));
    underTest = new EnginesFactory(standaloneAnalysers, mock(LanguageClientLogOutput.class),
      mock(NodeJsRuntime.class), mock(ClientModulesProvider.class), Collections.emptyList());
    underTest = spy(underTest);
  }

  @Test
  void get_standalone_languages() {
    assertThat(EnginesFactory.getStandaloneLanguages()).containsExactlyInAnyOrder(
      Language.C,
      Language.CPP,
      Language.HTML,
      Language.JAVA,
      Language.JS,
      Language.PHP,
      Language.PYTHON,
      Language.SECRETS,
      Language.TS,
      Language.XML);
  }

  @Test
  void resolve_extra_plugin_key() {
    assertThat(EnginesFactory.guessPluginKey("file:///sonarsecrets.jar")).isEqualTo(Language.SECRETS.getPluginKey());
    assertThatThrownBy(() -> EnginesFactory.guessPluginKey("file:///unknown.jar"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Unknown analyzer.");
  }

  @Test
  void failIfJsTsAnalyserNotFound() {
    var standaloneAnalysers = List.of(Paths.get("sonarhtml.jar", "sonarxml.jar"));
    var factory = new EnginesFactory(standaloneAnalysers, mock(LanguageClientLogOutput.class),
      mock(NodeJsRuntime.class), mock(ClientModulesProvider.class), Collections.emptyList());

    assertThatThrownBy(() -> factory.createConnectedEngine("foo", mock(ServerConnectionSettings.class)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Embedded plugin not found: " + Language.JS.getLabel());
  }

  @Test
  void failIfHtmlAnalyserNotFound() {
    var standaloneAnalysers = List.of(Paths.get("sonarjs.jar", "sonarxml.jar"));
    var factory = new EnginesFactory(standaloneAnalysers, mock(LanguageClientLogOutput.class),
      mock(NodeJsRuntime.class), mock(ClientModulesProvider.class), Collections.emptyList());

    assertThatThrownBy(() -> factory.createConnectedEngine("foo", mock(ServerConnectionSettings.class)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Embedded plugin not found: " + Language.HTML.getLabel());
  }

  @Test
  void failIfXmlAnalyserNotFound() {
    var standaloneAnalysers = List.of(Paths.get("sonarjs.jar", "sonarhtml.jar"));
    var factory = new EnginesFactory(standaloneAnalysers, mock(LanguageClientLogOutput.class),
      mock(NodeJsRuntime.class), mock(ClientModulesProvider.class), Collections.emptyList());

    assertThatThrownBy(() -> factory.createConnectedEngine("foo", mock(ServerConnectionSettings.class)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Embedded plugin not found: " + Language.XML.getLabel());
  }
}
