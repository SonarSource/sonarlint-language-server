/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
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
package org.sonarsource.sonarlint.ls.notebooks;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.lsp4j.NotebookDocumentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static java.lang.String.format;

/**
 * Keep track of notebooks opened in the editor, with associated metadata.
 */
public class OpenNotebooksCache {
  private final LanguageClientLogger lsLogOutput;
  private final NotebookDiagnosticPublisher notebookDiagnosticPublisher;

  private final Map<URI, VersionedOpenNotebook> openNotebooksPerFileURI = new ConcurrentHashMap<>();

  public OpenNotebooksCache(LanguageClientLogger lsLogOutput, NotebookDiagnosticPublisher notebookDiagnosticPublisher) {
    this.lsLogOutput = lsLogOutput;
    this.notebookDiagnosticPublisher = notebookDiagnosticPublisher;
  }

  public URI getNotebookUriFromCellUri(URI cellUri) {
    var notebookContainingCellUri =
      this.openNotebooksPerFileURI
        .values()
        .stream()
        .filter(notebook -> notebook.getCellUris().contains(cellUri.toString()))
        .findFirst();
    return notebookContainingCellUri.map(VersionedOpenNotebook::getUri).orElse(null);
  }

  public boolean isKnownCellUri(URI cellUri) {
    var notebookContainingCellUri =
      this.openNotebooksPerFileURI
        .values()
        .stream()
        .filter(notebook -> notebook.getCellUris().contains(cellUri.toString()))
        .findFirst();
    return notebookContainingCellUri.isPresent();
  }

  public VersionedOpenNotebook didOpen(URI fileUri, int version, List<TextDocumentItem> cells) {
    var file = VersionedOpenNotebook.create(fileUri, version, cells, notebookDiagnosticPublisher);
    openNotebooksPerFileURI.put(fileUri, file);
    return file;
  }

  public void didChange(URI fileUri, int version, NotebookDocumentChangeEvent changeEvent) {
    if (!openNotebooksPerFileURI.containsKey(fileUri)) {
      lsLogOutput.warn(format("Illegal state. File \"%s\" is reported changed but we missed the open notification", fileUri));
    } else {
      var openNotebook = openNotebooksPerFileURI.get(fileUri);
      openNotebook.didChange(version, changeEvent);
    }
  }

  public void didClose(URI fileUri) {
    openNotebooksPerFileURI.remove(fileUri);
  }

  public Optional<VersionedOpenNotebook> getFile(URI fileUri) {
    return Optional.ofNullable(openNotebooksPerFileURI.get(fileUri));
  }

  public boolean isNotebook(URI fileUri) {
    return openNotebooksPerFileURI.containsKey(fileUri);
  }

  public Collection<VersionedOpenNotebook> getAll() {
    return openNotebooksPerFileURI.values();
  }

}
