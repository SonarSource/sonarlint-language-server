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
package org.sonarsource.sonarlint.ls.connected.events;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.ls.ForcedAnalysisCoordinator;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import testutils.SonarLintLogTester;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerSentEventsTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private ServerSentEventsHandlerService underTest;

  ForcedAnalysisCoordinator forcedAnalysisCoordinator = mock(ForcedAnalysisCoordinator.class);
  ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);

  @BeforeEach
  void init() {
    underTest = new ServerSentEventsHandler(forcedAnalysisCoordinator, bindingManager);
  }

  @Test
  void handleHotspotEventTest() {
    var filePath = Path.of("severFilePath");
    var connectionId = "connectionId";
    var projectKey = "myProject";
    var fullFileUri = URI.create("file:///my/workspace/serverFilePath");

    when(bindingManager.fullFilePathFromRelative(filePath, connectionId, projectKey))
      .thenReturn(Optional.of(fullFileUri));

    underTest.handleHotspotEvent(new DidReceiveServerHotspotEvent(connectionId, projectKey, filePath));

    verify(forcedAnalysisCoordinator).didReceiveHotspotEvent(fullFileUri);
  }


}
