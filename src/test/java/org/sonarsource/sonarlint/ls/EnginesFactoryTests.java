/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
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

import static org.assertj.core.api.Assertions.assertThat;
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
    underTest = new EnginesFactory(standaloneAnalysers, Collections.emptyMap(), mock(LanguageClientLogOutput.class),
      mock(NodeJsRuntime.class), mock(ClientModulesProvider.class));
    underTest = spy(underTest);
  }

  @Test
  void get_standalone_languages() {
    assertThat(EnginesFactory.getStandaloneLanguages()).containsExactlyInAnyOrder(
      Language.C,
      Language.CLOUDFORMATION,
      Language.CS,
      Language.CSS,
      Language.CPP,
      Language.DOCKER,
      Language.GO,
      Language.HTML,
      Language.IPYTHON,
      Language.JAVA,
      Language.JS,
      Language.KUBERNETES,
      Language.PHP,
      Language.PYTHON,
      Language.SECRETS,
      Language.TERRAFORM,
      Language.TS,
      Language.XML,
      Language.YAML);
  }

}
