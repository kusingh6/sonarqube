package org.sonar.api.server.ws;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

public class Context {
  private final Map<String, Controller> controllers = Maps.newHashMap();

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
  public NewController createController(String path) {
    return new NewController(this, path);
  }

  void register(NewController newController) {
    if (controllers.containsKey(newController.path)) {
      throw new IllegalStateException(
        format("The web service '%s' is defined multiple times", newController.path));
    }
    controllers.put(newController.path, new Controller(newController));
  }

  @CheckForNull
  public Controller controller(String key) {
    return controllers.get(key);
  }

  public List<Controller> controllers() {
    return ImmutableList.copyOf(controllers.values());
  }
}