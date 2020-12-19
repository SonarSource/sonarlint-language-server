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

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;

public class Rule {

  @SerializedName("key")
  @Expose
  private String key;

  @SerializedName("name")
  @Expose
  private String name;

  @SerializedName("activeByDefault")
  @Expose
  private boolean activeByDefault;

  @SerializedName("severity")
  @Expose
  private String severity;

  @SerializedName("type")
  @Expose
  private  String type;

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public boolean isActiveByDefault() {
    return activeByDefault;
  }

  public String getSeverity() {
    return severity;
  }

  public Rule(String key, String name, boolean activeByDefault,String severity,String type) {
    this.key = key;
    this.name = name;
    this.activeByDefault = activeByDefault;
    this.severity = severity;
    this.type = type;
  }

  public static Rule of(StandaloneRuleDetails d) {
    return new Rule(d.getKey(), d.getName(), d.isActiveByDefault(),d.getSeverity(),d.getType());
  }
}
