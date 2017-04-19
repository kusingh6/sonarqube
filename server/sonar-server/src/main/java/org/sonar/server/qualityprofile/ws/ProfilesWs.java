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
package org.sonar.server.qualityprofile.ws;

import org.sonar.api.server.ws.NewAction;
import org.sonar.api.server.ws.NewController;
import org.sonar.api.server.ws.WebService;
import org.sonar.server.ws.RemovedWebServiceHandler;

/**
 * List of quality profiles WS implemented in Rails.
 * New WS on quality profiles MUST be declared in {@link org.sonar.server.qualityprofile.ws.QProfilesWs}
 */
public class ProfilesWs implements WebService {

  public static final String API_ENDPOINT = "api/profiles";

  private final OldRestoreAction restoreAction;

  public ProfilesWs(OldRestoreAction restoreAction) {
    this.restoreAction = restoreAction;
  }

  @Override
  public NewController define() {
    NewController controller = new NewController(API_ENDPOINT)
      .setDescription("Removed since 6.3, please use api/qualityprofiles instead")
      .setSince("4.4");
    restoreAction.define();
    defineListAction(controller);
    defineIndexAction(controller);
    return controller;
  }

  private static void defineIndexAction(NewController controller) {
    new NewAction("index")
      .setDescription("Get a profile.<br/>" +
        "The web service is removed and you're invited to use api/qualityprofiles/search instead")
      .setSince("3.3")
      .setDeprecatedSince("5.2")
      .setHandler(RemovedWebServiceHandler.INSTANCE)
      .setResponseExample(RemovedWebServiceHandler.INSTANCE.getResponseExample());
  }

  private static void defineListAction(NewController controller) {
    new NewAction("list")
      .setDescription("Get a list of profiles.<br/>" +
        "The web service is removed and you're invited to use api/qualityprofiles/search instead")
      .setSince("3.3")
      .setDeprecatedSince("5.2")
      .setHandler(RemovedWebServiceHandler.INSTANCE)
      .setResponseExample(RemovedWebServiceHandler.INSTANCE.getResponseExample());
  }
}
