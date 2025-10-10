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

import org.junit.Test;

import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;

import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.model.headers.Age;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;

import static org.apache.pekko.http.javadsl.server.Directives.*;

public class HeadersTest extends JUnitRouteTest {
  final RawHeader testHeaderInstance = RawHeader.create("X-Test-Header", "abcdef-test");
  final Age ageHeaderInstance = Age.create(1000);

  @Test
  public void testValueByName() {
    TestRoute route = testRoute(headerValueByName("X-Test-Header", value -> complete(value)));

    route
        .run(HttpRequest.create())
        .assertStatusCode(400)
        .assertEntity("Request is missing required HTTP header 'X-Test-Header'");

    route
        .run(HttpRequest.create().addHeader(testHeaderInstance))
        .assertStatusCode(200)
        .assertEntity("abcdef-test");
  }

  @Test
  public void testOptionalValueByName() {
    TestRoute route =
        testRoute(optionalHeaderValueByName("X-Test-Header", value -> complete(value.toString())));

    route.run(HttpRequest.create()).assertStatusCode(200).assertEntity("Optional.empty");

    route
        .run(HttpRequest.create().addHeader(testHeaderInstance))
        .assertStatusCode(200)
        .assertEntity("Optional[abcdef-test]");
  }

  @Test
  public void testValueByClass() {
    TestRoute route = testRoute(headerValueByType(Age.class, age -> complete(age.value())));

    route
        .run(HttpRequest.create())
        .assertStatusCode(400)
        .assertEntity("Request is missing required HTTP header 'Age'");

    route
        .run(HttpRequest.create().addHeader(ageHeaderInstance))
        .assertStatusCode(200)
        .assertEntity("1000");
  }

  @Test
  public void testOptionalValueByClass() {
    TestRoute route =
        testRoute(optionalHeaderValueByType(Age.class, age -> complete(age.toString())));

    route.run(HttpRequest.create()).assertStatusCode(200).assertEntity("Optional.empty");

    route
        .run(HttpRequest.create().addHeader(ageHeaderInstance))
        .assertStatusCode(200)
        .assertEntity("Optional[Age: 1000]");
  }
}
