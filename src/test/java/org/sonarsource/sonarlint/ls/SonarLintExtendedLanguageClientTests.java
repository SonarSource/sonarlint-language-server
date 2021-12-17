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
import org.sonarsource.sonarlint.core.container.standalone.rule.DefaultStandaloneRuleParam;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamDefinition;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamType;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowRuleDescriptionParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SonarLintExtendedLanguageClientTests {

  @Test
  void test_equals_hashCode() {
    var apiParam = Mockito.mock(SonarLintRuleParamDefinition.class);
    when(apiParam.name()).thenReturn("name");
    when(apiParam.description()).thenReturn("description");
    when(apiParam.type()).thenReturn(SonarLintRuleParamType.INTEGER);
    when(apiParam.defaultValue()).thenReturn("42");
    var param1 = new DefaultStandaloneRuleParam(apiParam);

    var ruleDesc1 = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type1", "severity1", Collections.singleton(param1));
    var ruleDescSame1 = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type1", "severity1", Collections.singleton(param1));

    assertThat(ruleDesc1).hasSameHashCodeAs(ruleDescSame1)
      .isEqualTo(ruleDesc1)
      .isEqualTo(ruleDescSame1)
      .isNotEqualTo("foo")
      .isNotEqualTo(null);

    var ruleDescDiffKey = new ShowRuleDescriptionParams("key2", "name1", "desc1", "type1", "severity1", Collections.singleton(param1));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffKey.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffKey);

    var ruleDescDiffName = new ShowRuleDescriptionParams("key1", "name2", "desc1", "type1", "severity1", Collections.singleton(param1));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffName.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffName);

    var ruleDescDiffDesc = new ShowRuleDescriptionParams("key1", "name1", "desc2", "type1", "severity1", Collections.singleton(param1));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffDesc.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffDesc);

    var ruleDescDiffType = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type2", "severity1", Collections.singleton(param1));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffType.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffType);

    var ruleDescDiffSeverity = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type1", "severity2", Collections.singleton(param1));
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffSeverity.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffSeverity);

    var ruleDescDiffParams = new ShowRuleDescriptionParams("key1", "name1", "desc1", "type1", "severity1", Collections.emptyList());
    assertThat(ruleDesc1.hashCode()).isNotEqualTo(ruleDescDiffParams.hashCode());
    assertThat(ruleDesc1).isNotEqualTo(ruleDescDiffParams);

    var exposedParam = ruleDesc1.getParameters()[0];
    assertThat(exposedParam).hasSameHashCodeAs(ruleDescSame1.getParameters()[0])
      .isEqualTo(exposedParam)
      .isNotEqualTo("param")
      .isNotEqualTo(null);

    var paramDiffName = new SonarLintExtendedLanguageClient.RuleParameter("other", exposedParam.description, exposedParam.defaultValue);
    assertThat(exposedParam.hashCode()).isNotEqualTo(paramDiffName.hashCode());
    assertThat(exposedParam).isNotEqualTo(paramDiffName);

    var paramDiffDesc = new SonarLintExtendedLanguageClient.RuleParameter(exposedParam.name, "other", exposedParam.defaultValue);
    assertThat(exposedParam.hashCode()).isNotEqualTo(paramDiffDesc.hashCode());
    assertThat(exposedParam).isNotEqualTo(paramDiffDesc);

    var paramDiffDefaultValue = new SonarLintExtendedLanguageClient.RuleParameter(exposedParam.name, exposedParam.description, "other");
    assertThat(exposedParam.hashCode()).isNotEqualTo(paramDiffDefaultValue.hashCode());
    assertThat(exposedParam).isNotEqualTo(paramDiffDefaultValue);
  }

}
