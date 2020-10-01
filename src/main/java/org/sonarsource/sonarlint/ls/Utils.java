/*
 * SonarLint Language Server
 * Copyright (C) 2009-2020 SonarSource SA
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
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import javax.annotation.CheckForNull;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
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

  static void baz() throws NoSuchPaddingException, NoSuchAlgorithmException {
    Cipher c0 = Cipher.getInstance("AES"); // Noncompliant: by default ECB mode is chosen
    Cipher c1 = Cipher.getInstance("AES/ECB/NoPadding"); // Noncompliant: ECB doesn't provide serious message confidentiality
    Cipher c3 = Cipher.getInstance("Blowfish/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality
    Cipher c4 = Cipher.getInstance("DES/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality

    Cipher c6 = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c7 = Cipher.getInstance("Blowfish/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c8 = Cipher.getInstance("DES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c9 = Cipher.getInstance("AES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
    Cipher c10 = Cipher.getInstance("Blowfish/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
    Cipher c11 = Cipher.getInstance("DES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks

    Cipher c14 = Cipher.getInstance("RSA/NONE/NoPadding"); // Noncompliant: RSA without OAEP padding scheme is not recommanded

  }

  /**
   *
   * @param i - some integer
   * @param c - same character
   * @return - constant double
   */
  static Double foo(Integer i, Character c) {
    return 2.0;
  }

  static void bar() throws NoSuchPaddingException, NoSuchAlgorithmException {
    Cipher c0 = Cipher.getInstance("AES"); // Noncompliant: by default ECB mode is chosen
    Cipher c1 = Cipher.getInstance("AES/ECB/NoPadding"); // Noncompliant: ECB doesn't provide serious message confidentiality
    Cipher c3 = Cipher.getInstance("Blowfish/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality
    Cipher c4 = Cipher.getInstance("DES/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality

    Cipher c6 = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c7 = Cipher.getInstance("Blowfish/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c8 = Cipher.getInstance("DES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c9 = Cipher.getInstance("AES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
    Cipher c10 = Cipher.getInstance("Blowfish/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
    Cipher c11 = Cipher.getInstance("DES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks

    Cipher c14 = Cipher.getInstance("RSA/NONE/NoPadding"); // Noncompliant: RSA without OAEP padding scheme is not recommanded
  }

  static void baZ() throws NoSuchPaddingException, NoSuchAlgorithmException {
    Cipher c0 = Cipher.getInstance("AES"); // Noncompliant: by default ECB mode is chosen
    Cipher c1 = Cipher.getInstance("AES/ECB/NoPadding"); // Noncompliant: ECB doesn't provide serious message confidentiality
    Cipher c3 = Cipher.getInstance("Blowfish/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality
    Cipher c4 = Cipher.getInstance("DES/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality

    Cipher c6 = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c7 = Cipher.getInstance("Blowfish/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c8 = Cipher.getInstance("DES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c9 = Cipher.getInstance("AES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
    Cipher c10 = Cipher.getInstance("Blowfish/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
    Cipher c11 = Cipher.getInstance("DES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks

    Cipher c14 = Cipher.getInstance("RSA/NONE/NoPadding"); // Noncompliant: RSA without OAEP padding scheme is not recommanded
  }

  static void bAz() throws NoSuchPaddingException, NoSuchAlgorithmException {
    Cipher c0 = Cipher.getInstance("AES"); // Noncompliant: by default ECB mode is chosen
    Cipher c1 = Cipher.getInstance("AES/ECB/NoPadding"); // Noncompliant: ECB doesn't provide serious message confidentiality
    Cipher c3 = Cipher.getInstance("Blowfish/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality
    Cipher c4 = Cipher.getInstance("DES/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality

    Cipher c6 = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c7 = Cipher.getInstance("Blowfish/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c8 = Cipher.getInstance("DES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c9 = Cipher.getInstance("AES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
    Cipher c10 = Cipher.getInstance("Blowfish/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
    Cipher c11 = Cipher.getInstance("DES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks

    Cipher c14 = Cipher.getInstance("RSA/NONE/NoPadding"); // Noncompliant: RSA without OAEP padding scheme is not recommanded
  }

  static void Baz() throws NoSuchPaddingException, NoSuchAlgorithmException {
    Cipher c0 = Cipher.getInstance("AES"); // Noncompliant: by default ECB mode is chosen
    Cipher c1 = Cipher.getInstance("AES/ECB/NoPadding"); // Noncompliant: ECB doesn't provide serious message confidentiality
    Cipher c3 = Cipher.getInstance("Blowfish/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality
    Cipher c4 = Cipher.getInstance("DES/ECB/PKCS5Padding"); // Noncompliant: ECB doesn't provide serious message confidentiality

    Cipher c6 = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c7 = Cipher.getInstance("Blowfish/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c8 = Cipher.getInstance("DES/CBC/PKCS5Padding"); // Noncompliant: CBC with PKCS5 is vulnerable to oracle padding attacks
    Cipher c9 = Cipher.getInstance("AES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
    Cipher c10 = Cipher.getInstance("Blowfish/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks
    Cipher c11 = Cipher.getInstance("DES/CBC/PKCS7Padding"); // Noncompliant: CBC with PKCS7 is vulnerable to oracle padding attacks

    Cipher c14 = Cipher.getInstance("RSA/NONE/NoPadding"); // Noncompliant: RSA without OAEP padding scheme is not recommanded
  }
}
