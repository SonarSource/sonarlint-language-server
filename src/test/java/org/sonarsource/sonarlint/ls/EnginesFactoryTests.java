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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.ModulesProvider;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;

import static java.net.URI.create;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class EnginesFactoryTests {

  private static final Path FAKE_TYPESCRIPT_PATH = Paths.get("some/path");
  private EnginesFactory underTest;

  @BeforeEach
  public void prepare() throws Exception {
    underTest = new EnginesFactory(asList(create("file://plugin1.jar").toURL(), create("file://plugin2.jar").toURL()), mock(LanguageClientLogOutput.class), mock(NodeJsRuntime.class), mock(ModulesProvider.class), Collections.emptyList());
    underTest = spy(underTest);
  }

  @Test
  public void pass_typescript_path_to_standalone_engine() throws Exception {
    underTest.initialize(FAKE_TYPESCRIPT_PATH);

    ArgumentCaptor<StandaloneGlobalConfiguration> argCaptor = ArgumentCaptor.forClass(StandaloneGlobalConfiguration.class);
    StandaloneSonarLintEngine mockEngine = mock(StandaloneSonarLintEngine.class);
    doReturn(mockEngine).when(underTest).newStandaloneEngine(argCaptor.capture());

    StandaloneSonarLintEngine createdEngine = underTest.createStandaloneEngine();

    assertThat(createdEngine).isSameAs(mockEngine);
    StandaloneGlobalConfiguration capturedConfig = argCaptor.getValue();
    assertThat(capturedConfig.extraProperties()).containsEntry("sonar.typescript.internal.typescriptLocation", FAKE_TYPESCRIPT_PATH.toString());
    assertThat(capturedConfig.getEnabledLanguages()).containsOnly(Language.HTML, Language.JAVA, Language.JS, Language.PHP, Language.PYTHON, Language.SECRETS, Language.TS);
  }

  @Test
  public void no_typescript_to_standalone_engine() throws Exception {
    underTest.initialize(null);

    ArgumentCaptor<StandaloneGlobalConfiguration> argCaptor = ArgumentCaptor.forClass(StandaloneGlobalConfiguration.class);
    StandaloneSonarLintEngine mockEngine = mock(StandaloneSonarLintEngine.class);
    doReturn(mockEngine).when(underTest).newStandaloneEngine(argCaptor.capture());

    StandaloneSonarLintEngine createdEngine = underTest.createStandaloneEngine();

    assertThat(createdEngine).isSameAs(mockEngine);
    StandaloneGlobalConfiguration capturedConfig = argCaptor.getValue();
    assertThat(capturedConfig.extraProperties()).isEmpty();
  }

  @Test
  public void pass_typescript_path_to_connected_engine() throws Exception {
    underTest.initialize(FAKE_TYPESCRIPT_PATH);

    ArgumentCaptor<ConnectedGlobalConfiguration> argCaptor = ArgumentCaptor.forClass(ConnectedGlobalConfiguration.class);
    ConnectedSonarLintEngine mockEngine = mock(ConnectedSonarLintEngine.class);
    doReturn(mockEngine).when(underTest).newConnectedEngine(argCaptor.capture());

    ConnectedSonarLintEngine createdEngine = underTest.createConnectedEngine("foo");

    assertThat(createdEngine).isSameAs(mockEngine);
    ConnectedGlobalConfiguration capturedConfig = argCaptor.getValue();
    assertThat(capturedConfig.extraProperties()).containsEntry("sonar.typescript.internal.typescriptLocation", FAKE_TYPESCRIPT_PATH.toString());
    assertThat(capturedConfig.getEnabledLanguages()).containsOnly(Language.APEX, Language.HTML, Language.JAVA, Language.JS, Language.PHP, Language.PLSQL, Language.PYTHON, Language.SECRETS, Language.TS);
  }

  @Test
  public void no_typescript_to_connected_engine() throws Exception {
    underTest.initialize(null);

    ArgumentCaptor<ConnectedGlobalConfiguration> argCaptor = ArgumentCaptor.forClass(ConnectedGlobalConfiguration.class);
    ConnectedSonarLintEngine mockEngine = mock(ConnectedSonarLintEngine.class);
    doReturn(mockEngine).when(underTest).newConnectedEngine(argCaptor.capture());

    ConnectedSonarLintEngine createdEngine = underTest.createConnectedEngine("foo");

    assertThat(createdEngine).isSameAs(mockEngine);
    ConnectedGlobalConfiguration capturedConfig = argCaptor.getValue();
    assertThat(capturedConfig.extraProperties()).isEmpty();
  }

  @Test
  public void get_standalone_languages() {
    assertThat(EnginesFactory.getStandaloneLanguages()).containsExactlyInAnyOrder(
      Language.HTML,
      Language.JAVA,
      Language.JS,
      Language.PHP,
      Language.PYTHON,
      Language.SECRETS,
      Language.TS
    );
  }

  @Test
  public void resolve_extra_plugin_key() {
    assertThat(EnginesFactory.guessPluginKey("file:///sonarsecrets.jar")).isEqualTo(Language.SECRETS.getPluginKey());
    assertThatThrownBy(() -> EnginesFactory.guessPluginKey("file:///unknown.jar"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Unknown analyzer.");
  }
}
