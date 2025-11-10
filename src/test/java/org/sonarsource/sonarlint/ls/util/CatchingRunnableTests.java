/*
 * SonarLint Language Server
 * Copyright (C) 2009-2025 SonarSource Sàrl
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;


class CatchingRunnableTests {
  List<String> fakeLogs = new ArrayList<>();
  boolean callVerifier = false;
  Consumer<Throwable> dummyConsumer = (t) -> fakeLogs.add("I have failed");

  @Test
  void shouldApplyConsumerWithThrowable() {
    var underTest = new CatchingRunnable(throwingRunnable, dummyConsumer);
    underTest.run();

    assertThat(fakeLogs).hasSize(1);
  }

  @Test
  void shouldApplyNormalConsumer() {
    var underTest = new CatchingRunnable(normalRunnable, dummyConsumer);
    underTest.run();

    assertThat(callVerifier).isTrue();
  }

  Runnable throwingRunnable = () -> {
    throw new IllegalArgumentException("This is not allowed");
  };

  Runnable normalRunnable = () -> {
    callVerifier = true;
  };

}
