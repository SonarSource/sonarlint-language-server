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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.sonarsource.sonarlint.ls.backend.BackendService;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBinding;
import org.sonarsource.sonarlint.ls.connected.BindingSources;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.util.CatchingRunnable;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static java.net.URI.create;
import static org.sonarsource.sonarlint.ls.util.URIUtils.isAncestor;

public class WorkspaceFoldersManager {
  private final Map<URI, WorkspaceFolderWrapper> folders = new ConcurrentHashMap<>();
  private final Map<String, Boolean> analysisReadiness = new ConcurrentHashMap<>();
  private final List<WorkspaceFolderLifecycleListener> listeners = new CopyOnWriteArrayList<>();
  private BindingSources sources;
  private final BackendServiceFacade backendServiceFacade;
  private final LanguageClientLogger logOutput;
  private final ExecutorService executor;
  private final List<WorkspaceFolder> workspaceFolders = new ArrayList<>();

  public WorkspaceFoldersManager(BackendServiceFacade backendServiceFacade, LanguageClientLogger logOutput) {
    this(Executors.newCachedThreadPool(Utils.threadFactory("SonarLint folders manager", false)), backendServiceFacade, logOutput);
  }

  WorkspaceFoldersManager(ExecutorService executor, BackendServiceFacade backendServiceFacade, LanguageClientLogger logOutput) {
    this.executor = executor;
    this.backendServiceFacade = backendServiceFacade;
    this.logOutput = logOutput;
  }

  // Can't use constructor injection because of cyclic dependency
  public void setBindingSources(BindingSources sources) {
    this.sources = sources;
  }

  public void initialize(@Nullable List<WorkspaceFolder> workspaceFolders) {
    if (workspaceFolders != null) {
      workspaceFolders.forEach(wf -> {
        var uri = create(wf.getUri());
        addFolder(wf, uri);
      });
    }
  }

  public void initialized() {
    executor.submit(new CatchingRunnable(() -> {
      backendServiceFacade.getBackendService().addWorkspaceFolders(this.workspaceFolders, getBindingProvider());
      declareSubScopes(this.workspaceFolders);
    }, t -> logOutput.errorWithStackTrace("Failed to initialize workspace folders.", t)));
  }

  private void declareSubScopes(List<WorkspaceFolder> addedFolders) {
    addedFolders.forEach(folder -> Optional.ofNullable(folders.get(create(folder.getUri()))).ifPresent(owner -> {
      var subFolders = sources.subFolders(owner);
      if (!subFolders.isEmpty()) {
        backendServiceFacade.getBackendService().addSubFolders(owner.getUri().toString(), subFolders,
          subFolder -> sources.bindingOfScope(owner, create(subFolder.getUri())));
      }
      owner.markScopesDeclared();
      listeners.forEach(l -> l.scopesDeclared(owner));
    }));
  }

  public void didChangeWorkspaceFolders(WorkspaceFoldersChangeEvent event) {
    logOutput.debug("Processing didChangeWorkspaceFolders event");
    var removedSubFolderIds = new ArrayList<String>();
    for (var removed : event.getRemoved()) {
      var removedUri = create(removed.getUri());
      Optional.ofNullable(folders.get(removedUri)).ifPresent(wrapper -> sources.subFolders(wrapper).forEach(subFolder -> removedSubFolderIds.add(subFolder.getUri())));
      removeFolder(removedUri);
    }
    for (var added : event.getAdded()) {
      var uri = create(added.getUri());
      var addedWrapper = addFolder(added, uri);
      listeners.forEach(l -> l.added(addedWrapper));
    }
    executor.submit(new CatchingRunnable(() -> {
      backendServiceFacade.getBackendService().addWorkspaceFolders(event.getAdded(), getBindingProvider());
      declareSubScopes(event.getAdded());
      event.getRemoved().forEach(removed -> removeFolderFromBackend(removed.getUri()));
      removedSubFolderIds.forEach(this::removeFolderFromBackend);
    }, t -> logOutput.errorWithStackTrace("Failed to add workspace folder", t)));

  }

  private void removeFolder(URI uri) {
    var removed = folders.remove(uri);
    var workspaceFolder = this.workspaceFolders.stream().filter(wf -> wf.getUri().equalsIgnoreCase(uri.toString())).findFirst().orElse(null);
    if (workspaceFolder != null) {
      this.workspaceFolders.remove(workspaceFolder);
    }
    if (removed == null) {
      logOutput.warn("Unregistered workspace folder was missing: " + uri);
    }
    sources.forget(uri);
    logOutput.debug(format("Folder %s removed", removed));
  }

  private WorkspaceFolderWrapper addFolder(WorkspaceFolder added, URI uri) {
    var addedWrapper = new WorkspaceFolderWrapper(uri, added, logOutput);
    if (folders.put(uri, addedWrapper) != null) {
      logOutput.warn(format("Registered workspace folder %s was already added", addedWrapper));
    } else {
      logOutput.debug(format("Folder %s added", addedWrapper));
      this.workspaceFolders.add(added);
    }
    return addedWrapper;
  }

  private Function<WorkspaceFolder, Optional<ProjectBinding>> getBindingProvider() {
    return folder -> {
      var uri = create(folder.getUri());
      return Optional.ofNullable(folders.get(uri)).flatMap(owner -> sources.bindingOfScope(owner, uri));
    };
  }

  private void removeFolderFromBackend(String removedUri) {
    backendServiceFacade.getBackendService().removeWorkspaceFolder(removedUri);
  }

  public Optional<WorkspaceFolderWrapper> findFolderForFile(URI uri) {
    var folderUriCandidates = folders.keySet().stream()
      .filter(wfRoot -> isAncestor(wfRoot, uri))
      // Sort by path descending length to prefer the deepest one in case of multiple nested workspace folders
      .sorted(Comparator.<URI>comparingInt(wfRoot -> wfRoot.getPath().length()).reversed())
      .toList();
    if (folderUriCandidates.isEmpty()) {
      return Optional.empty();
    }
    if (folderUriCandidates.size() > 1) {
      logOutput.debug(format("Multiple candidates workspace folders to contains %s. Default to the deepest one.", uri));
    }
    return Optional.of(folders.get(folderUriCandidates.get(0)));
  }

  /**
   * Resolve the effective configuration scope id for a file: the deepest declared sub-project scope containing it when
   * its workspace folder has one, otherwise the workspace folder's own scope id, otherwise
   * {@link BackendService#ROOT_CONFIGURATION_SCOPE}.
   */
  public String resolveConfigScopeId(URI fileUri) {
    return findFolderForFile(fileUri)
      .map(folder -> sources.scopeUriOf(folder, fileUri).toString())
      .orElse(BackendService.ROOT_CONFIGURATION_SCOPE);
  }

  public Collection<WorkspaceFolderWrapper> getAll() {
    return new ArrayList<>(folders.values());
  }

  public void addListener(WorkspaceFolderLifecycleListener listener) {
    listeners.add(listener);
  }

  public void removeListener(WorkspaceFolderLifecycleListener listener) {
    listeners.remove(listener);
  }

  public void shutdown() {
    Utils.shutdownAndAwait(executor, true);
  }

  public void updateAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
    configurationScopeIds.forEach(s -> logOutput.debug(format("Analysis readiness changed for config scope `%s` to %b", s, areReadyForAnalysis)));
    configurationScopeIds.forEach(folderUri -> analysisReadiness.put(folderUri, areReadyForAnalysis));
  }

  public boolean isReadyForAnalysis(String folderUri) {
    return analysisReadiness.getOrDefault(folderUri, false);
  }

}
