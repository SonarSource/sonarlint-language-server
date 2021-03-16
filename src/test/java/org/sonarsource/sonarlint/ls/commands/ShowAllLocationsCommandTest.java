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
package org.sonarsource.sonarlint.ls.commands;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssueLocation;
import org.sonarsource.sonarlint.ls.LocalCodeFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShowAllLocationsCommandTest {

  @Test
  void shouldBuildCommandParamsFromIssue() {
    Issue issue = mock(Issue.class);
    ClientInputFile file = mock(ClientInputFile.class);
    when(issue.getInputFile()).thenReturn(file);
    URI fileUri = URI.create("file:///tmp/plop");
    when(file.uri()).thenReturn(fileUri);
    when(issue.getMessage()).thenReturn("message");
    when(issue.getSeverity()).thenReturn("severity");
    when(issue.getRuleKey()).thenReturn("ruleKey");

    Issue.Flow flow1 = mock(Issue.Flow.class);
    IssueLocation loc11 = mock(IssueLocation.class);
    when(loc11.getTextRange()).thenReturn(new TextRange(0, 0, 0, 7));
    IssueLocation loc12 = mock(IssueLocation.class);
    when(loc12.getTextRange()).thenReturn(new TextRange(2, 2, 2, 9));
    List<IssueLocation> locations1 = Arrays.asList(loc11, loc12);
    when(flow1.locations()).thenReturn(locations1);
    Issue.Flow flow2 = mock(Issue.Flow.class);
    IssueLocation loc2 = mock(IssueLocation.class);
    when(loc2.getTextRange()).thenReturn(new TextRange(4, 0, 4, 7));
    List<IssueLocation> locations2 = Collections.singletonList(loc2);
    when(flow2.locations()).thenReturn(locations2);
    List<Issue.Flow> flows = Arrays.asList(flow1, flow2);
    when(issue.flows()).thenReturn(flows);

    ShowAllLocationsCommand.Param params = ShowAllLocationsCommand.params(issue);
    assertThat(params).extracting(
      "fileUri",
      "message",
      "severity",
      "ruleKey"
      ).containsExactly(
        fileUri,
        "message",
        "severity",
        "ruleKey"
      );
    assertThat(params.getFlows()).hasSize(2);
    assertThat(params.getFlows().get(0).getLocations()).hasSize(2);
    assertThat(params.getFlows().get(1).getLocations()).hasSize(1);
    assertThat(params.getConnectionId()).isNull();
    assertThat(params.getCreationDate()).isNull();
  }

  @Test
  void pathResolverTest() {
    ServerIssue issue = mock(ServerIssue.class);
    when(issue.getFilePath()).thenReturn("filePath");
    ServerIssue.Flow flow = mock(ServerIssue.Flow.class);
    when(issue.getFlows()).thenReturn(Collections.singletonList(flow));
    when(issue.creationDate()).thenReturn(Instant.EPOCH);

    String locationFilePath = "locationFilePath";

    ServerIssueLocation location1 = mock(ServerIssueLocation.class);
    when(location1.getFilePath()).thenReturn(locationFilePath);
    TextRange range1 = new TextRange(0, 1, 1, 1);
    when(location1.getTextRange()).thenReturn(range1);
    when(location1.getCodeSnippet()).thenReturn("some code");

    ServerIssueLocation location2 = mock(ServerIssueLocation.class);
    when(location2.getFilePath()).thenReturn(locationFilePath);
    TextRange range2 = new TextRange(2, 1, 2, 1);
    when(location2.getTextRange()).thenReturn(range2);
    when(location2.getCodeSnippet()).thenReturn("other code");

    when(flow.locations()).thenReturn(Arrays.asList(location1, location2));

    LocalCodeFile mockCodeFile = mock(LocalCodeFile.class);
    when(mockCodeFile.codeAt(range1)).thenReturn("some code");
    when(mockCodeFile.codeAt(range2)).thenReturn(null);
    Map<URI, LocalCodeFile> cache = new HashMap<>();
    cache.put(Paths.get(locationFilePath).toUri(), mockCodeFile);

    String connectionId = "connectionId";

    ShowAllLocationsCommand.Param param = new ShowAllLocationsCommand.Param(issue, connectionId, ShowAllLocationsCommandTest::resolvePath, cache);

    assertThat(param.getConnectionId()).isEqualTo(connectionId);
    assertThat(param.getCreationDate()).isEqualTo("1970-01-01T00:00:00Z");
    assertThat(param.getFileUri().toString()).endsWith("filePath");
    List<ShowAllLocationsCommand.Location> allLocations = param.getFlows().get(0).getLocations();
    ShowAllLocationsCommand.Location firstLocation = allLocations.get(0);
    assertThat(firstLocation.getUri().toString()).endsWith(locationFilePath);
    assertThat(firstLocation.getFilePath()).isEqualTo(locationFilePath);
    assertThat(firstLocation.getExists()).isTrue();
    assertThat(firstLocation.getCodeMatches()).isTrue();

    ShowAllLocationsCommand.Location secondLocation = allLocations.get(1);
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
