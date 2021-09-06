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
package org.sonarsource.sonarlint.ls;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ScmIgnoredCacheTests {

  private static final URI FAKE_URI = URI.create("file://foo.txt");
  private final SonarLintExtendedLanguageClient mockClient = mock(SonarLintExtendedLanguageClient.class);
  private final ScmIgnoredCache underTest = new ScmIgnoredCache(mockClient);

  @Test
  void ignored_status_should_be_cached_if_true() {
    when(mockClient.isIgnoredByScm(FAKE_URI.toString())).thenReturn(CompletableFuture.completedFuture(true));
    assertThat(underTest.isIgnored(FAKE_URI)).contains(true);
    assertThat(underTest.isIgnored(FAKE_URI)).contains(true);
    verify(mockClient, times(1)).isIgnoredByScm(FAKE_URI.toString());
    verifyNoMoreInteractions(mockClient);
  }

  @Test
  void ignored_status_should_be_cached_if_false() {
    when(mockClient.isIgnoredByScm(FAKE_URI.toString())).thenReturn(CompletableFuture.completedFuture(false));
    assertThat(underTest.isIgnored(FAKE_URI)).contains(false);
    assertThat(underTest.isIgnored(FAKE_URI)).contains(false);
    verify(mockClient, times(1)).isIgnoredByScm(FAKE_URI.toString());
    verifyNoMoreInteractions(mockClient);
  }

  @Test
  void ignored_status_should_be_cached_if_error() {
    CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();
    completableFuture.completeExceptionally(new IllegalStateException("Future failed"));
    when(mockClient.isIgnoredByScm(FAKE_URI.toString())).thenReturn(completableFuture);
    assertThat(underTest.isIgnored(FAKE_URI)).isEmpty();
    assertThat(underTest.isIgnored(FAKE_URI)).isEmpty();
    verify(mockClient, times(1)).isIgnoredByScm(FAKE_URI.toString());
    verifyNoMoreInteractions(mockClient);
  }

  @Test
  void status_should_be_empty_if_exception() {
    when(mockClient.isIgnoredByScm(FAKE_URI.toString())).thenThrow(new IllegalStateException("Cancelled"));
    assertThat(underTest.isIgnored(FAKE_URI)).isEmpty();
  }

}
