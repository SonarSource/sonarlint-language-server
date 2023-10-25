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
package org.sonarsource.sonarlint.ls.commands;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.clientapi.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.clientapi.common.FlowDto;
import org.sonarsource.sonarlint.core.clientapi.common.LocationDto;
import org.sonarsource.sonarlint.core.clientapi.common.TextRangeDto;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.ls.LocalCodeFile;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;

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
  ProjectBindingManager projectBindingManager = mock(ProjectBindingManager.class);
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
    when(issue.getTextRange()).thenReturn(new TextRange(1, 2, 3, 4));

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
    var showIssueParams = new ShowIssueParams(textRangeDto, "connectionId", "rule:S1234",
      "issueKey", "/src/java/main/myFile.py", "this is wrong",
      "29.09.2023", "print('1234')", false, List.of());

    when(projectBindingManager.serverPathToFileUri(showIssueParams.getServerRelativeFilePath())).thenReturn(Optional.of(fileInAWorkspaceFolderPath.toUri()));

    var result = new ShowAllLocationsCommand.Param(showIssueParams, projectBindingManager, "connectionId");

    assertTrue(result.getCodeMatches());
  }

  @Test
  void shouldBuildCommandParamsFromShowIssueParamsForFileLevelIssue() {
    var textRangeDto = new TextRangeDto(0, 0, 0, 0);
    var showIssueParams = new ShowIssueParams(textRangeDto, "connectionId", "rule:S1234",
      "issueKey", "/src/java/main/myFile.py", "this is wrong",
      "29.09.2023", """
      print('1234')
      print('aa')
      print('b')""", false, List.of());

    when(projectBindingManager.serverPathToFileUri(showIssueParams.getServerRelativeFilePath())).thenReturn(Optional.of(fileInAWorkspaceFolderPath.toUri()));

    var result = new ShowAllLocationsCommand.Param(showIssueParams, projectBindingManager, "connectionId");

    assertTrue(result.getCodeMatches());
  }

  @Test
  void shouldBuildCommandParamsFromShowIssueParamsForInvalidTextRange() {
    var textRangeDto = new TextRangeDto(-1, 0, -2, 0);
    var showIssueParams = new ShowIssueParams(textRangeDto, "connectionId", "rule:S1234",
      "issueKey", "/src/java/main/myFile.py", "this is wrong",
      "29.09.2023", "print('1234')", false, List.of());

    when(projectBindingManager.serverPathToFileUri(showIssueParams.getServerRelativeFilePath())).thenReturn(Optional.of(fileInAWorkspaceFolderPath.toUri()));

    var result = new ShowAllLocationsCommand.Param(showIssueParams, projectBindingManager, "connectionId");

    assertFalse(result.getCodeMatches());
  }

  @Test
  void shouldBuildCommandParamsFromShowIssueParamsWithFlows() {
    var textRangeDto1 = new TextRangeDto(1, 0, 1, 13);
    var textRangeDto2 = new TextRangeDto(2, 0, 2, 11);
    var textRangeDto3 = new TextRangeDto(3, 0, 3, 10);
    var location1 = new LocationDto(textRangeDto2, "nope", "/src/java/main/myFile.py", "print('b')");
    var location2 = new LocationDto(textRangeDto3, "nope", "/src/java/main/myFile.py", "print('b')");
    var flow = new FlowDto(List.of(location1, location2));

    var showIssueParams = new ShowIssueParams(textRangeDto1, "connectionId", "rule:S1234",
      "issueKey", "/src/java/main/myFile.py", "this is wrong", "29.09.2023",
      "print('1234')", false, List.of(flow));

    when(projectBindingManager.serverPathToFileUri(showIssueParams.getServerRelativeFilePath())).thenReturn(Optional.of(fileInAWorkspaceFolderPath.toUri()));

    var result = new ShowAllLocationsCommand.Param(showIssueParams, projectBindingManager, "connectionId");

    assertTrue(result.getCodeMatches());
    assertFalse(result.getFlows().get(0).getLocations().get(0).getCodeMatches());
    assertTrue(result.getFlows().get(0).getLocations().get(1).getCodeMatches());
  }

  @Test
  void pathResolverTest() {
    var issue = mock(ServerTaintIssue.class);
    when(issue.getFilePath()).thenReturn("filePath");
    var flow = mock(ServerTaintIssue.Flow.class);
    when(issue.getFlows()).thenReturn(List.of(flow));
    when(issue.getCreationDate()).thenReturn(Instant.EPOCH);
    when(issue.getSeverity()).thenReturn(IssueSeverity.BLOCKER);

    var locationFilePath = "locationFilePath";
    var location1 = mock(ServerTaintIssue.ServerIssueLocation.class);
    when(location1.getFilePath()).thenReturn(locationFilePath);
    var range1 = new TextRangeWithHash(0, 1, 1, 1, "82b3b93d07679915d2486e460668a907");
    when(location1.getTextRange()).thenReturn(range1);

    var location2 = mock(ServerTaintIssue.ServerIssueLocation.class);
    when(location2.getFilePath()).thenReturn(locationFilePath);
    var range2 = new TextRangeWithHash(2, 1, 2, 1, "658282ae00158c6a5fdd52f7f7b513b6");
    when(location2.getTextRange()).thenReturn(range2);

    when(flow.locations()).thenReturn(List.of(location1, location2));

    var mockCodeFile = mock(LocalCodeFile.class);
    when(mockCodeFile.codeAt(range1)).thenReturn("some code");
    when(mockCodeFile.codeAt(range2)).thenReturn(null);
    var cache = new HashMap<>(Map.of(Paths.get(locationFilePath).toUri(), mockCodeFile));

    var connectionId = "connectionId";

    var param = new ShowAllLocationsCommand.Param(issue, connectionId, ShowAllLocationsCommandTests::resolvePath, cache);

    assertThat(param.getConnectionId()).isEqualTo(connectionId);
    assertThat(param.getCreationDate()).isEqualTo("1970-01-01T00:00:00Z");
    assertThat(param.getFileUri().toString()).endsWith("filePath");
    var allLocations = param.getFlows().get(0).getLocations();
    var firstLocation = allLocations.get(0);
    assertThat(firstLocation.getUri().toString()).endsWith(locationFilePath);
    assertThat(firstLocation.getFilePath()).isEqualTo(locationFilePath);
    assertThat(firstLocation.getExists()).isTrue();
    assertThat(firstLocation.getCodeMatches()).isTrue();

    var secondLocation = allLocations.get(1);
    assertThat(secondLocation.getUri().toString()).endsWith(locationFilePath);
    assertThat(firstLocation.getFilePath()).isEqualTo(locationFilePath);
    assertThat(secondLocation.getExists()).isFalse();
    assertThat(secondLocation.getCodeMatches()).isFalse();
  }

  private static Optional<URI> resolvePath(String s) {
    try {
      return Optional.of(Paths.get(s).toUri());
    } catch (InvalidPathException e) {
      return Optional.empty();
    }
  }
}
