package org.sonar.api.server.ws;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;

public class NewAction {
  final String key;
  String deprecatedKey;
  String description;
  String since;
  String deprecatedSince;
  boolean post = false;
  boolean isInternal = false;
  RequestHandler handler;
  Map<String, NewParam> newParams = Maps.newHashMap();
  URL responseExample = null;
  List<Change> changelog = new ArrayList<>();

  public NewAction(String key) {
    this.key = key;
  }

  public NewAction setDeprecatedKey(@Nullable String s) {
    this.deprecatedKey = s;
    return this;
  }

  /**
   * Used in Orchestrator
   */
  public NewAction setDescription(@Nullable String description) {
    this.description = description;
    return this;
  }

  /**
   * @since 5.6
   */
  public NewAction setDescription(@Nullable String description, Object... descriptionArgument) {
    this.description = description == null ? null : String.format(description, descriptionArgument);
    return this;
  }

  public NewAction setSince(@Nullable String s) {
    this.since = s;
    return this;
  }

  /**
   * @since 5.3
   */
  public NewAction setDeprecatedSince(@Nullable String deprecatedSince) {
    this.deprecatedSince = deprecatedSince;
    return this;
  }

  public NewAction setPost(boolean b) {
    this.post = b;
    return this;
  }

  /**
   * Internal actions are not displayed by default in the web api documentation. They are
   * displayed only when the check-box "Show Internal API" is selected. By default
   * an action is not internal.
   */
  public NewAction setInternal(boolean b) {
    this.isInternal = b;
    return this;
  }

  public NewAction setHandler(RequestHandler h) {
    this.handler = h;
    return this;
  }

  /**
   * Link to the document containing an example of response. Content must be UTF-8 encoded.
   * <br>
   * Example:
   * <pre>
   *   newAction.setResponseExample(getClass().getResource("/org/sonar/my-ws-response-example.json"));
   * </pre>
   *
   * @since 4.4
   */
  public NewAction setResponseExample(@Nullable URL url) {
    this.responseExample = url;
    return this;
  }

  /**
   * List of changes made to the contract or valuable insight. Example: changes to the response format.
   *
   * @since 6.4
   */
  public NewAction setChangelog(Change... changes) {
    this.changelog = Arrays.stream(requireNonNull(changes))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    return this;
  }

  public NewParam createParam(String paramKey) {
    checkState(!newParams.containsKey(paramKey), "The parameter '%s' is defined multiple times in the action '%s'", paramKey, key);
    NewParam newParam = new NewParam(paramKey);
    newParams.put(paramKey, newParam);
    return newParam;
  }

  /**
   * @deprecated since 4.4. Use {@link #createParam(String paramKey)} instead.
   */
  @Deprecated
  public NewAction createParam(String paramKey, @Nullable String description) {
    createParam(paramKey).setDescription(description);
    return this;
  }

  /**
   * Add predefined parameters related to pagination of results.
   */
  public NewAction addPagingParams(int defaultPageSize) {
    createParam(Param.PAGE)
      .setDescription("1-based page number")
      .setExampleValue("42")
      .setDeprecatedKey("pageIndex", "5.2")
      .setDefaultValue("1");

    createParam(Param.PAGE_SIZE)
      .setDescription("Page size. Must be greater than 0.")
      .setExampleValue("20")
      .setDeprecatedKey("pageSize", "5.2")
      .setDefaultValue(String.valueOf(defaultPageSize));
    return this;
  }

  /**
   * Add predefined parameters related to pagination of results with a maximum page size.
   * Note the maximum is a documentation only feature. It does not check anything.
   */
  public NewAction addPagingParams(int defaultPageSize, int maxPageSize) {
    addPageParam();
    addPageSize(defaultPageSize, maxPageSize);
    return this;
  }

  public NewAction addPageParam() {
    createParam(Param.PAGE)
      .setDescription("1-based page number")
      .setExampleValue("42")
      .setDeprecatedKey("pageIndex", "5.2")
      .setDefaultValue("1");
    return this;
  }

  public NewAction addPageSize(int defaultPageSize, int maxPageSize) {
    createParam(Param.PAGE_SIZE)
      .setDescription("Page size. Must be greater than 0 and less than " + maxPageSize)
      .setExampleValue("20")
      .setDeprecatedKey("pageSize", "5.2")
      .setDefaultValue(String.valueOf(defaultPageSize));
    return this;
  }

  /**
   * Creates the parameter {@link org.sonar.api.server.ws.Param#FIELDS}, which is
   * used to restrict the number of fields returned in JSON response.
   */
  public NewAction addFieldsParam(Collection<?> possibleValues) {
    createFieldsParam(possibleValues);
    return this;
  }

  public NewParam createFieldsParam(Collection<?> possibleValues) {
    return createParam(Param.FIELDS)
      .setDescription("Comma-separated list of the fields to be returned in response. All the fields are returned by default.")
      .setPossibleValues(possibleValues);
  }

  /**
   *
   * Creates the parameter {@link org.sonar.api.server.ws.Param#TEXT_QUERY}, which is
   * used to search for a subset of fields containing the supplied string.
   * <p>
   * The fields must be in the <strong>plural</strong> form (ex: "names", "keys").
   * </p>
   */
  public NewAction addSearchQuery(String exampleValue, String... pluralFields) {
    String actionDescription = format("Limit search to %s that contain the supplied string.", Joiner.on(" or ").join(pluralFields));
    createParam(Param.TEXT_QUERY)
      .setDescription(actionDescription)
      .setExampleValue(exampleValue);
    return this;
  }

  /**
   * Add predefined parameters related to sorting of results.
   */
  public <V> NewAction addSortParams(Collection<V> possibleValues, @Nullable V defaultValue, boolean defaultAscending) {
    createSortParams(possibleValues, defaultValue, defaultAscending);
    return this;
  }

  /**
   * Add predefined parameters related to sorting of results.
   */
  public <V> NewParam createSortParams(Collection<V> possibleValues, @Nullable V defaultValue, boolean defaultAscending) {
    createParam(Param.ASCENDING)
      .setDescription("Ascending sort")
      .setBooleanPossibleValues()
      .setDefaultValue(defaultAscending);

    return createParam(Param.SORT)
      .setDescription("Sort field")
      .setDeprecatedKey("sort", "5.4")
      .setDefaultValue(defaultValue)
      .setPossibleValues(possibleValues);
  }

  /**
   * Add 'selected=(selected|deselected|all)' for select-list oriented WS.
   */
  public NewAction addSelectionModeParam() {
    createParam(Param.SELECTED)
      .setDescription("Depending on the value, show only selected items (selected=selected), deselected items (selected=deselected), " +
        "or all items with their selection status (selected=all).")
      .setDefaultValue(SelectionMode.SELECTED.value())
      .setPossibleValues(SelectionMode.possibleValues());
    return this;
  }
}