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
package org.sonarsource.sonarlint.ls.connected;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.issuetracking.CachingIssueTracker;
import org.sonarsource.sonarlint.core.issuetracking.InMemoryIssueTrackerCache;
import org.sonarsource.sonarlint.core.issuetracking.IssueTrackerCache;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.tracking.IssueTrackable;
import org.sonarsource.sonarlint.ls.settings.ServerConnectionSettings;

import static java.util.function.Predicate.not;

public class ServerIssueTrackerWrapper {

  private final ConnectedSonarLintEngine engine;
  private final ServerConnectionSettings.EndpointParamsAndHttpClient endpointParamsAndHttpClient;
  private final ProjectBinding projectBinding;
  private final Supplier<String> getReferenceBranchNameForFolder;

  private final IssueTrackerCache<Issue> issueTrackerCache;
  private final IssueTrackerCache<Issue> hotspotsTrackerCache;
  private final CachingIssueTracker cachingIssueTracker;
  private final CachingIssueTracker cachingHotspotsTracker;
  private final org.sonarsource.sonarlint.core.tracking.ServerIssueTracker tracker;

  ServerIssueTrackerWrapper(ConnectedSonarLintEngine engine, ServerConnectionSettings.EndpointParamsAndHttpClient endpointParamsAndHttpClient,
    ProjectBinding projectBinding, Supplier<String> getReferenceBranchNameForFolder) {
    this.engine = engine;
    this.endpointParamsAndHttpClient = endpointParamsAndHttpClient;
    this.projectBinding = projectBinding;
    this.getReferenceBranchNameForFolder = getReferenceBranchNameForFolder;

    this.issueTrackerCache = new InMemoryIssueTrackerCache();
    this.hotspotsTrackerCache = new InMemoryIssueTrackerCache();
    this.cachingIssueTracker = new CachingIssueTracker(issueTrackerCache);
    this.cachingHotspotsTracker = new CachingIssueTracker(hotspotsTrackerCache);
    this.tracker = new org.sonarsource.sonarlint.core.tracking.ServerIssueTracker(cachingIssueTracker, cachingHotspotsTracker);
  }

  public void matchAndTrack(String filePath, Collection<Issue> issues, IssueListener issueListener, boolean shouldFetchServerIssues) {
    if (issues.isEmpty()) {
      issueTrackerCache.put(filePath, Collections.emptyList());
      return;
    }

    cachingIssueTracker.matchAndTrackAsNew(filePath, toIssueTrackables(issues));
    cachingHotspotsTracker.matchAndTrackAsNew(filePath, toHotspotTrackables(issues));
    if (shouldFetchServerIssues) {
      tracker.update(endpointParamsAndHttpClient.getEndpointParams(), endpointParamsAndHttpClient.getHttpClient(), engine, projectBinding,
        Collections.singleton(filePath), getReferenceBranchNameForFolder.get());
    } else {
      tracker.update(engine, projectBinding, getReferenceBranchNameForFolder.get(), Collections.singleton(filePath));
    }

    issueTrackerCache.getLiveOrFail(filePath).stream()
      .filter(not(Trackable::isResolved))
      .forEach(trackable -> issueListener.handle(new DelegatingIssue(trackable)));
    hotspotsTrackerCache.getLiveOrFail(filePath).stream()
      .filter(not(Trackable::isResolved))
      .forEach(trackable -> issueListener.handle(new DelegatingIssue(trackable)));
  }

  private static Collection<Trackable> toIssueTrackables(Collection<Issue> issues) {
    return issues.stream()
      .filter(it -> it.getType() != RuleType.SECURITY_HOTSPOT)
      .map(IssueTrackable::new).collect(Collectors.toList());

  }

  private static Collection<Trackable> toHotspotTrackables(Collection<Issue> issues) {
    return issues.stream().filter(it-> it.getType() == RuleType.SECURITY_HOTSPOT)
      .map(IssueTrackable::new).collect(Collectors.toList());
  }
}
