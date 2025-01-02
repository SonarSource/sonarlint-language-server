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
package org.sonarsource.sonarlint.ls.clientapi;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Locale;

/**
 * A proxy authenticator that relies on system properties. Loosely based on Apache HTTP Component's implementation.
 */
class SystemPropertiesAuthenticator extends Authenticator {
  @Override
  protected PasswordAuthentication getPasswordAuthentication() {
    if (getRequestorType() == RequestorType.PROXY) {
      final var protocol = getRequestingProtocol().toLowerCase(Locale.ROOT);
      final var proxyHost = System.getProperty(protocol + ".proxyHost");
      if (proxyHost == null) {
        return null;
      }
      final var proxyPort = System.getProperty(protocol + ".proxyPort");
      if (proxyPort == null) {
        return null;
      }

      if (proxyHost.equalsIgnoreCase(getRequestingHost()) && Integer.parseInt(proxyPort) == getRequestingPort()) {
        final var proxyUser = System.getProperty(protocol + ".proxyUser");
        if (proxyUser == null) {
          return null;
        }
        final var proxyPassword = System.getProperty(protocol + ".proxyPassword");
        // Seems to be OK.
        return new PasswordAuthentication(proxyUser, proxyPassword == null ? new char[0] : proxyPassword.toCharArray());
      }
    }
    return null;
  }
}
