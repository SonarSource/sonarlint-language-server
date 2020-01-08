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
package org.sonarsource.sonarlint.ls.mediumtests;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.GetJavaConfigResponse;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;

class JavaMediumTests extends AbstractLanguageServerMediumTests {

  @BeforeAll
  public static void initialize() throws Exception {
    initialize(ImmutableMap.<String, String>builder()
      .put("telemetryStorage", "not/exists")
      .put("productName", "SLCORE tests")
      .put("productVersion", "0.1")
      .build());
  }

  @Test
  public void skipJavaIfNoClasspath() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true, false, true);

    String uri = getUri("skipJavaIfNoClasspath.java");

    client.javaConfigs.put(uri, null);

    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "java", 1, "public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}")));
    toBeClosed.add(uri);

    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains(
        "[Debug] Skipping analysis of Java file '" + uri + "' because SonarLint was unable to query project configuration (classpath, source level, ...)"));
  }

  @Test
  public void analyzeSimpleJavaFileReuseCachedClasspath() throws Exception {
    String uri = getUri("analyzeSimpleJavaFileOnOpen.java");

    GetJavaConfigResponse javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(false);
    javaConfigResponse.setClasspath(new String[0]);
    client.javaConfigs.put(uri, javaConfigResponse);

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "java", "public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(2, 0, 2, 31, "java:S125", "sonarlint", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));

    String cacheMsg = "[Debug] Cached Java config for file '" + uri + "'";
    await().atMost(5, SECONDS).untilAsserted(() -> {
      assertThat(client.logs)
        .extracting(withoutTimestamp())
        .contains(
          cacheMsg);
    });

    client.logs.clear();
    client.javaConfigs.put(uri, null);

    diagnostics = didChangeAndWaitForDiagnostics(uri, "public class Foo {\n  public static void main() {\n  System.out.println(\"foo\");\n}\n}");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 12, "java:S106", "sonarlint", "Replace this use of System.out or System.err by a logger.", DiagnosticSeverity.Warning));

    assertThat(client.logs).extracting(withoutTimestamp()).doesNotContain(cacheMsg);
  }

  @Test
  public void analyzeSimpleJavaTestFileOnOpen() throws Exception {
    String uri = getUri("analyzeSimpleJavaTestFileOnOpen.java");

    GetJavaConfigResponse javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(true);
    javaConfigResponse.setClasspath(new String[] {Paths.get(this.getClass().getResource("/junit-4.12.jar").toURI()).toAbsolutePath().toString()});
    client.javaConfigs.put(uri, javaConfigResponse);

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "java",
      "import org.junit.Test;\npublic class FooTest {\n  @Test\n  public void test() {\n String s = \"foo\";\n}\n}");

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactlyInAnyOrder(
        tuple(3, 14, 3, 18, "java:S2699", "sonarlint", "Add at least one assertion to this test case.", DiagnosticSeverity.Error));
  }

  @Test
  public void testClassPathUpdateTriggersNewAnalysis() throws Exception {
    String uri = getUri("testClassPathUpdate.java");

    GetJavaConfigResponse javaConfigResponse = new GetJavaConfigResponse();
    String projectRoot = "project/root";
    javaConfigResponse.setProjectRoot(projectRoot);
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(true);
    // Missing deps
    javaConfigResponse.setClasspath(new String[0]);
    client.javaConfigs.put(uri, javaConfigResponse);

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "java",
      "import org.junit.Test;\npublic class FooTest {\n  @Test\n  public void test() {\n String s = \"foo\";\n}\n}");

    assertThat(diagnostics).isEmpty();

    // Update classpath
    javaConfigResponse.setClasspath(new String[] {Paths.get(this.getClass().getResource("/junit-4.12.jar").toURI()).toAbsolutePath().toString()});
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.didClasspathUpdate(projectRoot);
    if (client.diagnosticsLatch.await(1, TimeUnit.MINUTES)) {
      diagnostics = client.getDiagnostics(uri);
    } else {
      throw new AssertionError("No diagnostics received after 1 minute");
    }

    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactlyInAnyOrder(
        tuple(3, 14, 3, 18, "java:S2699", "sonarlint", "Add at least one assertion to this test case.", DiagnosticSeverity.Error));
  }

}
