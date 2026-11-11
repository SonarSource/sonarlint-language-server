/*
 * SonarLint Language Server
 * Copyright (C) SonarSource Sàrl
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

import java.util.List;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.FuzzySearchProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.FuzzySearchProjectsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectSearchProtocolTests {
  private final MessageJsonHandler json = new MessageJsonHandler(
    ServiceEndpoints.getSupportedMethods(SonarLintExtendedLanguageServer.class));

  @Test
  void readsProjectSearchRequest() {
    var request = (RequestMessage) json.parseMessage("""
      {"jsonrpc":"2.0","id":1,"method":"sonarlint/fuzzySearchProjects","params":{"connectionId":"<default>","searchText":"needle"}}
      """);

    var params = (FuzzySearchProjectsParams) request.getParams();
    assertThat(params.getConnectionId()).isEqualTo("<default>");
    assertThat(params.getSearchText()).isEqualTo("needle");
  }

  @Test
  void serializesOrderedSearchResultsAndEmptyResults() {
    var response = new FuzzySearchProjectsResponse(List.of(new SonarProjectDto("outside-catalogue", "Zebra"), new SonarProjectDto("another", "Alpha")));

    assertThat(json.getGson().toJson(response)).isEqualTo("""
      {"topResults":[{"key":"outside-catalogue","name":"Zebra"},{"key":"another","name":"Alpha"}]}""");
    assertThat(json.getGson().toJson(new FuzzySearchProjectsResponse(List.of()))).isEqualTo("{\"topResults\":[]}");
  }
}
