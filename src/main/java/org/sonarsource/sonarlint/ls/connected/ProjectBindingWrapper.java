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
package org.sonarsource.sonarlint.ls.connected;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;

public class ProjectBindingWrapper {

  private final String connectionId;
  private final ProjectBinding binding;
  private final ConnectedSonarLintEngine engine;
  private final ServerIssueTrackerWrapper issueTrackerWrapper;

  public ProjectBindingWrapper(String connectionId, ProjectBinding binding, ConnectedSonarLintEngine engine, ServerIssueTrackerWrapper issueTrackerWrapper) {
    this.connectionId = connectionId;
    this.binding = binding;
    this.engine = engine;
    this.issueTrackerWrapper = issueTrackerWrapper;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public ProjectBinding getBinding() {
    return binding;
  }

  public ConnectedSonarLintEngine getEngine() {
    return engine;
  }

  public ServerIssueTrackerWrapper getServerIssueTracker() {
    return issueTrackerWrapper;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

}
