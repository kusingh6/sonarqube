package org.sonar.api.server.ws;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.annotation.concurrent.Immutable;

import com.google.common.collect.ImmutableMap;

@Immutable
public
class Controller {
  private final String path;
  private final String description;
  private final String since;
  private final Map<String, Action> actions;

  Controller(NewController newController) {
    checkState(!newController.actions.isEmpty(), "At least one action must be declared in the web service '%s'", newController.path);
    this.path = newController.path;
    this.description = newController.description;
    this.since = newController.since;
    ImmutableMap.Builder<String, Action> mapBuilder = ImmutableMap.builder();
    for (NewAction newAction : newController.actions.values()) {
      mapBuilder.put(newAction.key, new Action(this, newAction));
    }
    this.actions = mapBuilder.build();
  }

  public String path() {
    return path;
  }

  @CheckForNull
  public String description() {
    return description;
  }

  @CheckForNull
  public String since() {
    return since;
  }

  @CheckForNull
  public Action action(String actionKey) {
    return actions.get(actionKey);
  }

  public Collection<Action> actions() {
    return actions.values();
  }

  /**
   * Returns true if all the actions are for internal use
   *
   * @see org.sonar.api.server.ws.Action#isInternal()
   * @since 4.3
   */
  public boolean isInternal() {
    for (Action action : actions()) {
      if (!action.isInternal()) {
        return false;
      }
    }
    return true;
  }
}