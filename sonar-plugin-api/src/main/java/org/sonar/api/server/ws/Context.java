package org.sonar.api.server.ws;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

public class Context {
  private final Map<String, Controller> controllers = Maps.newHashMap();

  public void register(NewController newController) {
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