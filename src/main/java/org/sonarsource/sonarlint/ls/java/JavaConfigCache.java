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
package org.sonarsource.sonarlint.ls.java;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.Utils;
import org.sonarsource.sonarlint.ls.file.FileLanguageCache;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

public class JavaConfigCache {
  private static final Logger LOG = Loggers.get(JavaConfigCache.class);
  private final SonarLintExtendedLanguageClient client;
  private final FileLanguageCache fileLanguageCache;
  public final Map<URI, Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse>> javaConfigPerFileURI = new ConcurrentHashMap<>();

  public JavaConfigCache(SonarLintExtendedLanguageClient client, FileLanguageCache fileLanguageCache) {
    this.client = client;
    this.fileLanguageCache = fileLanguageCache;
  }

  public Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse> get(URI fileUri) {
    return Optional.ofNullable(javaConfigPerFileURI.get(fileUri)).orElse(Optional.empty());
  }

  public void remove(URI fileUri) {
    javaConfigPerFileURI.remove(fileUri);
  }

  public void clear() {
    javaConfigPerFileURI.clear();
  }

  public Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse> getOrFetch(URI fileUri) {
    Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse> javaConfigOpt;
    try {
      javaConfigOpt = getOrFetchAsync(fileUri).get(1, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      Utils.interrupted(e);
      javaConfigOpt = empty();
    } catch (Exception e) {
      LOG.warn("Unable to get Java config", e);
      javaConfigOpt = empty();
    }
    return javaConfigOpt;
  }

  /**
   * Try to fetch Java config. In case of any error, cache an empty result to avoid repeated calls.
   */
  private CompletableFuture<Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse>> getOrFetchAsync(URI fileUri) {
    if (!fileLanguageCache.isJava(fileUri)) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse> javaConfigFromCache = javaConfigPerFileURI.get(fileUri);
    if (javaConfigFromCache != null) {
      return CompletableFuture.completedFuture(javaConfigFromCache);
    }
    return client.getJavaConfig(fileUri.toString())
      .handle((r, t) -> {
        if (t != null) {
          LOG.error("Unable to fetch Java configuration of file " + fileUri, t);
        }
        return r;
      })
      .thenApply(javaConfig -> {
        Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse> configOpt = ofNullable(javaConfig);
        javaConfigPerFileURI.put(fileUri, configOpt);
        LOG.debug("Cached Java config for file '{}'", fileUri);
        return configOpt;
      });
  }

  public void clear(URI projectUri) {
    for (Iterator<Map.Entry<URI, Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse>>> it = javaConfigPerFileURI.entrySet().iterator(); it.hasNext();) {
      Map.Entry<URI, Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse>> entry = it.next();
      Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse> cachedResponseOpt = entry.getValue();
      // If we have cached an empty result, still clear the value on classpath update to force next analysis to re-attempt fetch
      if (!cachedResponseOpt.isPresent() || sameProject(projectUri, cachedResponseOpt.get())) {
        it.remove();
        LOG.debug("Evicted Java config cache for file '{}'", entry.getKey());
      }
    }
  }

  private static boolean sameProject(URI projectUri, SonarLintExtendedLanguageClient.GetJavaConfigResponse cachedResponse) {
    // Compare file and not directly URI because
    // file:/foo/bar and file:///foo/bar/ are not considered equals by java.net.URI
    return Paths.get(URI.create(cachedResponse.getProjectRoot())).equals(Paths.get(projectUri));
  }
}
