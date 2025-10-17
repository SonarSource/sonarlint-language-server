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
import org.sonarsource.sonarlint.ls.SonarLintExtendedLanguageClient.ShowMissingRequirementsNotification;

public class ShowMissingRequirementsNotificationDeserializer implements JsonDeserializer<ShowMissingRequirementsNotification> {

  @Override
  public ShowMissingRequirementsNotification deserialize(JsonElement element, Type type, JsonDeserializationContext context)
    throws JsonParseException {

    var primitive = element.getAsJsonPrimitive();
    if (primitive.isJsonNull()) {
      return ShowMissingRequirementsNotification.LSP_MESSAGE;
    }

    if (primitive.isBoolean()) {
      return Boolean.TRUE.equals(primitive.getAsBoolean()) ? ShowMissingRequirementsNotification.YES : ShowMissingRequirementsNotification.NO;
    }

    switch (primitive.getAsString()) {
      case "Yes":
        return ShowMissingRequirementsNotification.YES;
      case "No":
        return ShowMissingRequirementsNotification.NO;
      case "MessageOnly":
        return ShowMissingRequirementsNotification.LSP_MESSAGE;

      default:
        throw new JsonParseException("Cannot deserialize missing requirements notifications.");
    }
  }

}
