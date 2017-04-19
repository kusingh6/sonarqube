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
package org.sonar.server.measure.ws;

import org.sonar.api.server.ws.NewAction;
import org.sonar.api.server.ws.NewController;
import org.sonar.api.server.ws.WebService;
import org.sonar.server.ws.RemovedWebServiceHandler;

public class TimeMachineWs implements WebService {

  @Override
  public NewController define() {
    NewController controller = new NewController("api/timemachine")
      .setDescription("Removed since 6.3, please use api/measures/search_history instead")
      .setSince("2.10");
    defineIndexAction(controller);
    return controller;
  }

  private static void defineIndexAction(NewController controller) {
    new NewAction("index")
      .setDescription("The web service is removed and you're invited to use api/measures/search_history instead")
      .setSince("2.10")
      .setDeprecatedSince("6.3")
      .setHandler(RemovedWebServiceHandler.INSTANCE)
      .setResponseExample(RemovedWebServiceHandler.INSTANCE.getResponseExample());
  }

}
