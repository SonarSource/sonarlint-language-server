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
package org.sonarsource.sonarlint.ls;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.ServerMode;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.notebooks.NotebookDiagnosticPublisher;
import org.sonarsource.sonarlint.ls.notebooks.OpenNotebooksCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AnalysisSchedulerTests {

  private static final URI JS_FILE_URI = URI.create("file://foo.js");
  private static final VersionedOpenFile JS_FILE = new VersionedOpenFile(JS_FILE_URI, "javascript", 1, "alert();");
  private AnalysisScheduler underTest;
  private AnalysisTaskExecutor taskExecutor;
  private OpenFilesCache openFilesCache;
  private OpenNotebooksCache openNotebooksCache;
  private LanguageClientLogger lsLogOutput;
  private SonarLintExtendedLanguageClient client;

  @BeforeEach
  public void init() {
    lsLogOutput = mock(LanguageClientLogger.class);
    taskExecutor = mock(AnalysisTaskExecutor.class);
    client = mock(SonarLintExtendedLanguageClient.class);
    when(client.filterOutExcludedFiles(any()))
      .thenReturn(CompletableFuture.completedFuture(
        new SonarLintExtendedLanguageClient.FileUrisResult(List.of("file://Foo1.java", "file://Foo2.java"))));
    openFilesCache = new OpenFilesCache(lsLogOutput);
    openNotebooksCache = new OpenNotebooksCache(lsLogOutput, mock(NotebookDiagnosticPublisher.class));
    underTest = new AnalysisScheduler(lsLogOutput, mock(WorkspaceFoldersManager.class), mock(ProjectBindingManager.class), openFilesCache,
      openNotebooksCache, taskExecutor, 200, client);

    underTest.initialize();
  }

  @AfterEach
  public void stop() {
    underTest.shutdown();
  }

  @Test
  void shouldScheduleAnalysisWithIssueRefreshOnOpen() {
    underTest.didOpen(JS_FILE);

    ArgumentCaptor<AnalysisTask> taskCaptor = ArgumentCaptor.forClass(AnalysisTask.class);
    verify(taskExecutor, timeout(1000)).run(taskCaptor.capture());

    AnalysisTask submittedTask = taskCaptor.getValue();
    assertThat(submittedTask.getFilesToAnalyze()).containsExactly(JS_FILE);
    assertThat(submittedTask.shouldFetchServerIssues()).isTrue();
  }

  @Test
  void shouldSkipAnalysisOfNonFSFiles() {
    underTest.didOpen(new VersionedOpenFile(URI.create("ftp://foo.js"), "javascript", 1, "alert();"));

    verify(taskExecutor, timeout(1000).times(0)).run(any());

    verify(lsLogOutput).warn("URI 'ftp://foo.js' is not in local filesystem, analysis not supported");
  }

  @Test
  void shouldScheduleAnalysisWithoutIssueRefreshOnChange() {
    var file = openFilesCache.didOpen(JS_FILE_URI, "javascript", "alert();", 1);
    underTest.didChange(file.getUri());

    ArgumentCaptor<AnalysisTask> taskCaptor = ArgumentCaptor.forClass(AnalysisTask.class);
    verify(taskExecutor, timeout(1000)).run(taskCaptor.capture());

    AnalysisTask submittedTask = taskCaptor.getValue();
    assertThat(submittedTask.getFilesToAnalyze()).containsExactly(file);
    assertThat(submittedTask.shouldFetchServerIssues()).isFalse();
  }

  @Test
  void shouldScheduleAnalysisWithoutIssueRefreshOnHotspotEventReceived() {
    var file = openFilesCache.didOpen(JS_FILE_URI, "javascript", "alert();", 1);
    underTest.didReceiveHotspotEvent(file.getUri());

    ArgumentCaptor<AnalysisTask> taskCaptor = ArgumentCaptor.forClass(AnalysisTask.class);
    verify(taskExecutor, timeout(1000)).run(taskCaptor.capture());

    AnalysisTask submittedTask = taskCaptor.getValue();
    assertThat(submittedTask.getFilesToAnalyze()).containsExactly(file);
    assertThat(submittedTask.shouldFetchServerIssues()).isFalse();
  }

  @Test
  void shouldBatchAnalysisOnChange() {
    var file1 = openFilesCache.didOpen(JS_FILE_URI, "javascript", "alert();", 1);
    var file2 = openFilesCache.didOpen(URI.create("file://foo2.js"), "javascript", "alert();", 1);
    underTest.didChange(file1.getUri());
    underTest.didChange(file2.getUri());

    ArgumentCaptor<AnalysisTask> taskCaptor = ArgumentCaptor.forClass(AnalysisTask.class);
    verify(taskExecutor, timeout(1000)).run(taskCaptor.capture());

    AnalysisTask submittedTask = taskCaptor.getValue();
    assertThat(submittedTask.getFilesToAnalyze()).containsExactlyInAnyOrder(file1, file2);
    assertThat(submittedTask.shouldFetchServerIssues()).isFalse();
  }

  @Test
  void shouldBatchAnalysisOnChangeWithNotebook() {
    var notebook1 = openNotebooksCache.didOpen(URI.create("file:///some/notebook1.ipynb"), 1, Collections.emptyList());
    var notebook2 = openNotebooksCache.didOpen(URI.create("file:///some/notebook2.ipynb"), 2, Collections.emptyList());
    underTest.didChange(notebook1.getUri());
    underTest.didChange(notebook2.getUri());

    ArgumentCaptor<AnalysisTask> taskCaptor = ArgumentCaptor.forClass(AnalysisTask.class);
    verify(taskExecutor, timeout(1000)).run(taskCaptor.capture());

    AnalysisTask submittedTask = taskCaptor.getValue();
    assertThat(submittedTask.getFilesToAnalyze()).hasSize(2);
    assertThat(submittedTask.getFilesToAnalyze().iterator().next().getUri()).isIn(notebook1.getUri(), notebook2.getUri());
    assertThat(submittedTask.getFilesToAnalyze().iterator().next().getLanguageId()).isEqualTo("ipynb");
    assertThat(submittedTask.shouldFetchServerIssues()).isFalse();
  }

  @Test
  void shouldScheduleAnalysisOfAllOpenJavaFilesWithoutIssueRefreshOnClasspathChange() {
    openFilesCache.didOpen(URI.create("file://Foo1.java"), "java", "class Foo1 {}", 1);
    openFilesCache.didOpen(URI.create("file://Foo2.java"), "java", "class Foo2 {}", 1);
    openFilesCache.didOpen(URI.create("file://Foo.js"), "javascript", "alert();", 1);

    underTest.didClasspathUpdate();

    ArgumentCaptor<AnalysisTask> taskCaptor = ArgumentCaptor.forClass(AnalysisTask.class);
    verify(taskExecutor, timeout(1000)).run(taskCaptor.capture());

    AnalysisTask submittedTask = taskCaptor.getValue();
    assertThat(submittedTask.getFilesToAnalyze()).hasSize(2).extracting(VersionedOpenFile::getLanguageId).containsOnly("java");
    assertThat(submittedTask.shouldFetchServerIssues()).isFalse();
  }

  @Test
  void shouldScheduleAnalysisOfAllOpenJavaFilesWithoutIssueRefreshOnJavaServerModeChangeToStandard() {
    openFilesCache.didOpen(URI.create("file://Foo1.java"), "java", "class Foo1 {}", 1);
    openFilesCache.didOpen(URI.create("file://Foo2.java"), "java", "class Foo2 {}", 1);
    openFilesCache.didOpen(URI.create("file://Foo.js"), "javascript", "alert();", 1);

    underTest.didServerModeChange(ServerMode.STANDARD);

    ArgumentCaptor<AnalysisTask> taskCaptor = ArgumentCaptor.forClass(AnalysisTask.class);
    verify(taskExecutor, timeout(1000)).run(taskCaptor.capture());

    AnalysisTask submittedTask = taskCaptor.getValue();
    assertThat(submittedTask.getFilesToAnalyze()).hasSize(2).extracting(VersionedOpenFile::getLanguageId).containsOnly("java");
    assertThat(submittedTask.shouldFetchServerIssues()).isFalse();
  }

  @Test
  void shouldNotScheduleAnalysisOnJavaServerModeChangeOutToStandard() {
    openFilesCache.didOpen(URI.create("file://Foo1.java"), "java", "class Foo1 {}", 1);
    openFilesCache.didOpen(URI.create("file://Foo2.java"), "java", "class Foo2 {}", 1);
    openFilesCache.didOpen(URI.create("file://Foo.js"), "javascript", "alert();", 1);

    underTest.didServerModeChange(ServerMode.LIGHTWEIGHT);

    verify(taskExecutor, timeout(1000).times(0)).run(any());
  }

  @Test
  void shouldCancelPreviousAnalysisTaskOnChange() {
    // Mock an long analysis
    doAnswer(invocation -> {
      AnalysisTask task = invocation.getArgument(0);
      while (!task.isCanceled()) {
        Thread.sleep(10);
      }
      return null;

    }).when(taskExecutor).run(any());

    ArgumentCaptor<AnalysisTask> taskCaptor1 = ArgumentCaptor.forClass(AnalysisTask.class);

    var file = openFilesCache.didOpen(JS_FILE_URI, "javascript", "alert();", 1);
    underTest.didChange(file.getUri());
    verify(taskExecutor, timeout(1000)).run(taskCaptor1.capture());
    var task1 = taskCaptor1.getValue();
    assertThat(task1.isCanceled()).isFalse();
    assertThat(task1.getFilesToAnalyze()).extracting(VersionedOpenFile::getVersion).containsOnly(1);

    reset(taskExecutor);

    openFilesCache.didChange(JS_FILE_URI, "alert();", 2);
    underTest.didChange(file.getUri());

    waitAtMost(1, TimeUnit.SECONDS).untilAsserted(() -> assertThat(task1.isCanceled()).isTrue());

    ArgumentCaptor<AnalysisTask> taskCaptor2 = ArgumentCaptor.forClass(AnalysisTask.class);
    verify(taskExecutor, timeout(1000)).run(taskCaptor2.capture());
    var task2 = taskCaptor2.getValue();
    assertThat(task2.getFilesToAnalyze()).extracting(VersionedOpenFile::getVersion).containsOnly(2);
    task2.getFuture().cancel(false);

    waitAtMost(1, TimeUnit.SECONDS).untilAsserted(() -> assertThat(task2.getFuture().isDone()).isTrue());
  }

  @Test
  void shouldCancelPreviousAnalysisFutureIfNotYetStarted() {
    // Mock an long analysis that we can stop from outside
    AtomicBoolean analysisTaskShouldStop = new AtomicBoolean();
    doAnswer(invocation -> {
      AnalysisTask task = invocation.getArgument(0);
      while (!analysisTaskShouldStop.get() && !task.isCanceled()) {
        Thread.sleep(10);
      }
      analysisTaskShouldStop.set(false);
      return null;

    }).when(taskExecutor).run(any());

    var file = openFilesCache.didOpen(JS_FILE_URI, "javascript", "alert(1);", 1);
    underTest.didOpen(file);
    verify(lsLogOutput, timeout(1000)).debug("Queuing analysis of file \"" + JS_FILE_URI + "\" (version 1)");
    verify(taskExecutor, timeout(1000)).run(any());

    reset(taskExecutor);

    openFilesCache.didChange(JS_FILE_URI, "alert(2);", 2);
    underTest.didChange(file.getUri());

    verify(lsLogOutput, timeout(1000)).debug("Queuing analysis of file \"" + JS_FILE_URI + "\" (version 2)");

    // Analysis of version 2 is stuck in the executor service queue because analysis of version 1 is still running
    verify(taskExecutor, timeout(1000).times(0)).run(any());

    reset(taskExecutor);

    openFilesCache.didChange(JS_FILE_URI, "alert(3);", 3);
    underTest.didChange(file.getUri());

    verify(lsLogOutput, timeout(1000).times(1)).debug("Attempt to cancel previous analysis...");
    verify(lsLogOutput, timeout(1000)).debug("Queuing analysis of file \"" + JS_FILE_URI + "\" (version 3)");
    verifyNoMoreInteractions(lsLogOutput);

    analysisTaskShouldStop.set(true);

    ArgumentCaptor<AnalysisTask> onChangeTaskCaptor2 = ArgumentCaptor.forClass(AnalysisTask.class);
    verify(taskExecutor, timeout(10000)).run(onChangeTaskCaptor2.capture());
    var task2 = onChangeTaskCaptor2.getValue();
    assertThat(task2.getFilesToAnalyze()).extracting(VersionedOpenFile::getVersion).containsOnly(3);
  }

}
