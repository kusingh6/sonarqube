package org.sonar.api.server.ws;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Arrays.asList;

import java.util.Collection;
import java.util.Map;

import com.google.common.collect.Maps;

public enum SelectionMode {
  SELECTED("selected"), DESELECTED("deselected"), ALL("all");

  private final String paramValue;

  private static final Map<String, SelectionMode> BY_VALUE = Maps.uniqueIndex(asList(values()), input -> input.paramValue);

  SelectionMode(String paramValue) {
    this.paramValue = paramValue;
  }

  public String value() {
    return paramValue;
  }

  public static SelectionMode fromParam(String paramValue) {
    checkArgument(BY_VALUE.containsKey(paramValue));
    return BY_VALUE.get(paramValue);
  }

  public static Collection<String> possibleValues() {
    return BY_VALUE.keySet();
  }
}