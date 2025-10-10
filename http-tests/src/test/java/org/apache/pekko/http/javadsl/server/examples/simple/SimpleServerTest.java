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

package org.apache.pekko.http.javadsl.server.examples.simple;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.testkit.*;

import org.junit.Test;

public class SimpleServerTest extends JUnitRouteTest {
  TestRoute route = testRoute(new SimpleServerApp().createRoute());

  @Test
  public void testAdd() {
    TestRouteResult response = route.run(HttpRequest.GET("/add?x=42&y=23"));

    response.assertStatusCode(200).assertEntity("42 + 23 = 65");
  }

  @Test
  public void testMultiplyAsync() {
    TestRouteResult response = route.run(HttpRequest.GET("/multiplyAsync/42/23"));

    response.assertStatusCode(200).assertEntity("42 * 23 = 966");
  }

  @Test
  public void testPostWithBody() {
    TestRouteResult response = route.run(HttpRequest.POST("/hello").withEntity("John"));

    response.assertStatusCode(200).assertEntity("Hello John!");
  }
}
