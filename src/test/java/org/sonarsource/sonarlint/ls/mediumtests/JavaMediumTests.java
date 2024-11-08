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
package org.sonarsource.sonarlint.ls.mediumtests;

import java.io.IOException;
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
import org.eclipse.lsp4j.WorkspaceFolder;
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
  private static Path analysisDir;

  private static String MODULE_1_ROOT_URI;
  private static String MODULE_2_ROOT_URI;

  @BeforeAll
  static void initialize() throws Exception {
    analysisDir = makeStaticTempDir();
    module1Path = makeStaticTempDir();
    module2Path = makeStaticTempDir();
    MODULE_1_ROOT_URI = module1Path.toUri().toString();
    MODULE_2_ROOT_URI = module2Path.toUri().toString();

    initialize(Map.of(
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1",
      "productKey", "productKey"), new WorkspaceFolder(analysisDir.toUri().toString(), "AnalysisDir"));
  }

  @BeforeEach
  void setup() throws IOException {
    getFolderSettings(analysisDir.toUri().toString());
    org.apache.commons.io.FileUtils.cleanDirectory(analysisDir.toFile());
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
    var uri = getUri("skipJavaIfNoClasspath.java", analysisDir);

    client.javaConfigs.put(uri, null);

    didOpen(uri, "java", "public class Foo {\n public static final String AWS_SECRET_KEY = \"AKIAIGKECZXA7EXAMPLF\";\n public static void main() {\n  // System.out.println(\"foo\");\n }\n}");

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(0, 13, 0, 16, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(1, 46, 1, 66, "secrets:S6290", "sonarqube", "Make sure the access granted with this AWS access key ID is restricted", DiagnosticSeverity.Warning),
        tuple(3, 5, 3, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning)));
    awaitUntilAsserted(() -> assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains(
        "[Debug] Analysis of Java file \"" + uri + "\" may not show all issues because SonarLint was unable to query project configuration (classpath, source level, ...)"));
  }

  @Test
  void analyzeSimpleJavaFileReuseCachedClasspath() throws Exception {
    var uri = getUri("analyzeSimpleJavaFileOnOpen.java", analysisDir);

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
        tuple(0, 13, 0, 16, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning)));

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
        tuple(0, 13, 0, 16, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(3, 2, 3, 12, "java:S106", "sonarqube", "Replace this use of System.out by a logger.", DiagnosticSeverity.Warning)));

    assertThat(client.logs).extracting(withoutTimestamp()).doesNotContain(cacheMsg);
  }

  @Test
  void analyzeSimpleJavaFileWithFlows() throws Exception {
    var uri = getUri("AnalyzeSimpleJavaFileWithFlows.java", analysisDir);

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
        tuple(7, 11, 7, 26, "java:S2259", "sonarqube", "\"NullPointerException\" will be thrown when invoking method \"doSomeThingWith()\". [+5 locations]",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning)));
  }

  @Test
  void analyzeSimpleJavaFilePassVmClasspath() throws Exception {
    var javaHome = Paths.get(System.getProperty("java.home"));
    var currentJdkHome = javaHome.endsWith("jre") ? javaHome.getParent() : javaHome;
    var isModular = Files.exists(currentJdkHome.resolve("lib/jrt-fs.jar"));

    setShowAnalyzerLogs(client.globalSettings, true);
    notifyConfigurationChangeOnClient();

    var uri = getUri("analyzeSimpleJavaFileOnOpen.java", analysisDir);

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
        tuple(0, 13, 0, 16, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning)));

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
    var uri = getUri("analyzeSimpleJavaTestFileOnOpen.java", analysisDir);

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
        tuple(3, 14, 3, 18, "java:S2699", "sonarqube", "Add at least one assertion to this test case.", DiagnosticSeverity.Warning)));
  }

  @Test
  void testClassPathUpdateEvictCacheAndTriggersNewAnalysis(@TempDir Path projectRoot) throws Exception {
    var uri = getUri("testClassPathUpdate.java", analysisDir);

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
      .extracting(withoutTimestampAndMillis())
      .contains("[Info] Analysis detected 0 issues and 0 Security Hotspots in XXXms"));
    client.logs.clear();

    // Update classpath
    javaConfigResponse.setClasspath(new String[]{Paths.get(this.getClass().getResource("/junit-4.12.jar").toURI()).toAbsolutePath().toString()});
    lsProxy.didClasspathUpdate(new DidClasspathUpdateParams(projectRootUri2));

    awaitUntilAsserted(() -> assertThat(client.getDiagnostics(uri))
      .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
      .containsExactlyInAnyOrder(
        tuple(3, 14, 3, 18, "java:S2699", "sonarqube", "Add at least one assertion to this test case.", DiagnosticSeverity.Warning)));

    assertThat(client.logs)
      .extracting(withoutTimestamp())
      .contains(
        "[Debug] Evicted Java config cache for file \"" + uri + "\"");
  }

  @Test
  void testJavaServerModeUpdateToStandardTriggersNewAnalysis() throws Exception {
    var uri = getUri("testJavaServerModeUpdate.java", analysisDir);

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
        tuple(0, 13, 0, 16, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning)));

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
        tuple(0, 13, 0, 16, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
        tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning)));
  }

  @Test
  void shouldBatchAnalysisFromTheSameModule() throws Exception {
    var file1module1 = getUri("Foo1.java", analysisDir);
    var file2module1 = getUri("Foo2.java", analysisDir);
    var nonJavaFilemodule1 = getUri("Another.py", analysisDir);

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

    awaitUntilAsserted(() -> {
      assertThat(client.getDiagnostics(file1module1))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
        .containsExactlyInAnyOrder(
          tuple(0, 13, 0, 17, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
          tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
          tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));

      assertThat(client.getDiagnostics(file2module1))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
        .containsExactlyInAnyOrder(
          tuple(0, 13, 0, 17, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
          tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
          tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));

      assertThat(client.getDiagnostics(nonJavaFilemodule1))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
        .containsExactlyInAnyOrder(
          tuple(1, 2, 1, 6, "python:S1481", "sonarqube", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
          tuple(2, 2, 2, 7, "python:S1481", "sonarqube", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning));
    });

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

    awaitUntilAsserted(() -> {
      assertThat(client.getDiagnostics(file1module1))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
        .containsExactlyInAnyOrder(
          tuple(0, 13, 0, 17, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
          tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
          tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));

      assertThat(client.getDiagnostics(file2module1))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
        .containsExactlyInAnyOrder(
          tuple(0, 13, 0, 17, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
          tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
          tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));

      assertThat(client.getDiagnostics(nonJavaFilemodule1))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
        .containsExactlyInAnyOrder(
          tuple(1, 2, 1, 6, "python:S1481", "sonarqube", "Remove the unused local variable \"toto\".", DiagnosticSeverity.Warning),
          tuple(2, 2, 2, 7, "python:S1481", "sonarqube", "Remove the unused local variable \"plouf\".", DiagnosticSeverity.Warning));
    });
  }

  @Test
  void shouldNotBatchAnalysisFromDifferentModules() throws Exception {
    var file1module1 = getUri("file1.java", analysisDir);
    var file2module2 = getUri("file2.java", analysisDir);

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

    awaitUntilAsserted(() -> {
      assertThat(client.getDiagnostics(file1module1))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
        .containsExactlyInAnyOrder(
          tuple(0, 13, 0, 16, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
          tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
          tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));

      assertThat(client.getDiagnostics(file2module2))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
        .containsExactlyInAnyOrder(
          tuple(0, 13, 0, 16, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
          tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
          tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));
    });

    client.logs.clear();

    // two consecutive changes on different modules should not be batched
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file1module1, 2),
        List.of(new TextDocumentContentChangeEvent("public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}"))));
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(file2module2, 2),
        List.of(new TextDocumentContentChangeEvent("public class Foo {\n  public static void main() {\n  // System.out.println(\"foo\");\n}\n}"))));

    awaitUntilAsserted(() -> {
      assertThat(client.getDiagnostics(file1module1))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
        .containsExactlyInAnyOrder(
          tuple(0, 13, 0, 16, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
          tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
          tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));

      assertThat(client.getDiagnostics(file2module2))
        .extracting(startLine(), startCharacter(), endLine(), endCharacter(), code(), Diagnostic::getSource, Diagnostic::getMessage, Diagnostic::getSeverity)
        .containsExactlyInAnyOrder(
          tuple(0, 13, 0, 16, "java:S1118", "sonarqube", "Add a private constructor to hide the implicit public one.", DiagnosticSeverity.Warning),
          tuple(0, 0, 0, 0, "java:S1220", "sonarqube", "Move this file to a named package.", DiagnosticSeverity.Warning),
          tuple(2, 5, 2, 31, "java:S125", "sonarqube", "This block of commented-out lines of code should be removed.", DiagnosticSeverity.Warning));
    });
  }
}
