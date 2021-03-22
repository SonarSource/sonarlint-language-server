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
package org.sonarsource.sonarlint.ls;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import javax.annotation.CheckForNull;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class Utils {

  private static final Logger LOG = Loggers.get(Utils.class);

  private Utils() {
  }

  // See the changelog for any evolutions on how properties are parsed:
  // https://github.com/eclipse/lsp4j/blob/master/CHANGELOG.md
  // (currently JsonElement, used to be Map<String, Object>)
  @CheckForNull
  public static Map<String, Object> parseToMap(Object obj) {
    try {
      return new Gson().fromJson((JsonElement) obj, Map.class);
    } catch (JsonSyntaxException e) {
      throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Expected a JSON map but was: " + obj, e));
    }
  }

  public static ThreadFactory threadFactory(String name, boolean daemon) {
    return runnable -> {
      Thread result = new Thread(runnable, name);
      result.setDaemon(daemon);
      return result;
    };
  }

  public static void interrupted(InterruptedException e) {
    LOG.debug("Interrupted!", e);
    Thread.currentThread().interrupt();
  }

  public static String pluralize(long nbItems, String itemName) {
    return pluralize(nbItems, itemName, itemName + "s");
  }

  public static String pluralize(long nbItems, String singular, String plural) {
    return nbItems == 1 ? singular : plural;
  }
}
