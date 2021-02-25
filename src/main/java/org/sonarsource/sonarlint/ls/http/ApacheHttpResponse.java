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
package org.sonarsource.sonarlint.ls.http;

import java.io.IOException;
import java.io.InputStream;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;

public class ApacheHttpResponse implements HttpClient.Response {

  private static final Logger LOG = LoggerFactory.getLogger(ApacheHttpResponse.class);
  private static final String BODY_ERROR_MESSAGE = "Error reading body content";
  private String requestUrl;
  private ClassicHttpResponse response;

  public ApacheHttpResponse(String requestUrl, ClassicHttpResponse response) {
    this.requestUrl = requestUrl;
    this.response = response;
  }

  @Override
  public int code() {
    return response.getCode();
  }


  @Override
  public String bodyAsString() {
    try {
      return EntityUtils.toString(response.getEntity());
    } catch (IOException | ParseException e) {
      throw new IllegalStateException(BODY_ERROR_MESSAGE, e);
    }
  }

  @Override
  public InputStream bodyAsStream() {
    try {
      return response.getEntity().getContent();
    } catch (IOException e) {
      throw new IllegalStateException(BODY_ERROR_MESSAGE, e);
    }
  }

  @Override
  public void close() {
    try {
      response.close();
    } catch (IOException e) {
      LOG.error("Can't close response: ", e);
    }
  }

  @Override
  public String url() {
    return requestUrl;
  }
}
