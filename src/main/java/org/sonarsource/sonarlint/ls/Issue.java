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
package org.sonarsource.sonarlint.ls;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

//todo check that all methods here are needed; remove unused
public interface Issue {

  UUID getIssueId();
  IssueSeverity getSeverity();

  RuleType getType();

  Optional<CleanCodeAttribute> getCleanCodeAttribute();

  Map<SoftwareQuality, ImpactSeverity> getImpacts();

  String getRuleKey();

  List<Flow> flows();

  List<QuickFix> quickFixes();

  Optional<String> getRuleDescriptionContextKey();

  Optional<VulnerabilityProbability> getVulnerabilityProbability();

  @CheckForNull
  ClientInputFile getInputFile();

  @CheckForNull
  String getMessage();

  @CheckForNull
  TextRangeDto getTextRange();

  RawIssue getRawIssue();

  @CheckForNull
  default Integer getStartLine() {
    TextRangeDto textRange = this.getTextRange();
    return textRange != null ? textRange.getStartLine() : null;
  }

  @CheckForNull
  default Integer getStartLineOffset() {
    TextRangeDto textRange = this.getTextRange();
    return textRange != null ? textRange.getStartLineOffset() : null;
  }

  @CheckForNull
  default Integer getEndLine() {
    TextRangeDto textRange = this.getTextRange();
    return textRange != null ? textRange.getEndLine() : null;
  }

  @CheckForNull
  default Integer getEndLineOffset() {
    TextRangeDto textRange = this.getTextRange();
    return textRange != null ? textRange.getEndLineOffset() : null;
  }
}
