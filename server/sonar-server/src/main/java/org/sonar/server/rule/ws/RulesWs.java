/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonar.server.rule.ws;

import org.sonar.api.server.ws.NewController;
import org.sonar.api.server.ws.WebService;

public class RulesWs implements WebService {

  private final RulesWsAction[] actions;

  public RulesWs(RulesWsAction... actions) {
    this.actions = actions;
  }

  @Override
  public NewController define() {
    NewController controller = new NewController("api/rules")
      .setDescription("Get and update some details of automatic rules, and manage custom rules.");

    for (RulesWsAction action : actions) {
      action.define();
    }

    return controller;
  }
}
