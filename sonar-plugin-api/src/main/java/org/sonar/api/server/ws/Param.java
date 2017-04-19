package org.sonar.api.server.ws;

import static java.lang.String.format;

import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.concurrent.Immutable;

@Immutable
public
class Param {
  public static final String TEXT_QUERY = "q";
  public static final String PAGE = "p";
  public static final String PAGE_SIZE = "ps";
  public static final String FIELDS = "f";
  public static final String SORT = "s";
  public static final String ASCENDING = "asc";
  public static final String FACETS = "facets";
  public static final String SELECTED = "selected";

  private final String key;
  private final String since;
  private final String deprecatedSince;
  private final String deprecatedKey;
  private final String deprecatedKeySince;
  private final String description;
  private final String exampleValue;
  private final String defaultValue;
  private final boolean required;
  private final boolean internal;
  private final Set<String> possibleValues;

  protected Param(Action action, NewParam newParam) {
    this.key = newParam.key;
    this.since = newParam.since;
    this.deprecatedSince = newParam.deprecatedSince;
    this.deprecatedKey = newParam.deprecatedKey;
    this.deprecatedKeySince = newParam.deprecatedKeySince;
    this.description = newParam.description;
    this.exampleValue = newParam.exampleValue;
    this.defaultValue = newParam.defaultValue;
    this.required = newParam.required;
    this.internal = newParam.internal;
    this.possibleValues = newParam.possibleValues;
    if (required && defaultValue != null) {
      throw new IllegalArgumentException(format("Default value must not be set on parameter '%s?%s' as it's marked as required", action, key));
    }
  }

  public String key() {
    return key;
  }

  /**
   * @since 5.3
   */
  @CheckForNull
  public String since() {
    return since;
  }

  /**
   * @since 5.3
   */
  @CheckForNull
  public String deprecatedSince() {
    return deprecatedSince;
  }

  /**
   * @since 5.0
   */
  @CheckForNull
  public String deprecatedKey() {
    return deprecatedKey;
  }

  /**
   * @since 6.4
   */
  @CheckForNull
  public String deprecatedKeySince() {
    return deprecatedKeySince;
  }

  @CheckForNull
  public String description() {
    return description;
  }

  /**
   * @since 4.4
   */
  @CheckForNull
  public String exampleValue() {
    return exampleValue;
  }

  /**
   * Is the parameter required or optional ?
   *
   * @since 4.4
   */
  public boolean isRequired() {
    return required;
  }

  /**
   * Is the parameter internal ?
   *
   * @since 6.2
   * @see NewParam#setInternal(boolean)
   */
  public boolean isInternal() {
    return internal;
  }

  /**
   * @since 4.4
   */
  @CheckForNull
  public Set<String> possibleValues() {
    return possibleValues;
  }

  /**
   * @since 4.4
   */
  @CheckForNull
  public String defaultValue() {
    return defaultValue;
  }

  @Override
  public String toString() {
    return key;
  }
}