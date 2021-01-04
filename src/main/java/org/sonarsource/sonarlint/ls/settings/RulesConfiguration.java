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
package org.sonarsource.sonarlint.ls.settings;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;

class RulesConfiguration {

  private static final String LEVEL_ON = "on";

  private final Set<ConfiguredRule> rules;

  private RulesConfiguration(Set<ConfiguredRule> rules) {
    this.rules = rules;
  }

  static RulesConfiguration parse(Map<String, Object> rulesSettings) {
    return new RulesConfiguration(rulesSettings.entrySet().stream()
      .map(ConfiguredRule::new)
      .filter(r -> r.key != null)
      .collect(Collectors.toSet()));
  }

  Collection<RuleKey> excludedRules() {
    return rules.stream().filter(r -> !LEVEL_ON.equals(r.level)).map(r -> r.key).collect(Collectors.toSet());
  }

  Collection<RuleKey> includedRules() {
    return rules.stream().filter(r -> LEVEL_ON.equals(r.level)).map(r -> r.key).collect(Collectors.toSet());
  }

  Map<RuleKey, Map<String, String>> ruleParameters() {
    return rules.stream().filter(r -> r.parameters != null && !r.parameters.isEmpty())
      .collect(Collectors.toMap(r -> r.key, r -> r.parameters));
  }

  static class ConfiguredRule {
    final RuleKey key;
    final String level;
    final Map<String, String> parameters;

    @SuppressWarnings("unchecked")
    ConfiguredRule(Map.Entry<String, Object> ruleJson) {
      this.key = safeParseRuleKey(ruleJson.getKey());
      if (ruleJson.getValue() instanceof Map) {
        Map<String, Object> config = (Map<String, Object>) ruleJson.getValue();
        this.level = safeParseLevel(config);
        this.parameters = safeParseParameters(config);
      } else {
        level = null;
        parameters = Collections.emptyMap();
      }
    }

    @CheckForNull
    private static RuleKey safeParseRuleKey(String key) {
      try {
        return RuleKey.parse(key);
      } catch (Exception any) {
        return null;
      }
    }

    @CheckForNull
    private static String safeParseLevel(Map<String, Object> config) {
      Object levelValue = config.get("level");
      return levelValue instanceof String ? (String) levelValue : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> safeParseParameters(Map<String, Object> config) {
      Object parametersValue = config.get("parameters");
      Map<String, Object> parameters = parametersValue instanceof Map ? (Map<String, Object>) parametersValue : Collections.emptyMap();
      return parameters.entrySet().stream()
        .filter(e -> e.getValue() != null)
        .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), safeStringValue(e.getValue())))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String safeStringValue(Object paramValue) {
      if (paramValue instanceof Double) {
        double parsedValue = (double) paramValue;
        if (parsedValue == Math.floor(parsedValue)) {
          // Special case for integer-like 'number' value: return string that can be parsed as integer
          return Long.toString((long) parsedValue);
        }
      }
      return paramValue.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ConfiguredRule that = (ConfiguredRule) o;
      return Objects.equals(key, that.key) &&
        Objects.equals(level, that.level) &&
        parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, level, parameters);
    }
  }
}
