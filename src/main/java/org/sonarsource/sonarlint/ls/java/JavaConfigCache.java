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
package org.sonarsource.sonarlint.ls.java;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.Utils;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionnedOpenFile;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

public class JavaConfigCache {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintExtendedLanguageClient client;
  private final OpenFilesCache openFilesCache;
  public final Map<URI, Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse>> javaConfigPerFileURI = new ConcurrentHashMap<>();

  public JavaConfigCache(SonarLintExtendedLanguageClient client, OpenFilesCache openFilesCache) {
    this.client = client;
    this.openFilesCache = openFilesCache;
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
    Optional<VersionnedOpenFile> openFile = openFilesCache.getFile(fileUri);
    if (openFile.isPresent() && !openFile.get().isJava()) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    if (javaConfigPerFileURI.containsKey(fileUri)) {
      return CompletableFuture.completedFuture(javaConfigPerFileURI.get(fileUri));
    }
    return client.getJavaConfig(fileUri.toString())
      .handle((r, t) -> {
        if (t != null) {
          LOG.error("Unable to fetch Java configuration of file " + fileUri, t);
        }
        return r;
      })
      .thenApply(javaConfig -> {
        var configOpt = ofNullable(javaConfig);
        javaConfigPerFileURI.put(fileUri, configOpt);
        LOG.debug("Cached Java config for file '{}'", fileUri);
        return configOpt;
      });
  }

  public void clear(URI projectUri) {
    for (var it = javaConfigPerFileURI.entrySet().iterator(); it.hasNext();) {
      var entry = it.next();
      var cachedResponseOpt = entry.getValue();
      // If we have cached an empty result, still clear the value on classpath update to force next analysis to re-attempt fetch
      if (cachedResponseOpt.isEmpty() || sameProject(projectUri, cachedResponseOpt.get())) {
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
