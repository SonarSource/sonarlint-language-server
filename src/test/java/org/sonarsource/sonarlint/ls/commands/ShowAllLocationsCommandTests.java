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
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueLocationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.FlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.LocationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;

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
      print('b')
      print('kkkk')""");
  }

  @Test
  void shouldBuildCommandParamsFromIssue() {
    var issue = mock(DelegatingFinding.class);
    var fileUri = URI.create("file:///tmp/plop");
    when(issue.getMessage()).thenReturn("message");
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);
    when(issue.getRuleKey()).thenReturn("ruleKey");
    when(issue.getFileUri()).thenReturn(fileUri);

    var flow1 = mock(IssueFlowDto.class);
    var loc11 = mock(IssueLocationDto.class);
    when(loc11.getTextRange()).thenReturn(new TextRangeDto(0, 0, 0, 7));
    var loc12 = mock(IssueLocationDto.class);
    when(loc12.getTextRange()).thenReturn(new TextRangeDto(2, 2, 2, 9));
    var locations1 = List.of(loc11, loc12);
    when(flow1.getLocations()).thenReturn(locations1);
    var flow2 = mock(IssueFlowDto.class);
    var loc2 = mock(IssueLocationDto.class);
    when(loc2.getTextRange()).thenReturn(new TextRangeDto(4, 0, 4, 7));
    var locations2 = List.of(loc2);
    when(flow2.getLocations()).thenReturn(locations2);
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
    var ideFilePath = Path.of(FILE_PYTHON);
    var flow1 = mock(FlowDto.class);
    var loc11 = mock(LocationDto.class);
    when(loc11.getTextRange()).thenReturn(new TextRangeDto(1, 0, 1, 7));
    when(loc11.getCodeSnippet()).thenReturn("");
    when(loc11.getIdeFilePath()).thenReturn(ideFilePath);
    var loc12 = mock(LocationDto.class);
    when(loc12.getTextRange()).thenReturn(new TextRangeDto(2, 2, 2, 9));
    when(loc12.getCodeSnippet()).thenReturn("f=1");
    when(loc12.getIdeFilePath()).thenReturn(ideFilePath);
    var locations1 = List.of(loc11, loc12);
    when(flow1.getLocations()).thenReturn(locations1);
    var flow2 = mock(FlowDto.class);
    var loc2 = mock(LocationDto.class);
    when(loc2.getTextRange()).thenReturn(new TextRangeDto(2, 0, 4, 7));
    when(loc2.getCodeSnippet()).thenReturn("return;");
    when(loc2.getIdeFilePath()).thenReturn(ideFilePath);
    var locations2 = List.of(loc2);
    when(flow2.getLocations()).thenReturn(locations2);
    var flows = List.of(flow1, flow2);

    var textRangeDto = new TextRangeDto(1, 0, 1, 13);
    var showIssueParams = new ShowIssueParams(workspaceFolderPath.toUri().toString(), new IssueDetailsDto(textRangeDto, "rule:S1234",
      "issueKey", Path.of("myFile.py"), "this is wrong",
      "29.09.2023", "print('1234')", false, flows));

    var result = new ShowAllLocationsCommand.Param(showIssueParams, "connectionId", true);

    assertTrue(result.getCodeMatches());
    assertThat(result.getFileUri()).hasToString(workspaceFolderPath.toUri() + "myFile.py");
  }

  @Test
  void shouldBuildCommandParamsFromShowIssueParamsForFileLevelIssue() {
    var textRangeDto = new TextRangeDto(0, 0, 0, 0);
    var showIssueParams = new ShowIssueParams(workspaceFolderPath.toUri().toString(), new IssueDetailsDto(textRangeDto, "rule:S1234",
      "issueKey", Path.of("myFile.py"), "this is wrong",
      "29.09.2023", """
      print('1234')
      print('aa')
      print('b')
      print('kkkk')""", false, List.of()));

    var result = new ShowAllLocationsCommand.Param(showIssueParams, "connectionId", true);

    assertTrue(result.getCodeMatches());
  }

  @Test
  void shouldBuildCommandParamsFromShowIssueParamsForInvalidTextRange() {
    var textRangeDto = new TextRangeDto(-1, 0, -2, 0);
    var showIssueParams = new ShowIssueParams(workspaceFolderPath.toUri().toString(), new IssueDetailsDto(textRangeDto, "rule:S1234",
      "issueKey", Path.of("myFile.py"), "this is wrong",
      "29.09.2023", "print('1234')", false, List.of()));

    var result = new ShowAllLocationsCommand.Param(showIssueParams, "connectionId", true);

    assertFalse(result.getCodeMatches());
  }


}
