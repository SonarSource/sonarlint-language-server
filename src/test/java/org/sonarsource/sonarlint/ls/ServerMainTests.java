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

import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.shaded.org.apache.commons.io.output.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class ServerMainTests {

  private ByteArrayOutputStream out = new ByteArrayOutputStream();
  private ByteArrayOutputStream err = new ByteArrayOutputStream();
  private ServerMain underTest = new ServerMain(new PrintStream(out), new PrintStream(err));

  @BeforeEach
  public void prepare() {
    underTest = spy(underTest);
    doThrow(new RuntimeException("exit called")).when(underTest).exitWithError();
  }

  @Test
  public void testRequiredArguments() {

    RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
      underTest.startLanguageServer();
    });

    assertThat(thrown).hasMessage("exit called");
    assertThat(err.toString(StandardCharsets.UTF_8))
      .isEqualTo("Usage: java -jar sonarlint-server.jar <jsonRpcPort> " +
              "[-analyzers file:///path/to/analyzer1.jar [file:///path/to/analyzer2.jar] ...] " +
              "[-extraAnalyzers file:///path/to/analyzer3.jar [file:///path/to/analyzer4.jar] ...]" + System.lineSeparator());
  }

  @Test
  public void testInvalidPortArgument() {

    RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
      underTest.startLanguageServer("not_a_number");
    });

    assertThat(thrown).hasMessage("exit called");
    assertThat(err.toString(StandardCharsets.UTF_8))
      .contains("Invalid port provided as first parameter");
  }

  @Test
  public void testInvalidPluginURL() {

    RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
      underTest.startLanguageServer("1", "-analyzers", "http/invalid");
    });

    assertThat(thrown).hasMessage("exit called");
    assertThat(err.toString(StandardCharsets.UTF_8))
      .contains("Invalid argument at position 3. Expected an URL.");
  }

  @Test
  public void testInvalidExtraPluginURL() {

    RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
      underTest.startLanguageServer("1", "-analyzers", "http://analyzer.jar", "-extraAnalyzers", "http/invalid");
    });

    assertThat(thrown).hasMessage("exit called");
    assertThat(err.toString(StandardCharsets.UTF_8))
      .contains("Invalid argument at position 5. Expected an URL.");
  }

  @Test
  public void testExtractingAnalyzersPositive() {
    String[] args = {"-analyzers", "http://url1.jar", "http://url2.jar"};
    Collection<URL> urls = underTest.extractAnalyzers(args);
    assertThat(urls).extracting(URL::getProtocol, URL::getHost)
            .containsExactly(tuple("http", "url1.jar"), tuple("http", "url2.jar"));
  }

  @Test
  public void testExtractingAnalyzersExitsIfNoKey() {
    String[] args = {"http://url1.jar", "http://url2.jar"};

    assertThrows(RuntimeException.class, ()-> underTest.extractAnalyzers(args));
  }

  @Test
  public void testExtractingAnalyzersExitsOnMalformedUrl() {
    String[] args = {"-analyzers", "url1"};

    assertThrows(RuntimeException.class, ()-> underTest.extractAnalyzers(args));
  }

  @Test
  public void testExtractingExtraAnalyzersPositive() {
    String[] args = {"-extraAnalyzers", "http://url1.jar", "http://url2.jar"};
    Collection<URL> urls = underTest.extractExtraAnalyzers(args);
    assertThat(urls).extracting(URL::getProtocol, URL::getHost)
            .containsExactly(tuple("http", "url1.jar"), tuple("http", "url2.jar"));
  }

  @Test
  public void testExtractingExtraAnalyzersReturnsEmptyListIfNoKey() {
    String[] args = {"http://url1.jar", "http://url2.jar"};

    Collection<URL> urls = underTest.extractExtraAnalyzers(args);
    assertThat(urls).isEmpty();
  }

  @Test
  public void testExtractingExtraAnalyzersExitsOnMalformedUrl() {
    String[] args = {"-extraAnalyzers", "url1"};

    assertThrows(RuntimeException.class, ()-> underTest.extractExtraAnalyzers(args));
  }

}
