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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderLifecycleListener;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;

/**
 * Keeps the configuration scopes declared to the backend: one scope per workspace folder. It opens a scope when its
 * folder is added, closes it when the folder is removed, rebinds it when the binding of the folder changes, and
 * remembers what it declared. Changes run one after the other on a single thread; the lookups never wait for them.
 */
public class ConfigurationScopeManager implements WorkspaceFolderLifecycleListener {

  private final WorkspaceFoldersManager foldersManager;
  private final BackendServiceFacade backendServiceFacade;
  private final LanguageClientLogger logOutput;
  private final ExecutorService executor;
  private final Map<URI, FolderScope> folders = new ConcurrentHashMap<>();
  private final Map<URI, Optional<ProjectBinding>> folderBindings = new ConcurrentHashMap<>();
  private Function<WorkspaceFolderWrapper, Optional<ProjectBinding>> bindingResolver;

  public ConfigurationScopeManager(WorkspaceFoldersManager foldersManager, BackendServiceFacade backendServiceFacade, LanguageClientLogger logOutput) {
    this(Executors.newSingleThreadExecutor(Utils.threadFactory("SonarLint configuration scopes", false)), foldersManager, backendServiceFacade, logOutput);
  }

  public ConfigurationScopeManager(ExecutorService executor, WorkspaceFoldersManager foldersManager, BackendServiceFacade backendServiceFacade,
    LanguageClientLogger logOutput) {
    this.executor = executor;
    this.foldersManager = foldersManager;
    this.backendServiceFacade = backendServiceFacade;
    this.logOutput = logOutput;
  }

  // Can't use constructor injection because of cyclic dependency
  public void setBindingResolver(Function<WorkspaceFolderWrapper, Optional<ProjectBinding>> bindingResolver) {
    this.bindingResolver = bindingResolver;
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
      folders.put(folder.getUri(), new FolderScope(folder));
      update(folder);
    });
  }

  public void folderRemoved(WorkspaceFolderWrapper removed) {
    var scope = folders.remove(removed.getUri());
    if (scope != null) {
      executor.execute(() -> backendServiceFacade.getBackendService().removeWorkspaceFolder(scopeIdOf(removed)));
    }
  }

  /**
   * Computes the binding of the folder again and declares the difference to the backend.
   *
   * @return whether the scope of the folder was opened or its binding changed
   */
  public CompletableFuture<Boolean> update(WorkspaceFolderWrapper folder) {
    var scope = folders.get(folder.getUri());
    if (scope == null) {
      return CompletableFuture.completedFuture(false);
    }
    return CompletableFuture.supplyAsync(() -> apply(scope), executor)
      .exceptionally(t -> {
        logOutput.errorWithStackTrace(format("Failed to update the configuration scope of folder %s", folder.getUri()), t);
        return false;
      });
  }

  /**
   * The configuration scope a file belongs to: its workspace folder, or {@link BackendService#ROOT_CONFIGURATION_SCOPE}
   * for a file outside every workspace folder.
   */
  public String resolveConfigScopeId(URI fileUri) {
    return findConfigScopeId(fileUri).orElse(BackendService.ROOT_CONFIGURATION_SCOPE);
  }

  /**
   * The configuration scope of a file inside a workspace folder, empty for a file outside every folder.
   */
  public Optional<String> findConfigScopeId(URI fileUri) {
    return foldersManager.findFolderForFile(fileUri).map(this::scopeIdOf);
  }

  public String scopeIdOf(WorkspaceFolderWrapper folder) {
    return folder.getUri().toString();
  }

  public Optional<ProjectBinding> bindingOf(WorkspaceFolderWrapper folder) {
    return folderBindings.computeIfAbsent(folder.getUri(), uri -> bindingResolver.apply(folder));
  }

  public Optional<ProjectBinding> bindingIfExists(WorkspaceFolderWrapper folder) {
    return folderBindings.getOrDefault(folder.getUri(), Optional.empty());
  }

  public void forgetBindingsOf(String connectionId) {
    folderBindings.values().removeIf(binding -> binding.isPresent() && binding.get().connectionId().equals(connectionId));
  }

  public Stream<ProjectBinding> bindings() {
    return folderBindings.values().stream().flatMap(Optional::stream);
  }

  public void shutdown() {
    Utils.shutdownAndAwait(executor, true);
  }

  private boolean apply(FolderScope scope) {
    var folder = scope.folder;
    if (folders.get(folder.getUri()) != scope) {
      return false;
    }
    var backendService = backendServiceFacade.getBackendService();
    folderBindings.remove(folder.getUri());
    var binding = bindingOf(folder).orElse(null);
    if (!scope.opened) {
      backendService.addWorkspaceFolders(List.of(new WorkspaceFolder(scopeIdOf(folder), folder.getLspFolder().getName())), declared -> Optional.ofNullable(binding));
      scope.opened = true;
      scope.binding = binding;
      return true;
    }
    if (Objects.equals(scope.binding, binding)) {
      return false;
    }
    if (binding == null) {
      backendService.unbind(scopeIdOf(folder));
    } else {
      backendService.bind(scopeIdOf(folder), binding);
    }
    scope.binding = binding;
    return true;
  }

  private static final class FolderScope {
    private final WorkspaceFolderWrapper folder;
    private boolean opened;
    @Nullable
    private ProjectBinding binding;

    private FolderScope(WorkspaceFolderWrapper folder) {
      this.folder = folder;
    }
  }
}
