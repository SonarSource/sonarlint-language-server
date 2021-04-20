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
package org.sonarsource.sonarlint.ls.mediumtests;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
  void skipJavaIfNoClasspath() throws Exception {
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
  void analyzeSimpleJavaFileReuseCachedClasspath() throws Exception {
    String uri = getUri("analyzeSimpleJavaFileOnOpen.java");

    GetJavaConfigResponse javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(false);
    javaConfigResponse.setClasspath(new String[] { "/does/not/exist" });
    client.javaConfigs.put(uri, javaConfigResponse);

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "java", "public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}");

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(2, 5, 2, 31, "java:S125", "sonarlint", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));

    String ignoredMsg = "[Debug] Classpath '/does/not/exist' from configuration does not exist, skipped";
    String cacheMsg = "[Debug] Cached Java config for file '" + uri + "'";
    await().atMost(5, SECONDS).untilAsserted(() -> {
      assertThat(client.logs)
        .extracting(withoutTimestamp())
        .containsAll(Arrays.asList(ignoredMsg, cacheMsg));
    });

    client.logs.clear();
    client.javaConfigs.put(uri, null);

    diagnostics = didChangeAndWaitForDiagnostics(uri, "public class Foo {\n  public static void main() {\n  System.out.println(\"foo\");\n}\n}");

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(2, 2, 2, 12, "java:S106", "sonarlint", "Replace this use of System.out or System.err by a logger.", DiagnosticSeverity.Warning));

    assertThat(client.logs).extracting(withoutTimestamp()).doesNotContain(cacheMsg);
  }

  @Test
  void analyzeSimpleJavaFileWithFlows() throws Exception {
    String uri = getUri("AnalyzeSimpleJavaFileWithFlows.java");

    GetJavaConfigResponse javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(false);
    javaConfigResponse.setClasspath(new String[0]);
    client.javaConfigs.put(uri, javaConfigResponse);

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "java",
      "public class AnalyzeSimpleJavaFileWithFlows {\n" +
      "  private AnalyzeSimpleJavaFileWithFlows() {}\n" +
      "  static int computeValue(int input) {\n" +
      "    String message = \"polop\";\n" +
      "    if (input == 42) {\n" +
      "      message = null;\n" +
      "    }\n" +
      "    return doSomeThingWith(message);\n" +
      "  }\n" +
      "  private static int doSomeThingWith(String param) {\n" +
      "    return param.length();\n" +
      "  }\n" +
      "}");

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactly(
        tuple(7, 11, 7, 26, "java:S2259", "sonarlint", "\"NullPointerException\" will be thrown when invoking method \"doSomeThingWith()\". [+5 locations]", DiagnosticSeverity.Warning));
  }

  @Test
  void analyzeSimpleJavaFilePassVmClasspath() throws Exception {
    Path javaHome = Paths.get(System.getProperty("java.home"));
    Path currentJdkHome = javaHome.endsWith("jre") ? javaHome.getParent() : javaHome;
    boolean isModular = Files.exists(currentJdkHome.resolve("lib/jrt-fs.jar"));

    emulateConfigurationChangeOnClient("", true, true, true);

    String uri = getUri("analyzeSimpleJavaFileOnOpen.java");

    GetJavaConfigResponse javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(false);
    javaConfigResponse.setClasspath(new String[0]);
    javaConfigResponse.setVmLocation(currentJdkHome.toString());
    client.javaConfigs.put(uri, javaConfigResponse);

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "java", "public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}");

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(2, 5, 2, 31, "java:S125", "sonarlint", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));

    String jrtFsJarPath = currentJdkHome.resolve(isModular ? "lib/jrt-fs.jar" : "jre/lib/rt.jar").toString();
    await().atMost(5, SECONDS).untilAsserted(() -> {
      assertThat(client.logs)
        .extracting(withoutTimestamp())
        .containsSubsequence(
          "[Debug] Property 'sonar.java.jdkHome' set with: " + currentJdkHome,
          "[Debug] Property 'sonar.java.jdkHome' resolved with:" + System.lineSeparator() + "["+ jrtFsJarPath + "]",
          "[Debug] Property 'sonar.java.libraries' resolved with:" + System.lineSeparator() + "[" + jrtFsJarPath + "]"
        );
    });
  }

  @Test
  void analyzeSimpleJavaTestFileOnOpen() throws Exception {
    String uri = getUri("analyzeSimpleJavaTestFileOnOpen.java");

    GetJavaConfigResponse javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(true);
    javaConfigResponse.setClasspath(new String[] {Paths.get(this.getClass().getResource("/junit-4.12.jar").toURI()).toAbsolutePath().toString()});
    client.javaConfigs.put(uri, javaConfigResponse);

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "java",
      "import org.junit.Test;\npublic class FooTest {\n  @Test\n  public void test() {\n String s = \"foo\";\n}\n}");

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(3, 14, 3, 18, "java:S2699", "sonarlint", "Add at least one assertion to this test case.", DiagnosticSeverity.Warning));
  }

  @Test
  void testClassPathUpdateEvictCacheAndTriggersNewAnalysis(@TempDir Path projectRoot) throws Exception {
    String uri = getUri("testClassPathUpdate.java");

    String projectRootUri = projectRoot.toUri().toString();
    // Emulate vscode-java that returns URI with different format in GetJavaConfigResponse and didClasspathUpdate
    // file:///home/julien/Prog/Projects/plugins/sonar-clirr/
    // file:/home/julien/Prog/Projects/plugins/sonar-clirr
    String projectRootUri1 = projectRootUri.endsWith("/") ? projectRootUri : projectRootUri + "/";

    String projectRootUri2 = projectRootUri;
    projectRootUri2 = projectRootUri2.replace("file:///", "file:/");
    projectRootUri2 = projectRootUri2.endsWith("/") ? projectRootUri2.substring(0, projectRootUri2.length() - 1) : projectRootUri2;

    GetJavaConfigResponse javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setProjectRoot(projectRootUri1);
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(true);
    // Missing deps
    javaConfigResponse.setClasspath(new String[0]);
    client.javaConfigs.put(uri, javaConfigResponse);

    List<Diagnostic> diagnostics = didOpenAndWaitForDiagnostics(uri, "java",
      "import org.junit.Test;\npublic class FooTest {\n  @Test\n  public void test() {\n String s = \"foo\";\n}\n}");

    assertThat(diagnostics).isEmpty();
    client.logs.clear();

    // Update classpath
    javaConfigResponse.setClasspath(new String[] {Paths.get(this.getClass().getResource("/junit-4.12.jar").toURI()).toAbsolutePath().toString()});
    client.diagnosticsLatch = new CountDownLatch(1);
    lsProxy.didClasspathUpdate(projectRootUri2);
    if (client.diagnosticsLatch.await(1, TimeUnit.MINUTES)) {
      diagnostics = client.getDiagnostics(uri);
    } else {
      throw new AssertionError("No diagnostics received after 1 minute");
    }

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(3, 14, 3, 18, "java:S2699", "sonarlint", "Add at least one assertion to this test case.", DiagnosticSeverity.Warning));

    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains(
        "[Debug] Evicted Java config cache for file '" + uri + "'"));
  }

  @Test
  void testJavaServerModeUpdateToStandardTriggersNewAnalysis() throws Exception {
    emulateConfigurationChangeOnClient("**/*Test.js", true, false, true);

    String uri = getUri("testJavaServerModeUpdate.java");

    // Simulate null Java config response due to serverMode=LightWeight
    client.javaConfigs.put(uri, null);

    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "java", 1, "public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}")));
    toBeClosed.add(uri);

    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains(
        "[Debug] Skipping analysis of Java file '" + uri + "' because SonarLint was unable to query project configuration (classpath, source level, ...)"));

    // Prepare config response
    GetJavaConfigResponse javaConfigResponse = new GetJavaConfigResponse();
    String projectRoot = "project/root";
    javaConfigResponse.setProjectRoot(projectRoot);
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(false);
    javaConfigResponse.setClasspath(new String[0]);
    client.javaConfigs.put(uri, javaConfigResponse);

    client.diagnosticsLatch = new CountDownLatch(1);

    // Notify of changes in server mode due to temporary activation of standard mode
    lsProxy.didJavaServerModeChange("Hybrid");
    lsProxy.didJavaServerModeChange("Standard");

    List<Diagnostic> diagnostics;
    if (client.diagnosticsLatch.await(1, TimeUnit.MINUTES)) {
      diagnostics = client.getDiagnostics(uri);
    } else {
      throw new AssertionError("No diagnostics received after 1 minute");
    }

    assertThat(diagnostics)
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(2, 5, 2, 31, "java:S125", "sonarlint", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));
  }
}
