/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.analysis.api.TextEdit;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.ls.folders.InFolderClientInputFile;

// comment new
public class DelegatingIssue implements Issue {
  private final Issue issue;
  private final IssueSeverity severity;

  DelegatingIssue(Issue issue, @Nullable IssueSeverity userSeverity) {
    this.issue = issue;
    this.severity = userSeverity != null ? userSeverity : issue.getSeverity();
  }

  @Override
  public IssueSeverity getSeverity() {
    return severity;
  }

  @CheckForNull
  @Override
  public RuleType getType() {
    return issue.getType();
  }

  @CheckForNull
  @Override
  public String getMessage() {
    return issue.getMessage();
  }

  @Override
  public String getRuleKey() {
    return issue.getRuleKey();
  }

  @CheckForNull
  @Override
  public Integer getStartLine() {
    return issue.getStartLine();
  }

  @CheckForNull
  @Override
  public Integer getStartLineOffset() {
    return issue.getStartLineOffset();
  }

  @CheckForNull
  @Override
  public Integer getEndLine() {
    return issue.getEndLine();
  }

  @CheckForNull
  @Override
  public Integer getEndLineOffset() {
    return issue.getEndLineOffset();
  }

  @Override
  public List<Flow> flows() {
    return issue.flows();
  }

  @CheckForNull
  @Override
  public ClientInputFile getInputFile() {
    return issue.getInputFile();
  }

  @CheckForNull
  @Override
  public TextRange getTextRange() {
    return issue.getTextRange();
  }

  @Override
  public List<QuickFix> quickFixes() {
    return List.of(getMultiFileQuickFixForFile(issue.getInputFile()));
  }

  @Override
  public Optional<String> getRuleDescriptionContextKey() {
    return issue.getRuleDescriptionContextKey();
  }


  private QuickFix getMultiFileQuickFixForFile(ClientInputFile file) {
    var edits = new ArrayList<TextEdit>();
    edits.add(new TextEdit(new TextRange(1, 0, 2, 0), "// Multi file quickfix text"));
    var fileEdits = new ArrayList<ClientInputFileEdit>();
    var fileEdit = new ClientInputFileEdit(file, edits);
    fileEdits.add(fileEdit);
    fileEdits.addAll(getOtherFileEdit());

    return new QuickFix(fileEdits, "Dummy multi file quickfix");
  }

  private Collection<ClientInputFileEdit> getOtherFileEdit() {
    var fileEdits = new ArrayList<ClientInputFileEdit>();
    fileEdits.add(getFileEditForFile());
    return fileEdits;
  }

  private ClientInputFileEdit getFileEditForFile() {
    var fullFile = "file:///c:/Users/Knize/IntellijProjects/sonarlint-language-server/src/main/java/org/sonarsource/sonarlint/ls/connected/DelegatingIssue.java";
    var fileUri = URI.create(fullFile);
    var baseProjectFolder = "file:///c:/Users/Knize/IntellijProjects/sonarlint-language-server";
    var folderUri = URI.create(baseProjectFolder);
    var folderPath = Paths.get(folderUri);
    var clientInputFile = new InFolderClientInputFile(
      URI.create(fullFile),
      folderPath.relativize(Paths.get(fileUri)).toString(), false);
    var edits = new ArrayList<TextEdit>();
    edits.add(new TextEdit(new TextRange(1, 0, 2, 0), "// Multi file quickfix text\n"));
    return new ClientInputFileEdit(clientInputFile, edits);
  }
}
