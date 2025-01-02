/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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
    var rule1 = parseRule("""
      {
        "xoo:rule1": {
          "level": "on",
          "parameters": {
            "param1": "value1"
          }
        }
      }""");
    var sameAsRule1 = parseRule("""
      {
        "xoo:rule1": {
          "level": "on",
          "parameters": {
            "param1": "value1"
          }
        }
      }""");
    var keyDiffers = parseRule("""
      {
        "xoo:rule2": {
          "level": "on",
          "parameters": {
            "param1": "value1"
          }
        }
      }""");
    var levelDiffers = parseRule("""
      {
        "xoo:rule1": {
          "level": "off",
          "parameters": {
            "param1": "value1"
          }
        }
      }""");
    var noParams = parseRule("""
      {
        "xoo:rule1": {
          "level": "on"
        }
      }""");
    var paramsDiffer = parseRule("""
      {
        "xoo:rule1": {
          "level": "on",
          "parameters": {
            "param1": "value2"
          }
        }
      }""");

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

  @Test
  void configuredRule_should_be_on_when_on() {
    var rule1 = parseRule("""
      {
        "xoo:rule1": {
          "level": "on",
          "parameters": {
            "param1": "value1"
          }
        }
      }""");

    assertThat(rule1.level).isEqualTo("on");
  }

  @Test
  void configuredRule_should_be_off_when_off() {
    var rule1 = parseRule("""
      {
        "xoo:rule1": {
          "level": "off",
          "parameters": {
            "param1": "value1"
          }
        }
      }""");

    assertThat(rule1.level).isEqualTo("off");
  }

  @Test
  void configuredRule_should_be_on_when_not_specified() {
    var rule1 = parseRule("""
      {
        "xoo:rule1": {
          "parameters": {
            "param1": "value1"
          }
        }
      }""");

    assertThat(rule1.level).isEqualTo("on");
  }

  private static RulesConfiguration.ConfiguredRule parseRule(String ruleJson) {
    return new RulesConfiguration.ConfiguredRule(new Gson().<Map<String, Object>>fromJson(ruleJson, Map.class).entrySet().iterator().next());
  }
}
