/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource SA
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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MissingRequirementsNotificationDisplayOptionDeserializerTest {
  private final JsonDeserializationContext mockContext = mock(JsonDeserializationContext.class);
  private final MissingRequirementsNotificationDisplayOptionDeserializer underTest = new MissingRequirementsNotificationDisplayOptionDeserializer();

  @Test
  void should_default_to_error_only() {
    var result = underTest.deserialize(JsonNull.INSTANCE, null, mockContext);

    assertEquals(SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.ERROR_ONLY, result);
  }

  @Test
  void should_support_old_clients_with_boolean_values() {
    var trueResult = underTest.deserialize(new JsonPrimitive(true), null, mockContext);
    var falseResult = underTest.deserialize(new JsonPrimitive(false), null, mockContext);

    assertEquals(SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.FULL, trueResult);
    assertEquals(SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.DO_NOT_SHOW_AGAIN, falseResult);
  }

  @Test
  void should_support_new_clients_with_message_display_options() {
    var fullResult = underTest.deserialize(new JsonPrimitive("full"), null, mockContext);
    var errorOnlyResult = underTest.deserialize(new JsonPrimitive("error_only"), null, mockContext);
    var neverAgainResult = underTest.deserialize(new JsonPrimitive("never_again"), null, mockContext);

    assertEquals(SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.FULL, fullResult);
    assertEquals(SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.ERROR_ONLY, errorOnlyResult);
    assertEquals(SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.DO_NOT_SHOW_AGAIN, neverAgainResult);

    var randomPrimitive = new JsonPrimitive("random");
    assertThrows(JsonParseException.class, () -> underTest.deserialize(randomPrimitive, null, mockContext));
  }
}
