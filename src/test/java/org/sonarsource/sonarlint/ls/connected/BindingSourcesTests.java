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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingSourcesTests {

  private static final URI REPO = URI.create("file:///repo/");
  private static final WorkspaceFolderWrapper FOLDER = new WorkspaceFolderWrapper(REPO, new WorkspaceFolder(REPO.toString(), "repo"), mock(LanguageClientLogger.class));
  private static final Scope SCOPE = new Scope(REPO, "repo", ".", Origin.SETTINGS);

  private final BindingSource first = mock(BindingSource.class);
  private final BindingSource second = mock(BindingSource.class);
  private final BindingSources underTest = new BindingSources(List.of(first, second));

  @Test
  void bindingOf_is_the_binding_of_the_first_source_that_has_one() {
    var firstBinding = ScopeBinding.of("first", "connection", "target");
    when(first.bindingOf(FOLDER, SCOPE)).thenReturn(Optional.of(firstBinding));
    when(second.bindingOf(FOLDER, SCOPE)).thenReturn(Optional.of(ScopeBinding.of("second", "connection", "target")));

    assertThat(underTest.bindingOf(FOLDER, SCOPE)).contains(firstBinding);
    verify(second, never()).bindingOf(FOLDER, SCOPE);
  }

  @Test
  void bindingOf_falls_through_to_the_next_source_when_the_first_has_none() {
    var secondBinding = ScopeBinding.of("second", "connection", "target");
    when(first.bindingOf(FOLDER, SCOPE)).thenReturn(Optional.empty());
    when(second.bindingOf(FOLDER, SCOPE)).thenReturn(Optional.of(secondBinding));

    assertThat(underTest.bindingOf(FOLDER, SCOPE)).contains(secondBinding);
  }

  @Test
  void bindingOf_is_empty_when_no_source_has_a_binding() {
    assertThat(underTest.bindingOf(FOLDER, SCOPE)).isEmpty();
  }
}
