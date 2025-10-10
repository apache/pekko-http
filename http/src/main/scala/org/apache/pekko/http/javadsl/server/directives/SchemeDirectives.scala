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

package org.apache.pekko.http.javadsl.server.directives

import java.util.function.{ Function => JFunction }
import java.util.function.Supplier

import org.apache.pekko
import pekko.http.javadsl.server.Route
import pekko.http.scaladsl.server.{ Directives => D }

abstract class SchemeDirectives extends RouteDirectives {

  /**
   * Extracts the Uri scheme from the request.
   */
  def extractScheme(inner: JFunction[String, Route]): Route = RouteAdapter {
    D.extractScheme { s => inner.apply(s).delegate }
  }

  /**
   * Rejects all requests whose Uri scheme does not match the given one.
   */
  def scheme(name: String, inner: Supplier[Route]): Route = RouteAdapter {
    D.scheme(name) { inner.get().delegate }
  }
}
