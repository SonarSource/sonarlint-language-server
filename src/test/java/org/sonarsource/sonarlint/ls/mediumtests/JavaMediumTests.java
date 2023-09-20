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
package org.sonarsource.sonarlint.ls.mediumtests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.GetJavaConfigResponse;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.DidClasspathUpdateParams;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.DidJavaServerModeChangeParams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.appendIfMissing;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class JavaMediumTests extends AbstractLanguageServerMediumTests {

  private static Path module1Path;

  private static Path module2Path;

  private static String MODULE_1_ROOT_URI;
  private static String MODULE_2_ROOT_URI;

  @BeforeAll
  static void initialize() throws Exception {
    module1Path = makeStaticTempDir();
    module2Path = makeStaticTempDir();
    MODULE_1_ROOT_URI = module1Path.toUri().toString();
    MODULE_2_ROOT_URI = module2Path.toUri().toString();

    initialize(Map.of(
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1"));
  }

  @Override
  protected void setupGlobalSettings(Map<String, Object> globalSettings) {
    client.readyForTestsLatch = new CountDownLatch(1);
    setShowVerboseLogs(client.globalSettings, true);
  }

  @Override
  protected void verifyConfigurationChangeOnClient() {
    try {
      assertTrue(client.readyForTestsLatch.await(15, SECONDS));
    } catch (InterruptedException e) {
      fail(e);
    }
  }

  @Test
  void analyseJavaFilesAsNonJavaIfNoClasspath() throws Exception {
    var uri = getUri("skipJavaIfNoClasspath.java");

    client.javaConfigs.put(uri, null);

    didOpen(uri, "java", "public class Foo {\n public static final String KEY = \"AKIAIGKECZXA7EXAMPLF\";\n public static void main() {\n  // System.out.println(\"foo\");\n }\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarlint", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(1, 35, 1, 55, "secrets:S6290", "sonarlint", "Make sure this AWS Access Key ID is not disclosed.", DiagnosticSeverity.Warning),
        tuple(3, 5, 3, 31, "java:S125", "sonarlint", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning)));
    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains(
        "[Debug] Analysis of Java file \"" + uri + "\" may not show all issues because SonarLint was unable to query project configuration (classpath, source level, ...)"));
  }

  @Test
  void analyzeSimpleJavaFileReuseCachedClasspath() throws Exception {
    var uri = getUri("analyzeSimpleJavaFileOnOpen.java");

    var javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setProjectRoot(MODULE_1_ROOT_URI);
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(false);
    javaConfigResponse.setClasspath(new String[]{"/does/not/exist"});
    client.javaConfigs.put(uri, javaConfigResponse);

    didOpen(uri, "java", "public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarlint", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(2, 5, 2, 31, "java:S125", "sonarlint", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning)));

    var ignoredMsg = "[Debug] Classpath \"/does/not/exist\" from configuration does not exist, skipped";
    var cacheMsg = "[Debug] Cached Java config for file \"" + uri + "\"";
    assertThat(client.logs)
      .extracting(withoutTimestamp())
      .containsAll(List.of(ignoredMsg, cacheMsg));

    client.logs.clear();
    client.javaConfigs.put(uri, null);

    didChange(uri, "public class Foo {\n\n  public static void main() {\n  System.out.println(\"foo\");\n}\n}");
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarlint", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(3, 2, 3, 12, "java:S106", "sonarlint", "Replace this use of System.out by a logger.", DiagnosticSeverity.Warning)));

    assertThat(client.logs).extracting(withoutTimestamp()).doesNotContain(cacheMsg);
  }

  @Test
  void analyzeSimpleJavaFileWithFlows() throws Exception {
    var uri = getUri("AnalyzeSimpleJavaFileWithFlows.java");

    var javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setProjectRoot(MODULE_1_ROOT_URI);
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(false);
    javaConfigResponse.setClasspath(new String[0]);
    client.javaConfigs.put(uri, javaConfigResponse);

    didOpen(uri, "java",
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

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(7, 11, 7, 26, "java:S2259", "sonarlint", "\"NullPointerException\" will be thrown when invoking method \"doSomeThingWith()\". [+5 locations]",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarlint", "Move this file to a named package.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleJavaFilePassVmClasspath() throws Exception {
    var javaHome = Paths.get(System.getProperty("java.home"));
    var currentJdkHome = javaHome.endsWith("jre") ? javaHome.getParent() : javaHome;
    var isModular = Files.exists(currentJdkHome.resolve("lib/jrt-fs.jar"));

    setShowAnalyzerLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    var uri = getUri("analyzeSimpleJavaFileOnOpen.java");

    var javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setProjectRoot(MODULE_1_ROOT_URI);
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(false);
    javaConfigResponse.setClasspath(new String[0]);
    javaConfigResponse.setVmLocation(currentJdkHome.toString());
    client.javaConfigs.put(uri, javaConfigResponse);

    didOpen(uri, "java", "public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarlint", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(2, 5, 2, 31, "java:S125", "sonarlint", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning)));

    var jrtFsJarPath = currentJdkHome.resolve(isModular ? "lib/jrt-fs.jar" : "jre/lib/rt.jar").toString();
    assertThat(client.logs)
      .extracting(withoutTimestamp())
      .containsSubsequence(
        "[Debug] Property 'sonar.java.jdkHome' set with: " + currentJdkHome,
        "[Debug] Property 'sonar.java.jdkHome' resolved with:" + System.lineSeparator() + "[" + jrtFsJarPath + "]",
        "[Debug] Property 'sonar.java.libraries' resolved with:" + System.lineSeparator() + "[" + jrtFsJarPath + "]");
  }

  @Test
  void analyzeSimpleJavaTestFileOnOpen() throws Exception {
    var uri = getUri("analyzeSimpleJavaTestFileOnOpen.java");

    var javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setProjectRoot(MODULE_1_ROOT_URI);
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(true);
    javaConfigResponse.setClasspath(new String[]{Paths.get(this.getClass().getResource("/junit-4.12.jar").toURI()).toAbsolutePath().toString()});
    client.javaConfigs.put(uri, javaConfigResponse);

    didOpen(uri, "java",
      "import org.junit.Test;\npublic class FooTest {\n  @Test\n  public void test() {\n String s = \"foo\";\n}\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(3, 14, 3, 18, "java:S2699", "sonarlint", "Add at least one assertion to this test case.", DiagnosticSeverity.Warning)));
  }

  @Test
  void testClassPathUpdateEvictCacheAndTriggersNewAnalysis(@TempDir Path projectRoot) throws Exception {
    var uri = getUri("testClassPathUpdate.java");

    var projectRootUri = projectRoot.toUri().toString();
    // Emulate vscode-java that returns URI with different format in GetJavaConfigResponse and didClasspathUpdate
    // file:///home/julien/Prog/Projects/plugins/sonar-clirr/
    // file:/home/julien/Prog/Projects/plugins/sonar-clirr
    var projectRootUri1 = appendIfMissing(projectRootUri, "/");
    var projectRootUri2 = removeEnd(projectRootUri.replace("file:///", "file:/"), "/");

    var javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setProjectRoot(projectRootUri1);
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(true);
    // Missing deps
    javaConfigResponse.setClasspath(new String[0]);
    client.javaConfigs.put(uri, javaConfigResponse);

    didOpen(uri, "java",
      "import org.junit.Test;\npublic class FooTest {\n  @Test\n  public void test() {\n String s = \"foo\";\n}\n}");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Info] Found 0 issues"));
    client.logs.clear();

    // Update classpath
    javaConfigResponse.setClasspath(new String[]{Paths.get(this.getClass().getResource("/junit-4.12.jar").toURI()).toAbsolutePath().toString()});
    lsProxy.didClasspathUpdate(new DidClasspathUpdateParams(projectRootUri2));

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(3, 14, 3, 18, "java:S2699", "sonarlint", "Add at least one assertion to this test case.", DiagnosticSeverity.Warning)));

    assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains(
        "[Debug] Evicted Java config cache for file \"" + uri + "\"");
  }

  @Test
  void testJavaServerModeUpdateToStandardTriggersNewAnalysis() throws Exception {
    var uri = getUri("testJavaServerModeUpdate.java");

    // Simulate null Java config response due to serverMode=LightWeight
    client.javaConfigs.put(uri, null);

    didOpen(uri, "java", "public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains(
        "[Debug] Analysis of Java file \"" + uri + "\" may not show all issues because SonarLint was unable to query project configuration (classpath, source level, ...)"));
    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarlint", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(2, 5, 2, 31, "java:S125", "sonarlint", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning)));

    // Prepare config response
    var javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setProjectRoot(MODULE_1_ROOT_URI);
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(false);
    javaConfigResponse.setClasspath(new String[0]);
    client.javaConfigs.put(uri, javaConfigResponse);

    lsProxy.didJavaServerModeChange(new DidJavaServerModeChangeParams("Hybrid"));

    lsProxy.didJavaServerModeChange(new DidJavaServerModeChangeParams("Standard"));

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarlint", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarlint", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(2, 5, 2, 31, "java:S125", "sonarlint", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning)));
  }

  @Test
  void shouldBatchAnalysisFromTheSameModule() throws Exception {
    var file1module1 = getUri("Foo1.java");
    var file2module1 = getUri("Foo2.java");
    var nonJavaFilemodule1 = getUri("Another.py");

    // Prepare config response
    var javaConfigResponse = new GetJavaConfigResponse();
    javaConfigResponse.setProjectRoot(MODULE_1_ROOT_URI);
    javaConfigResponse.setSourceLevel("1.8");
    javaConfigResponse.setTest(false);
    javaConfigResponse.setClasspath(new String[0]);
    client.javaConfigs.put(file1module1, javaConfigResponse);
    client.javaConfigs.put(file2module1, javaConfigResponse);

    didOpen(file1module1, "java", "public class Foo1 {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}");
    didOpen(file2module1, "java", "public class Foo2 {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}");
    didOpen(nonJavaFilemodule1, "python", "def foo():\n  toto = 0\n  plouf = 0\n");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains("[Info] Found 3 issues",
        "[Info] Found 3 issues",
        "[Info] Found 2 issues"));

    client.logs.clear();

    // consecutive changes should be batched
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file1module1, 2),
        List.of(new TextDocumentContentChangeEvent("public class Foo1 {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file2module1, 2),
        List.of(new TextDocumentContentChangeEvent("public class Foo2 {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(nonJavaFilemodule1, 2),
        List.of(new TextDocumentContentChangeEvent("def foo():\n  toto = 0\n  plouf = 0\n"))));

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .containsSubsequence(
        "[Debug] Queuing analysis of 3 files",
        "[Info] Analyzing 3 files...",
        "[Info] Found 8 issues"));
  }

  @Test
  void shouldNotBatchAnalysisFromDifferentModules() throws Exception {
    var file1module1 = getUri("file1.java");
    var file2module2 = getUri("file2.java");

    // Prepare config response
    var javaConfigResponse1 = new GetJavaConfigResponse();
    javaConfigResponse1.setProjectRoot(MODULE_1_ROOT_URI);
    javaConfigResponse1.setSourceLevel("1.8");
    javaConfigResponse1.setTest(false);
    javaConfigResponse1.setClasspath(new String[0]);
    client.javaConfigs.put(file1module1, javaConfigResponse1);

    var javaConfigResponse2 = new GetJavaConfigResponse();
    javaConfigResponse2.setProjectRoot(MODULE_2_ROOT_URI);
    javaConfigResponse2.setSourceLevel("1.8");
    javaConfigResponse2.setTest(false);
    javaConfigResponse2.setClasspath(new String[0]);
    client.javaConfigs.put(file2module2, javaConfigResponse2);

    // two consecutive open files from different modules should not be batched
    didOpen(file1module1, "java", "public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}");
    didOpen(file2module2, "java", "public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}");

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .containsSubsequence("[Info] Found 3 issues",
        "[Info] Found 3 issues"));

    client.logs.clear();

    // two consecutive changes on different modules should not be batched
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file1module1, 2),
        List.of(new TextDocumentContentChangeEvent("public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file2module2, 2),
        List.of(new TextDocumentContentChangeEvent("public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}"))));

    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .containsSubsequence(
        "[Info] Found 3 issues",
        "[Info] Found 3 issues")
      // We don't know the order of analysis for the 2 files, so we can't have a single assertion
      .contains(
        "[Info] Analyzing file \"" + file1module1 + "\"...",
        "[Info] Analyzing file \"" + file2module2 + "\"..."));
  }
}
