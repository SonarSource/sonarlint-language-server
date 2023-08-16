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
package org.sonarsource.sonarlint.ls.connected.domain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.sonarsource.sonarlint.ls.AnalysisScheduler.SONARCLOUD_TAINT_SOURCE;
import static org.sonarsource.sonarlint.ls.AnalysisScheduler.SONARQUBE_TAINT_SOURCE;
import static org.sonarsource.sonarlint.ls.util.Utils.textRangeWithHashFromTextRange;

class TaintIssueTests {
  @TempDir
  Path basedir;
  private Path fileInAWorkspaceFolderPath;
  private static final String FILE_PHP = "fileInAWorkspaceFolderPath.php";
  private static final String FILE_JAVA = "fileInAWorkspaceFolderPath.java";
  private static final String ISSUE_KEY1 = "TEST_ISSUE_KEY1";
  private static final String ISSUE_KEY2 = "TEST_ISSUE_KEY2";
  private static final Instant CREATION_DATE = Instant.now();
  private static final String RULE_KEY = "javasecurity:S3649";
  private static final String RULE_KEY2 = "javasecurity:S2631";
  private static final IssueSeverity ISSUE_SEVERITY = IssueSeverity.BLOCKER;
  private static final RuleType RULE_TYPE = RuleType.VULNERABILITY;
  private static TaintVulnerabilityRaisedEvent.Location MAIN_LOCATION1;
  private static TaintVulnerabilityRaisedEvent.Location MAIN_LOCATION2;
  ServerTaintIssue.ServerIssueLocation LOCATION1;
  ServerTaintIssue.ServerIssueLocation LOCATION2;
  ServerTaintIssue.ServerIssueLocation LOCATION3;
  ServerTaintIssue.Flow FLOW1;
  ServerTaintIssue.Flow FLOW2;


  @BeforeEach
  public void prepare() throws IOException, ExecutionException, InterruptedException {
    Path workspaceFolderPath = basedir.resolve("myWorkspaceFolder");
    Files.createDirectories(workspaceFolderPath);
    fileInAWorkspaceFolderPath = workspaceFolderPath.resolve(FILE_PHP);
    Files.createFile(fileInAWorkspaceFolderPath);
    Path anotherFileInAWorkspaceFolderPath = workspaceFolderPath.resolve(FILE_JAVA);
    Files.createFile(anotherFileInAWorkspaceFolderPath);

    MAIN_LOCATION1 = new TaintVulnerabilityRaisedEvent.Location(fileInAWorkspaceFolderPath.toUri().toString(),
      "Change this code to not construct SQL queries directly from user-controlled data.",
      new TaintVulnerabilityRaisedEvent.Location.TextRange(1, 2, 3, 4, "blablabla"));

    MAIN_LOCATION2 = new TaintVulnerabilityRaisedEvent.Location(anotherFileInAWorkspaceFolderPath.toUri().toString(),
      "Regular expressions should not be vulnerable to Denial of Service attacks",
      new TaintVulnerabilityRaisedEvent.Location.TextRange(3, 44, 5, 13, "lalala"));

    LOCATION1 = new ServerTaintIssue.ServerIssueLocation(fileInAWorkspaceFolderPath.toString(), new TextRangeWithHash(1, 2, 3, 4, "1234"), "come on");
    LOCATION2 = new ServerTaintIssue.ServerIssueLocation(fileInAWorkspaceFolderPath.toString(), new TextRangeWithHash(6, 7, 8, 9, "6789"), "really???");
    LOCATION3 = new ServerTaintIssue.ServerIssueLocation(anotherFileInAWorkspaceFolderPath.toString(), new TextRangeWithHash(15, 7, 22, 44, "458741"), "hmmm");

    FLOW1 = new ServerTaintIssue.Flow(List.of(LOCATION1, LOCATION2));
    FLOW2 = new ServerTaintIssue.Flow(List.of(LOCATION3));
  }

  @Test
  void shouldConvertServerTaintIssuesToTaintIssues() {
    ServerTaintIssue serverTaintIssue = new ServerTaintIssue(ISSUE_KEY1, false, RULE_KEY, MAIN_LOCATION1.getMessage(),
      fileInAWorkspaceFolderPath.toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE,
      textRangeWithHashFromTextRange(MAIN_LOCATION1.getTextRange()), "java_se" , CleanCodeAttribute.TRUSTWORTHY,
      Map.of(SoftwareQuality.SECURITY, ImpactSeverity.LOW));
    serverTaintIssue.setFlows(List.of(FLOW1));

    TaintIssue taintIssue = TaintIssue.from(serverTaintIssue, SONARCLOUD_TAINT_SOURCE);

    assertAll("TaintIssue is equal to ServerTaintIssue", () -> {
      assertEquals(serverTaintIssue.getKey(), taintIssue.getKey());
      assertEquals(serverTaintIssue.isResolved(), taintIssue.isResolved());
      assertEquals(serverTaintIssue.getRuleKey(), taintIssue.getRuleKey());
      assertEquals(serverTaintIssue.getMessage(), taintIssue.getMessage());
      assertEquals(serverTaintIssue.getFilePath(), taintIssue.getFilePath());
      assertEquals(serverTaintIssue.getCreationDate(), taintIssue.getCreationDate());
      assertEquals(serverTaintIssue.getSeverity(), taintIssue.getSeverity());
      assertEquals(serverTaintIssue.getType(), taintIssue.getType());
      assertEquals(serverTaintIssue.getFlows(), taintIssue.getFlows());
      assertEquals(serverTaintIssue.getTextRange(), taintIssue.getTextRange());
      assertEquals(serverTaintIssue.getRuleDescriptionContextKey(), taintIssue.getRuleDescriptionContextKey());
      assertEquals(serverTaintIssue.getCleanCodeAttribute(), taintIssue.getCleanCodeAttribute());
      assertEquals(serverTaintIssue.getImpacts(), taintIssue.getImpacts());
      assertEquals(SONARCLOUD_TAINT_SOURCE, taintIssue.getSource());
    });

  }

  @Test
  void shouldConvertListOfServerTaintIssuesToListOfTaintIssues() {
    ServerTaintIssue serverTaintIssue1 = new ServerTaintIssue(ISSUE_KEY1, false, RULE_KEY, MAIN_LOCATION1.getMessage(),
      fileInAWorkspaceFolderPath.toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION1.getTextRange()), null,
      CleanCodeAttribute.TRUSTWORTHY,  Map.of(SoftwareQuality.SECURITY, ImpactSeverity.LOW));
    serverTaintIssue1.setFlows(List.of(FLOW1));

    ServerTaintIssue serverTaintIssue2 = new ServerTaintIssue(ISSUE_KEY2, false, RULE_KEY2, MAIN_LOCATION1.getMessage(),
      fileInAWorkspaceFolderPath.toString(), CREATION_DATE, ISSUE_SEVERITY, RULE_TYPE, textRangeWithHashFromTextRange(MAIN_LOCATION2.getTextRange()), null,
      CleanCodeAttribute.TESTED,   Map.of(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW));
    serverTaintIssue1.setFlows(List.of(FLOW2));

    List<TaintIssue> taintIssues = TaintIssue.from(List.of(serverTaintIssue1, serverTaintIssue2), false);

    assertEquals(2, taintIssues.size());

    var taintIssue1 = taintIssues.get(0);
    var taintIssue2 = taintIssues.get(1);

    assertAll("TaintIssue1 is the same as ServerTaintIssue1", () -> {
      assertEquals(serverTaintIssue1.getKey(), taintIssue1.getKey());
      assertEquals(serverTaintIssue1.isResolved(), taintIssue1.isResolved());
      assertEquals(serverTaintIssue1.getRuleKey(), taintIssue1.getRuleKey());
      assertEquals(serverTaintIssue1.getMessage(), taintIssue1.getMessage());
      assertEquals(serverTaintIssue1.getFilePath(), taintIssue1.getFilePath());
      assertEquals(serverTaintIssue1.getCreationDate(), taintIssue1.getCreationDate());
      assertEquals(serverTaintIssue1.getSeverity(), taintIssue1.getSeverity());
      assertEquals(serverTaintIssue1.getType(), taintIssue1.getType());
      assertEquals(serverTaintIssue1.getFlows(), taintIssue1.getFlows());
      assertEquals(serverTaintIssue1.getTextRange(), taintIssue1.getTextRange());
      assertEquals(serverTaintIssue1.getCleanCodeAttribute(), taintIssue1.getCleanCodeAttribute());
      assertEquals(serverTaintIssue1.getImpacts(), taintIssue1.getImpacts());
      assertEquals(SONARQUBE_TAINT_SOURCE, taintIssue1.getSource());
    });

    assertAll("TaintIssue2 is the same as ServerTaintIssue2", () -> {
      assertEquals(serverTaintIssue2.getKey(), taintIssue2.getKey());
      assertEquals(serverTaintIssue2.isResolved(), taintIssue2.isResolved());
      assertEquals(serverTaintIssue2.getRuleKey(), taintIssue2.getRuleKey());
      assertEquals(serverTaintIssue2.getMessage(), taintIssue2.getMessage());
      assertEquals(serverTaintIssue2.getFilePath(), taintIssue2.getFilePath());
      assertEquals(serverTaintIssue2.getCreationDate(), taintIssue2.getCreationDate());
      assertEquals(serverTaintIssue2.getSeverity(), taintIssue2.getSeverity());
      assertEquals(serverTaintIssue2.getType(), taintIssue2.getType());
      assertEquals(serverTaintIssue2.getFlows(), taintIssue2.getFlows());
      assertEquals(serverTaintIssue2.getTextRange(), taintIssue2.getTextRange());
      assertEquals(serverTaintIssue2.getCleanCodeAttribute(), taintIssue2.getCleanCodeAttribute());
      assertEquals(serverTaintIssue2.getImpacts(), taintIssue2.getImpacts());
      assertEquals(SONARQUBE_TAINT_SOURCE, taintIssue2.getSource());
    });
  }
}
