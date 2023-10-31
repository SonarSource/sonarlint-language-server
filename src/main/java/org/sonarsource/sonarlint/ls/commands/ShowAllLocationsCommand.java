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

import java.net.URI;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.clientapi.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.clientapi.common.FlowDto;
import org.sonarsource.sonarlint.core.clientapi.common.LocationDto;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.ls.LocalCodeFile;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.util.Utils;

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
    private final TextRange textRange;
    private boolean codeMatches = false;

    private Param(Issue issue) {
      this.fileUri = nullableUri(issue.getInputFile());
      this.message = issue.getMessage();
      this.severity = issue.getSeverity().toString();
      this.ruleKey = issue.getRuleKey();
      this.flows = issue.flows().stream().map(Flow::new).toList();
      this.textRange = issue.getTextRange();
      this.connectionId = null;
      this.creationDate = null;
    }

    public Param(ShowIssueParams showIssueParams, ProjectBindingManager projectBindingManager, String connectionId) {
      this.fileUri = projectBindingManager.serverPathToFileUri(showIssueParams.getServerRelativeFilePath()).orElse(null);
      this.message = showIssueParams.getMessage();
      this.severity = "";
      this.ruleKey = showIssueParams.getRuleKey();
      this.flows = showIssueParams.getFlows().stream().map(flowDto -> new Flow(flowDto, projectBindingManager)).toList();
      this.textRange = new TextRange(showIssueParams.getTextRange().getStartLine(),
        showIssueParams.getTextRange().getStartLineOffset(),
        showIssueParams.getTextRange().getEndLine(),
        showIssueParams.getTextRange().getEndLineOffset());
      this.connectionId = connectionId;
      this.creationDate = showIssueParams.getCreationDate();
      if (this.fileUri != null) {
        try {
          String localCode;
          if (this.textRange.getStartLine() == 0 || this.textRange.getEndLine() == 0) {
            // this is a file-level issue
            localCode = LocalCodeFile.from(this.fileUri).content();
          } else {
            localCode = LocalCodeFile.from(this.fileUri).codeAt(this.textRange);
          }
          if (localCode == null) {
            this.codeMatches = false;
          } else {
            this.codeMatches = showIssueParams.getCodeSnippet().equals(localCode);
          }
        } catch (Exception e) {
          // not a valid range
          this.codeMatches = false;
        }
      }
    }

    Param(ServerTaintIssue issue, String connectionId, Function<String, Optional<URI>> pathResolver, Map<URI, LocalCodeFile> localFileCache) {
      this.fileUri = pathResolver.apply(issue.getFilePath()).orElse(null);
      this.message = issue.getMessage();
      this.severity = issue.getSeverity().toString();
      this.ruleKey = issue.getRuleKey();
      this.flows = issue.getFlows().stream().map(f -> new Flow(f, pathResolver, localFileCache)).toList();
      this.textRange = issue.getTextRange();
      this.connectionId = connectionId;
      this.creationDate = DateTimeFormatter.ISO_DATE_TIME.format(issue.getCreationDate().atOffset(ZoneOffset.UTC));
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

    public TextRange getTextRange() {
      return textRange;
    }

    public boolean getCodeMatches() {
      return codeMatches;
    }
  }

  static class Flow {
    private final List<Location> locations;

    private Flow(org.sonarsource.sonarlint.core.analysis.api.Flow flow) {
      this.locations = flow.locations().stream().map(Location::new).toList();
    }

    private Flow(FlowDto flow, ProjectBindingManager projectBindingManager) {
      this.locations = flow.getLocations().stream().map(locationDto -> new Location(locationDto, new HashMap<>(), projectBindingManager)).toList();
    }

    private Flow(ServerTaintIssue.Flow flow, Function<String, Optional<URI>> pathResolver, Map<URI, LocalCodeFile> localFileCache) {
      this.locations = flow.locations().stream().map(l -> new Location(l, pathResolver, localFileCache)).toList();
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
    private boolean exists = false;
    private boolean codeMatches = false;

    private Location(IssueLocation location) {
      this.textRange = location.getTextRange();
      this.uri = nullableUri(location.getInputFile());
      this.filePath = this.uri == null ? null : this.uri.getPath();
      this.message = location.getMessage();
      this.exists = true;
      this.codeMatches = true;
    }

    private Location(LocationDto location, Map<URI, LocalCodeFile> localCodeCache, ProjectBindingManager projectBindingManager) {
      this.textRange = new TextRange(location.getTextRange().getStartLine(),
        location.getTextRange().getStartLineOffset(),
        location.getTextRange().getEndLine(),
        location.getTextRange().getEndLineOffset());
      this.uri = projectBindingManager.serverPathToFileUri(location.getFilePath()).orElse(null);
      this.message = location.getMessage();
      this.filePath = location.getFilePath();
      String localCode = codeExists(localCodeCache);
      if (localCode != null) {
        this.exists = true;
        var locationTextRange = location.getTextRange();
        if (locationTextRange == null) {
          this.codeMatches = false;
        } else {
          this.codeMatches = location.getCodeSnippet().equals(localCode);
        }
      }
    }

    private String codeExists(Map<URI, LocalCodeFile> localCodeCache) {
      if (this.uri == null) {
        this.exists = false;
        this.codeMatches = false;
      } else {
        String localCode = localCodeCache.computeIfAbsent(this.uri, LocalCodeFile::from).codeAt(this.textRange);
        if (localCode == null) {
          this.exists = false;
          this.codeMatches = false;
        } else {
          return localCode;
        }
      }
      return null;
    }

    private Location(ServerTaintIssue.ServerIssueLocation location, Function<String, Optional<URI>> pathResolver, Map<URI, LocalCodeFile> localCodeCache) {
      this.textRange = location.getTextRange();
      this.uri = pathResolver.apply(location.getFilePath()).orElse(null);
      this.filePath = location.getFilePath();
      this.message = location.getMessage();
      String localCode = codeExists(localCodeCache);
      if (localCode != null) {
        this.exists = true;
        var locationTextRange = location.getTextRange();
        if (locationTextRange == null) {
          this.codeMatches = false;
        } else {
          var textRangeHash = locationTextRange.getHash();
          var localCodeHash = Utils.hash(localCode);
          this.codeMatches = textRangeHash.equals(localCodeHash);
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

  public static Param params(ServerTaintIssue issue, String connectionId, Function<String, Optional<URI>> pathResolver) {
    return new Param(issue, connectionId, pathResolver, new HashMap<>());
  }

  @CheckForNull
  private static URI nullableUri(@Nullable ClientInputFile inputFile) {
    return Optional.ofNullable(inputFile).map(ClientInputFile::uri).orElse(null);
  }
}
