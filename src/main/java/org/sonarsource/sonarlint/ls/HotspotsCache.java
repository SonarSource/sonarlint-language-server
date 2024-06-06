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

import com.google.gson.JsonObject;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.Diagnostic;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.ls.connected.DelegatingHotspot;
import org.sonarsource.sonarlint.ls.file.VersionedOpenFile;

import static org.sonarsource.sonarlint.ls.util.Utils.isDelegatingIssueWithServerIssueKey;

public class HotspotsCache {
  private final Map<URI, Map<String, DelegatingHotspot>> hotspotsPerIdPerFileURI = new ConcurrentHashMap<>();

  public void clear(URI fileUri) {
    hotspotsPerIdPerFileURI.remove(fileUri);
  }

  /**
   * Keep only the entries for the given set of files
   *
   * @param openFiles the set of file URIs to keep
   * @return the set of file URIs that were removed
   */
  public Set<URI> keepOnly(Collection<VersionedOpenFile> openFiles) {
    var keysBeforeRemoval = new HashSet<>(hotspotsPerIdPerFileURI.keySet());
    var keysToRetain = openFiles.stream().map(VersionedOpenFile::getUri).collect(Collectors.toSet());
    hotspotsPerIdPerFileURI.keySet().retainAll(keysToRetain);
    hotspotsPerIdPerFileURI.keySet().retainAll(keysToRetain);
    keysBeforeRemoval.removeAll(hotspotsPerIdPerFileURI.keySet());
    return keysBeforeRemoval;
  }

  public void analysisStarted(VersionedOpenFile versionedOpenFile) {
    hotspotsPerIdPerFileURI.remove(versionedOpenFile.getUri());
  }

  public void reportHotspots(Map<URI, List<RaisedHotspotDto>> hotspotsByFileUri) {
    hotspotsByFileUri.forEach((fileUri, hotspots) -> hotspotsPerIdPerFileURI.computeIfAbsent(fileUri, u -> new ConcurrentHashMap<>())
      .putAll(hotspots.stream().collect(Collectors.toMap(i -> i.getId().toString(), i -> new DelegatingHotspot(i, fileUri, i.getStatus(), i.getVulnerabilityProbability())))));
  }

  public int count(URI f) {
    return get(f).size();
  }

  public void removeFindingWithServerKey(String fileUriStr, String key) {
    var fileUri = URI.create(fileUriStr);
    var hotspots = hotspotsPerIdPerFileURI.get(fileUri);
    if (hotspots != null) {
      var first = hotspots.entrySet()
        .stream()
        .filter(hotspotEntry -> isDelegatingIssueWithServerIssueKey(key, hotspotEntry.getValue()) || isLocalIssueWithKey(key, hotspotEntry.getValue()))
        .map(Map.Entry::getKey)
        .findFirst();
      first.ifPresent(hotspots::remove);
    }
  }

  private static boolean isLocalIssueWithKey(String key, DelegatingHotspot findingDto) {
    return key.equals(findingDto.getIssueId().toString());
  }

  public Optional<Map.Entry<String, RaisedHotspotDto>> findHotspotPerId(String fileUriStr, String serverIssueKey) {
    var fileUri = URI.create(fileUriStr);
    var hotspots = hotspotsPerIdPerFileURI.get(fileUri);
    if (hotspots != null) {
      return hotspots.entrySet()
        .stream()
        .filter(hotspotEntry -> isDelegatingIssueWithServerIssueKey(serverIssueKey, hotspotEntry.getValue()))
        .findFirst()
        .map(entry -> Map.entry(entry.getKey(), entry.getValue().getRaisedHotspotDto()));
    }
    return Optional.empty();
  }

  public void updateHotspotStatus(String fileUriStr, String serverIssueKey, HotspotStatus newStatus) {
    var hotspotPerId = findHotspotPerId(fileUriStr, serverIssueKey);
    if (hotspotPerId.isPresent()) {
      var raisedFindings = hotspotPerId.get().getValue();
      var delegatingHotspot = new DelegatingHotspot(raisedFindings, URI.create(fileUriStr), raisedFindings.getStatus(), raisedFindings.getVulnerabilityProbability());
      var clonedDelegatingHotspot = delegatingHotspot.cloneWithNewStatus(newStatus);
      var hotspotsByKey = hotspotsPerIdPerFileURI.get(URI.create(fileUriStr));
      if (hotspotsByKey != null) {
        hotspotsByKey.put(hotspotPerId.get().getKey(), clonedDelegatingHotspot);
      }
    }
  }

  public Optional<DelegatingHotspot> getHotspotForDiagnostic(URI fileUri, Diagnostic d) {
    var issuesForFile = get(fileUri);
    return Optional.ofNullable(d.getData())
      .map(JsonObject.class::cast)
      .map(jsonObject -> jsonObject.get("entryKey").getAsString())
      .map(issuesForFile::get);
  }

  public Map<String, DelegatingHotspot> get(URI fileUri) {
    return hotspotsPerIdPerFileURI.getOrDefault(fileUri, Map.of());
  }
}
