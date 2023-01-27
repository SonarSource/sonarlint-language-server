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
package org.sonarsource.sonarlint.ls.settings;

import com.google.gson.Gson;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RulesConfigurationTests {

  @Test
  void configuredRuleShouldHaveEqualsAndHashCode() {
    var rule1 = parseRule("{\n" +
      "  \"xoo:rule1\": {\n" +
      "    \"level\": \"on\",\n" +
      "    \"parameters\": {\n" +
      "      \"param1\": \"value1\"\n" +
      "    }\n" +
      "  }\n" +
      "}");
    var sameAsRule1 = parseRule("{\n" +
      "  \"xoo:rule1\": {\n" +
      "    \"level\": \"on\",\n" +
      "    \"parameters\": {\n" +
      "      \"param1\": \"value1\"\n" +
      "    }\n" +
      "  }\n" +
      "}");
    var keyDiffers = parseRule("{\n" +
      "  \"xoo:rule2\": {\n" +
      "    \"level\": \"on\",\n" +
      "    \"parameters\": {\n" +
      "      \"param1\": \"value1\"\n" +
      "    }\n" +
      "  }\n" +
      "}");
    var levelDiffers = parseRule("{\n" +
      "  \"xoo:rule1\": {\n" +
      "    \"level\": \"off\",\n" +
      "    \"parameters\": {\n" +
      "      \"param1\": \"value1\"\n" +
      "    }\n" +
      "  }\n" +
      "}");
    var noParams = parseRule("{\n" +
      "  \"xoo:rule1\": {\n" +
      "    \"level\": \"on\"\n" +
      "  }\n" +
      "}");
    var paramsDiffer = parseRule("{\n" +
      "  \"xoo:rule1\": {\n" +
      "    \"level\": \"on\",\n" +
      "    \"parameters\": {\n" +
      "      \"param1\": \"value2\"\n" +
      "    }\n" +
      "  }\n" +
      "}");

    assertThat(rule1)
      .hasSameHashCodeAs(sameAsRule1)
      .isEqualTo(rule1)
      .isNotEqualTo("not a rule")
      .isEqualTo(sameAsRule1)
      .isNotEqualTo(keyDiffers)
      .isNotEqualTo(levelDiffers)
      .isNotEqualTo(noParams)
      .isNotEqualTo(paramsDiffer);
  }

  private static RulesConfiguration.ConfiguredRule parseRule(String ruleJson) {
    return new RulesConfiguration.ConfiguredRule(new Gson().<Map<String, Object>>fromJson(ruleJson, Map.class).entrySet().iterator().next());
  }
}
