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

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * What a {@code .sonar-config.json} says about one workspace folder: the project that contains the folder (bound
 * on the folder's own scope), and the projects nested below it (child scopes).
 */
public record ResolvedSonarConfig(@Nullable SonarConfigProject direct, List<SonarConfigProject> children) {

  public List<SonarConfigProject> allProjects() {
    var all = new ArrayList<SonarConfigProject>();
    if (direct != null) {
      all.add(direct);
    }
    all.addAll(children);
    return all;
  }
}
