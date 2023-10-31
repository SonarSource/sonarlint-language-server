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
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.clientapi.common.TokenDto;
import org.sonarsource.sonarlint.core.clientapi.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.ls.IssuesCache;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer;
import org.sonarsource.sonarlint.ls.connected.DelegatingIssue;

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

  @NotNull
  public static ValidateConnectionParams getValidateConnectionParamsForNewConnection(SonarLintExtendedLanguageServer.ConnectionCheckParams params) {
    Either<TokenDto, UsernamePasswordDto> credentials = Either.forLeft(new TokenDto(params.getToken()));
    return params.getOrganization() != null ? new ValidateConnectionParams(
      new TransientSonarCloudConnectionDto(params.getOrganization(), credentials)
    ) : new ValidateConnectionParams(new TransientSonarQubeConnectionDto(params.getServerUrl(), credentials));
  }

  @NotNull
  public static String getConnectionNameFromConnectionCheckParams(SonarLintExtendedLanguageServer.ConnectionCheckParams params) {
    var connectionName = params.getServerUrl() == null ? params.getOrganization() : params.getServerUrl();
    return params.getConnectionId() == null ? connectionName : params.getConnectionId();
  }

  public static HotspotStatus hotspotStatusOfTitle(String title) {
    return Arrays.stream(HotspotStatus.values()).filter(hotspotStatus -> hotspotStatus.getTitle().equals(title)).findFirst()
      .orElseThrow(() -> new IllegalArgumentException("There is no such hotspot status: " + title));
  }

  public static HotspotStatus hotspotStatusValueOfHotspotReviewStatus(HotspotReviewStatus reviewStatus) {
    return HotspotStatus.valueOf(reviewStatus.name());
  }

  public static HotspotReviewStatus hotspotReviewStatusValueOfHotspotStatus(HotspotStatus status) {
    return HotspotReviewStatus.valueOf(status.name());
  }

  public static String formatSha256Fingerprint(String decodedFingerprint) {
    var split = toUpperCaseAndSplitInPairs(decodedFingerprint);
    var sb = new StringBuilder();
    for (var i = 0; i < split.length; i++) {
      sb.append(split[i]);
      if (i == split.length / 2 - 1) {
        sb.append("\n");
      } else if (i < split.length - 1) {
        sb.append(" ");
      }
    }
    return sb.toString();
  }

  public static String formatSha1Fingerprint(String decodedFingerprint) {
    var split = toUpperCaseAndSplitInPairs(decodedFingerprint);
    return String.join(" ", split);
  }

  private static String[] toUpperCaseAndSplitInPairs(String str) {
    return str.toUpperCase(Locale.ROOT).split("(?<=\\G.{2})");
  }

  public static <T> Optional<T> safelyGetCompletableFuture(CompletableFuture<T> future) {
    try {
      return Optional.of(future.get());
    } catch (InterruptedException e) {
      interrupted(e);
    } catch (ExecutionException e) {
      LOG.warn("Future computation completed with an exception", e);
    }
    return Optional.empty();
  }

  public static boolean isDelegatingIssueWithServerIssueKey(String serverIssueKey, Map.Entry<String, IssuesCache.VersionedIssue> issueEntry) {
    return issueEntry.getValue().issue() instanceof DelegatingIssue delegatingIssue
      && (serverIssueKey.equals(delegatingIssue.getServerIssueKey()));
  }
}
