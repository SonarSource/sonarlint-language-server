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

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.container.standalone.rule.DefaultStandaloneRuleParam;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SonarLintExtendedLanguageClientTests {

  @Test
  void test_equals_hashCode() {
    RulesDefinition.Param apiParam = Mockito.mock(RulesDefinition.Param.class);
    when(apiParam.name()).thenReturn("name");
    when(apiParam.description()).thenReturn("description");
    when(apiParam.type()).thenReturn(RuleParamType.INTEGER);
    when(apiParam.defaultValue()).thenReturn("42");
    StandaloneRuleParam param1 = new DefaultStandaloneRuleParam(apiParam);

    ShowRuleDescriptionParams ruleDesc1 = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type1", "severity1", Collections.singleton(param1));
    ShowRuleDescriptionParams ruleDescSame1 = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type1", "severity1", Collections.singleton(param1));

    assertThat(ruleDesc1).hasSameHashCodeAs(ruleDescSame1)
      .isEqualTo(ruleDesc1)
      .isEqualTo(ruleDescSame1)
      .isNotEqualTo("foo")
      .isNotEqualTo(null);

    ShowRuleDescriptionParams ruleDescDiffKey = new ShowRuleDescriptionParams("key2", "name1", "desc1", "type1", "severity1", Collections.singleton(param1));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffKey.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffKey);

    ShowRuleDescriptionParams ruleDescDiffName = new ShowRuleDescriptionParams("key1", "name2", "desc1", "type1", "severity1", Collections.singleton(param1));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffName.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffName);

    ShowRuleDescriptionParams ruleDescDiffDesc = new ShowRuleDescriptionParams("key1", "name1", "desc2", "type1", "severity1", Collections.singleton(param1));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffDesc.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffDesc);

    ShowRuleDescriptionParams ruleDescDiffType = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type2", "severity1", Collections.singleton(param1));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffType.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffType);

    ShowRuleDescriptionParams ruleDescDiffSeverity = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type1", "severity2", Collections.singleton(param1));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffSeverity.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffSeverity);

    ShowRuleDescriptionParams ruleDescDiffParams = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type1", "severity1", Collections.emptyList());
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffParams.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffParams);

    SonarLintExtendedLanguageClient.RuleParameter exposedParam = ruleDesc1.getParameters()[0];
    assertThat(exposedParam).hasSameHashCodeAs(ruleDescSame1.getParameters()[0])
      .isEqualTo(exposedParam)
      .isNotEqualTo("param")
      .isNotEqualTo(null);

    SonarLintExtendedLanguageClient.RuleParameter paramDiffName = new SonarLintExtendedLanguageClient.RuleParameter("other", exposedParam.description, exposedParam.defaultValue);
    assertThat(exposedParam.hashCode()).isNotEqualTo(paramDiffName.hashCode());
    assertThat(exposedParam).isNotEqualTo(paramDiffName);

    SonarLintExtendedLanguageClient.RuleParameter paramDiffDesc = new SonarLintExtendedLanguageClient.RuleParameter(exposedParam.name, "other", exposedParam.defaultValue);
    assertThat(exposedParam.hashCode()).isNotEqualTo(paramDiffDesc.hashCode());
    assertThat(exposedParam).isNotEqualTo(paramDiffDesc);

    SonarLintExtendedLanguageClient.RuleParameter paramDiffDefaultValue = new SonarLintExtendedLanguageClient.RuleParameter(exposedParam.name, exposedParam.description, "other");
    assertThat(exposedParam.hashCode()).isNotEqualTo(paramDiffDefaultValue.hashCode());
    assertThat(exposedParam).isNotEqualTo(paramDiffDefaultValue);
  }

}
