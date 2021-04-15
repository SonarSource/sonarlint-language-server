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
package org.sonarsource.sonarlint.ls.connected.notifications;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;

import static java.util.Collections.singletonList;

public class BindingUpdateNotification {

  private final LanguageClient client;
  private final Set<String> projectKeysHavingActiveNotification = Collections.synchronizedSet(new HashSet<>());

  public BindingUpdateNotification(LanguageClient client) {
    this.client = client;
  }

  public CompletableFuture<Boolean> notifyBindingUpdateAvailable(String projectKey) {
    if (projectKeysHavingActiveNotification.contains(projectKey)) {
      // don't show the notification twice
      return CompletableFuture.completedFuture(false);
    }
    projectKeysHavingActiveNotification.add(projectKey);
    return client.showMessageRequest(shouldUpdateBindingRequest(projectKey))
      .thenApply(Objects::nonNull)
      .whenComplete((b, e) -> projectKeysHavingActiveNotification.remove(projectKey));
  }

  private static ShowMessageRequestParams shouldUpdateBindingRequest(String projectKey) {
    ShowMessageRequestParams requestParams = new ShowMessageRequestParams(singletonList(new MessageActionItem("Update")));
    requestParams.setMessage("SonarLint - A binding update is available for project " + projectKey);
    return requestParams;
  }
}
