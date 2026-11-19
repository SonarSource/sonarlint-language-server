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
package org.sonarsource.sonarlint.ls.telemetry;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.BindingSourcesObservedParams;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderLifecycleListener;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScopeManager;

/**
 * Reports the binding sources of the workspace folders once per session: once the folders present at initialization
 * are declared, it sends what the binding sources observed for them and stops listening. Folders added later and
 * binding changes are picked up by the next session. The report runs on its own executor, never on the thread that
 * declares the scopes.
 */
public class BindingSourceTelemetry implements WorkspaceFolderLifecycleListener {

  private final WorkspaceFoldersManager foldersManager;
  private final ConfigurationScopeManager configurationScopeManager;
  private final BindingSourceObservations observations;
  private final SonarLintTelemetry telemetry;
  private final Executor reportExecutor;

  public BindingSourceTelemetry(WorkspaceFoldersManager foldersManager, ConfigurationScopeManager configurationScopeManager, BindingSourceObservations observations,
    SonarLintTelemetry telemetry) {
    this(foldersManager, configurationScopeManager, observations, telemetry, ForkJoinPool.commonPool());
  }

  BindingSourceTelemetry(WorkspaceFoldersManager foldersManager, ConfigurationScopeManager configurationScopeManager, BindingSourceObservations observations,
    SonarLintTelemetry telemetry, Executor reportExecutor) {
    this.foldersManager = foldersManager;
    this.configurationScopeManager = configurationScopeManager;
    this.observations = observations;
    this.telemetry = telemetry;
    this.reportExecutor = reportExecutor;
  }

  @Override
  public void initialized(Collection<WorkspaceFolderWrapper> initial) {
    foldersManager.removeListener(this);
    var declarations = initial.stream().map(configurationScopeManager::firstDeclarationOf).toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(declarations).thenRunAsync(() -> {
      var sources = observations.drain(initial);
      if (!sources.isEmpty()) {
        telemetry.bindingSourcesObserved(new BindingSourcesObservedParams(sources));
      }
    }, reportExecutor);
  }
}
