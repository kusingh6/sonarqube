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
package org.sonar.server.ws;

import com.google.common.collect.Iterables;

import org.sonar.api.server.ws.Action;
import org.sonar.api.server.ws.Context;
import org.sonar.api.server.ws.NewController;

public class WsActionTester {

  public static final String CONTROLLER_KEY = "test";
  private final Action action;

  public WsActionTester(WsAction wsAction) {
    Context context = new Context();
    NewController newController = new NewController(CONTROLLER_KEY);
    wsAction.define();
    return newController;
    action = Iterables.get(context.controller(CONTROLLER_KEY).actions(), 0);
  }

  public Action getDef() {
    return action;
  }

  public TestRequest newRequest() {
    TestRequest request = new TestRequest();
    request.setAction(action);
    return request;
  }
}
