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
package org.sonarsource.sonarlint.ls.settings;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;

class RulesConfiguration {

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
    return rules.stream().filter(r -> "off".equals(r.level)).map(r -> r.key).collect(Collectors.toSet());
  }

  Collection<RuleKey> includedRules() {
    return rules.stream().filter(r -> "on".equals(r.level)).map(r -> r.key).collect(Collectors.toSet());
  }

  Map<RuleKey, Map<String, String>> ruleParameters() {
    return rules.stream().filter(r -> r.parameters != null && !r.parameters.isEmpty())
      .collect(Collectors.toMap(r -> r.key, r -> r.parameters));
  }

  private static class ConfiguredRule {
    final RuleKey key;
    final String level;
    final Map<String, String> parameters;

    private ConfiguredRule(Map.Entry<String, Object> ruleJson) {
      this.key = safeParseRuleKey(ruleJson);
      if (ruleJson.getValue() instanceof Map) {
        Map<String, Object> config = (Map<String, Object>) ruleJson.getValue();
        this.level = (String) config.get("level");
        this.parameters = (Map<String, String>) config.get("parameters");
      } else {
        level = null;
        parameters = Collections.emptyMap();
      }
    }
  }

  @CheckForNull
  private static RuleKey safeParseRuleKey(Map.Entry<String, Object> e) {
    try {
      return RuleKey.parse(e.getKey());
    } catch (Exception any) {
      return null;
    }
  }
}
