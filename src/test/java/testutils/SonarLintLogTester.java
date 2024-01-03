/*
 * SonarLint Language Server
 * Copyright (C) 2009-2024 SonarSource SA
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
package testutils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;


/**
 * <b>For tests only</b>
 * <br>
 * This JUnit 5 extension allows to access logs in tests.
 * <br>
 * Warning - not compatible with parallel execution of tests in the same JVM fork.
 * <br>
 * Example:
 * <pre>
 * public class MyClass {
 *   private final SonarLintLogger logger = SonarLintLogger.get();
 *
 *   public void doSomething() {
 *     logger.info("foo");
 *   }
 * }
 *
 * class MyClassTests {
 *   &#064;org.junit.jupiter.api.extension.RegisterExtension
 *   SonarLintLogTester logTester = new SonarLintLogTester();
 *
 *   &#064;org.junit.jupiter.api.Test
 *   public void test_log() {
 *     new MyClass().doSomething();
 *
 *     assertThat(logTester.logs()).containsOnly("foo");
 *   }
 * }
 * </pre>
 *
 */
public class SonarLintLogTester implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

  private final Queue<String> logs = new ConcurrentLinkedQueue<>();
  private final Map<MessageType, Queue<String>> logsByLevel = new ConcurrentHashMap<>();
  private final LanguageClientLogOutput LOG;

  public SonarLintLogTester() {
    var client = new LanguageClient() {
      @Override
      public void telemetryEvent(Object object) {

      }

      @Override
      public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {

      }

      @Override
      public void showMessage(MessageParams messageParams) {

      }

      @Override
      public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return null;
      }

      @Override
      public void logMessage(MessageParams params) {
        logs.add(params.getMessage());
        logsByLevel.computeIfAbsent(params.getType(), l -> new ConcurrentLinkedQueue<>()).add(params.getMessage());
      }
    };
    var logger = new LanguageClientLogger(client);
    logger.initialize(true);
    LOG = new LanguageClientLogOutput(logger, false);
  }


  @Override
  public void beforeTestExecution(ExtensionContext context) throws Exception {

  }

  @Override
  public void afterTestExecution(ExtensionContext context) throws Exception {
    clear();
  }
  /**
   * Logs in chronological order (item at index 0 is the oldest one)
   */
  public List<String> logs() {
    return List.copyOf(logs);
  }

  /**
   * Logs in chronological order (item at index 0 is the oldest one) for
   * a given level
   */
  public List<String> logs(MessageType level) {
    return Optional.ofNullable(logsByLevel.get(level)).map(List::copyOf).orElse(List.of());
  }

  public void clear() {
    logs.clear();
    logsByLevel.clear();
  }

  public LanguageClientLogOutput getLogger() {
    return LOG;
  }
}
