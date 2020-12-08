/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressKind;
import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.eclipse.lsp4j.WorkDoneProgressReport;
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
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class ProgressManagerTests {

  private static final Either<String, Number> FAKE_CLIENT_TOKEN = Either.forLeft("foo");
  private final LanguageClient client = mock(LanguageClient.class);
  private final ProgressManager underTest = new ProgressManager(client);

  @Test
  void noop_progress_if_no_client_support() {
    underTest.setWorkDoneProgressSupportedByClient(false);

    AtomicBoolean done = new AtomicBoolean();
    AtomicBoolean subdone = new AtomicBoolean();
    AtomicBoolean subsubdone = new AtomicBoolean();

    underTest.doWithProgress("Title", null, mock(CancelChecker.class), p -> {
      p.start("Title");
      p.doInSubProgress("Sub title", 0.1f, subP -> {
        subP.doInSubProgress("Sub sub", 0.1f, subSubP -> subsubdone.set(true));
        subdone.set(true);
      });
      p.checkCanceled();
      p.asCoreMonitor();
      p.end("Unused");
      done.set(true);
    });

    verifyZeroInteractions(client);
    assertThat(done.get()).isTrue();
    assertThat(subdone.get()).isTrue();
    assertThat(subsubdone.get()).isTrue();
  }

  @Test
  void should_start_stop_progress() {
    underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, mock(CancelChecker.class), p -> {
    });

    ArgumentCaptor<ProgressParams> params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(2)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getKind())
      .containsExactly(
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.begin),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.end));

    WorkDoneProgressNotification start = params.getAllValues().get(0).getValue();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);
    WorkDoneProgressNotification end = params.getAllValues().get(1).getValue();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isNull();
  }

  @Test
  void should_stop_progress_once() {
    underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, mock(CancelChecker.class), p -> {
      p.end("Completed");
    });

    ArgumentCaptor<ProgressParams> params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(2)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getKind())
      .containsExactly(
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.begin),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.end));

    WorkDoneProgressNotification start = params.getAllValues().get(0).getValue();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);
    WorkDoneProgressNotification end = params.getAllValues().get(1).getValue();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isEqualTo("Completed");
  }

  @Test
  void should_stop_progress_if_error() {
    assertThrows(RuntimeException.class,
      () -> underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, mock(CancelChecker.class), p -> {
        throw new RuntimeException("An error");
      }));

    ArgumentCaptor<ProgressParams> params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(2)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getKind())
      .containsExactly(
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.begin),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.end));

    WorkDoneProgressNotification start = params.getAllValues().get(0).getValue();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);
    WorkDoneProgressNotification end = params.getAllValues().get(1).getValue();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isEqualTo("An error");
  }

  @Test
  void should_stop_progress_if_canceled() {
    CancelChecker cancelChecker = mock(CancelChecker.class);
    underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, cancelChecker, p -> {
      when(cancelChecker.isCanceled()).thenReturn(true);
      p.checkCanceled();
    });

    ArgumentCaptor<ProgressParams> params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(2)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getKind())
      .containsExactly(
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.begin),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.end));

    WorkDoneProgressNotification start = params.getAllValues().get(0).getValue();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);
    WorkDoneProgressNotification end = params.getAllValues().get(1).getValue();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isEqualTo("Canceled");
  }

  @Test
  void should_create_server_side_initiated_progress_if_supported() {
    underTest.setWorkDoneProgressSupportedByClient(true);

    ArgumentCaptor<WorkDoneProgressCreateParams> createParams = ArgumentCaptor.forClass(WorkDoneProgressCreateParams.class);
    when(client.createProgress(createParams.capture())).thenReturn(CompletableFuture.completedFuture(null));

    underTest.doWithProgress("Title", null, mock(CancelChecker.class), p -> {
    });

    Either<String, Number> generatedToken = createParams.getValue().getToken();

    ArgumentCaptor<ProgressParams> params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(2)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getKind())
      .containsExactly(
        tuple(generatedToken, WorkDoneProgressKind.begin),
        tuple(generatedToken, WorkDoneProgressKind.end));

    WorkDoneProgressNotification start = params.getAllValues().get(0).getValue();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);
    WorkDoneProgressNotification end = params.getAllValues().get(1).getValue();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isNull();
  }

  @Test
  void test_sub_progress() {

    underTest.doWithProgress("Title", FAKE_CLIENT_TOKEN, mock(CancelChecker.class), p -> {
      // Report 10%
      p.asCoreMonitor().setFraction(0.1f);
      // From 10 to 60%
      p.doInSubProgress("Sub", 0.5f, subP -> {
        // From 10 to 15%
        subP.doInSubProgress("SubSub", 0.1f, subSub -> {
          // Should report 12.5% (or 12 if rounded)
          subSub.asCoreMonitor().setFraction(0.5f);
        });
        // Automatically report 15%
      });
      // Automatically report 60%
    });

    ArgumentCaptor<ProgressParams> params = ArgumentCaptor.forClass(ProgressParams.class);
    verify(client, times(6)).notifyProgress(params.capture());

    assertThat(params.getAllValues()).extracting(ProgressParams::getToken, p -> p.getValue().getKind())
      .containsExactly(
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.begin),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.report),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.report),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.report),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.report),
        tuple(FAKE_CLIENT_TOKEN, WorkDoneProgressKind.end));

    WorkDoneProgressNotification start = params.getAllValues().get(0).getValue();
    assertThat(start).isInstanceOf(WorkDoneProgressBegin.class);

    WorkDoneProgressNotification progress1 = params.getAllValues().get(1).getValue();
    assertThat(progress1).isInstanceOf(WorkDoneProgressReport.class);
    assertThat(((WorkDoneProgressReport) progress1).getPercentage()).isEqualTo(10);

    WorkDoneProgressNotification progress2 = params.getAllValues().get(2).getValue();
    assertThat(progress2).isInstanceOf(WorkDoneProgressReport.class);
    assertThat(((WorkDoneProgressReport) progress2).getPercentage()).isEqualTo(12);

    WorkDoneProgressNotification progress3 = params.getAllValues().get(3).getValue();
    assertThat(progress3).isInstanceOf(WorkDoneProgressReport.class);
    assertThat(((WorkDoneProgressReport) progress3).getPercentage()).isEqualTo(15);

    WorkDoneProgressNotification progress4 = params.getAllValues().get(4).getValue();
    assertThat(progress4).isInstanceOf(WorkDoneProgressReport.class);
    assertThat(((WorkDoneProgressReport) progress4).getPercentage()).isEqualTo(60);

    WorkDoneProgressNotification end = params.getAllValues().get(5).getValue();
    assertThat(end).isInstanceOf(WorkDoneProgressEnd.class);
    assertThat(((WorkDoneProgressEnd) end).getMessage()).isNull();
  }

}
