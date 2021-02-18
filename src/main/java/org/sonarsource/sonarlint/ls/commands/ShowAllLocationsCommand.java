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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;

public final class ShowAllLocationsCommand {

  public static final String ID = "SonarLint.ShowAllLocations";

  private ShowAllLocationsCommand() {
    // NOP
  }

  static class Param {
    private final URI fileUri;
    private final String message;
    private final String severity;
    private final String ruleKey;
    private final List<Flow> flows;

    private Param(Issue issue) {
      this.fileUri = nullableUri(issue.getInputFile());
      this.message = issue.getMessage();
      this.severity = issue.getSeverity();
      this.ruleKey = issue.getRuleKey();
      this.flows = issue.flows().stream().map(Flow::new).collect(Collectors.toList());
    }

    public URI getFileUri() {
      return fileUri;
    }

    public String getMessage() {
      return message;
    }

    public String getSeverity() {
      return severity;
    }

    public String getRuleKey() {
      return ruleKey;
    }

    public List<Flow> getFlows() {
      return flows;
    }
  }

  static class Flow {
    private final List<Location> locations;

    private Flow(Issue.Flow flow) {
      this.locations = flow.locations().stream().map(Location::new).collect(Collectors.toList());
    }

    public List<Location> getLocations() {
      return locations;
    }
  }

  static class Location {
    private final TextRange textRange;
    private final URI uri;
    private final String message;

    private Location(IssueLocation location) {
      this.textRange = location.getTextRange();
      this.uri = nullableUri(location.getInputFile());
      this.message = location.getMessage();
    }

    public TextRange getTextRange() {
      return textRange;
    }

    public URI getUri() {
      return uri;
    }

    public String getMessage() {
      return message;
    }
  }

  public static Param params(Issue issue) {
    return new Param(issue);
  }

  @CheckForNull
  private static URI nullableUri(@Nullable ClientInputFile inputFile) {
    return Optional.ofNullable(inputFile).map(ClientInputFile::uri).orElse(null);
  }
}
