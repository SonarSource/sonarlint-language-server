/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.ServerMode;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionnedOpenFile;
import org.sonarsource.sonarlint.ls.folders.WorkspaceFoldersManager;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class AnalysisSchedulerTests {

  private static final URI JS_FILE_URI = URI.create("file://foo.js");
  private static final VersionnedOpenFile JS_FILE = new VersionnedOpenFile(JS_FILE_URI, "javascript", 1, "alert();");
  private AnalysisScheduler underTest;
  private AnalysisTaskExecutor taskExecutor;
  private OpenFilesCache openFilesCache;
  private LanguageClientLogger lsLogOutput;

  @BeforeEach
  public void init() {
    lsLogOutput = mock(LanguageClientLogger.class);
    taskExecutor = mock(AnalysisTaskExecutor.class);
    openFilesCache = new OpenFilesCache(lsLogOutput);
    underTest = new AnalysisScheduler(lsLogOutput, mock(WorkspaceFoldersManager.class), mock(ProjectBindingManager.class), openFilesCache,
      taskExecutor, 200);

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
    underTest.didOpen(new VersionnedOpenFile(URI.create("ftp://foo.js"), "javascript", 1, "alert();"));

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
  void shouldScheduleAnalysisOfAllOpenJavaFilesWithoutIssueRefreshOnClasspathChange() {
    openFilesCache.didOpen(URI.create("file://Foo1.java"), "java", "class Foo1 {}", 1);
    openFilesCache.didOpen(URI.create("file://Foo2.java"), "java", "class Foo2 {}", 1);
    openFilesCache.didOpen(URI.create("file://Foo.js"), "javascript", "alert();", 1);

    underTest.didClasspathUpdate();

    ArgumentCaptor<AnalysisTask> taskCaptor = ArgumentCaptor.forClass(AnalysisTask.class);
    verify(taskExecutor, timeout(1000)).run(taskCaptor.capture());

    AnalysisTask submittedTask = taskCaptor.getValue();
    assertThat(submittedTask.getFilesToAnalyze()).hasSize(2).extracting(VersionnedOpenFile::getLanguageId).containsOnly("java");
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
    assertThat(submittedTask.getFilesToAnalyze()).hasSize(2).extracting(VersionnedOpenFile::getLanguageId).containsOnly("java");
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
  void shouldCancelPreviousAnalysisOnChange() {
    ArgumentCaptor<AnalysisTask> taskCaptor1 = ArgumentCaptor.forClass(AnalysisTask.class);

    var file = openFilesCache.didOpen(JS_FILE_URI, "javascript", "alert();", 1);
    underTest.didChange(file.getUri());
    verify(taskExecutor, timeout(1000)).run(taskCaptor1.capture());
    var task1 = taskCaptor1.getValue();
    assertThat(task1.isCanceled()).isFalse();
    assertThat(task1.getFilesToAnalyze()).extracting(VersionnedOpenFile::getVersion).containsOnly(1);

    reset(taskExecutor);

    openFilesCache.didChange(JS_FILE_URI, "alert();", 2);
    underTest.didChange(file.getUri());

    waitAtMost(1, TimeUnit.SECONDS).untilAsserted(() -> assertThat(task1.isCanceled()).isTrue());

    task1.setFinished(true);

    ArgumentCaptor<AnalysisTask> taskCaptor2 = ArgumentCaptor.forClass(AnalysisTask.class);
    verify(taskExecutor, timeout(1000)).run(taskCaptor2.capture());
    var task2 = taskCaptor2.getValue();
    assertThat(task2.getFilesToAnalyze()).extracting(VersionnedOpenFile::getVersion).containsOnly(2);
  }

}
