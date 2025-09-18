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
package org.sonarsource.sonarlint.ls;

import java.util.Collections;
import java.util.Map;

public record SonarLintLanguageServerInitializationOptions(
  boolean showVerboseLogs,
  String workspaceName,
  boolean firstSecretDetected,
  String productKey,
  String productName,
  String productVersion,
  Map<String, Object> additionalAttributes,
  String clientNodePath,
  boolean focusOnNewCode,
  Boolean automaticAnalysis,
  String omnisharpDirectory,
  String csharpOssPath,
  String csharpEnterprisePath,
  String eslintBridgeServerPath,
  boolean enableNotebooks,
  // keep loose-typing for fields coming from settings, to allow graceful handling in case of user mistakes
  Map<String, Object> rules,
  Map<String, Object> connections) {

  public SonarLintLanguageServerInitializationOptions {
    if (rules == null) {
      rules = Collections.emptyMap();
    }
    if (connections == null) {
      connections = Collections.emptyMap();
    }
    if (automaticAnalysis == null) {
      automaticAnalysis = Boolean.TRUE;
    }
  }

}
