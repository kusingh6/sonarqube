package org.sonar.api.server.ws;

import static java.lang.String.format;

import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Maps;

public class NewController {
  private final Context context;
  final String path;
  String description;
  String since;
  final Map<String, NewAction> actions = Maps.newHashMap();

  NewController(Context context, String path) {
    if (StringUtils.isBlank(path)) {
      throw new IllegalArgumentException("WS controller path must not be empty");
    }
    if (StringUtils.startsWith(path, "/") || StringUtils.endsWith(path, "/")) {
      throw new IllegalArgumentException("WS controller path must not start or end with slash: " + path);
    }
    this.context = context;
    this.path = path;
  }

  /**
   * Important - this method must be called in order to apply changes and make the
   * controller available in {@link org.sonar.api.server.ws.Context#controllers()}
   */
  public void done() {
    context.register(this);
  }

  /**
   * Optional description (accept HTML)
   */
  public NewController setDescription(@Nullable String s) {
    this.description = s;
    return this;
  }

  /**
   * Optional version when the controller was created
   */
  public NewController setSince(@Nullable String s) {
    this.since = s;
    return this;
  }

  public NewAction createAction(String actionKey) {
    if (actions.containsKey(actionKey)) {
      throw new IllegalStateException(
        format("The action '%s' is defined multiple times in the web service '%s'", actionKey, path));
    }
    NewAction action = new NewAction(actionKey);
    actions.put(actionKey, action);
    return action;
  }
}