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
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.Locale;

public class MissingRequirementsNotificationDisplayOptionDeserializer implements
  JsonDeserializer<SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption> {
  @Override
  public SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption deserialize(JsonElement jsonElement, Type type,
    JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
    if (jsonElement.isJsonNull()) {
      return SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.ERROR_ONLY;
    }

    var primitive = jsonElement.getAsJsonPrimitive();

    if (primitive.isBoolean()) {
      return primitive.getAsBoolean() ? SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.FULL
        : SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.DO_NOT_SHOW_AGAIN;
    }

    return switch (primitive.getAsString().toLowerCase(Locale.US)) {
      case "full" -> SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.FULL;
      case "error_only" -> SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.ERROR_ONLY;
      case "never_again" -> SonarLintExtendedLanguageClient.MissingRequirementsNotificationDisplayOption.DO_NOT_SHOW_AGAIN;
      default -> throw new JsonParseException("Cannot deserialize missing requirements notifications.");
    };
  }
}
