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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class EnginesFactoryTests {
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void get_standalone_languages() {
    assertThat(EnginesFactory.getStandaloneLanguages()).containsExactlyInAnyOrder(
      Language.AZURERESOURCEMANAGER,
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
      Language.JSON,
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
