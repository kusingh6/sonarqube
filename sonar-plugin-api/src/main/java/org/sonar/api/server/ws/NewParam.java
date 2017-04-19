package org.sonar.api.server.ws;

import static java.util.Arrays.asList;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.collect.Sets;

public class NewParam {
  String key;
  String since;
  String deprecatedSince;
  String deprecatedKey;
  String deprecatedKeySince;
  String description;
  String exampleValue;
  String defaultValue;
  boolean required = false;
  boolean internal = false;
  Set<String> possibleValues = null;

  NewParam(String key) {
    this.key = key;
  }

  /**
   * @since 5.3
   */
  public NewParam setSince(@Nullable String since) {
    this.since = since;
    return this;
  }

  /**
   * @since 5.3
   */
  public NewParam setDeprecatedSince(@Nullable String deprecatedSince) {
    this.deprecatedSince = deprecatedSince;
    return this;
  }

  /**
   * @since 5.0
   * @deprecated since 6.4
   * @see #setDeprecatedKey(String, String) 
   */
  @Deprecated
  public NewParam setDeprecatedKey(@Nullable String s) {
    this.deprecatedKey = s;
    return this;
  }

  /**
   *
   * @param deprecatedSince Version when the old key was replaced/deprecated. Ex: 5.6
   * @since 6.4
   */
  public NewParam setDeprecatedKey(@Nullable String key, @Nullable String deprecatedSince) {
    this.deprecatedKey = key;
    this.deprecatedKeySince = deprecatedSince;
    return this;
  }

  public NewParam setDescription(@Nullable String description) {
    this.description = description;
    return this;
  }

  /**
   * @since 5.6
   */
  public NewParam setDescription(@Nullable String description, Object... descriptionArgument) {
    this.description = description == null ? null : String.format(description, descriptionArgument);
    return this;
  }

  /**
   * Is the parameter required or optional ? Default value is false (optional).
   *
   * @since 4.4
   */
  public NewParam setRequired(boolean b) {
    this.required = b;
    return this;
  }

  /**
   * Internal parameters are not displayed by default in the web api documentation. They are
   * displayed only when the check-box "Show Internal API" is selected. By default
   * a parameter is not internal.
   *
   * @since 6.2
   */
  public NewParam setInternal(boolean b) {
    this.internal = b;
    return this;
  }

  /**
   * @since 4.4
   */
  public NewParam setExampleValue(@Nullable Object s) {
    this.exampleValue = (s != null) ? s.toString() : null;
    return this;
  }

  /**
   * Exhaustive list of possible values when it makes sense, for example
   * list of severities.
   *
   * @since 4.4
   */
  public NewParam setPossibleValues(@Nullable Object... values) {
    return setPossibleValues(values == null ? Collections.emptyList() : asList(values));
  }

  /**
   * @since 4.4
   */
  public NewParam setBooleanPossibleValues() {
    return setPossibleValues("true", "false", "yes", "no");
  }

  /**
   * Exhaustive list of possible values when it makes sense, for example
   * list of severities.
   *
   * @since 4.4
   */
  public NewParam setPossibleValues(@Nullable Collection<?> values) {
    if (values == null || values.isEmpty()) {
      this.possibleValues = null;
    } else {
      this.possibleValues = Sets.newLinkedHashSet();
      for (Object value : values) {
        this.possibleValues.add(value.toString());
      }
    }
    return this;
  }

  /**
   * @since 4.4
   */
  public NewParam setDefaultValue(@Nullable Object o) {
    this.defaultValue = (o != null) ? o.toString() : null;
    return this;
  }

  @Override
  public String toString() {
    return key;
  }
}