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

import com.google.common.annotations.VisibleForTesting;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssueLocation;
import org.sonarsource.sonarlint.ls.LocalCodeFile;

public final class ShowAllLocationsCommand {

  public static final String ID = "SonarLint.ShowAllLocations";

  private ShowAllLocationsCommand() {
    // NOP
  }

  public static class Param {
    private final URI fileUri;
    private final String message;
    private final String severity;
    private final String ruleKey;
    private final List<Flow> flows;
    private final String connectionId;
    private final String creationDate;

    private Param(Issue issue) {
      this.fileUri = nullableUri(issue.getInputFile());
      this.message = issue.getMessage();
      this.severity = issue.getSeverity();
      this.ruleKey = issue.getRuleKey();
      this.flows = issue.flows().stream().map(Flow::new).collect(Collectors.toList());
      this.connectionId = null;
      this.creationDate = null;
    }

    @VisibleForTesting
    Param(ServerIssue issue, String connectionId, Function<String, Optional<URI>> pathResolver, Map<URI, LocalCodeFile> localFileCache) {
      this.fileUri = pathResolver.apply(issue.getFilePath()).orElse(null);
      this.message = issue.getMessage();
      this.severity = issue.severity();
      this.ruleKey = issue.ruleKey();
      this.flows = issue.getFlows().stream().map(f -> new Flow(f, pathResolver, localFileCache)).collect(Collectors.toList());
      this.connectionId = connectionId;
      this.creationDate = DateTimeFormatter.ISO_DATE_TIME.format(issue.creationDate().atOffset(ZoneOffset.UTC));
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

    @CheckForNull
    public String getConnectionId() {
      return connectionId;
    }

    @CheckForNull
    public String getCreationDate() {
      return creationDate;
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

    private Flow(ServerIssue.Flow flow, Function<String, Optional<URI>> pathResolver, Map<URI, LocalCodeFile> localFileCache) {
      this.locations = flow.locations().stream().map(l -> new Location(l, pathResolver, localFileCache)).collect(Collectors.toList());
    }

    public List<Location> getLocations() {
      return locations;
    }
  }

  static class Location {
    private final TextRange textRange;
    private final URI uri;
    private final String filePath;
    private final String message;
    private final boolean exists;
    private final boolean codeMatches;

    private Location(IssueLocation location) {
      this.textRange = location.getTextRange();
      this.uri = nullableUri(location.getInputFile());
      this.filePath = this.uri == null ? null : this.uri.getPath();
      this.message = location.getMessage();
      this.exists = true;
      this.codeMatches = true;
    }

    private Location(ServerIssueLocation location, Function<String, Optional<URI>> pathResolver, Map<URI, LocalCodeFile> localCodeCache) {
      this.textRange = location.getTextRange();
      this.uri = pathResolver.apply(location.getFilePath()).orElse(null);
      this.filePath = location.getFilePath();
      this.message = location.getMessage();
      if (this.uri == null) {
        this.exists = false;
        this.codeMatches = false;
      } else {
        String localCode = localCodeCache.computeIfAbsent(this.uri, LocalCodeFile::from).codeAt(this.textRange);
        if (localCode == null) {
          this.exists = false;
          this.codeMatches = false;
        } else {
          this.exists = true;
          String remoteSnippet = location.getCodeSnippet();
          this.codeMatches = remoteSnippet != null && remoteSnippet.equals(localCode);
        }
      }
    }

    public TextRange getTextRange() {
      return textRange;
    }

    public URI getUri() {
      return uri;
    }

    public String getFilePath() {
      return filePath;
    }

    public String getMessage() {
      return message;
    }

    public boolean getExists() {
      return exists;
    }

    public boolean getCodeMatches() {
      return codeMatches;
    }
  }

  public static Param params(Issue issue) {
    return new Param(issue);
  }

  public static Param params(ServerIssue issue, String connectionId, Function<String, Optional<URI>> pathResolver) {
    return new Param(issue, connectionId, pathResolver, new HashMap<>());
  }

  @CheckForNull
  private static URI nullableUri(@Nullable ClientInputFile inputFile) {
    return Optional.ofNullable(inputFile).map(ClientInputFile::uri).orElse(null);
  }
}
