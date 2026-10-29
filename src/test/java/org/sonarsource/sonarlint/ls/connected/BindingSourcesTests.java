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
  private static final URI MODULE_A = URI.create("file:///repo/moduleA/");
  private static final WorkspaceFolderWrapper FOLDER = new WorkspaceFolderWrapper(REPO, new WorkspaceFolder(REPO.toString(), "repo"), mock(LanguageClientLogger.class));

  private final BindingSource first = mock(BindingSource.class);
  private final BindingSource second = mock(BindingSource.class);
  private final BindingSources underTest = new BindingSources(List.of(first, second));

  @Test
  void scopesOf_is_what_the_first_source_that_defines_scopes_says() {
    var firstScopes = List.of(new Scope(MODULE_A, "module-a", "moduleA", Origin.SONAR_CONFIG, first));
    when(first.scopesOf(FOLDER)).thenReturn(firstScopes);
    when(second.scopesOf(FOLDER)).thenReturn(List.of(new Scope(REPO, "repo", ".", Origin.SONAR_CONFIG, second)));

    assertThat(underTest.scopesOf(FOLDER)).isEqualTo(firstScopes);
    verify(second, never()).scopesOf(FOLDER);
  }

  @Test
  void scopesOf_falls_through_to_the_next_source_when_the_first_defines_none() {
    var secondScopes = List.of(new Scope(REPO, "repo", ".", Origin.SONAR_CONFIG, second));
    when(first.scopesOf(FOLDER)).thenReturn(List.of());
    when(second.scopesOf(FOLDER)).thenReturn(secondScopes);

    assertThat(underTest.scopesOf(FOLDER)).isEqualTo(secondScopes);
  }

  @Test
  void scopesOf_is_empty_when_no_source_defines_scopes() {
    assertThat(underTest.scopesOf(FOLDER)).isEmpty();
  }

  @Test
  void bindingOf_is_the_binding_given_by_the_source_that_owns_the_scope() {
    var scope = new Scope(MODULE_A, "module-a", "moduleA", Origin.SONAR_CONFIG, second);
    var binding = ScopeBinding.of("module-a", "connection", "target");
    when(second.bindingOf(FOLDER, scope)).thenReturn(Optional.of(binding));

    assertThat(underTest.bindingOf(FOLDER, scope)).contains(binding);
    verify(first, never()).bindingOf(FOLDER, scope);
  }

  @Test
  void bindingOf_is_empty_when_the_owner_says_the_scope_is_unbound() {
    var scope = new Scope(REPO, "repo", ".", Origin.SONAR_CONFIG, first);
    when(first.bindingOf(FOLDER, scope)).thenReturn(Optional.empty());

    assertThat(underTest.bindingOf(FOLDER, scope)).isEmpty();
  }
}
