package org.sonar.api.server.ws;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.annotation.concurrent.Immutable;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.google.common.collect.ImmutableMap;

@Immutable
public
class Action {
  private static final Logger LOGGER = Loggers.get(Action.class);

  private final String key;
  private final String deprecatedKey;
  private final String path;
  private final String description;
  private final String since;
  private final String deprecatedSince;
  private final boolean post;
  private final boolean isInternal;
  private final RequestHandler handler;
  private final Map<String, Param> params;
  private final URL responseExample;
  private final List<Change> changelog;

  Action(Controller controller, NewAction newAction) {
    this.key = newAction.key;
    this.deprecatedKey = newAction.deprecatedKey;
    this.path = format("%s/%s", controller.path(), key);
    this.description = newAction.description;
    this.since = newAction.since;
    this.deprecatedSince = newAction.deprecatedSince;
    this.post = newAction.post;
    this.isInternal = newAction.isInternal;
    this.responseExample = newAction.responseExample;
    this.handler = newAction.handler;
    this.changelog = newAction.changelog;

    checkState(this.handler != null, "RequestHandler is not set on action %s", path);
    logWarningIf(isNullOrEmpty(this.description), "Description is not set on action " + path);
    logWarningIf(isNullOrEmpty(this.since), "Since is not set on action " + path);
    logWarningIf(!this.post && this.responseExample == null, "The response example is not set on action " + path);

    ImmutableMap.Builder<String, Param> paramsBuilder = ImmutableMap.builder();
    for (NewParam newParam : newAction.newParams.values()) {
      paramsBuilder.put(newParam.key, new Param(this, newParam));
    }
    this.params = paramsBuilder.build();
  }

  private static void logWarningIf(boolean condition, String message) {
    if (condition) {
      LOGGER.warn(message);
    }
  }

  public String key() {
    return key;
  }

  public String deprecatedKey() {
    return deprecatedKey;
  }

  public String path() {
    return path;
  }

  @CheckForNull
  public String description() {
    return description;
  }

  /**
   * Set if different than controller.
   */
  @CheckForNull
  public String since() {
    return since;
  }

  @CheckForNull
  public String deprecatedSince() {
    return deprecatedSince;
  }

  public boolean isPost() {
    return post;
  }

  /**
   * @see NewAction#setChangelog(Change...)
   * @since 6.4
   */
  public List<Change> changelog() {
    return changelog;
  }

  /**
   * @see NewAction#setInternal(boolean)
   */
  public boolean isInternal() {
    return isInternal;
  }

  public RequestHandler handler() {
    return handler;
  }

  /**
   * @see org.sonar.api.server.ws.NewAction#setResponseExample(java.net.URL)
   */
  @CheckForNull
  public URL responseExample() {
    return responseExample;
  }

  /**
   * @see org.sonar.api.server.ws.NewAction#setResponseExample(java.net.URL)
   */
  @CheckForNull
  public String responseExampleAsString() {
    try {
      if (responseExample != null) {
        return StringUtils.trim(IOUtils.toString(responseExample, StandardCharsets.UTF_8));
      }
      return null;
    } catch (IOException e) {
      throw new IllegalStateException("Fail to load " + responseExample, e);
    }
  }

  /**
   * @see org.sonar.api.server.ws.NewAction#setResponseExample(java.net.URL)
   */
  @CheckForNull
  public String responseExampleFormat() {
    if (responseExample != null) {
      return StringUtils.lowerCase(FilenameUtils.getExtension(responseExample.getFile()));
    }
    return null;
  }

  @CheckForNull
  public Param param(String key) {
    return params.get(key);
  }

  public Collection<Param> params() {
    return params.values();
  }

  @Override
  public String toString() {
    return path;
  }
}