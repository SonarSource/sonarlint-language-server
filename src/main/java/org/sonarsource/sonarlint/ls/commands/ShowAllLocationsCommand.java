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

import java.net.URI;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueLocationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.FlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.LocationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.ls.LocalCodeFile;
import org.sonarsource.sonarlint.ls.connected.DelegatingFinding;
import org.sonarsource.sonarlint.ls.domain.TaintIssue;
import org.sonarsource.sonarlint.ls.util.Utils;

import static org.sonarsource.sonarlint.ls.util.TextRangeUtils.textRangeWithHashDtoToTextRangeDto;
import static org.sonarsource.sonarlint.ls.util.URIUtils.getFullFileUriFromFragments;

public final class ShowAllLocationsCommand {

  public static final String ID = "SonarLint.ShowAllLocations";

  private ShowAllLocationsCommand() {
    // NOP
  }

  public static class Param {
    private final URI fileUri;
    private final String message;
    private final boolean shouldOpenRuleDescription;
    private final String severity;
    private final String ruleKey;
    private final List<Flow> flows;
    private final String connectionId;
    private final String creationDate;
    private final TextRangeDto textRange;
    private boolean codeMatches = false;


    private Param(DelegatingFinding issue) {
      this.fileUri = issue.getFileUri();
      this.message = issue.getMessage();
      var issueSeverity = issue.getSeverity();
      this.severity = issueSeverity != null ? issueSeverity.toString() : "";
      this.ruleKey = issue.getRuleKey();
      this.flows = issue.flows().stream().map(Flow::new).toList();
      this.textRange = issue.getTextRange();
      this.connectionId = null;
      this.creationDate = null;
      this.shouldOpenRuleDescription = true;
    }

    public Param(ShowIssueParams showIssueParams, @Nullable String connectionId, boolean shouldOpenRuleDescription) {
      this.fileUri = getFullFileUriFromFragments(showIssueParams.getConfigurationScopeId(), showIssueParams.getIssueDetails().getIdeFilePath());
      this.message = showIssueParams.getIssueDetails().getMessage();
      this.shouldOpenRuleDescription = shouldOpenRuleDescription;
      this.severity = "";
      this.ruleKey = showIssueParams.getIssueDetails().getRuleKey();
      this.flows = showIssueParams.getIssueDetails().getFlows().stream().map(f -> new Flow(f, showIssueParams.getConfigurationScopeId())).toList();
      this.textRange = new TextRangeDto(showIssueParams.getIssueDetails().getTextRange().getStartLine(),
        showIssueParams.getIssueDetails().getTextRange().getStartLineOffset(),
        showIssueParams.getIssueDetails().getTextRange().getEndLine(),
        showIssueParams.getIssueDetails().getTextRange().getEndLineOffset());
      this.connectionId = connectionId;
      this.creationDate = showIssueParams.getIssueDetails().getCreationDate();
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
          this.codeMatches = showIssueParams.getIssueDetails().getCodeSnippet().equals(localCode);
        }
      } catch (Exception e) {
        // not a valid range
        this.codeMatches = false;
      }
    }

    Param(TaintIssue taint, String connectionId, Map<URI, LocalCodeFile> localFileCache) {
      this.fileUri = getFullFileUriFromFragments(taint.getWorkspaceFolderUri(), taint.getIdeFilePath());
      this.message = taint.getMessage();
      this.severity = taint.getSeverityMode().isLeft() ? taint.getSeverityMode().getLeft().getSeverity().toString() : "";
      this.ruleKey = taint.getRuleKey();
      this.flows = taint.getFlows().stream().map(f -> new Flow(f, localFileCache, taint.getWorkspaceFolderUri())).toList();
      this.textRange = textRangeWithHashDtoToTextRangeDto(taint.getTextRange());
      this.connectionId = connectionId;
      this.creationDate = DateTimeFormatter.ISO_DATE_TIME.format(taint.getIntroductionDate().atOffset(ZoneOffset.UTC));
      this.shouldOpenRuleDescription = true;
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

    public TextRangeDto getTextRange() {
      return textRange;
    }

    public boolean getCodeMatches() {
      return codeMatches;
    }

    public boolean isShouldOpenRuleDescription() {
      return shouldOpenRuleDescription;
    }

  }

  static class Flow {
    private final List<Location> locations;

    private Flow(IssueFlowDto flow) {
      this.locations = flow.getLocations().stream().map(Location::new).toList();
    }

    private Flow(FlowDto flow, String workspaceFolderUri) {
      this.locations = flow.getLocations().stream().map(locationDto -> new Location(locationDto, new HashMap<>(), workspaceFolderUri)).toList();
    }

    private Flow(TaintVulnerabilityDto.FlowDto flow, Map<URI, LocalCodeFile> localFileCache, String workspaceFolderUri) {
      this.locations = flow.getLocations().stream().map(l -> new Location(l, localFileCache, workspaceFolderUri)).toList();
    }

    public List<Location> getLocations() {
      return locations;
    }
  }

  static class Location {
    private final TextRangeWithHashDto textRange;
    private URI uri;
    private final String filePath;
    private final String message;
    private boolean exists = false;
    private boolean codeMatches = false;

    private Location(IssueLocationDto location) {
      var locationTextRange = location.getTextRange();
      this.textRange = locationTextRange != null ? new TextRangeWithHashDto(locationTextRange.getStartLine(),
        locationTextRange.getStartLineOffset(),
        locationTextRange.getEndLine(),
        locationTextRange.getEndLineOffset(), "") : null;
      this.uri = location.getFileUri();
      this.filePath = this.uri == null ? null : this.uri.getPath();
      this.message = location.getMessage();
      this.exists = true;
      this.codeMatches = true;
    }

    private Location(LocationDto location, Map<URI, LocalCodeFile> localCodeCache, String workspaceFolderUri) {
      this.textRange = new TextRangeWithHashDto(location.getTextRange().getStartLine(),
        location.getTextRange().getStartLineOffset(),
        location.getTextRange().getEndLine(),
        location.getTextRange().getEndLineOffset(), Utils.hash(location.getCodeSnippet()));
      this.uri = getFullFileUriFromFragments(workspaceFolderUri, location.getIdeFilePath());
      this.message = location.getMessage();
      this.filePath = location.getIdeFilePath().toUri().toString();
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
      } else {
        String localCode = localCodeCache.computeIfAbsent(this.uri, LocalCodeFile::from).codeAt(this.textRange);
        if (localCode == null) {
          this.exists = false;
        } else {
          return localCode;
        }
      }
      return null;
    }

    private Location(TaintVulnerabilityDto.FlowDto.LocationDto location, Map<URI, LocalCodeFile> localCodeCache, String workspaceFolderUri) {
      this.textRange = location.getTextRange();
      var locationFilePath = location.getFilePath();
      if (locationFilePath != null) {
        this.uri = getFullFileUriFromFragments(workspaceFolderUri, locationFilePath);
        this.filePath = locationFilePath.toString();
      } else {
        this.filePath = "Could not locate file";
      }
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

    @CheckForNull
    public TextRangeWithHashDto getTextRange() {
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

    public boolean isCodeMatches() {
      return codeMatches;
    }
  }

  public static Param params(DelegatingFinding issue) {
    return new Param(issue);
  }

  public static Param params(TaintIssue issue, String connectionId) {
    return new Param(issue, connectionId, new HashMap<>());
  }

}
