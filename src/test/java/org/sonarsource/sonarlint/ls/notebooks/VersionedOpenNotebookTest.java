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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VersionedOpenNotebookTest {

  @Test
  void shouldConcatenateCells() throws IOException {
    var tmpUri = URI.create("file:///some/notebook.ipynb");

    var cell1 = new TextDocumentItem();
    cell1.setUri(tmpUri.toString() + "#cell1");
    cell1.setText("cell1 line1\ncell1 line2\n");

    var cell2 = new TextDocumentItem();
    cell2.setUri(tmpUri.toString() + "#cell2");
    cell2.setText("cell2 line1\ncell2 line2\n");

    var cell3 = new TextDocumentItem();
    cell3.setUri(tmpUri.toString() + "#cell3");
    cell3.setText("cell3 line1\ncell3 line2\n");

    var underTest = VersionedOpenNotebook.create(tmpUri, 1, List.of(cell1, cell2, cell3), mock(NotebookDiagnosticPublisher.class));

    var clientInputFile = underTest.asInputFile(Path.of(URI.create("file:///some")));

    assertThat(clientInputFile.uri()).isEqualTo(tmpUri);
    assertThat(clientInputFile.contents()).isEqualTo("cell1 line1\ncell1 line2\ncell2 line1\ncell2 line2\ncell3 line1\ncell3 line2\n");
    assertThat(clientInputFile.isTest()).isFalse();
    assertThat(clientInputFile.language()).isNull();

    assertThat(underTest.getCellUri(0).get()).hasFragment("cell1");
    assertThat(underTest.getCellUri(1).get()).hasFragment("cell1");
    assertThat(underTest.getCellUri(2).get()).hasFragment("cell2");
    assertThat(underTest.getCellUri(3).get()).hasFragment("cell2");
    assertThat(underTest.getCellUri(4).get()).hasFragment("cell3");
    assertThat(underTest.getCellUri(5).get()).hasFragment("cell3");
    assertThat(underTest.getCellUri(6)).isEmpty();
  }
}
