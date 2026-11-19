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

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.BindingSourcesObservedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ObservedBindingSourceDto;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScopeManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingSourceTelemetryTests {

  private final WorkspaceFoldersManager foldersManager = new WorkspaceFoldersManager(mock(LanguageClientLogger.class));
  private final ConfigurationScopeManager configurationScopeManager = mock(ConfigurationScopeManager.class);
  private final SonarLintTelemetry telemetry = mock(SonarLintTelemetry.class);
  private final BindingSourceObservations observations = new BindingSourceObservations();
  private final Map<URI, CompletableFuture<Void>> declarations = new HashMap<>();
  private final WorkspaceFolderWrapper first = folder("first");
  private final WorkspaceFolderWrapper second = folder("second");
  private BindingSourceTelemetry underTest;

  @BeforeEach
  void prepare() {
    when(configurationScopeManager.firstDeclarationOf(any())).thenAnswer(invocation -> declarations.getOrDefault(
      invocation.<WorkspaceFolderWrapper>getArgument(0).getUri(), CompletableFuture.completedFuture(null)));
    underTest = new BindingSourceTelemetry(foldersManager, configurationScopeManager, observations, telemetry, Runnable::run);
    foldersManager.addListener(underTest);
  }

  @Test
  void reports_what_was_observed_for_the_initial_folders_once_they_are_all_declared() {
    var secondDeclaration = new CompletableFuture<Void>();
    declarations.put(second.getUri(), secondDeclaration);
    observations.boundBySettings(first);

    underTest.initialized(List.of(first, second));
    assertThat(reports()).isEmpty();
    observations.boundBySettings(second);
    secondDeclaration.complete(null);

    assertThat(reports()).singleElement().extracting(BindingSourcesObservedParams::getSources).asList()
      .extracting(source -> ((ObservedBindingSourceDto) source).getDeclarationRoot())
      .containsExactly(Paths.get(first.getUri()), Paths.get(second.getUri()));
  }

  @Test
  void reports_only_once_per_session_and_stops_listening() {
    foldersManager.initialize(List.of(first.getLspFolder(), second.getLspFolder()));
    observations.boundBySettings(first);
    foldersManager.initialized();

    observations.boundBySettings(second);
    foldersManager.initialized();

    assertThat(reports()).singleElement().extracting(report -> report.getSources().size()).isEqualTo(1);
  }

  @Test
  void does_not_report_folders_added_later() {
    observations.boundBySettings(first);
    underTest.initialized(List.of(first));

    observations.boundBySettings(second);
    underTest.added(second);

    assertThat(reports()).singleElement().extracting(report -> report.getSources().size()).isEqualTo(1);
  }

  @Test
  void does_not_report_when_no_initial_folder_is_bound() {
    observations.unbound(first);

    underTest.initialized(List.of(first));

    assertThat(reports()).isEmpty();
  }

  @Test
  void reports_on_its_executor_rather_than_on_the_thread_that_declares_the_scopes() {
    var pending = new ArrayList<Runnable>();
    var onExecutor = new BindingSourceTelemetry(foldersManager, configurationScopeManager, observations, telemetry, pending::add);
    observations.boundBySettings(first);

    onExecutor.initialized(List.of(first));
    assertThat(reports()).isEmpty();
    pending.forEach(Runnable::run);

    assertThat(reports()).hasSize(1);
  }

  private static WorkspaceFolderWrapper folder(String name) {
    var uri = URI.create("file:///workspace/" + name + "/");
    return new WorkspaceFolderWrapper(uri, new WorkspaceFolder(uri.toString(), name), mock(LanguageClientLogger.class));
  }

  private List<BindingSourcesObservedParams> reports() {
    var captor = ArgumentCaptor.forClass(BindingSourcesObservedParams.class);
    verify(telemetry, atLeast(0)).bindingSourcesObserved(captor.capture());
    return captor.getAllValues();
  }
}
