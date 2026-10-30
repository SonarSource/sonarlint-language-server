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
package org.sonarsource.sonarlint.ls.scopes;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import testutils.ImmediateExecutorService;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurationScopeManagerTests {

  private static final URI REPO = URI.create("file:///repo/");
  private static final ProjectBinding BINDING = new ProjectBinding("connection", "root");

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private final BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  private final BackendService backendService = mock(BackendService.class);
  private final WorkspaceFoldersManager foldersManager = mock(WorkspaceFoldersManager.class);
  private final WorkspaceFolderWrapper folder = new WorkspaceFolderWrapper(REPO, new WorkspaceFolder(REPO.toString(), "repo"), logTester.getLogger());
  private ConfigurationScopeManager underTest;
  private Optional<ProjectBinding> binding = Optional.empty();

  @BeforeEach
  void prepare() {
    when(backendServiceFacade.getBackendService()).thenReturn(backendService);
    underTest = new ConfigurationScopeManager(new ImmediateExecutorService(), foldersManager, backendServiceFacade, logTester.getLogger());
    underTest.setBindingResolver(addedFolder -> binding);
  }

  @Test
  void adding_a_folder_opens_its_scope_with_the_binding_of_the_folder() {
    binding = Optional.of(BINDING);

    underTest.foldersAdded(List.of(folder));

    verify(backendService).addConfigurationScope(REPO.toString(), BackendService.ROOT_CONFIGURATION_SCOPE, "repo", BINDING);
  }

  @Test
  void a_folder_without_a_binding_is_opened_unbound() {
    underTest.foldersAdded(List.of(folder));

    verify(backendService).addConfigurationScope(REPO.toString(), BackendService.ROOT_CONFIGURATION_SCOPE, "repo", null);
  }

  @Test
  void updating_binds_the_scope_when_the_folder_got_a_binding() {
    underTest.foldersAdded(List.of(folder));
    clearInvocations(backendService);
    binding = Optional.of(BINDING);

    var changed = underTest.update(folder).join();

    assertThat(changed).isTrue();
    verify(backendService).bind(REPO.toString(), BINDING);
  }

  @Test
  void updating_rebinds_the_scope_when_the_binding_changed() {
    binding = Optional.of(BINDING);
    underTest.foldersAdded(List.of(folder));
    clearInvocations(backendService);
    var other = new ProjectBinding("connection", "other");
    binding = Optional.of(other);

    assertThat(underTest.update(folder).join()).isTrue();

    verify(backendService).bind(REPO.toString(), other);
  }

  @Test
  void updating_unbinds_the_scope_when_the_folder_lost_its_binding() {
    binding = Optional.of(BINDING);
    underTest.foldersAdded(List.of(folder));
    clearInvocations(backendService);
    binding = Optional.empty();

    assertThat(underTest.update(folder).join()).isTrue();

    verify(backendService).unbind(REPO.toString());
  }

  @Test
  void updating_without_a_difference_touches_nothing() {
    binding = Optional.of(BINDING);
    underTest.foldersAdded(List.of(folder));
    clearInvocations(backendService);

    assertThat(underTest.update(folder).join()).isFalse();

    verify(backendService, never()).addConfigurationScope(any(), any(), any(), any());
    verify(backendService, never()).bind(any(), any());
    verify(backendService, never()).unbind(any());
  }

  @Test
  void removing_a_folder_closes_its_scope() {
    underTest.foldersAdded(List.of(folder));

    underTest.folderRemoved(folder);

    verify(backendService).removeConfigurationScope(REPO.toString());
  }

  @Test
  void removing_a_folder_that_was_not_added_does_nothing() {
    underTest.folderRemoved(folder);

    verify(backendService, never()).removeConfigurationScope(any());
  }

  @Test
  void the_binding_of_a_folder_is_computed_once_and_kept() {
    var resolutions = new AtomicInteger();
    underTest.setBindingResolver(resolved -> {
      resolutions.incrementAndGet();
      return Optional.of(BINDING);
    });

    assertThat(underTest.bindingIfExists(folder)).isEmpty();
    assertThat(underTest.bindingOf(folder)).contains(BINDING);
    assertThat(underTest.bindingOf(folder)).contains(BINDING);

    assertThat(resolutions).hasValue(1);
    assertThat(underTest.bindingIfExists(folder)).contains(BINDING);
  }


  @Test
  void the_bindings_of_a_connection_can_be_forgotten() {
    binding = Optional.of(BINDING);
    underTest.bindingOf(folder);

    underTest.forgetBindingsOf("other-connection");
    assertThat(underTest.bindingIfExists(folder)).contains(BINDING);

    underTest.forgetBindingsOf("connection");
    assertThat(underTest.bindingIfExists(folder)).isEmpty();
  }

  @Test
  void a_removed_folder_is_not_updated_again() {
    underTest.foldersAdded(List.of(folder));
    underTest.folderRemoved(folder);
    clearInvocations(backendService);
    binding = Optional.of(BINDING);

    assertThat(underTest.update(folder).join()).isFalse();

    verify(backendService, never()).addConfigurationScope(any(), any(), any(), any());
    verify(backendService, never()).bind(any(), any());
  }

  @Test
  void the_scope_of_a_file_is_the_folder_containing_it() {
    var inFolder = URI.create("file:///repo/some/Foo.java");
    when(foldersManager.findFolderForFile(inFolder)).thenReturn(Optional.of(folder));

    assertThat(underTest.resolveConfigScopeId(inFolder)).isEqualTo(REPO.toString());
    assertThat(underTest.findConfigScopeId(inFolder)).contains(REPO.toString());
  }

  @Test
  void a_file_outside_every_folder_is_in_the_root_scope() {
    var outside = URI.create("file:///other/Foo.java");
    when(foldersManager.findFolderForFile(outside)).thenReturn(Optional.empty());

    assertThat(underTest.resolveConfigScopeId(outside)).isEqualTo(BackendService.ROOT_CONFIGURATION_SCOPE);
    assertThat(underTest.findConfigScopeId(outside)).isEmpty();
  }

  @Test
  void the_folder_events_open_and_close_the_scope() {
    underTest.initialized(List.of(folder));
    verify(backendService).addConfigurationScope(eq(REPO.toString()), eq(BackendService.ROOT_CONFIGURATION_SCOPE), eq("repo"), any());

    underTest.removed(folder);
    verify(backendService).removeConfigurationScope(REPO.toString());
  }

  @Test
  void an_added_folder_opens_its_scope() {
    underTest.added(folder);

    verify(backendService).addConfigurationScope(eq(REPO.toString()), eq(BackendService.ROOT_CONFIGURATION_SCOPE), eq("repo"), any());
  }

  @Test
  void the_scope_id_of_a_folder_is_its_uri() {
    assertThat(underTest.scopeIdOf(folder)).isEqualTo(REPO.toString());
  }

  @Test
  void a_failure_of_the_backend_is_logged_and_the_scope_is_opened_on_the_next_change() {
    doThrow(new IllegalStateException("backend down")).doNothing().when(backendService).addConfigurationScope(any(), any(), any(), any());
    underTest.foldersAdded(List.of(folder));
    assertThat(logTester.logs()).anyMatch(log -> log.contains("Failed to update the configuration scope of folder " + REPO));

    assertThat(underTest.update(folder).join()).isTrue();

    verify(backendService, times(2)).addConfigurationScope(any(), any(), any(), any());
  }

  @Test
  void a_failure_of_the_backend_while_removing_a_folder_is_logged() {
    underTest.foldersAdded(List.of(folder));
    doThrow(new IllegalStateException("backend down")).when(backendService).removeConfigurationScope(any());

    underTest.folderRemoved(folder);

    assertThat(logTester.logs()).anyMatch(log -> log.contains("Failed to remove the configuration scope of folder " + REPO));
  }
}
