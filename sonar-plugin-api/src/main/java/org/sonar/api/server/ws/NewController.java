package org.sonar.api.server.ws;

import static java.lang.String.format;

import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Maps;

public class NewController {
  final String path;
  String description;
  String since;
  final Map<String, NewAction> actions = Maps.newHashMap();

  /**
   * Create a new controller.
   * <br>
   * Structure of request URL is <code>http://&lt;server&gt;/&lt;controller path&gt;/&lt;action path&gt;?&lt;parameters&gt;</code>.
   *
   * @param path the controller path must not start or end with "/". It is recommended to start with "api/"
   *             and to use lower-case format with underscores, for example "api/coding_rules". Usual actions
   *             are "search", "list", "show", "create" and "delete".
   *             the plural form is recommended - ex: api/projects
   */
  public NewController(String path) {
    if (StringUtils.isBlank(path)) {
      throw new IllegalArgumentException("WS controller path must not be empty");
    }
    if (StringUtils.startsWith(path, "/") || StringUtils.endsWith(path, "/")) {
      throw new IllegalArgumentException("WS controller path must not start or end with slash: " + path);
    }
    this.path = path;
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

  private void add(NewAction action) {
    String actionKey = action.key;
    if (actions.containsKey(actionKey)) {
      throw new IllegalStateException(
        format("The action '%s' is defined multiple times in the web service '%s'", actionKey, path));
    }
    actions.put(actionKey, action);
  }
}