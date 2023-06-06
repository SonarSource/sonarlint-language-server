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
package org.sonarsource.sonarlint.ls.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

public class Utils {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final Pattern MATCH_ALL_WHITESPACES = Pattern.compile("\\s");
  private static final String MESSAGE_WITH_PLURALIZED_SUFFIX = "%s [+%d %s]";
  private static final String FILE_SCHEME = "file";


  private Utils() {
  }

  // See the changelog for any evolutions on how properties are parsed:
  // https://github.com/eclipse/lsp4j/blob/master/CHANGELOG.md
  // (currently JsonElement, used to be Map<String, Object>)
  @CheckForNull
  public static Map<String, Object> parseToMap(Object obj) {
    try {
      return new Gson().fromJson((JsonElement) obj, Map.class);
    } catch (JsonSyntaxException e) {
      throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Expected a JSON map but was: " + obj, e));
    }
  }

  public static ThreadFactory threadFactory(String name, boolean daemon) {
    return runnable -> {
      var result = new Thread(runnable, name);
      result.setDaemon(daemon);
      return result;
    };
  }

  public static void interrupted(InterruptedException e) {
    LOG.debug("Interrupted!", e);
    Thread.currentThread().interrupt();
  }

  public static void shutdownAndAwait(ExecutorService executor, boolean stopActiveTasks) {
    if (stopActiveTasks) {
      executor.shutdownNow();
    } else {
      executor.shutdown();
    }
    try {
      executor.awaitTermination(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

  }

  public static String pluralize(long nbItems, String itemName) {
    return pluralize(nbItems, itemName, itemName + "s");
  }

  public static String pluralize(long nbItems, String singular, String plural) {
    return nbItems == 1 ? singular : plural;
  }

  public static Range convert(Issue issue) {
    if (issue.getStartLine() == null) {
      return new Range(new Position(0, 0), new Position(0, 0));
    }
    return new Range(
      new Position(
        issue.getStartLine() - 1,
        issue.getStartLineOffset()),
      new Position(
        issue.getEndLine() - 1,
        issue.getEndLineOffset()));
  }


  public static Range convert(ServerTaintIssue issue) {
    return convert(issue.getTextRange());
  }

  public static Range convert(ServerTaintIssue.ServerIssueLocation issue) {
    return convert(issue.getTextRange());
  }

  public static Range convert(@Nullable TextRangeWithHash textRange) {
    if (textRange == null) {
      return new Range(new Position(0, 0), new Position(0, 0));
    }
    return new Range(
      new Position(
        textRange.getStartLine() - 1,
        textRange.getStartLineOffset()),
      new Position(
        textRange.getEndLine() - 1,
        textRange.getEndLineOffset()));
  }

  public static boolean locationMatches(ServerTaintIssue i, Diagnostic d) {
    return convert(i).equals(d.getRange());
  }

  public static DiagnosticSeverity severity(IssueSeverity severity) {
    switch (severity) {
      case BLOCKER:
      case CRITICAL:
      case MAJOR:
        return DiagnosticSeverity.Warning;
      case MINOR:
        return DiagnosticSeverity.Information;
      case INFO:
      default:
        return DiagnosticSeverity.Hint;
    }
  }

  public static DiagnosticSeverity hotspotSeverity(VulnerabilityProbability vulnerabilityProbability) {
    switch (vulnerabilityProbability) {
      case HIGH:
        return DiagnosticSeverity.Error;
      case LOW:
        return DiagnosticSeverity.Information;
      case MEDIUM:
      default:
        return DiagnosticSeverity.Warning;
    }
  }

  public static String buildMessageWithPluralizedSuffix(@Nullable String issueMessage, long nbItems, String itemName) {
    return String.format(MESSAGE_WITH_PLURALIZED_SUFFIX, issueMessage, nbItems, pluralize(nbItems, itemName));
  }

  public static boolean uriHasFileScheme(URI uri) {
    return uri.getScheme().equalsIgnoreCase(FILE_SCHEME);
  }

  public static String hash(String codeSnippet) {
    String codeSnippetWithoutWhitespaces = MATCH_ALL_WHITESPACES.matcher(codeSnippet).replaceAll("");
    return DigestUtils.md5Hex(codeSnippetWithoutWhitespaces);
  }

  public static TextRangeWithHash textRangeWithHashFromTextRange(TaintVulnerabilityRaisedEvent.Location.TextRange textRange) {
    return new TextRangeWithHash(textRange.getStartLine(),
      textRange.getStartLineOffset(),
      textRange.getEndLine(),
      textRange.getEndLineOffset(),
      textRange.getHash());
  }
}
