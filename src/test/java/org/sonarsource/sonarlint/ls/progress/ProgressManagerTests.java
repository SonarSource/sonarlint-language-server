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
package org.sonarsource.sonarlint.ls.progress;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressKind;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProgressManagerTests {

  private static final Either<String, Integer> FAKE_CLIENT_TOKEN = Either.forLeft("foo");
  private final LanguageClient client = mock(LanguageClient.class);
  private final ProgressManager underTest = new ProgressManager(client);

  @Test
  void noop_progress_if_no_client_support() {
    underTest.setWorkDoneProgressSupportedByClient(false);

    var done = new AtomicBoolean();
    var subdone = new AtomicBoolean();
    var subsubdone = new AtomicBoolean();

    underTest.doWithProgress("Title", null, mock(CancelChecker.class), p -> {
      p.doInSubProgress("Sub title", 0.1f, subP -> {
        subP.doInSubProgress("Sub sub", 0.1f, subSubP -> subsubdone.set(true));
        subdone.set(true);
      });
      p.checkCanceled();
      p.asCoreMonitor();
      p.end("Unused");
      done.set(true);
    });

    verifyNoInteractions(client);
    assertThat(done.get()).isTrue();
    assertThat(subdone.get()).isTrue();
    assertThat(subsubdone.get()).isTrue();
  }

  @Test
  void should_start_stop_progress() {
    underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, mock(CancelChecker.class), p -> {
    });

    var params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(2)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getLeft().getKind())
      .containsExactly(
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.begin),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.end));

    var start = params.getAllValues().get(0).getValue().getLeft();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);
    var end = params.getAllValues().get(1).getValue().getLeft();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isNull();
  }

  @Test
  void should_stop_progress_once() {
    underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, mock(CancelChecker.class), p -> {
      p.end("Completed");
    });

    var params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(2)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getLeft().getKind())
      .containsExactly(
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.begin),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.end));

    var start = params.getAllValues().get(0).getValue().getLeft();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);
    var end = params.getAllValues().get(1).getValue().getLeft();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isEqualTo("Completed");
  }

  @Test
  void should_stop_progress_if_error() {
    assertThrows(RuntimeException.class,
      () -> underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, mock(CancelChecker.class), p -> {
        throw new RuntimeException("An error");
      }));

    var params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(2)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getLeft().getKind())
      .containsExactly(
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.begin),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.end));

    var start = params.getAllValues().get(0).getValue().getLeft();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);
    var end = params.getAllValues().get(1).getValue().getLeft();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isEqualTo("An error");
  }

  @Test
  void should_stop_progress_if_canceled_by_cancel_token() {
    var cancelChecker = mock(CancelChecker.class);
    underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, cancelChecker, p -> {
      when(cancelChecker.isCanceled()).thenReturn(true);
      p.checkCanceled();
    });

    var params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(2)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getLeft().getKind())
      .containsExactly(
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.begin),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.end));

    var start = params.getAllValues().get(0).getValue().getLeft();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);
    var end = params.getAllValues().get(1).getValue().getLeft();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isEqualTo("Canceled");
  }

  @Test
  void should_stop_progress_if_canceled_by_client_request() {
    underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, mock(CancelChecker.class), p -> {
      underTest.cancelProgress(new WorkDoneProgressCancelParams(FAKE_CLIENT_TOKEN));
      p.checkCanceled();
    });

    var params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(2)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getLeft().getKind())
      .containsExactly(
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.begin),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.end));

    var start = params.getAllValues().get(0).getValue().getLeft();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);
    var end = params.getAllValues().get(1).getValue().getLeft();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isEqualTo("Canceled");
  }

  @Test
  void should_create_server_side_initiated_progress_if_supported() {
    underTest.setWorkDoneProgressSupportedByClient(true);

    var createParams = ArgumentCaptor.forClass(WorkDoneProgressCreateParams.class);
    when(client.createProgress(createParams.capture())).thenReturn(CompletableFuture.completedFuture(null));

    underTest.doWithProgress("Title", null, mock(CancelChecker.class), p -> {
    });

    var generatedToken = createParams.getValue().getToken();

    var params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(2)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getLeft().getKind())
      .containsExactly(
        tuple(generatedToken, WorkDoneProgressKind.begin),
        tuple(generatedToken, WorkDoneProgressKind.end));

    var start = params.getAllValues().get(0).getValue().getLeft();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);
    var end = params.getAllValues().get(1).getValue().getLeft();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isNull();
  }

  @Test
  void test_sub_progress() {
    underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, mock(CancelChecker.class), p -> {
      p.asCoreMonitor().setMessage("Working");
      // Report 10%
      p.asCoreMonitor().setFraction(0.1f);
      // From 10 to 60%
      p.doInSubProgress("Sub", 0.5f, subP -> {
        // From 10 to 15%
        subP.doInSubProgress("SubSub1", 0.1f, subSub -> {
          // Should report 12.5% (or 12 if rounded)
          subSub.asCoreMonitor().setFraction(0.5f);
          // Reports 15%
          subSub.end("Sub sub ended");
        });
        // From 15 to 20%
        subP.doInSubProgress("SubSub2", 0.1f, subSub -> {
          // Should report 17.5% (or 17 if rounded)
          subSub.asCoreMonitor().setFraction(0.5f);
        }); // Automatically reports 20%
      });
      // Automatically reports 60%
    });

    var params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(12)).notifyProgress(params.capture());

    var start = params.getAllValues().get(0).getValue().getLeft();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);

    assertThat(params.getAllValues().subList(1, 11))
      .extracting("value.left.message", "value.left.percentage")
      .containsExactly(
        tuple("Working", 0),
        tuple("Working", 10),
        tuple("Sub", 10),
        tuple("Sub - SubSub1", 10),
        tuple("Sub - SubSub1", 12),
        tuple("Sub - SubSub1 - Sub sub ended", 15),
        tuple("Sub - SubSub2", 15),
        tuple("Sub - SubSub2", 17),
        tuple("Sub - SubSub2 - Completed", 20),
        tuple("Sub - Completed", 60));

    var end = params.getAllValues().get(11).getValue().getLeft();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isNull();
  }

  @Test
  void test_non_cancelable_section() {
    underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, mock(CancelChecker.class), p -> {
      p.doInSubProgress("Sub", 0.5f, subP -> {
        subP.asCoreMonitor().executeNonCancelableSection(() -> subP.asCoreMonitor().setMessage("Non cancelable"));
      });
    });

    var params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(7)).notifyProgress(params.capture());

    var start = params.getAllValues().get(0).getValue().getLeft();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);

    assertThat(params.getAllValues().subList(1, 6))
      .extracting("value.left.message", "value.left.percentage", "value.left.cancellable")
      .containsExactly(
        tuple("Sub", 0, null),
        tuple("Sub", 0, false),
        tuple("Sub - Non cancelable", 0, null),
        tuple("Sub - Non cancelable", 0, true),
        tuple("Sub - Completed", 50, null));

    var end = params.getAllValues().get(6).getValue().getLeft();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isNull();
  }

}
