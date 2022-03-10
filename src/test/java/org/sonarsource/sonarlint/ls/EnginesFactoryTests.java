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
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.analysis.api.ClientModulesProvider;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class EnginesFactoryTests {

  private static final Path FAKE_TYPESCRIPT_PATH = Paths.get("some/path");
  private EnginesFactory underTest;

  @BeforeEach
  void prepare() throws Exception {
    underTest = new EnginesFactory(List.of(Paths.get("plugin1.jar"), Paths.get("plugin2.jar")), mock(LanguageClientLogOutput.class),
      mock(NodeJsRuntime.class), mock(ClientModulesProvider.class), Collections.emptyList());
    underTest = spy(underTest);
  }

  @Test
  void pass_typescript_path_to_standalone_engine() throws Exception {
    underTest.initialize(FAKE_TYPESCRIPT_PATH);

    var argCaptor = ArgumentCaptor.forClass(StandaloneGlobalConfiguration.class);
    var mockEngine = mock(StandaloneSonarLintEngine.class);
    doReturn(mockEngine).when(underTest).newStandaloneEngine(argCaptor.capture());

    var createdEngine = underTest.createStandaloneEngine();

    assertThat(createdEngine).isSameAs(mockEngine);
    var capturedConfig = argCaptor.getValue();
    assertThat(capturedConfig.extraProperties()).containsEntry("sonar.typescript.internal.typescriptLocation", FAKE_TYPESCRIPT_PATH.toString());
    assertThat(capturedConfig.getEnabledLanguages()).containsOnly(Language.C, Language.CPP, Language.HTML, Language.JAVA, Language.JS, Language.PHP, Language.PYTHON, Language.SECRETS, Language.TS,
      Language.XML);
  }

  @Test
  void no_typescript_to_standalone_engine() throws Exception {
    underTest.initialize(null);

    var argCaptor = ArgumentCaptor.forClass(StandaloneGlobalConfiguration.class);
    var mockEngine = mock(StandaloneSonarLintEngine.class);
    doReturn(mockEngine).when(underTest).newStandaloneEngine(argCaptor.capture());

    var createdEngine = underTest.createStandaloneEngine();

    assertThat(createdEngine).isSameAs(mockEngine);
    var capturedConfig = argCaptor.getValue();
    assertThat(capturedConfig.extraProperties()).isEmpty();
  }

  @Test
  void pass_typescript_path_to_connected_engine() throws Exception {
    underTest.initialize(FAKE_TYPESCRIPT_PATH);

    var argCaptor = ArgumentCaptor.forClass(ConnectedGlobalConfiguration.class);
    var mockEngine = mock(ConnectedSonarLintEngine.class);
    doReturn(mockEngine).when(underTest).newConnectedEngine(argCaptor.capture());

    var createdEngine = underTest.createConnectedEngine("foo");

    assertThat(createdEngine).isSameAs(mockEngine);
    var capturedConfig = argCaptor.getValue();
    assertThat(capturedConfig.extraProperties()).containsEntry("sonar.typescript.internal.typescriptLocation", FAKE_TYPESCRIPT_PATH.toString());
    assertThat(capturedConfig.getEnabledLanguages()).containsOnly(Language.APEX, Language.C, Language.CPP, Language.HTML, Language.JAVA, Language.JS, Language.PHP, Language.PLSQL, Language.PYTHON,
      Language.SECRETS, Language.TS, Language.XML);
  }

  @Test
  void no_typescript_to_connected_engine() throws Exception {
    underTest.initialize(null);

    var argCaptor = ArgumentCaptor.forClass(ConnectedGlobalConfiguration.class);
    var mockEngine = mock(ConnectedSonarLintEngine.class);
    doReturn(mockEngine).when(underTest).newConnectedEngine(argCaptor.capture());

    var createdEngine = underTest.createConnectedEngine("foo");

    assertThat(createdEngine).isSameAs(mockEngine);
    var capturedConfig = argCaptor.getValue();
    assertThat(capturedConfig.extraProperties()).isEmpty();
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
}
