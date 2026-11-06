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
package org.sonarsource.sonarlint.ls.folders;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads a JSON object as a map of strings, discarding every value that is not a string.
 */
class StringValuesOnlyAdapter extends TypeAdapter<Map<String, String>> {

  @Override
  public void write(JsonWriter out, Map<String, String> value) throws IOException {
    out.beginObject();
    for (var entry : value.entrySet()) {
      out.name(entry.getKey()).value(entry.getValue());
    }
    out.endObject();
  }

  @Override
  public Map<String, String> read(JsonReader in) throws IOException {
    if (in.peek() != JsonToken.BEGIN_OBJECT) {
      in.skipValue();
      return null;
    }
    var strings = new LinkedHashMap<String, String>();
    in.beginObject();
    while (in.hasNext()) {
      var name = in.nextName();
      if (in.peek() == JsonToken.STRING) {
        strings.put(name, in.nextString());
      } else {
        in.skipValue();
      }
    }
    in.endObject();
    return strings;
  }
}
