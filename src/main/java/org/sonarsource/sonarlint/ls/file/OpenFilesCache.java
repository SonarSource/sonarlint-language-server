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
package org.sonarsource.sonarlint.ls.file;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static java.lang.String.format;

/**
 * Keep track of files opened in the editor, with associated metadata.
 */
public class OpenFilesCache {
  private final LanguageClientLogger lsLogOutput;

  private final Map<URI, VersionedOpenFile> openFilesPerFileURI = new ConcurrentHashMap<>();

  public OpenFilesCache(LanguageClientLogger lsLogOutput) {
    this.lsLogOutput = lsLogOutput;
  }

  public VersionedOpenFile didOpen(URI fileUri, String languageId, String fileContent, int version) {
    var file = new VersionedOpenFile(fileUri, languageId, version, fileContent);
    openFilesPerFileURI.put(fileUri, file);
    return file;
  }

  public void didChange(URI fileUri, String fileContent, int version) {
    if (!openFilesPerFileURI.containsKey(fileUri)) {
      lsLogOutput.warn(format("Illegal state. File \"%s\" is reported changed but we missed the open notification", fileUri));
    }
    openFilesPerFileURI.computeIfPresent(fileUri, (uri, previous) -> new VersionedOpenFile(uri, previous.getLanguageId(), version, fileContent));
  }

  public void didClose(URI fileUri) {
    openFilesPerFileURI.remove(fileUri);
  }

  public Optional<VersionedOpenFile> getFile(URI fileUri) {
    return Optional.ofNullable(openFilesPerFileURI.get(fileUri));
  }

  public Collection<VersionedOpenFile> getAll() {
    return openFilesPerFileURI.values();
  }

}
