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
package org.sonarsource.sonarlint.ls.util;

import java.net.URI;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

import static org.assertj.core.api.Assertions.assertThat;

class UtilsTests {

  @ParameterizedTest
  @CsvSource({
    "0,vulnerabilities",
    "1,vulnerability",
    "42,vulnerabilities"
  })
  void shouldPluralizeVulnerability(long nbItems, String expected) {
    assertThat(Utils.pluralize(nbItems, "vulnerability", "vulnerabilities")).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "0,issues",
    "1,issue",
    "42,issues"
  })
  void shouldPluralizeIssue(long nbItems, String expected) {
    assertThat(Utils.pluralize(nbItems, "issue")).isEqualTo(expected);
  }



  @Test
  void uriHasFileSchemaTest() {
    assertThat(Utils.uriHasFileScheme(URI.create("file:///path"))).isTrue();
    assertThat(Utils.uriHasFileScheme(URI.create("notfile:///path"))).isFalse();
  }

  @Test
  void shouldCorrectlyMapHotspotSeverity() {
    assertThat(Utils.hotspotSeverity(VulnerabilityProbability.HIGH)).isEqualTo(DiagnosticSeverity.Error);
    assertThat(Utils.hotspotSeverity(VulnerabilityProbability.MEDIUM)).isEqualTo(DiagnosticSeverity.Warning);
    assertThat(Utils.hotspotSeverity(VulnerabilityProbability.LOW)).isEqualTo(DiagnosticSeverity.Information);
  }


}
