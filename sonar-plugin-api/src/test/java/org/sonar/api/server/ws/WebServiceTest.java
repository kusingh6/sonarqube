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
package org.sonar.api.server.ws;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import org.apache.commons.lang.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class WebServiceTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public LogTester logTester = new LogTester();

  Context context = new Context();

  @Test
  public void no_web_services_by_default() {
    assertThat(context.controllers()).isEmpty();
    assertThat(context.controller("metric")).isNull();
  }

  @Test
  public void define_web_service() {
    MetricWs metricWs = new MetricWs();

    metricWs.define();

    Controller controller = context.controller("api/metric");
    assertThat(controller).isNotNull();
    assertThat(controller.path()).isEqualTo("api/metric");
    assertThat(controller.description()).isEqualTo("Metrics");
    assertThat(controller.since()).isEqualTo("3.2");
    assertThat(controller.actions()).hasSize(2);
    assertThat(controller.isInternal()).isFalse();
    Action showAction = controller.action("show");
    assertThat(showAction).isNotNull();
    assertThat(showAction.key()).isEqualTo("show");
    assertThat(showAction.description()).isEqualTo("Show metric");
    assertThat(showAction.handler()).isNotNull();
    assertThat(showAction.responseExample()).isNotNull();
    assertThat(showAction.responseExampleFormat()).isNotEmpty();
    assertThat(showAction.responseExampleAsString()).isNotEmpty();
    assertThat(showAction.deprecatedSince()).isNull();
    assertThat(showAction.changelog()).isEmpty();
    // same as controller
    assertThat(showAction.since()).isEqualTo("4.2");
    assertThat(showAction.isPost()).isFalse();
    assertThat(showAction.isInternal()).isFalse();
    assertThat(showAction.path()).isEqualTo("api/metric/show");
    Action createAction = controller.action("create");
    assertThat(createAction).isNotNull();
    assertThat(createAction.key()).isEqualTo("create");
    assertThat(createAction.toString()).isEqualTo("api/metric/create");
    assertThat(createAction.deprecatedSince()).isEqualTo("5.3");
    // overrides controller version
    assertThat(createAction.since()).isEqualTo("4.1");
    assertThat(createAction.isPost()).isTrue();
    assertThat(createAction.isInternal()).isTrue();
    assertThat(createAction.changelog()).extracting(Change::getVersion, Change::getDescription).containsOnly(
      tuple("6.4", "Last event"), tuple("6.0", "Old event"), tuple("4.5.6", "Very old event"));
  }

  @Test
  public void fail_if_duplicated_ws_keys() {
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("The web service 'api/metric' is defined multiple times");

    MetricWs metricWs = new MetricWs();
    metricWs.define();
    ((WebService) context -> {
      NewController newController = context.createController("api/metric");
      newDefaultAction(newController, "delete");
      return newController;
    }).define();
  }

  @Test
  public void fail_if_no_action_handler() {
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("RequestHandler is not set on action rule/show");

    ((WebService) context -> {
      NewController controller = context.createController("rule");
      newDefaultAction(controller, "show")
        .setHandler(null);
      return controller;
    }).define();
  }

  @Test
  public void fail_if_duplicated_action_keys() {
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("The action 'delete' is defined multiple times in the web service 'rule'");

    ((WebService) context -> {
      NewController newController = context.createController("rule");
      newDefaultAction(newController, "create");
      newDefaultAction(newController, "delete");
      newDefaultAction(newController, "delete");
      return newController;
    }).define();
  }

  @Test
  public void fail_if_no_actions() {
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("At least one action must be declared in the web service 'rule'");

    ((WebService) context -> context.createController("rule").akjsndflkajdsnfalkdsnf()).define();
  }

  @Test
  public void fail_if_no_controller_path() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("WS controller path must not be empty");

    ((WebService) context -> context.createController(null).akjsndflkajdsnfalkdsnf()).define();
  }

  @Test
  public void controller_path_must_not_start_with_slash() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("WS controller path must not start or end with slash: /hello");

    ((WebService) context -> context.createController("/hello").akjsndflkajdsnfalkdsnf()).define();
  }

  @Test
  public void controller_path_must_not_end_with_slash() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("WS controller path must not start or end with slash: hello/");

    ((WebService) context -> context.createController("hello/").akjsndflkajdsnfalkdsnf()).define();
  }

  @Test
  public void handle_request() throws Exception {
    MetricWs metricWs = new MetricWs();
    metricWs.define();

    assertThat(metricWs.showCalled).isFalse();
    assertThat(metricWs.createCalled).isFalse();
    context.controller("api/metric").action("show").handler().handle(mock(Request.class), mock(Response.class));
    assertThat(metricWs.showCalled).isTrue();
    assertThat(metricWs.createCalled).isFalse();
    context.controller("api/metric").action("create").handler().handle(mock(Request.class), mock(Response.class));
    assertThat(metricWs.createCalled).isTrue();
  }

  @Test
  public void action_parameters() {
    ((WebService) context -> {
      NewController newController = context.createController("api/rule");
      NewAction newAction = newDefaultAction(newController, "create");
      newAction
        .createParam("key")
        .setDescription("Key of the new rule");
      newAction
        .createParam("severity")
        .setDefaultValue("MAJOR")
        .setSince("4.4")
        .setDeprecatedSince("5.3")
        .setDeprecatedKey("old-severity", "4.5")
        .setPossibleValues("INFO", "MAJOR", "BLOCKER");
      newAction.createParam("internal")
        .setInternal(true);
      newAction.addPagingParams(20);
      newAction.addFieldsParam(Arrays.asList("name", "severity"));
      newAction.addSortParams(Arrays.asList("name", "updatedAt", "severity"), "updatedAt", false);

      return newController;
    }).define();

    Action action = context.controller("api/rule").action("create");
    assertThat(action.params()).hasSize(8);

    Param keyParam = action.param("key");
    assertThat(keyParam.key()).isEqualTo("key");
    assertThat(keyParam.description()).isEqualTo("Key of the new rule");
    assertThat(keyParam.isInternal()).isFalse();
    assertThat(keyParam.toString()).isEqualTo("key");

    Param severityParam = action.param("severity");
    assertThat(severityParam.key()).isEqualTo("severity");
    assertThat(severityParam.description()).isNull();
    assertThat(severityParam.deprecatedSince()).isEqualTo("5.3");
    assertThat(severityParam.since()).isEqualTo("4.4");
    assertThat(severityParam.deprecatedKey()).isEqualTo("old-severity");
    assertThat(severityParam.deprecatedKeySince()).isEqualTo("4.5");
    assertThat(severityParam.defaultValue()).isEqualTo("MAJOR");
    assertThat(severityParam.possibleValues()).containsOnly("INFO", "MAJOR", "BLOCKER");

    Param internalParam = action.param("internal");
    assertThat(internalParam.isInternal()).isTrue();

    // predefined fields
    assertThat(action.param("p").defaultValue()).isEqualTo("1");
    assertThat(action.param("p").description()).isNotEmpty();
    assertThat(action.param("ps").defaultValue()).isEqualTo("20");
    assertThat(action.param("ps").description()).isNotEmpty();
    assertThat(action.param("f").possibleValues()).containsOnly("name", "severity");
    assertThat(action.param("s").possibleValues()).containsOnly("name", "severity", "updatedAt");
    assertThat(action.param("s").description()).isNotEmpty();
    assertThat(action.param("asc").defaultValue()).isEqualTo("false");
  }

  @Test
  public void param_metadata_as_objects() {
    ((WebService) context -> {
      NewController newController = context.createController("api/rule");
      NewAction create = newDefaultAction(newController, "create");
      create.createParam("status")
        .setDefaultValue(RuleStatus.BETA)
        .setPossibleValues(RuleStatus.BETA, RuleStatus.READY)
        .setExampleValue(RuleStatus.BETA);
      create.createParam("max")
        .setDefaultValue(11)
        .setPossibleValues(11, 13, 17)
        .setExampleValue(17);
      return newController;
    }).define();

    Action action = context.controller("api/rule").action("create");
    assertThat(action.param("status").defaultValue()).isEqualTo("BETA");
    assertThat(action.param("status").possibleValues()).containsOnly("BETA", "READY");
    assertThat(action.param("status").exampleValue()).isEqualTo("BETA");
    assertThat(action.param("max").defaultValue()).isEqualTo("11");
    assertThat(action.param("max").possibleValues()).containsOnly("11", "13", "17");
    assertThat(action.param("max").exampleValue()).isEqualTo("17");
  }

  @Test
  public void param_null_metadata() {
    ((WebService) context -> {
      NewController newController = context.createController("api/rule");
      NewAction create = newDefaultAction(newController, "create");
      create.createParam("status")
        .setDefaultValue(null)
        .setPossibleValues(Collections.emptyList())
        .setExampleValue(null);
      create.createParam("max")
        .setPossibleValues((Object[]) null);
      return newController;
    }).define();

    Action action = context.controller("api/rule").action("create");
    assertThat(action.param("status").defaultValue()).isNull();
    assertThat(action.param("status").possibleValues()).isNull();
    assertThat(action.param("status").exampleValue()).isNull();
    assertThat(action.param("max").possibleValues()).isNull();
  }

  @Test
  public void param_with_empty_possible_values() {
    ((WebService) context -> {
      NewController newController = context.createController("api/rule");
      NewAction create = newDefaultAction(newController, "create");
      create.createParam("status")
        .setPossibleValues(Collections.emptyList());
      return newController;
    }).define();

    Action action = context.controller("api/rule").action("create");
    // no possible values -> return null but not empty
    assertThat(action.param("status").possibleValues()).isNull();
  }

  @Test
  public void fail_if_required_param_has_default_value() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Default value must not be set on parameter 'api/rule/create?key' as it's marked as required");
    ((WebService) context -> {
      NewController controller = context.createController("api/rule");
      NewAction action = newDefaultAction(controller, "create");
      action.createParam("key").setRequired(true).setDefaultValue("abc");
      return controller;
    }).define();
  }

  @Test
  public void fail_if_duplicated_action_parameters() {
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("The parameter 'key' is defined multiple times in the action 'create'");

    ((WebService) context -> {
      NewController controller = context.createController("api/rule");
      NewAction action = newDefaultAction(controller, "create");
      action.createParam("key");
      action.createParam("key");
      return controller;
    }).define();
  }

  @Test
  public void ws_is_internal_if_all_actions_are_internal() {
    ((WebService) context -> {
      NewController newController = context.createController("api/rule");
      newDefaultAction(newController, "create").setInternal(true);
      newDefaultAction(newController, "update").setInternal(true);
      return newController;
    }).define();

    assertThat(context.controller("api/rule").isInternal()).isTrue();
  }

  @Test
  public void response_example() {
    MetricWs metricWs = new MetricWs();
    metricWs.define();
    Action action = context.controller("api/metric").action("create");

    assertThat(action.responseExampleFormat()).isEqualTo("txt");
    assertThat(action.responseExample()).isNotNull();
    assertThat(StringUtils.trim(action.responseExampleAsString())).isEqualTo("example of WS response");
  }

  @Test
  public void fail_to_open_response_example() {
    WebService ws = context -> {
      try {
        NewController controller = context.createController("foo");
        newDefaultAction(controller, "bar").setResponseExample(new URL("file:/does/not/exist"));
        return controller;
      } catch (MalformedURLException e) {
        e.printStackTrace();
      }
    };
    ws.define();

    Action action = context.controller("foo").action("bar");
    try {
      action.responseExampleAsString();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Fail to load file:/does/not/exist");
    }
  }

  @Test
  public void post_action_without_response_example() {
    WebService ws = context -> {
      NewController newController = context.createController("api/rule");
      newDefaultAction(newController, "list").setPost(true).setResponseExample(null);
      return newController;
    };
    ws.define();

    assertThat(logTester.logs(LoggerLevel.WARN))
      .doesNotContain("The response example is not set on action api/rule/list");
  }

  @Test
  public void fail_if_get_and_no_response_example() {
    ((WebService) context -> {
      NewController newController = context.createController("api/rule");
      newDefaultAction(newController, "list").setResponseExample(null);
      return newController;
    }).define();

    assertThat(logTester.logs(LoggerLevel.WARN))
      .contains("The response example is not set on action api/rule/list");
  }

  @Test
  public void log_if_since_on_an_action_is_empty() {
    ((WebService) context -> {
      NewController newController = context.createController("api/rule");
      newDefaultAction(newController, "list")
        .setSince("");
      return newController;
    }).define();

    assertThat(logTester.logs(LoggerLevel.WARN))
      .contains("Since is not set on action api/rule/list");
  }

  @Test
  public void log_if_since_on_an_action_is_null() {
    ((WebService) context -> {
      NewController newController = context.createController("api/rule");
      newDefaultAction(newController, "list")
        .setSince(null);
      return newController;
    }).define();

    assertThat(logTester.logs(LoggerLevel.WARN))
      .contains("Since is not set on action api/rule/list");
  }

  @Test
  public void log_if_action_description_is_empty() {
    ((WebService) context -> {
      NewController newController = context.createController("api/rule");
      newDefaultAction(newController, "list")
        .setDescription("");
      return newController;
    }).define();

    assertThat(logTester.logs(LoggerLevel.WARN))
      .contains("Description is not set on action api/rule/list");
  }

  @Test
  public void log_if_action_description_is_null() {
    ((WebService) context -> {
      NewController newController = context.createController("api/rule");
      newDefaultAction(newController, "list")
        .setDescription(null);
      return newController;
    }).define();

    assertThat(logTester.logs(LoggerLevel.WARN))
      .contains("Description is not set on action api/rule/list");
  }

  static class MetricWs implements WebService {
    boolean showCalled = false;
    boolean createCalled = false;

    @Override
    public NewController define() {
      NewController newController = new NewController("api/metric")
        .setDescription("Metrics")
        .setSince("3.2");

      newController.createAction("show")
        .setDescription("Show metric")
        .setSince("4.2")
        .setResponseExample(getClass().getResource("WebServiceTest/response-example.txt"))
        .setHandler(this::show);

      newController.createAction("create")
        .setDescription("Create metric")
        .setSince("4.1")
        .setDeprecatedSince("5.3")
        .setPost(true)
        .setInternal(true)
        .setResponseExample(getClass().getResource("WebServiceTest/response-example.txt"))
        .setChangelog(
          new Change("6.4", "Last event"),
          new Change("6.0", "Old event"),
          new Change("4.5.6", "Very old event"))
        .setHandler(this::create);

      return newController;
    }

    void show(Request request, Response response) {
      showCalled = true;
    }

    void create(Request request, Response response) {
      createCalled = true;
    }
  }

  private NewAction newDefaultAction(NewController controller, String actionKey) {
    return controller.createAction(actionKey)
      .setDescription("default description")
      .setSince("5.3")
      .setResponseExample(getClass().getResource("WebServiceTest/response-example.txt"))
      .setHandler(mock(RequestHandler.class));
  }
}
