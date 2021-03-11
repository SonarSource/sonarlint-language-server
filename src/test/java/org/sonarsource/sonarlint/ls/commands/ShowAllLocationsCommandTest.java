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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssueLocation;

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
  }

  @Test
  void pathResolverTest() {
    ServerIssue issue = mock(ServerIssue.class);
    when(issue.getFilePath()).thenReturn("filePath");
    ServerIssue.Flow flow = mock(ServerIssue.Flow.class);
    when(issue.getFlows()).thenReturn(Collections.singletonList(flow));
    ServerIssueLocation location = mock(ServerIssueLocation.class);
    when(location.getFilePath()).thenReturn("locationFilePath");
    when(flow.locations()).thenReturn(Collections.singletonList(location));

    ShowAllLocationsCommand.Param param = new ShowAllLocationsCommand.Param(issue, (s) -> {
      try {
        return Optional.of(Paths.get(s).toUri());
      } catch (InvalidPathException e) {
        e.printStackTrace();
        return Optional.empty();
      }
    }, new HashMap<>());

    assertThat(param.getFileUri().toString()).endsWith("filePath");
    assertThat(param.getFlows().get(0).getLocations().get(0).getUri().toString()).endsWith("locationFilePath");
  }

}
