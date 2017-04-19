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

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.resources.Languages;
import org.sonar.api.server.ws.Action;
import org.sonar.api.server.ws.Controller;
import org.sonar.db.DbClient;
import org.sonar.server.qualityprofile.QProfileBackuper;
import org.sonar.server.user.UserSession;
import org.sonar.server.ws.RemovedWebServiceHandler;
import org.sonar.server.ws.WsTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class ProfilesWsTest {

  private WsTester ws;

  @Before
  public void setUp() {
    ws = new WsTester(new ProfilesWs(
      new OldRestoreAction(mock(DbClient.class), mock(QProfileBackuper.class), mock(Languages.class), mock(QProfileWsSupport.class), mock(UserSession.class))));
  }

  @Test
  public void define_controller() {
    Controller controller = controller();
    assertThat(controller).isNotNull();
    assertThat(controller.path()).isEqualTo("api/profiles");
    assertThat(controller.description()).isNotEmpty();
    assertThat(controller.actions()).hasSize(3);
  }

  @Test
  public void define_index_action() {
    Controller controller = ws.controller("api/profiles");

    Action restoreProfiles = controller.action("index");
    assertThat(restoreProfiles).isNotNull();
    assertThat(restoreProfiles.handler()).isInstanceOf(RemovedWebServiceHandler.class);
    assertThat(restoreProfiles.responseExampleAsString()).isNotEmpty();
    assertThat(restoreProfiles.params()).isEmpty();
  }

  @Test
  public void define_list_action() {
    Controller controller = controller();

    Action listProfiles = controller.action("list");
    assertThat(listProfiles).isNotNull();
    assertThat(listProfiles.handler()).isInstanceOf(RemovedWebServiceHandler.class);
    assertThat(listProfiles.responseExampleAsString()).isNotEmpty();
    assertThat(listProfiles.params()).isEmpty();
  }

  private Controller controller() {
    return ws.controller("api/profiles");
  }
}
