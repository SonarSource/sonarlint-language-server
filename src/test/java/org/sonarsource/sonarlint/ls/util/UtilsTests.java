/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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
package org.sonarsource.sonarlint.ls.util;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.ConnectionCheckParams;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;
import org.sonarsource.sonarlint.ls.notebooks.DelegatingCellIssue;
import testutils.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.ls.util.Utils.getValidateConnectionParamsForNewConnection;
import static org.sonarsource.sonarlint.ls.util.Utils.isDelegatingIssueWithServerIssueKey;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilsTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @ParameterizedTest
  @CsvSource({
    "0,vulnerabilities",
    "1,vulnerability",
    "42,vulnerabilities"
  })
  void shouldPluralizeVulnerability(long nbItems, String expected) {
    assertThat(Utils.pluralize(nbItems, "vulnerability", "vulnerabilities")).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "0,issues",
    "1,issue",
    "42,issues"
  })
  void shouldPluralizeIssue(long nbItems, String expected) {
    assertThat(Utils.pluralize(nbItems, "issue")).isEqualTo(expected);
  }


  @Test
  void uriHasFileSchemeTest() {
    assertThat(Utils.uriHasFileScheme(URI.create("file:///path"))).isTrue();
    assertThat(Utils.uriHasFileScheme(URI.create("notfile:///path"))).isFalse();
  }

  @Test
  void formatSha256Fingerprint() {
    var fingerprint = Utils.formatSha256Fingerprint("B74DC3375CF9B45554DA74D2A76D8373CD943DDE588D497053851C244BCA68E5");

    assertThat(fingerprint).isEqualTo("B7 4D C3 37 5C F9 B4 55 54 DA 74 D2 A7 6D 83 73\n" +
      "CD 94 3D DE 58 8D 49 70 53 85 1C 24 4B CA 68 E5");
  }

  @Test
  void formatSha1Fingerprint() {
    var fingerprint = Utils.formatSha1Fingerprint("E589B41477E239FA147B91456041525B73E80337");

    assertThat(fingerprint).isEqualTo("E5 89 B4 14 77 E2 39 FA 14 7B 91 45 60 41 52 5B 73 E8 03 37");
  }

  @Test
  void shouldSafelyGetFailingFuture() {
    var future = CompletableFuture.runAsync(() -> {
      throw new UnsupportedOperationException("Error while computing future", new Throwable());
    });
    var futureResult = Utils.safelyGetCompletableFuture(future, logTester.getLogger());
    assertThat(futureResult).isEmpty();
    assertThat(logTester.logs(MessageType.Log)).anyMatch(log -> log.contains("Future computation completed with an exception"));
  }

  @Test
  void shouldSafelyGetInterruptedFuture() throws Exception {
    var future = mock(CompletableFuture.class);
    when(future.get()).thenThrow(new InterruptedException());
    var futureResult = Utils.safelyGetCompletableFuture(future, logTester.getLogger());
    assertThat(futureResult).isEmpty();
    assertThat(logTester.logs(MessageType.Log)).anyMatch(log -> log.contains("Interrupted!"));
  }

  @Test
  void shouldSafelyGetFuture() {
    var future = CompletableFuture.completedFuture(42);
    var futureResult = Utils.safelyGetCompletableFuture(future, logTester.getLogger());
    assertThat(futureResult).hasValue(42);
  }

  @Test
  void isDelegatingIssueWithServerIssueKeyTest() {
    var delegatingIssueWithServerKey = mock(DelegatingFinding.class);
    when(delegatingIssueWithServerKey.getServerIssueKey()).thenReturn("serverIssueKey");
    var delegatingIssueWithoutServerKey = mock(DelegatingFinding.class);
    var delegatingCellIssue = mock(DelegatingCellIssue.class);

    var delegatingIssueWithServerKeyResult = isDelegatingIssueWithServerIssueKey("serverIssueKey", delegatingIssueWithServerKey);
    var delegatingIssueWithoutServerKeyResult = isDelegatingIssueWithServerIssueKey("serverIssueKey", delegatingIssueWithoutServerKey);
    var delegatingCellIssueResult = isDelegatingIssueWithServerIssueKey("serverIssueKey", delegatingCellIssue);

    assertThat(delegatingIssueWithServerKeyResult).isTrue();
    assertThat(delegatingIssueWithoutServerKeyResult).isFalse();
    assertThat(delegatingCellIssueResult).isFalse();
  }

  @Test
  void getValidateConnectionParamsForNewSonarCloudConnection() {
    var myScOrganization = "my SC organization";
    var token = "token";
    var validateConnectionParams =
      getValidateConnectionParamsForNewConnection(new ConnectionCheckParams(token, myScOrganization, null, "EU"));

    var transientConnection = validateConnectionParams.getTransientConnection();
    assertTrue(transientConnection.isRight());
    assertThat(transientConnection.getRight().getOrganization()).isEqualTo(myScOrganization);
    assertTrue(transientConnection.getRight().getCredentials().isLeft());
    assertThat(transientConnection.getRight().getCredentials().getLeft().getToken()).isEqualTo(token);
  }

  @Test
  void getValidateConnectionParamsForNewSonarQubeConnection() {
    var token = "token";
    var serverUrl = "http://localhost:8080";
    var validateConnectionParams =
      getValidateConnectionParamsForNewConnection(new ConnectionCheckParams("token", null, serverUrl, null));

    var transientConnection = validateConnectionParams.getTransientConnection();
    assertTrue(transientConnection.isLeft());
    assertThat(transientConnection.getLeft().getServerUrl()).isEqualTo(serverUrl);
    assertTrue(transientConnection.getLeft().getCredentials().isLeft());
    assertThat(transientConnection.getLeft().getCredentials().getLeft().getToken()).isEqualTo(token);
  }

  @Test
  void getConnectionNameFromConnectionCheckParams() {
    var token = "token";
    var serverUrl = "http://localhost:8080";
    var myScOrganization = "my SC organization";
    var connectionId = "my connectionId";

    assertThat(Utils.getConnectionNameFromConnectionCheckParams(new ConnectionCheckParams(token, myScOrganization, null, "EU"))).isEqualTo(myScOrganization);
    assertThat(Utils.getConnectionNameFromConnectionCheckParams(new ConnectionCheckParams(token, null, serverUrl, null))).isEqualTo(serverUrl);
    assertThat(Utils.getConnectionNameFromConnectionCheckParams(new ConnectionCheckParams(connectionId))).isEqualTo(connectionId);
  }

  @Test
  void testFixWindowsURIEncoding() {
    assertThat(Utils.fixWindowsURIEncoding(URI.create("file:///c:/work/sonarlint-language-server"))).isEqualTo(URI.create("file:///c%3A/work/sonarlint-language-server"));
    assertThat(Utils.fixWindowsURIEncoding(URI.create("file:///c%3A/work/sonarlint-language-server"))).isEqualTo(URI.create("file:///c%3A/work/sonarlint-language-server"));
    assertThat(Utils.fixWindowsURIEncoding(URI.create("file:///path/work/sonarlint-language-server"))).isEqualTo(URI.create("file:///path/work/sonarlint-language-server"));
  }

  @Test
  void testConvertShowMessageSeverity() {
    assertThat(Utils.convertMessageType(org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType.ERROR)).isEqualTo(MessageType.Error);
    assertThat(Utils.convertMessageType(org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType.WARNING)).isEqualTo(MessageType.Warning);
    assertThat(Utils.convertMessageType(org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType.INFO)).isEqualTo(MessageType.Info);
    assertThrows(NullPointerException.class, () -> Utils.convertMessageType(null));
  }

  @Test
  void shouldRejectNullTokenInValidation() {
    var params = new ConnectionCheckParams(null, null, "http://localhost:8080", null);
    assertThatThrownBy(() -> getValidateConnectionParamsForNewConnection(params))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Token cannot be null or empty for connection validation");
  }

  @Test
  void shouldRejectEmptyTokenInValidation() {
    var params = new ConnectionCheckParams("", null, "http://localhost:8080", null);
    assertThatThrownBy(() -> getValidateConnectionParamsForNewConnection(params))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Token cannot be null or empty for connection validation");
  }
}
