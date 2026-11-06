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
package org.sonarsource.sonarlint.ls.folders;

import com.google.gson.annotations.JsonAdapter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;

/**
 * One element of the {@code projects} array of a {@code .sonar-config.json}, exactly as written in the file.
 */
record SonarConfigEntry(@Nullable String projectKey, @Nullable String path, @Nullable String serverUrl, @Nullable String organization, @Nullable String region,
  @Nullable @JsonAdapter(StringValuesOnlyAdapter.class) Map<String, String> settings) {

  static final String COMPILE_COMMANDS_PROPERTY = "sonar.cfamily.compile-commands";

  SonarConfigEntry(@Nullable String projectKey, @Nullable String path, @Nullable String serverUrl, @Nullable String organization, @Nullable String region) {
    this(projectKey, path, serverUrl, organization, region, null);
  }

  @CheckForNull
  String compileCommands() {
    return settings == null ? null : settings.get(COMPILE_COMMANDS_PROPERTY);
  }

  Optional<SonarCloudRegion> cloudRegion() {
    if (region == null) {
      return Optional.of(SonarCloudRegion.EU);
    }
    try {
      return Optional.of(SonarCloudRegion.valueOf(region.toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  boolean isValid() {
    return projectKey != null && path != null && (serverUrl != null || (organization != null && cloudRegion().isPresent()));
  }
}
