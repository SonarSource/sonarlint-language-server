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
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarLintExtendedLanguageClientTests {

  @Test
  public void test_equals_hashCode() {
    ShowRuleDescriptionParams ruleDesc1 = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type1", "severity1");
    ShowRuleDescriptionParams ruleDescSame1 = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type1", "severity1");

    assertThat(ruleDesc1.hashCode()).isEqualTo(ruleDescSame1.hashCode());
    assertThat(ruleDesc1).isEqualTo(ruleDesc1);
    assertThat(ruleDesc1).isEqualTo(ruleDescSame1);
    assertThat(ruleDesc1).isNotEqualTo("foo");
    assertThat(ruleDesc1).isNotEqualTo(null);

    ShowRuleDescriptionParams ruleDescDiffKey = new ShowRuleDescriptionParams("key2", "name1", "desc1", "type1", "severity1");
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffKey.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffKey);

    ShowRuleDescriptionParams ruleDescDiffName = new ShowRuleDescriptionParams("key1", "name2", "desc1", "type1", "severity1");
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffName.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffName);

    ShowRuleDescriptionParams ruleDescDiffDesc = new ShowRuleDescriptionParams("key1", "name1", "desc2", "type1", "severity1");
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffDesc.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffDesc);

    ShowRuleDescriptionParams ruleDescDiffType = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type2", "severity1");
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffType.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffType);

    ShowRuleDescriptionParams ruleDescDiffSeverity = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type1", "severity2");
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffSeverity.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffSeverity);

  }

}
