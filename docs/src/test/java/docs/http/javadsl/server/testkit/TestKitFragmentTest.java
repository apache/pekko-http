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

package docs.http.javadsl.server.testkit;

// #source-quote
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.junit.Test;

public class TestKitFragmentTest extends JUnitRouteTest {
  class FragmentTester extends AllDirectives {
    public Route createRoute(Route fragment) {
      return pathPrefix("test", () -> fragment);
    }
  }

  TestRoute fragment = testRoute(new MyAppFragment().createRoute());
  TestRoute testRoute = testRoute(new FragmentTester().createRoute(fragment.underlying()));

  @Test
  public void testFragment() {
    testRoute
        .run(HttpRequest.GET("/test"))
        .assertStatusCode(200)
        .assertEntity("Fragments of imagination");

    testRoute.run(HttpRequest.PUT("/test")).assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
  }
}
// #source-quote
