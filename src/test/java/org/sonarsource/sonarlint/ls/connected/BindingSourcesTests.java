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
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFolderWrapper;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.scopes.ConfigurationScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingSourcesTests {

  private static final URI REPO = URI.create("file:///repo/");
  private static final WorkspaceFolderWrapper FOLDER = new WorkspaceFolderWrapper(REPO, new WorkspaceFolder(REPO.toString(), "repo"), mock(LanguageClientLogger.class));
  private static final ConfigurationScope FIRST_SCOPE = new ConfigurationScope(REPO, "repo", ".", Origin.SETTINGS, new ProjectBinding("connection", "first"));
  private static final ConfigurationScope SECOND_SCOPE = new ConfigurationScope(REPO, "repo", ".", Origin.SETTINGS, new ProjectBinding("connection", "second"));

  private final BindingSource first = mock(BindingSource.class);
  private final BindingSource second = mock(BindingSource.class);
  private final BindingSources underTest = new BindingSources(List.of(first, second));

  @Test
  void scopesOf_are_the_scopes_of_the_first_source_that_has_any() {
    when(first.scopesOf(FOLDER)).thenReturn(List.of(FIRST_SCOPE));
    when(second.scopesOf(FOLDER)).thenReturn(List.of(SECOND_SCOPE));

    assertThat(underTest.scopesOf(FOLDER)).containsExactly(FIRST_SCOPE);
    verify(second, never()).scopesOf(FOLDER);
  }

  @Test
  void scopesOf_falls_through_to_the_next_source_when_the_first_has_none() {
    when(first.scopesOf(FOLDER)).thenReturn(List.of());
    when(second.scopesOf(FOLDER)).thenReturn(List.of(SECOND_SCOPE));

    assertThat(underTest.scopesOf(FOLDER)).containsExactly(SECOND_SCOPE);
  }

  @Test
  void scopesOf_is_empty_when_no_source_has_any() {
    when(first.scopesOf(FOLDER)).thenReturn(List.of());
    when(second.scopesOf(FOLDER)).thenReturn(List.of());

    assertThat(underTest.scopesOf(FOLDER)).isEmpty();
  }
}
