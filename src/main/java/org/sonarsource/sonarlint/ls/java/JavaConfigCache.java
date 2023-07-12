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
package org.sonarsource.sonarlint.ls.java;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.GetJavaConfigResponse;
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageServer.ServerMode;
import org.sonarsource.sonarlint.ls.file.OpenFilesCache;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

public class JavaConfigCache {
  private final SonarLintExtendedLanguageClient client;
  private final OpenFilesCache openFilesCache;
  private final LanguageClientLogger lsLogOutput;
  private final Map<URI, Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse>> javaConfigPerFileURI = new ConcurrentHashMap<>();
  private final Map<Path, List<Path>> jvmClasspathPerJavaHome = new ConcurrentHashMap<>();

  public JavaConfigCache(SonarLintExtendedLanguageClient client, OpenFilesCache openFilesCache, LanguageClientLogger lsLogOutput) {
    this.client = client;
    this.openFilesCache = openFilesCache;
    this.lsLogOutput = lsLogOutput;
  }

  public Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse> getOrFetch(URI fileUri) {
    Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse> javaConfigOpt;
    try {
      javaConfigOpt = getOrFetchAsync(fileUri).get(1, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      Utils.interrupted(e);
      javaConfigOpt = empty();
    } catch (Exception e) {
      lsLogOutput.error("Unable to get Java config", e);
      javaConfigOpt = empty();
    }
    return javaConfigOpt;
  }

  /**
   * Try to fetch Java config. In case of any error, cache an empty result to avoid repeated calls.
   */
  private CompletableFuture<Optional<SonarLintExtendedLanguageClient.GetJavaConfigResponse>> getOrFetchAsync(URI fileUri) {
    Optional<VersionedOpenFile> openFile = openFilesCache.getFile(fileUri);
    if (openFile.isPresent() && !openFile.get().isJava()) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    if (javaConfigPerFileURI.containsKey(fileUri)) {
      return CompletableFuture.completedFuture(javaConfigPerFileURI.get(fileUri));
    }
    return client.getJavaConfig(fileUri.toString())
      .handle((r, t) -> {
        if (t != null) {
          lsLogOutput.error("Unable to fetch Java configuration of file " + fileUri, t);
        }
        return r;
      })
      .thenApply(javaConfig -> {
        var configOpt = ofNullable(javaConfig);
        javaConfigPerFileURI.put(fileUri, configOpt);
        openFile.map(VersionedOpenFile::isJava)
          .filter(Boolean::booleanValue)
          .ifPresent(isJava -> lsLogOutput.debug("Cached Java config for file \"" + fileUri + "\""));
        return configOpt;
      });
  }

  public Map<String, String> configureJavaProperties(Set<URI> fileInTheSameModule, Map<URI, GetJavaConfigResponse> javaConfigs) {
    var partitionMainTest = fileInTheSameModule.stream().filter(javaConfigs::containsKey).collect(groupingBy(f -> javaConfigs.get(f).isTest()));
    var mainFiles = ofNullable(partitionMainTest.get(false)).orElse(List.of());
    var testFiles = ofNullable(partitionMainTest.get(true)).orElse(List.of());

    if (mainFiles.isEmpty() && testFiles.isEmpty()) {
      return Map.of();
    }

    Map<String, String> props = new HashMap<>();

    // Assume all files in the same module have the same vmLocation
    var commonConfig = javaConfigs.get(javaConfigs.keySet().iterator().next());
    var vmLocationStr = commonConfig.getVmLocation();
    List<Path> jdkClassesRoots = new ArrayList<>();
    if (vmLocationStr != null) {
      var vmLocation = Paths.get(vmLocationStr);
      jdkClassesRoots = getVmClasspathFromCacheOrCompute(vmLocation);
      props.put("sonar.java.jdkHome", vmLocationStr);
    }

    // Assume all main files have the same classpath
    if (!mainFiles.isEmpty()) {
      var mainConfig = javaConfigs.get(mainFiles.get(0));
      var classpath = computeClasspathSkipNonExisting(jdkClassesRoots, mainConfig);
      props.put("sonar.java.libraries", classpath);
    }

    // Assume all test files have the same classpath
    if (!testFiles.isEmpty()) {
      var testConfig = javaConfigs.get(testFiles.get(0));
      var classpath = computeClasspathSkipNonExisting(jdkClassesRoots, testConfig);
      props.put("sonar.java.test.libraries", classpath);
    }

    return props;
  }

  private String computeClasspathSkipNonExisting(List<Path> jdkClassesRoots, GetJavaConfigResponse testConfig) {
    return Stream.concat(
        jdkClassesRoots.stream().map(Path::toAbsolutePath).map(Path::toString),
        Stream.of(testConfig.getClasspath()))
      .filter(path -> {
        boolean exists = new File(path).exists();
        if (!exists) {
          lsLogOutput.debug(format("Classpath \"%s\" from configuration does not exist, skipped", path));
        }
        return exists;
      })
      .collect(joining(","));
  }

  private List<Path> getVmClasspathFromCacheOrCompute(Path vmLocation) {
    return jvmClasspathPerJavaHome.computeIfAbsent(vmLocation, JavaSdkUtil::getJdkClassesRoots);
  }

  public void didClasspathUpdate(URI projectUri) {
    // Clear cached value to force refetch during next analysis
    for (var it = javaConfigPerFileURI.entrySet().iterator(); it.hasNext(); ) {
      var entry = it.next();
      var cachedResponseOpt = entry.getValue();
      // If we have cached an empty result, still clear the value on classpath update to force next analysis to re-attempt fetch
      if (cachedResponseOpt.isEmpty() || sameProject(projectUri, cachedResponseOpt.get())) {
        it.remove();
        lsLogOutput.debug("Evicted Java config cache for file \"" + entry.getKey() + "\"");
      }
    }
  }

  private static boolean sameProject(URI projectUri, SonarLintExtendedLanguageClient.GetJavaConfigResponse cachedResponse) {
    // Compare file and not directly URI because
    // file:/foo/bar and file:///foo/bar/ are not considered equals by java.net.URI
    return Paths.get(URI.create(cachedResponse.getProjectRoot())).equals(Paths.get(projectUri));
  }

  public void didServerModeChange(ServerMode serverModeEnum) {
    lsLogOutput.debug("Clearing Java config cache on server mode change");
    javaConfigPerFileURI.clear();
  }

  public void didClose(URI fileUri) {
    javaConfigPerFileURI.remove(fileUri);
  }
}
