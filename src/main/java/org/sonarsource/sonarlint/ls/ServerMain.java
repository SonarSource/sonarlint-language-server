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

import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

public class ServerMain {

  private PrintStream out;
  private PrintStream err;

  public ServerMain(PrintStream out, PrintStream err) {
    this.out = out;
    this.err = err;
  }

  public static void main(String... args) {
    new ServerMain(System.out, System.err).startLanguageServer(args);
  }

  public void startLanguageServer(String... args) {
    if (args.length < 1) {
      err.println("Usage: java -jar sonarlint-server.jar <jsonRpcPort> [file:///path/to/analyzer1.jar [file:///path/to/analyzer2.jar] ...]");
      exitWithError();
    }
    int jsonRpcPort = parsePortArgument(args);

    Collection<URL> analyzers = new ArrayList<>();
    if (args.length > 1) {
      for (int i = 1; i < args.length; i++) {
        try {
          analyzers.add(new URL(args[i]));
        } catch (MalformedURLException e) {
          err.println("Invalid argument at position " + (i + 1) + ". Expected an URL.");
          e.printStackTrace(err);
          exitWithError();
        }
      }
    }

    out.println("Binding to " + jsonRpcPort);
    try {
      SonarLintLanguageServer.bySocket(jsonRpcPort, analyzers);
    } catch (IOException e) {
      err.println("Unable to connect to the client");
      e.printStackTrace(err);
      exitWithError();
    }
  }

  private int parsePortArgument(String... args) {
    try {
      return Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      err.println("Invalid port provided as first parameter");
      e.printStackTrace(err);
      exitWithError();
    }
    return 0;
  }

  void exitWithError() {
    System.exit(1);
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
