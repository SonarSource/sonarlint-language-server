/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * Cache of server connection globalSettings. This is to avoid querying the client all the time.
 */
public class ServerConnectionSettingsCache {

  private static final Logger LOG = Loggers.get(ServerConnectionSettingsCache.class);

  private final Map<String, ServerConnectionSettings> cache = new HashMap<>();

  /**
   * Parse the raw value received from client configuration and replace the content of the cache.
   */
  public void replace(@Nullable Object servers) {
    cache.clear();
    if (servers == null) {
      return;
    }

    List<Map<String, String>> maps = (List<Map<String, String>>) servers;

    maps.forEach(m -> {
      String serverId = m.get("serverId");
      String url = m.get("serverUrl");
      String token = m.get("token");
      String organization = m.get("organizationKey");

      if (!isBlank(serverId) && !isBlank(url) && !isBlank(token)) {
        cache.put(serverId, new ServerConnectionSettings(serverId, url, token, organization));
      } else {
        LOG.error("Incomplete server configuration. Required parameters must not be blank: serverId, serverUrl, token.");
      }
    });
  }

  public void forEach(BiConsumer<String, ServerConnectionSettings> action) {
    cache.forEach(action);
  }

  @CheckForNull
  public ServerConnectionSettings get(String serverId) {
    return cache.get(serverId);
  }
}
