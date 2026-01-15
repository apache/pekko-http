/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model;

import java.util.Map;

import org.apache.pekko.japi.Pair;

/** Simple model for `application/x-www-form-urlencoded` form data. */
public final class FormData {

  private final Query fields;

  public FormData(Query fields) {
    this.fields = fields;
  }

  /**
   * Returns the fields of this FormData instance.
   *
   * @return the fields
   * @since 2.0.0
   */
  public Query getFields() {
    return fields;
  }

  /** Converts this FormData to a RequestEntity using UTF8 encoding. */
  public RequestEntity toEntity() {
    return HttpEntities.create(
        ContentTypes.APPLICATION_X_WWW_FORM_URLENCODED, fields.render(HttpCharsets.UTF_8));
  }

  /** Returns empty FormData. */
  public static final FormData EMPTY = new FormData(Query.EMPTY);

  /** Creates the FormData from the given parameters. */
  @SafeVarargs
  public static FormData create(Pair<String, String>... params) {
    return new FormData(Query.create(params));
  }

  /** Creates the FormData from the given parameters. */
  public static FormData create(Map<String, String> params) {
    return new FormData(Query.create(params));
  }

  /** Creates a FormData from the given parameters. */
  public static FormData create(Iterable<Pair<String, String>> params) {
    return new FormData(Query.create(params));
  }
}
