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
package org.sonarsource.sonarlint.ls.commands;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.IssueLocation;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShowAllLocationsCommandTests {
  @TempDir
  Path basedir;
  private Path workspaceFolderPath;
  private Path fileInAWorkspaceFolderPath;
  private final String FILE_PYTHON = "myFile.py";

  @BeforeEach
  public void prepare() throws IOException, ExecutionException, InterruptedException {
    workspaceFolderPath = basedir.resolve("myWorkspaceFolder");
    Files.createDirectories(workspaceFolderPath);
    fileInAWorkspaceFolderPath = workspaceFolderPath.resolve(FILE_PYTHON);
    Files.createFile(fileInAWorkspaceFolderPath);
    Files.writeString(fileInAWorkspaceFolderPath, """
      print('1234')
      print('aa')
      print('b')""");
  }

  @Test
  void shouldBuildCommandParamsFromIssue() {
    var issue = mock(Issue.class);
    var file = mock(ClientInputFile.class);
    when(issue.getInputFile()).thenReturn(file);
    var fileUri = URI.create("file:///tmp/plop");
    when(file.uri()).thenReturn(fileUri);
    when(issue.getMessage()).thenReturn("message");
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getRuleKey()).thenReturn("ruleKey");

    var flow1 = mock(Flow.class);
    var loc11 = mock(IssueLocation.class);
    when(loc11.getTextRange()).thenReturn(new TextRange(0, 0, 0, 7));
    var loc12 = mock(IssueLocation.class);
    when(loc12.getTextRange()).thenReturn(new TextRange(2, 2, 2, 9));
    var locations1 = List.of(loc11, loc12);
    when(flow1.locations()).thenReturn(locations1);
    var flow2 = mock(Flow.class);
    var loc2 = mock(IssueLocation.class);
    when(loc2.getTextRange()).thenReturn(new TextRange(4, 0, 4, 7));
    var locations2 = List.of(loc2);
    when(flow2.locations()).thenReturn(locations2);
    var flows = List.of(flow1, flow2);
    when(issue.flows()).thenReturn(flows);
    when(issue.getTextRange()).thenReturn(new TextRangeDto(1, 2, 3, 4));

    var params = ShowAllLocationsCommand.params(issue);
    assertThat(params).extracting(
      "fileUri",
      "message",
      "severity",
      "ruleKey"
    ).containsExactly(
      fileUri,
      "message",
      "BLOCKER",
      "ruleKey"
    );
    assertThat(params.getFlows()).hasSize(2);
    assertThat(params.getFlows().get(0).getLocations()).hasSize(2);
    assertThat(params.getFlows().get(1).getLocations()).hasSize(1);
    assertThat(params.getConnectionId()).isNull();
    assertThat(params.getCreationDate()).isNull();
    assertThat(params.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(params.getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(params.getTextRange().getEndLine()).isEqualTo(3);
    assertThat(params.getTextRange().getEndLineOffset()).isEqualTo(4);
    assertThat(params.getCodeMatches()).isFalse();
  }

  @Test
  void shouldBuildCommandParamsFromShowIssueParams() {
    var textRangeDto = new TextRangeDto(1, 0, 1, 13);
    var showIssueParams = new ShowIssueParams("connectionId", new IssueDetailsDto(textRangeDto,  "rule:S1234",
      "issueKey", workspaceFolderPath.resolve("myFile.py"), "branch", "pr", "this is wrong",
      "29.09.2023", "print('1234')", false, List.of()));

    var result = new ShowAllLocationsCommand.Param(showIssueParams, "connectionId");

    assertTrue(result.getCodeMatches());
  }

  @Test
  void shouldBuildCommandParamsFromShowIssueParamsForFileLevelIssue() {
    var textRangeDto = new TextRangeDto(0, 0, 0, 0);
    var showIssueParams = new ShowIssueParams("connectionId", new IssueDetailsDto(textRangeDto, "rule:S1234",
      "issueKey", workspaceFolderPath.resolve("myFile.py"), "branch", null, "this is wrong",
      "29.09.2023", """
      print('1234')
      print('aa')
      print('b')""", false, List.of()));

    var result = new ShowAllLocationsCommand.Param(showIssueParams, "connectionId");

    assertTrue(result.getCodeMatches());
  }

  @Test
  void shouldBuildCommandParamsFromShowIssueParamsForInvalidTextRange() {
    var textRangeDto = new TextRangeDto(-1, 0, -2, 0);
    var showIssueParams = new ShowIssueParams("connectionId", new IssueDetailsDto(textRangeDto,  "rule:S1234",
      "issueKey", Path.of("/src/java/main/myFile.py"), "bb", "1234", "this is wrong",
      "29.09.2023", "print('1234')", false, List.of()));

    var result = new ShowAllLocationsCommand.Param(showIssueParams, "connectionId");

    assertFalse(result.getCodeMatches());
  }


}
