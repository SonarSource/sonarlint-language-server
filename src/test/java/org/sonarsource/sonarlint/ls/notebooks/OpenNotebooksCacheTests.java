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
import java.util.List;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.mediumtests.AbstractLanguageServerMediumTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenNotebooksCacheTests extends AbstractLanguageServerMediumTests {
  @Test
  void shouldStoreAndRemoveOpenedNotebook() {
    var notebookUri = URI.create("file:///some/notebook.ipynb");

    var cell1 = new TextDocumentItem();
    cell1.setUri(notebookUri + "#cell1");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var cell2 = new TextDocumentItem();
    cell2.setUri(notebookUri + "#cell2");
    cell2.setText("cell2 line1\ncell2 line2\n");
    var fakeNotebook = VersionedOpenNotebook.create(notebookUri, 1, List.of(cell1, cell2), mock(NotebookDiagnosticPublisher.class));

    var underTest = new OpenNotebooksCache(mock(LanguageClientLogger.class), mock(NotebookDiagnosticPublisher.class));
    underTest.didOpen(notebookUri, 1, List.of(cell1, cell2));

    var storedNotebook = underTest.getFile(notebookUri);
    assertThat(storedNotebook.get().getContent()).isEqualTo(fakeNotebook.getContent());
    assertThat(underTest.getAll()).hasSize(1);

    underTest.didClose(notebookUri);
    assertThat(underTest.getAll()).isEmpty();
  }

  @Test
  void shouldReturnNullWhenGetNotebookUriFromCellUriAndNotebookIsNotOpen() {
    var notebookUri = URI.create("file:///some/notebook.ipynb");

    var cell1 = new TextDocumentItem();
    cell1.setUri(notebookUri + "#cell1");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var cell2 = new TextDocumentItem();
    cell2.setUri(notebookUri + "#cell2");
    cell2.setText("cell2 line1\ncell2 line2\n");
    var fakeNotebook = VersionedOpenNotebook.create(notebookUri, 1, List.of(cell1, cell2), mock(NotebookDiagnosticPublisher.class));

    var underTest = new OpenNotebooksCache(mock(LanguageClientLogger.class), mock(NotebookDiagnosticPublisher.class));
    underTest.didOpen(notebookUri, 1, List.of(cell1, cell2));

    var cellUri = URI.create("vscode-notebook-cell:/Users/dda/Documents/jupyterlab-sonarlint/Jupyter%20Demo.ipynb#W2sZmlsZQ%3D%3D");
    assertThat(underTest.getNotebookUriFromCellUri(cellUri)).isNull();
  }

  @Test
  void shouldGetNotebookUriFromCellUri() {
    var notebookUri = URI.create("file:///Users/dda/Documents/jupyterlab-sonarlint/Jupyter%20Demo.ipynb");

    var cell1 = new TextDocumentItem();
    cell1.setUri("vscode-notebook-cell:/Users/dda/Documents/jupyterlab-sonarlint/Jupyter%20Demo.ipynb#W2sZmlsZQ%3D%3D");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var cell2 = new TextDocumentItem();
    cell2.setUri(notebookUri + "#cell2");
    cell2.setText("cell2 line1\ncell2 line2\n");
    var fakeNotebook = VersionedOpenNotebook.create(notebookUri, 1, List.of(cell1, cell2), mock(NotebookDiagnosticPublisher.class));

    var underTest = new OpenNotebooksCache(mock(LanguageClientLogger.class), mock(NotebookDiagnosticPublisher.class));
    underTest.didOpen(notebookUri, 1, List.of(cell1, cell2));

    var cellUri = URI.create("vscode-notebook-cell:/Users/dda/Documents/jupyterlab-sonarlint/Jupyter%20Demo.ipynb#W2sZmlsZQ%3D%3D");
    assertThat(underTest.getNotebookUriFromCellUri(cellUri)).isEqualTo(fakeNotebook.getUri());
  }

  @Test
  void shouldReturnIfUriIsCellUri() {
    var notebookUri = URI.create("file:///Users/dda/Documents/jupyterlab-sonarlint/Jupyter%20Demo.ipynb");

    var cell1 = new TextDocumentItem();
    cell1.setUri("vscode-notebook-cell:/Users/dda/Documents/jupyterlab-sonarlint/Jupyter%20Demo.ipynb#W2sZmlsZQ%3D%3D");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var cell2 = new TextDocumentItem();
    cell2.setUri(notebookUri + "#cell2");
    cell2.setText("cell2 line1\ncell2 line2\n");
    var fakeNotebook = VersionedOpenNotebook.create(notebookUri, 1, List.of(cell1, cell2), mock(NotebookDiagnosticPublisher.class));

    var underTest = new OpenNotebooksCache(mock(LanguageClientLogger.class), mock(NotebookDiagnosticPublisher.class));
    underTest.didOpen(notebookUri, 1, List.of(cell1, cell2));

    assertThat(underTest.isKnownCellUri(URI.create(cell1.getUri()))).isTrue();
    assertThat(underTest.isKnownCellUri(URI.create(cell2.getUri()))).isTrue();
    assertThat(underTest.isKnownCellUri(URI.create(notebookUri + "dsdas"))).isFalse();
  }

}
