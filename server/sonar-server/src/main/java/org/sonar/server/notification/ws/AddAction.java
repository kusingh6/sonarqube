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
package org.sonar.server.notification.ws;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.sonar.api.notifications.NotificationChannel;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.resources.Scopes;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.issue.notification.MyNewIssuesNotificationDispatcher;
import org.sonar.server.notification.NotificationCenter;
import org.sonar.server.notification.NotificationUpdater;
import org.sonar.server.notification.email.EmailNotificationChannel;
import org.sonar.server.user.UserSession;
import org.sonar.server.ws.KeyExamples;
import org.sonarqube.ws.client.notification.AddRequest;

import static java.util.Optional.empty;
import static org.sonar.core.util.Protobuf.setNullable;
import static org.sonar.core.util.stream.MoreCollectors.toList;
import static org.sonar.server.notification.NotificationDispatcherMetadata.GLOBAL_NOTIFICATION;
import static org.sonar.server.notification.NotificationDispatcherMetadata.PER_PROJECT_NOTIFICATION;
import static org.sonar.server.ws.WsUtils.checkRequest;
import static org.sonarqube.ws.client.notification.NotificationsWsParameters.ACTION_ADD;
import static org.sonarqube.ws.client.notification.NotificationsWsParameters.PARAM_CHANNEL;
import static org.sonarqube.ws.client.notification.NotificationsWsParameters.PARAM_PROJECT;
import static org.sonarqube.ws.client.notification.NotificationsWsParameters.PARAM_TYPE;

public class AddAction implements NotificationsWsAction {
  private final NotificationCenter notificationCenter;
  private final NotificationUpdater notificationUpdater;
  private final DbClient dbClient;
  private final ComponentFinder componentFinder;
  private final UserSession userSession;
  private final List<String> globalDispatchers;
  private final List<String> projectDispatchers;

  public AddAction(NotificationCenter notificationCenter, NotificationUpdater notificationUpdater, DbClient dbClient, ComponentFinder componentFinder, UserSession userSession) {
    this.notificationCenter = notificationCenter;
    this.notificationUpdater = notificationUpdater;
    this.dbClient = dbClient;
    this.componentFinder = componentFinder;
    this.userSession = userSession;
    this.globalDispatchers = notificationCenter.getDispatcherKeysForProperty(GLOBAL_NOTIFICATION, "true").stream().sorted().collect(toList());
    this.projectDispatchers = notificationCenter.getDispatcherKeysForProperty(PER_PROJECT_NOTIFICATION, "true").stream().sorted().collect(toList());
  }

  @Override
  public WebService.NewAction define() {
    WebService.NewAction action = context.createAction(ACTION_ADD)
      .setDescription("Add a notification for the authenticated user.<br>" +
        "Requires authentication. If a project is provided, requires the 'Browse' permission on the specified project.")
      .setSince("6.3")
      .setPost(true)
      .setHandler(this);

    action.createParam(PARAM_PROJECT)
      .setDescription("Project key")
      .setExampleValue(KeyExamples.KEY_PROJECT_EXAMPLE_001);

    List<NotificationChannel> channels = notificationCenter.getChannels();
    action.createParam(PARAM_CHANNEL)
      .setDescription("Channel through which the notification is sent. For example, notifications can be sent by email.")
      .setPossibleValues(channels)
      .setDefaultValue(EmailNotificationChannel.class.getSimpleName());

    action.createParam(PARAM_TYPE)
      .setDescription("Notification type. Possible values are for:" +
        "<ul>" +
        "  <li>Global notifications: %s</li>" +
        "  <li>Per project notifications: %s</li>" +
        "</ul>",
        String.join(", ", globalDispatchers),
        String.join(", ", projectDispatchers))
      .setRequired(true)
      .setExampleValue(MyNewIssuesNotificationDispatcher.KEY);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    Stream.of(request)
      .map(toWsRequest())
      .peek(checkPermissions())
      .forEach(add());

    response.noContent();
  }

  private Consumer<AddRequest> add() {
    return request -> {
      try (DbSession dbSession = dbClient.openSession(false)) {
        Optional<ComponentDto> project = searchProject(dbSession, request);
        notificationUpdater.add(dbSession, request.getChannel(), request.getType(), project.orElse(null));
        dbSession.commit();
      }
    };
  }

  private Optional<ComponentDto> searchProject(DbSession dbSession, AddRequest request) {
    Optional<ComponentDto> project = request.getProject() == null ? empty() : Optional.of(componentFinder.getByKey(dbSession, request.getProject()));
    project.ifPresent(p -> checkRequest(Qualifiers.PROJECT.equals(p.qualifier()) && Scopes.PROJECT.equals(p.scope()),
      "Component '%s' must be a project", request.getProject()));
    return project;
  }

  private Consumer<AddRequest> checkPermissions() {
    return request -> userSession.checkLoggedIn();
  }

  private Function<Request, AddRequest> toWsRequest() {
    return request -> {
      AddRequest.Builder requestBuilder = AddRequest.builder()
        .setType(request.mandatoryParam(PARAM_TYPE))
        .setChannel(request.mandatoryParam(PARAM_CHANNEL));
      String project = request.param(PARAM_PROJECT);
      setNullable(project, requestBuilder::setProject);
      AddRequest wsRequest = requestBuilder.build();

      if (wsRequest.getProject() == null) {
        checkRequest(globalDispatchers.contains(wsRequest.getType()), "Value of parameter '%s' (%s) must be one of: %s",
          PARAM_TYPE,
          wsRequest.getType(),
          globalDispatchers);
      } else {
        checkRequest(projectDispatchers.contains(wsRequest.getType()), "Value of parameter '%s' (%s) must be one of: %s",
          PARAM_TYPE,
          wsRequest.getType(),
          projectDispatchers);
      }

      return wsRequest;
    };
  }
}
