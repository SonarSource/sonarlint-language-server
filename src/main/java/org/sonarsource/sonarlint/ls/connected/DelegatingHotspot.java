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
package org.sonarsource.sonarlint.ls.connected;

import java.net.URI;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;

public class DelegatingHotspot extends DelegatingFinding {
  private final HotspotStatus reviewStatus;
  private final VulnerabilityProbability vulnerabilityProbability;

  public DelegatingHotspot(RaisedHotspotDto raisedHotspotDto, URI fileUri, HotspotStatus reviewStatus, @Nullable VulnerabilityProbability vulnerabilityProbability) {
    super(raisedHotspotDto, fileUri);
    this.reviewStatus = reviewStatus;
    this.vulnerabilityProbability = vulnerabilityProbability;
  }

  public DelegatingHotspot cloneWithNewStatus(HotspotStatus newStatus) {
    return new DelegatingHotspot((RaisedHotspotDto) this.finding, this.fileUri, newStatus, this.vulnerabilityProbability);
  }

  public HotspotStatus getReviewStatus() {
    return reviewStatus;
  }

  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }

  public RaisedHotspotDto getRaisedHotspotDto() {
    return (RaisedHotspotDto) this.finding;
  }
}
