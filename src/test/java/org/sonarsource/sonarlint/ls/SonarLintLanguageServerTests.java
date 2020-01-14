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

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.SonarLintLanguageServer.getHtmlDescription;

class SonarLintLanguageServerTests {

  @Test
  public void getHtmlDescription_appends_extended_description_when_non_empty() {
    String htmlDescription = "foo";
    String extendedDescription = "bar";

    RuleDetails ruleDetails = mock(RuleDetails.class);
    when(ruleDetails.getHtmlDescription()).thenReturn(htmlDescription);
    when(ruleDetails.getExtendedDescription()).thenReturn("");

    assertThat(getHtmlDescription(ruleDetails)).isEqualTo(htmlDescription);

    when(ruleDetails.getExtendedDescription()).thenReturn(extendedDescription);
    assertThat(getHtmlDescription(ruleDetails)).isEqualTo(htmlDescription + "<div>" + extendedDescription + "</div>");
  }

}
