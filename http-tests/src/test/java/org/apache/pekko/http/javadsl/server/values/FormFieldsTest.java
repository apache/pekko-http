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

package org.apache.pekko.http.javadsl.server.values;

import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.junit.Test;

import org.apache.pekko.http.javadsl.model.HttpCharsets;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.apache.pekko.japi.Pair;

import static org.apache.pekko.http.javadsl.server.Directives.*;

public class FormFieldsTest extends JUnitRouteTest {

  private Pair<String, String> param(String name, String value) {
    return Pair.create(name, value);
  }

  @SafeVarargs
  private final HttpRequest urlEncodedRequest(Pair<String, String>... params) {
    StringBuilder sb = new StringBuilder();
    boolean next = false;
    for (Pair<String, String> param : params) {
      if (next) {
        sb.append('&');
      }
      next = true;
      sb.append(param.first());
      sb.append('=');
      sb.append(param.second());
    }

    return HttpRequest.POST("/test")
        .withEntity(ContentTypes.APPLICATION_X_WWW_FORM_URLENCODED, sb.toString());
  }

  private HttpRequest singleParameterUrlEncodedRequest(String name, String value) {
    return urlEncodedRequest(param(name, value));
  }

  @Test
  public void testStringFormFieldExtraction() {
    TestRoute route = testRoute(formField("stringParam", value -> complete(value)));

    route
        .run(HttpRequest.create().withUri("/abc"))
        .assertStatusCode(400)
        .assertEntity("Request is missing required form field 'stringParam'");

    route
        .run(singleParameterUrlEncodedRequest("stringParam", "john"))
        .assertStatusCode(200)
        .assertEntity("john");
  }

  @Test
  public void testByteFormFieldExtraction() {
    TestRoute route =
        testRoute(
            formField(StringUnmarshallers.BYTE, "byteParam", value -> complete(value.toString())));

    route
        .run(HttpRequest.create().withUri("/abc"))
        .assertStatusCode(400)
        .assertEntity("Request is missing required form field 'byteParam'");

    route
        .run(singleParameterUrlEncodedRequest("byteParam", "test"))
        .assertStatusCode(400)
        .assertEntity(
            "The form field 'byteParam' was malformed:\n'test' is not a valid 8-bit signed integer value");

    route
        .run(singleParameterUrlEncodedRequest("byteParam", "1000"))
        .assertStatusCode(400)
        .assertEntity(
            "The form field 'byteParam' was malformed:\n'1000' is not a valid 8-bit signed integer value");

    route
        .run(singleParameterUrlEncodedRequest("byteParam", "48"))
        .assertStatusCode(200)
        .assertEntity("48");
  }

  @Test
  public void testOptionalIntFormFieldExtraction() {
    TestRoute route =
        testRoute(
            formFieldOptional(
                StringUnmarshallers.INTEGER,
                "optionalIntParam",
                value -> complete(value.toString())));

    route
        .run(HttpRequest.create().withUri("/abc"))
        .assertStatusCode(200)
        .assertEntity("Optional.empty");

    route
        .run(singleParameterUrlEncodedRequest("optionalIntParam", "23"))
        .assertStatusCode(200)
        .assertEntity("Optional[23]");
  }
}
