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
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static java.lang.String.format;
import static java.net.URI.create;
import static org.sonarsource.sonarlint.ls.util.URIUtils.isAncestor;

public class WorkspaceFoldersManager {
  private final Map<URI, WorkspaceFolderWrapper> folders = new ConcurrentHashMap<>();
  private final Map<String, Boolean> analysisReadiness = new ConcurrentHashMap<>();
  private final List<WorkspaceFolderLifecycleListener> listeners = new CopyOnWriteArrayList<>();
  private final LanguageClientLogger logOutput;

  public WorkspaceFoldersManager(LanguageClientLogger logOutput) {
    this.logOutput = logOutput;
  }

  public void initialize(@Nullable List<WorkspaceFolder> workspaceFolders) {
    if (workspaceFolders != null) {
      workspaceFolders.forEach(wf -> addFolder(wf, create(wf.getUri())));
    }
  }

  public void initialized() {
    var all = getAll();
    listeners.forEach(l -> l.initialized(all));
  }

  public void didChangeWorkspaceFolders(WorkspaceFoldersChangeEvent event) {
    logOutput.debug("Processing didChangeWorkspaceFolders event");
    event.getRemoved().forEach(removed -> removeFolder(create(removed.getUri())));
    for (var added : event.getAdded()) {
      var addedWrapper = addFolder(added, create(added.getUri()));
      listeners.forEach(l -> l.added(addedWrapper));
    }
  }

  private void removeFolder(URI uri) {
    var removed = folders.remove(uri);
    if (removed == null) {
      logOutput.warn("Unregistered workspace folder was missing: " + uri);
    } else {
      listeners.forEach(l -> l.removed(removed));
    }
    logOutput.debug(format("Folder %s removed", removed));
  }

  private WorkspaceFolderWrapper addFolder(WorkspaceFolder added, URI uri) {
    var addedWrapper = new WorkspaceFolderWrapper(uri, added, logOutput);
    if (folders.put(uri, addedWrapper) != null) {
      logOutput.warn(format("Registered workspace folder %s was already added", addedWrapper));
    } else {
      logOutput.debug(format("Folder %s added", addedWrapper));
    }
    return addedWrapper;
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


  public Collection<WorkspaceFolderWrapper> getAll() {
    return new ArrayList<>(folders.values());
  }

  public void addListener(WorkspaceFolderLifecycleListener listener) {
    listeners.add(listener);
  }

  public void removeListener(WorkspaceFolderLifecycleListener listener) {
    listeners.remove(listener);
  }

  public void updateAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
    configurationScopeIds.forEach(s -> logOutput.debug(format("Analysis readiness changed for config scope `%s` to %b", s, areReadyForAnalysis)));
    configurationScopeIds.forEach(folderUri -> analysisReadiness.put(folderUri, areReadyForAnalysis));
  }

  public boolean isReadyForAnalysis(String folderUri) {
    return analysisReadiness.getOrDefault(folderUri, false);
  }

}
