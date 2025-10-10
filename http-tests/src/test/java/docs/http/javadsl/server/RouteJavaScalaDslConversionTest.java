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

package docs.http.javadsl.server;

import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Directives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.server.directives.RouteAdapter;
import org.apache.pekko.http.scaladsl.server.RequestContext;
import org.apache.pekko.http.scaladsl.server.RouteResult;
import scala.Function1;
import scala.concurrent.Future;

public class RouteJavaScalaDslConversionTest {

  void scalaToJava() {
    // #scala-to-java
    scala.Function1<
            org.apache.pekko.http.scaladsl.server.RequestContext,
            scala.concurrent.Future<org.apache.pekko.http.scaladsl.server.RouteResult>>
        scalaRoute = someRoute();

    org.apache.pekko.http.javadsl.server.Route javaRoute = RouteAdapter.asJava(scalaRoute);
    // #scala-to-java
  }

  void javaToScala() {
    // #java-to-scala
    Route javaRoute = Directives.get(() -> Directives.complete("okey"));

    scala.Function1<RequestContext, Future<RouteResult>> scalaRoute = javaRoute.asScala();
    // #java-to-scala
  }

  private Function1<RequestContext, Future<RouteResult>> someRoute() {
    return null;
  }
}
