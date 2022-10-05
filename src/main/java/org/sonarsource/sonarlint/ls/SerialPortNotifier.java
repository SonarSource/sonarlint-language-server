/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
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

import com.fazecast.jSerialComm.SerialPort;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class SerialPortNotifier {

  private final SerialPort serialPort;
  private final PrintWriter output;

  public SerialPortNotifier() {
    var portName = System.getProperty("sonarlint.serialPort");
    if (portName != null) {
      this.serialPort = SerialPort.getCommPort(portName);
      serialPort.setComPortParameters(115_200, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
      serialPort.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
      serialPort.openPort();
      this.output = new PrintWriter(new OutputStreamWriter(serialPort.getOutputStream(), StandardCharsets.US_ASCII));
    } else {
      serialPort = null;
      output = null;
    }
  }

  public void send(String message) {
    if (output != null) {
      output.println(message);
      output.flush();
    }
  }

  public void shutdown() {
    try {
      output.close();
    } catch (Throwable ignored) {
      // NOP
    }
    try {
      serialPort.closePort();
    } catch (Throwable ignored) {
      // NOP
    }
  }
}
