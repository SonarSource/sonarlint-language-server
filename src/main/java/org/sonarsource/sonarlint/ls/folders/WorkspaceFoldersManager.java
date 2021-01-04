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
package org.sonarsource.sonarlint.ls.folders;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static java.net.URI.create;

public class WorkspaceFoldersManager {

  private static final Logger LOG = Loggers.get(WorkspaceFoldersManager.class);

  private final Map<URI, WorkspaceFolderWrapper> folders = new ConcurrentHashMap<>();
  private final List<WorkspaceFolderLifecycleListener> listeners = new ArrayList<>();

  public void initialize(@Nullable List<WorkspaceFolder> workspaceFolders) {
    if (workspaceFolders != null) {
      workspaceFolders.forEach(wf -> {
        URI uri = create(wf.getUri());
        addFolder(wf, uri);
      });
    }
  }

  public void didChangeWorkspaceFolders(WorkspaceFoldersChangeEvent event) {
    LOG.debug("Processing didChangeWorkspaceFolders event");
    for (WorkspaceFolder removed : event.getRemoved()) {
      URI uri = create(removed.getUri());
      removeFolder(uri);
    }
    for (WorkspaceFolder added : event.getAdded()) {
      URI uri = create(added.getUri());
      WorkspaceFolderWrapper addedWrapper = addFolder(added, uri);
      listeners.forEach(l -> l.added(addedWrapper));
    }
  }

  private void removeFolder(URI uri) {
    WorkspaceFolderWrapper removed = folders.remove(uri);
    if (removed == null) {
      LOG.warn("Unregistered workspace folder was missing: " + uri);
      return;
    }
    LOG.debug("Folder {} removed", removed);
    listeners.forEach(l -> l.removed(removed));
  }

  private WorkspaceFolderWrapper addFolder(WorkspaceFolder added, URI uri) {
    WorkspaceFolderWrapper addedWrapper = new WorkspaceFolderWrapper(uri, added);
    if (folders.put(uri, addedWrapper) != null) {
      LOG.warn("Registered workspace folder {} was already added", addedWrapper);
    } else {
      LOG.debug("Folder {} added", addedWrapper);
    }
    return addedWrapper;
  }

  public Optional<WorkspaceFolderWrapper> findFolderForFile(URI uri) {
    List<URI> folderUriCandidates = folders.keySet().stream()
      .filter(wfRoot -> isAncestor(wfRoot, uri))
      // Sort by path descending length to prefer the deepest one in case of multiple nested workspace folders
      .sorted(Comparator.<URI>comparingInt(wfRoot -> wfRoot.getPath().length()).reversed())
      .collect(Collectors.toList());
    if (folderUriCandidates.isEmpty()) {
      return Optional.empty();
    }
    if (folderUriCandidates.size() > 1) {
      LOG.debug("Multiple candidates workspace folders to contains {}. Default to the deepest one.", uri);
    }
    return Optional.of(folders.get(folderUriCandidates.get(0)));
  }

  // Visible for testing
  static boolean isAncestor(URI folderUri, URI fileUri) {
    if (folderUri.isOpaque() || fileUri.isOpaque()) {
      throw new IllegalArgumentException("Only hierarchical URIs are supported");
    }
    if (!folderUri.getScheme().equalsIgnoreCase(fileUri.getScheme())) {
      return false;
    }
    if (!Objects.equals(folderUri.getHost(), fileUri.getHost())) {
      return false;
    }
    if (folderUri.getPort() != fileUri.getPort()) {
      return false;
    }
    if (folderUri.getScheme().equalsIgnoreCase("file")) {
      return Paths.get(fileUri).startsWith(Paths.get(folderUri));
    }
    // Assume "/" is the separator of "folders"
    String[] fileSegments = fileUri.getPath().split("/");
    String[] folderSegments = folderUri.getPath().split("/");
    return folderSegments.length <= fileSegments.length && Arrays.equals(folderSegments, Arrays.copyOfRange(fileSegments, 0, folderSegments.length));
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
}
