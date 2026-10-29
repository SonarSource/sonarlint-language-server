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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.BindingSources;
import org.sonarsource.sonarlint.ls.connected.Origin;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.connected.Scope;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderLifecycleListener;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.util.URIUtils;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;

/**
 * Keeps the tree of configuration scopes declared to the backend: one scope per workspace folder, and the scopes
 * below it that the binding sources declare. On every event it asks the sources what should exist, opens and closes
 * the scopes that differ, rebinds the ones whose binding changed and remembers what it declared. Changes run one
 * after the other on a single thread; the lookups read what was last declared and never wait for a change.
 */
public class ConfigurationScopeManager implements WorkspaceFolderLifecycleListener {

  private final WorkspaceFoldersManager foldersManager;
  private final BindingSources sources;
  private final BackendServiceFacade backendServiceFacade;
  private final LanguageClientLogger logOutput;
  private final ExecutorService executor;
  private final Map<URI, FolderScopes> folders = new ConcurrentHashMap<>();
  private final List<ConfigurationScopeListener> listeners = new CopyOnWriteArrayList<>();

  public ConfigurationScopeManager(WorkspaceFoldersManager foldersManager, BindingSources sources, BackendServiceFacade backendServiceFacade,
    LanguageClientLogger logOutput) {
    this(Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint configuration scopes", false)), foldersManager, sources, backendServiceFacade, logOutput);
  }

  public ConfigurationScopeManager(ExecutorService executor, WorkspaceFoldersManager foldersManager, BindingSources sources, BackendServiceFacade backendServiceFacade,
    LanguageClientLogger logOutput) {
    this.executor = executor;
    this.foldersManager = foldersManager;
    this.sources = sources;
    this.backendServiceFacade = backendServiceFacade;
    this.logOutput = logOutput;
  }

  public void addListener(ConfigurationScopeListener listener) {
    listeners.add(listener);
  }

  @Override
  public void initialized(Collection<WorkspaceFolderWrapper> folders) {
    foldersAdded(folders);
  }

  @Override
  public void added(WorkspaceFolderWrapper added) {
    foldersAdded(List.of(added));
  }

  @Override
  public void removed(WorkspaceFolderWrapper removed) {
    folderRemoved(removed);
  }

  public void foldersAdded(Collection<WorkspaceFolderWrapper> added) {
    added.forEach(folder -> {
      folders.put(folder.getUri(), new FolderScopes(folder));
      update(folder);
    });
  }

  public void folderRemoved(WorkspaceFolderWrapper removed) {
    var state = folders.remove(removed.getUri());
    if (state != null) {
      state.firstDeclaration.complete(null);
      executor.execute(() -> close(state));
    }
  }

  /**
   * @return whether the scopes or their bindings changed
   */
  public CompletableFuture<Boolean> update(WorkspaceFolderWrapper folder) {
    var state = folders.get(folder.getUri());
    if (state == null) {
      return CompletableFuture.completedFuture(false);
    }
    return CompletableFuture.supplyAsync(() -> apply(state), executor)
      .exceptionally(t -> {
        logOutput.errorWithStackTrace(format("Failed to update the configuration scopes of folder %s", folder.getUri()), t);
        state.firstDeclaration.complete(null);
        return false;
      });
  }

  public CompletableFuture<Void> updateAll() {
    return CompletableFuture.allOf(folders.values().stream().map(state -> update(state.folder)).toArray(CompletableFuture[]::new));
  }

  /**
   * The configuration scope of a file: the deepest declared scope containing it when its workspace folder has one,
   * otherwise the folder's own scope, otherwise {@link BackendService#ROOT_CONFIGURATION_SCOPE}.
   */
  public String resolveConfigScopeId(URI fileUri) {
    return findConfigScopeId(fileUri).orElse(BackendService.ROOT_CONFIGURATION_SCOPE);
  }

  /**
   * The configuration scope of a file inside a workspace folder, empty for a file outside every folder.
   */
  public Optional<String> findConfigScopeId(URI fileUri) {
    return foldersManager.findFolderForFile(fileUri).map(folder -> scopeIdOf(folder, fileUri));
  }

  public String scopeIdOf(WorkspaceFolderWrapper folder, URI fileUri) {
    return Optional.ofNullable(folders.get(folder.getUri()))
      .flatMap(state -> deepestContaining(state.scopes.get(), fileUri))
      .map(scope -> scope.uri().toString())
      .orElse(folderScopeId(folder));
  }

  public Optional<ProjectBinding> bindingOf(WorkspaceFolderWrapper folder, URI fileUri) {
    var state = folders.get(folder.getUri());
    if (state == null) {
      return Optional.empty();
    }
    awaitFirstDeclaration(state);
    return deepestContaining(state.scopes.get(), fileUri).map(ConfiguredScope::binding);
  }

  public List<ConfiguredScope> boundScopes() {
    return folders.values().stream()
      .flatMap(state -> state.scopes.get().stream())
      .filter(scope -> scope.binding() != null)
      .toList();
  }

  public Stream<ProjectBinding> bindings() {
    return boundScopes().stream().map(ConfiguredScope::binding);
  }

  public void shutdown() {
    Utils.shutdownAndAwait(executor, true);
  }

  private boolean apply(FolderScopes state) {
    var folder = state.folder;
    if (folders.get(folder.getUri()) != state) {
      state.firstDeclaration.complete(null);
      return false;
    }
    var declared = state.scopes.get();
    var desired = desiredScopes(folder);
    state.scopes.set(desired);
    state.firstDeclaration.complete(null);
    try {
      declare(declared, desired);
    } catch (RuntimeException e) {
      state.scopes.set(declared);
      throw e;
    }
    var changed = !declared.equals(desired);
    if (changed) {
      listeners.forEach(ConfigurationScopeListener::scopesChanged);
    }
    return changed;
  }

  private List<ConfiguredScope> desiredScopes(WorkspaceFolderWrapper folder) {
    var folderUri = folder.getUri();
    var folderName = folder.getLspFolder().getName();
    var declared = sources.scopesOf(folder);
    var ownScope = declared.stream().filter(scope -> scope.uri().equals(folderUri)).findFirst();
    return List.of(ownScope
      .map(scope -> new ConfiguredScope(folderUri, folderName, scope.relativePath(), scope.origin(), bindingOf(folder, scope)))
      .orElseGet(() -> new ConfiguredScope(folderUri, folderName, ".", declared.isEmpty() ? Origin.SETTINGS : declared.get(0).origin(), null)));
  }

  private ProjectBinding bindingOf(WorkspaceFolderWrapper folder, Scope scope) {
    return sources.bindingOf(folder, scope).flatMap(binding -> {
      var projectBinding = binding.toProjectBinding();
      if (projectBinding.isEmpty()) {
        logOutput.warn(format("No connection configured for %s, scope %s is unbound", binding.target(), scope.uri()));
      }
      return projectBinding;
    }).orElse(null);
  }

  private void declare(List<ConfiguredScope> declared, List<ConfiguredScope> desired) {
    var backendService = backendServiceFacade.getBackendService();
    var declaredByUri = byUri(declared);
    var desiredByUri = byUri(desired);
    declared.stream()
      .filter(scope -> !desiredByUri.containsKey(scope.uri()))
      .forEach(scope -> backendService.removeConfigurationScope(scope.uri().toString()));
    desired.stream()
      .filter(scope -> !declaredByUri.containsKey(scope.uri()))
      .forEach(scope -> backendService.addConfigurationScope(scope.uri().toString(), BackendService.ROOT_CONFIGURATION_SCOPE, scope.name(), scope.binding()));
    desired.stream()
      .filter(scope -> declaredByUri.containsKey(scope.uri()) && !Objects.equals(declaredByUri.get(scope.uri()).binding(), scope.binding()))
      .forEach(this::rebind);
  }

  private void rebind(ConfiguredScope scope) {
    var backendService = backendServiceFacade.getBackendService();
    var binding = scope.binding();
    if (binding == null) {
      backendService.unbind(scope.uri().toString());
    } else {
      logOutput.info(format("Binding scope %s to projectKey=%s of connection '%s'", scope.uri(), binding.projectKey(), binding.connectionId()));
      backendService.bind(scope.uri().toString(), binding);
    }
  }

  private void close(FolderScopes state) {
    var backendService = backendServiceFacade.getBackendService();
    state.scopes.get().forEach(scope -> backendService.removeConfigurationScope(scope.uri().toString()));
    state.scopes.set(List.of());
    listeners.forEach(ConfigurationScopeListener::scopesChanged);
  }

  private void awaitFirstDeclaration(FolderScopes state) {
    try {
      state.firstDeclaration.get(1, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      logOutput.debugWithStackTrace("Interrupted!", e);
      Thread.currentThread().interrupt();
    } catch (ExecutionException | TimeoutException e) {
      logOutput.errorWithStackTrace(format("The configuration scopes of folder %s were not declared in time", state.folder.getUri()), e);
    }
  }

  private static String folderScopeId(WorkspaceFolderWrapper folder) {
    return folder.getUri().toString();
  }

  private static Optional<ConfiguredScope> deepestContaining(List<ConfiguredScope> scopes, URI fileUri) {
    return scopes.stream()
      .filter(scope -> URIUtils.isAncestor(scope.uri(), fileUri))
      .max(Comparator.comparingInt(scope -> scope.uri().getPath().length()));
  }

  private static Map<URI, ConfiguredScope> byUri(List<ConfiguredScope> scopes) {
    return scopes.stream().collect(Collectors.toMap(ConfiguredScope::uri, Function.identity()));
  }

  private static final class FolderScopes {
    private final WorkspaceFolderWrapper folder;
    private final CompletableFuture<Void> firstDeclaration = new CompletableFuture<>();
    private final AtomicReference<List<ConfiguredScope>> scopes = new AtomicReference<>(List.of());

    private FolderScopes(WorkspaceFolderWrapper folder) {
      this.folder = folder;
    }
  }
}
