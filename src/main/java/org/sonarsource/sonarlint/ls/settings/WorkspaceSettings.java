/*
 * SonarLint Language Server
 * Copyright (C) SonarSource Sàrl
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
import java.util.Objects;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.sonar.api.rule.RuleKey;

/**
 * Settings global to the entire workspace (user + machine + workspace scopes)
 */
@Immutable
public class WorkspaceSettings {

  private final boolean disableTelemetry;
  private final Map<String, ServerConnectionSettings> connections;
  private final Collection<RuleKey> excludedRules;
  private final Collection<RuleKey> includedRules;
  private final Map<RuleKey, Map<String, String>> ruleParameters;
  private final boolean showVerboseLogs;
  private final String pathToNodeExecutable;
  private final boolean focusOnNewCode;
  private final boolean automaticAnalysis;
  private final boolean ideLabsEnabled;

  private final String analysisExcludes;

  private WorkspaceSettings(Builder builder) {
    this.disableTelemetry = builder.disableTelemetry;
    this.connections = builder.connections;
    this.excludedRules = builder.excludedRules;
    this.includedRules = builder.includedRules;
    this.ruleParameters = builder.ruleParameters;
    this.showVerboseLogs = builder.showVerboseLogs;
    this.pathToNodeExecutable = builder.pathToNodeExecutable;
    this.focusOnNewCode = builder.focusOnNewCode;
    this.automaticAnalysis = builder.automaticAnalysis;
    this.analysisExcludes = builder.analysisExcludes;
    this.ideLabsEnabled = builder.ideLabsEnabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isDisableTelemetry() {
    return disableTelemetry;
  }

  public Map<String, ServerConnectionSettings> getServerConnections() {
    return Collections.unmodifiableMap(connections);
  }

  public Collection<RuleKey> getExcludedRules() {
    return excludedRules;
  }

  public Collection<RuleKey> getIncludedRules() {
    return includedRules;
  }

  public Map<RuleKey, Map<String, String>> getRuleParameters() {
    return ruleParameters;
  }

  public boolean hasLocalRuleConfiguration() {
    return !excludedRules.isEmpty() || !includedRules.isEmpty();
  }

  public boolean showVerboseLogs() {
    return showVerboseLogs;
  }

  public String pathToNodeExecutable() {
    return pathToNodeExecutable;
  }

  public boolean isFocusOnNewCode() {
    return focusOnNewCode;
  }

  public boolean isAutomaticAnalysis() {
    return automaticAnalysis;
  }

  public boolean isIdeLabsEnabled() {
    return ideLabsEnabled;
  }

  public String getAnalysisExcludes() {
    return analysisExcludes;
  }

  @Override
  public int hashCode() {
    return Objects.hash(disableTelemetry, focusOnNewCode, connections, excludedRules, includedRules, showVerboseLogs, pathToNodeExecutable);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    var other = (WorkspaceSettings) obj;
    return disableTelemetry == other.disableTelemetry && focusOnNewCode == other.focusOnNewCode && ideLabsEnabled == other.ideLabsEnabled
      && Objects.equals(connections, other.connections)
      && Objects.equals(excludedRules, other.excludedRules)
      && Objects.equals(includedRules, other.includedRules) && Objects.equals(ruleParameters, other.ruleParameters)
      && Objects.equals(showVerboseLogs, other.showVerboseLogs)
      && Objects.equals(pathToNodeExecutable, other.pathToNodeExecutable);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  public static class Builder {
    private boolean disableTelemetry;
    private Map<String, ServerConnectionSettings> connections;
    private Collection<RuleKey> excludedRules;
    private Collection<RuleKey> includedRules;
    private Map<RuleKey, Map<String, String>> ruleParameters;
    private boolean showVerboseLogs;
    private String pathToNodeExecutable;
    private boolean focusOnNewCode;
    private boolean automaticAnalysis;
    private boolean ideLabsEnabled;
    private String analysisExcludes;

    public Builder disableTelemetry(boolean disableTelemetry) {
      this.disableTelemetry = disableTelemetry;
      return this;
    }

    public Builder connections(Map<String, ServerConnectionSettings> connections) {
      this.connections = connections;
      return this;
    }

    public Builder excludedRules(Collection<RuleKey> excludedRules) {
      this.excludedRules = excludedRules;
      return this;
    }

    public Builder includedRules(Collection<RuleKey> includedRules) {
      this.includedRules = includedRules;
      return this;
    }

    public Builder ruleParameters(Map<RuleKey, Map<String, String>> ruleParameters) {
      this.ruleParameters = ruleParameters;
      return this;
    }

    public Builder showVerboseLogs(boolean showVerboseLogs) {
      this.showVerboseLogs = showVerboseLogs;
      return this;
    }

    public Builder pathToNodeExecutable(String pathToNodeExecutable) {
      this.pathToNodeExecutable = pathToNodeExecutable;
      return this;
    }

    public Builder focusOnNewCode(boolean focusOnNewCode) {
      this.focusOnNewCode = focusOnNewCode;
      return this;
    }

    public Builder automaticAnalysis(boolean automaticAnalysis) {
      this.automaticAnalysis = automaticAnalysis;
      return this;
    }

    public Builder ideLabsEnabled(boolean ideLabsEnabled) {
      this.ideLabsEnabled = ideLabsEnabled;
      return this;
    }

    public Builder analysisExcludes(String analysisExcludes) {
      this.analysisExcludes = analysisExcludes;
      return this;
    }

    public WorkspaceSettings build() {
      return new WorkspaceSettings(this);
    }
  }

}
