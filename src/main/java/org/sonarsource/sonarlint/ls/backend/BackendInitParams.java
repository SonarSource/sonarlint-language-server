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
package org.sonarsource.sonarlint.ls.backend;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.commons.Language;

public class BackendInitParams {

  private String telemetryProductKey;
  private Path storageRoot;
  private Set<Path> embeddedPluginPaths;
  private Map<String, Path> connectedModeEmbeddedPluginPathsByKey;
  private Set<Language> enabledLanguagesInStandaloneMode;
  private Set<Language> extraEnabledLanguagesInConnectedMode;
  private boolean enableSecurityHotspots;
  private List<SonarQubeConnectionConfigurationDto> sonarQubeConnections;
  private List<SonarCloudConnectionConfigurationDto> sonarCloudConnections;
  private String sonarlintUserHome;
  private Map<String, StandaloneRuleConfigDto> standaloneRuleConfigByKey;
  private String userAgent;
  private boolean isFocusOnNewCode;

  public String getTelemetryProductKey() {
    return telemetryProductKey;
  }

  public void setTelemetryProductKey(String telemetryProductKey) {
    this.telemetryProductKey = telemetryProductKey;
  }

  public Path getStorageRoot() {
    return storageRoot;
  }

  public void setStorageRoot(Path storageRoot) {
    this.storageRoot = storageRoot;
  }

  public Set<Path> getEmbeddedPluginPaths() {
    return embeddedPluginPaths;
  }

  public void setEmbeddedPluginPaths(Set<Path> embeddedPluginPaths) {
    this.embeddedPluginPaths = embeddedPluginPaths;
  }

  public Map<String, Path> getConnectedModeEmbeddedPluginPathsByKey() {
    return connectedModeEmbeddedPluginPathsByKey;
  }

  public void setConnectedModeEmbeddedPluginPathsByKey(Map<String, Path> connectedModeEmbeddedPluginPathsByKey) {
    this.connectedModeEmbeddedPluginPathsByKey = connectedModeEmbeddedPluginPathsByKey;
  }

  public Set<Language> getEnabledLanguagesInStandaloneMode() {
    return enabledLanguagesInStandaloneMode;
  }

  public void setEnabledLanguagesInStandaloneMode(Set<Language> enabledLanguagesInStandaloneMode) {
    this.enabledLanguagesInStandaloneMode = enabledLanguagesInStandaloneMode;
  }

  public Set<Language> getExtraEnabledLanguagesInConnectedMode() {
    return extraEnabledLanguagesInConnectedMode;
  }

  public void setExtraEnabledLanguagesInConnectedMode(Set<Language> extraEnabledLanguagesInConnectedMode) {
    this.extraEnabledLanguagesInConnectedMode = extraEnabledLanguagesInConnectedMode;
  }

  public boolean isEnableSecurityHotspots() {
    return enableSecurityHotspots;
  }

  public void setEnableSecurityHotspots(boolean enableSecurityHotspots) {
    this.enableSecurityHotspots = enableSecurityHotspots;
  }

  public List<SonarQubeConnectionConfigurationDto> getSonarQubeConnections() {
    return sonarQubeConnections;
  }

  public void setSonarQubeConnections(List<SonarQubeConnectionConfigurationDto> sonarQubeConnections) {
    this.sonarQubeConnections = sonarQubeConnections;
  }

  public List<SonarCloudConnectionConfigurationDto> getSonarCloudConnections() {
    return sonarCloudConnections;
  }

  public void setSonarCloudConnections(List<SonarCloudConnectionConfigurationDto> sonarCloudConnections) {
    this.sonarCloudConnections = sonarCloudConnections;
  }

  public String getSonarlintUserHome() {
    return sonarlintUserHome;
  }

  public void setSonarlintUserHome(String sonarlintUserHome) {
    this.sonarlintUserHome = sonarlintUserHome;
  }

  public void setStandaloneRuleConfigByKey(Map<String, StandaloneRuleConfigDto> standaloneRuleConfigByKey) {
    this.standaloneRuleConfigByKey = standaloneRuleConfigByKey;
  }

  public Map<String, StandaloneRuleConfigDto> getStandaloneRuleConfigByKey() {
    return this.standaloneRuleConfigByKey;
  }

  public boolean isFocusOnNewCode() {
    return isFocusOnNewCode;
  }

  public void setFocusOnNewCode(boolean focusOnNewCode) {
    isFocusOnNewCode = focusOnNewCode;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }
}
