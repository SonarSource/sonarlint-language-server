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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.BindingSources;
import org.sonarsource.sonarlint.ls.connected.Origin;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.connected.Scope;
import org.sonarsource.sonarlint.ls.connected.ScopeBinding;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import testutils.ImmediateExecutorService;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurationScopeManagerTests {

  private static final URI REPO = URI.create("file:///repo/");
  private static final ProjectBinding ROOT_BINDING = new ProjectBinding("connection", "root");
  private static final ProjectBinding OTHER_BINDING = new ProjectBinding("connection", "other");

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private final BindingSources sources = mock(BindingSources.class);
  private final WorkspaceFoldersManager foldersManager = mock(WorkspaceFoldersManager.class);
  private final BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  private final BackendService backendService = mock(BackendService.class);
  private final ConfigurationScopeListener listener = mock(ConfigurationScopeListener.class);
  private final WorkspaceFolderWrapper folder = new WorkspaceFolderWrapper(REPO, new WorkspaceFolder(REPO.toString(), "repo"), mock(LanguageClientLogger.class));
  private ConfigurationScopeManager underTest;

  @BeforeEach
  void prepare() {
    when(backendServiceFacade.getBackendService()).thenReturn(backendService);
    underTest = new ConfigurationScopeManager(new ImmediateExecutorService(), foldersManager, sources, backendServiceFacade, logTester.getLogger());
    underTest.addListener(listener);
  }

  @Test
  void adding_a_folder_opens_its_scope_with_the_binding_of_the_folder() {
    bound(ROOT_BINDING);

    underTest.foldersAdded(List.of(folder));

    verify(backendService).addConfigurationScope(REPO.toString(), BackendService.ROOT_CONFIGURATION_SCOPE, "repo", ROOT_BINDING);
    verify(listener).scopesChanged();
  }

  @Test
  void a_folder_without_binding_is_opened_unbound() {

    underTest.foldersAdded(List.of(folder));

    verify(backendService).addConfigurationScope(REPO.toString(), BackendService.ROOT_CONFIGURATION_SCOPE, "repo", null);
  }

  @Test
  void updating_rebinds_the_scopes_whose_binding_changed() {
    bound(ROOT_BINDING);
    underTest.foldersAdded(List.of(folder));
    clearInvocations(backendService, listener);
    bound(new ProjectBinding("connection", "other"));

    var changed = underTest.update(folder).join();

    assertThat(changed).isTrue();
    verify(backendService).bind(REPO.toString(), new ProjectBinding("connection", "other"));
    verify(listener).scopesChanged();
  }

  @Test
  void updating_unbinds_a_scope_that_lost_its_binding() {
    bound(ROOT_BINDING);
    underTest.foldersAdded(List.of(folder));
    clearInvocations(backendService, listener);
    unbound();

    underTest.update(folder).join();

    verify(backendService).unbind(REPO.toString());
  }

  @Test
  void updating_without_a_difference_touches_nothing() {
    underTest.foldersAdded(List.of(folder));
    clearInvocations(backendService, listener);

    var changed = underTest.update(folder).join();

    assertThat(changed).isFalse();
    verify(backendService, never()).addConfigurationScope(any(), any(), any(), any());
    verify(backendService, never()).removeConfigurationScope(any());
    verify(backendService, never()).bind(any(), any());
    verify(backendService, never()).unbind(any());
    verify(listener, never()).scopesChanged();
  }

  @Test
  void a_binding_without_a_matching_connection_leaves_the_scope_unbound_and_says_so() {
    when(sources.bindingOf(eq(folder), any(Scope.class))).thenReturn(Optional.of(ScopeBinding.of("root", null, "serverUrl=http://foo")));

    underTest.foldersAdded(List.of(folder));

    assertThat(underTest.boundScopes()).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("No connection configured for serverUrl=http://foo, scope " + REPO + " is unbound"));
  }

  @Test
  void removing_a_folder_closes_its_scope_and_tells_the_listeners() {
    underTest.foldersAdded(List.of(folder));
    clearInvocations(backendService, listener);

    underTest.folderRemoved(folder);

    verify(backendService).removeConfigurationScope(REPO.toString());
    verify(listener).scopesChanged();
    assertThat(underTest.boundScopes()).isEmpty();
  }

  @Test
  void removing_a_folder_that_was_not_added_does_nothing() {
    underTest.folderRemoved(folder);

    verify(backendService, never()).removeConfigurationScope(any());
    verify(listener, never()).scopesChanged();
  }

  @Test
  void a_lookup_waiting_on_a_replaced_folder_is_released_when_its_declaration_is_skipped() throws InterruptedException {
    var queued = new ArrayList<Runnable>();
    underTest = new ConfigurationScopeManager(queuingExecutor(queued), foldersManager, sources, backendServiceFacade, logTester.getLogger());
    underTest.foldersAdded(List.of(folder));
    var lookup = lookupInBackground();

    underTest.foldersAdded(List.of(folder));
    queued.forEach(Runnable::run);

    lookup.join(5_000);
    assertThat(lookup.isAlive()).isFalse();
  }

  @Test
  void a_lookup_waiting_on_a_removed_folder_is_released() throws InterruptedException {
    underTest = new ConfigurationScopeManager(queuingExecutor(new ArrayList<>()), foldersManager, sources, backendServiceFacade, logTester.getLogger());
    underTest.foldersAdded(List.of(folder));
    var lookup = lookupInBackground();

    underTest.folderRemoved(folder);

    lookup.join(5_000);
    assertThat(lookup.isAlive()).isFalse();
  }

  private Thread lookupInBackground() {
    var lookup = new Thread(() -> underTest.bindingOf(folder, URI.create("file:///repo/Foo.java")));
    lookup.start();
    await().atMost(5, TimeUnit.SECONDS).until(() -> lookup.getState() == Thread.State.TIMED_WAITING);
    return lookup;
  }

  private static ImmediateExecutorService queuingExecutor(List<Runnable> queued) {
    return new ImmediateExecutorService() {
      @Override
      public void execute(Runnable command) {
        queued.add(command);
      }
    };
  }

  @Test
  void a_removed_folder_is_not_updated_again() {
    underTest.foldersAdded(List.of(folder));
    underTest.folderRemoved(folder);
    clearInvocations(backendService, listener);

    assertThat(underTest.update(folder).join()).isFalse();

    verify(backendService, never()).addConfigurationScope(any(), any(), any(), any());
  }

  @Test
  void scopeIdOf_is_the_folder_when_nothing_is_declared_yet() {
    assertThat(underTest.scopeIdOf(folder, URI.create("file:///repo/moduleA/Foo.java"))).isEqualTo(REPO.toString());
  }

  @Test
  void findConfigScopeId_is_the_scope_of_the_file_inside_a_folder_and_empty_outside_every_folder() {
    var inFolder = URI.create("file:///repo/moduleA/Foo.java");
    var outside = URI.create("file:///other/Foo.java");
    underTest.foldersAdded(List.of(folder));
    when(foldersManager.findFolderForFile(inFolder)).thenReturn(Optional.of(folder));
    when(foldersManager.findFolderForFile(outside)).thenReturn(Optional.empty());

    assertThat(underTest.findConfigScopeId(inFolder)).contains(REPO.toString());
    assertThat(underTest.findConfigScopeId(outside)).isEmpty();
  }

  @Test
  void resolveConfigScopeId_is_the_root_scope_outside_every_folder() {
    var outside = URI.create("file:///other/Foo.java");
    when(foldersManager.findFolderForFile(outside)).thenReturn(Optional.empty());

    assertThat(underTest.resolveConfigScopeId(outside)).isEqualTo(BackendService.ROOT_CONFIGURATION_SCOPE);
  }

  @Test
  void the_folder_events_open_and_close_the_scopes() {

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
  void bindingOf_is_the_declared_binding_of_the_folder() {
    bound(ROOT_BINDING);
    underTest.foldersAdded(List.of(folder));

    assertThat(underTest.bindingOf(folder, URI.create("file:///repo/Foo.java"))).contains(ROOT_BINDING);
  }

  @Test
  void bindingOf_does_not_follow_what_the_sources_say_after_the_declaration() {
    bound(ROOT_BINDING);
    underTest.foldersAdded(List.of(folder));

    bound(OTHER_BINDING);

    assertThat(underTest.bindingOf(folder, URI.create("file:///repo/Foo.java"))).contains(ROOT_BINDING);
  }

  @Test
  void bindingOf_is_empty_for_a_folder_that_was_not_added() {
    assertThat(underTest.bindingOf(folder, URI.create("file:///repo/Foo.java"))).isEmpty();
  }

  @Test
  void the_bound_scopes_and_what_they_are_bound_to_can_be_listed() {
    bound(ROOT_BINDING);
    underTest.foldersAdded(List.of(folder));

    assertThat(underTest.boundScopes()).containsExactly(new ConfiguredScope(REPO, "repo", ".", Origin.SETTINGS, ROOT_BINDING));
    assertThat(underTest.bindings()).containsExactly(ROOT_BINDING);
  }

  @Test
  void what_the_backend_refused_is_not_remembered() {
    bound(ROOT_BINDING);
    doThrow(new IllegalStateException("backend down")).when(backendService).addConfigurationScope(any(), any(), any(), any());

    underTest.foldersAdded(List.of(folder));

    assertThat(underTest.boundScopes()).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("Failed to update the configuration scopes of folder " + REPO));
  }

  @Test
  void a_failure_while_asking_the_sources_does_not_stop_the_lookups() {
    when(sources.bindingOf(eq(folder), any(Scope.class))).thenThrow(new IllegalStateException("unreadable"));

    underTest.foldersAdded(List.of(folder));

    assertThat(underTest.bindingOf(folder, URI.create("file:///repo/Foo.java"))).isEmpty();
    assertThat(underTest.scopeIdOf(folder, URI.create("file:///repo/Foo.java"))).isEqualTo(REPO.toString());
  }

  @Test
  void updating_every_folder_updates_each_registered_one() {
    bound(ROOT_BINDING);
    underTest.foldersAdded(List.of(folder));
    clearInvocations(backendService, listener);
    bound(OTHER_BINDING);

    underTest.updateAll().join();

    verify(backendService).bind(REPO.toString(), OTHER_BINDING);
  }

  @Test
  void a_failure_of_the_backend_while_removing_a_folder_is_logged() {
    underTest.foldersAdded(List.of(folder));
    doThrow(new IllegalStateException("backend down")).when(backendService).removeConfigurationScope(any());

    underTest.folderRemoved(folder);

    assertThat(logTester.logs()).anyMatch(log -> log.contains("Failed to close the configuration scopes of folder " + REPO));
  }

  private void bound(ProjectBinding binding) {
    when(sources.bindingOf(eq(folder), any(Scope.class))).thenReturn(Optional.of(ScopeBinding.of(binding.projectKey(), binding.connectionId(), "target")));
  }

  private void unbound() {
    when(sources.bindingOf(eq(folder), any(Scope.class))).thenReturn(Optional.empty());
  }
}
